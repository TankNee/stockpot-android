package cn.neeto.stockpot.core.net

import cn.neeto.stockpot.core.data.LedgerStore
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.settings.Account
import cn.neeto.stockpot.core.settings.AuthStore
import cn.neeto.stockpot.core.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** 登录一次可能要多走一步：开着两步验证的账号，密码对了还得再输一次码。 */
sealed interface LoginOutcome {
    data object Done : LoginOutcome

    /** 五分钟内拿这张票据 + 验证码换令牌。 */
    data class NeedCode(val ticket: String) : LoginOutcome
}

/**
 * 账本同步。
 *
 * 和 Guodelist 那种「逐条 push」不同，这里的账本本来就是一整份 JSON，
 * 所以同步的单位就是这份文档：拉下来 → 合并 → 推上去，
 * 服务端用 `rev` 做乐观锁，撞车了就把云端那份带回来再合一次。
 *
 * 编辑之后 4 秒推一次；进前台、点「立即同步」也会同步。
 * 没登录时所有入口都是空操作，本地记账完全不受影响。
 */
class SyncManager(
    private val api: ApiClient,
    private val store: LedgerStore,
    private val auth: AuthStore,
    private val settings: SettingsStore,
    private val state: SyncStateStore,
) {

    enum class Status { IDLE, SYNCING, OK, ERROR }

    data class SyncUi(
        val signedIn: Boolean = false,
        val account: Account = Account(),
        val status: Status = Status.IDLE,
        val error: String? = null,
        /** ISO 时间串，空表示还没同步过 */
        val lastSync: String = "",
    )

    private val _ui = MutableStateFlow(
        SyncUi(
            signedIn = auth.token.isNotBlank(),
            account = if (auth.token.isNotBlank()) auth.account else Account(),
            lastSync = auth.lastSync,
        ),
    )
    val ui: StateFlow<SyncUi> = _ui.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pushJob: Job? = null

    /** 一轮同步跑完时又冒出改动，用它补一次（不动 pushJob，见 scheduleFollowUp）。 */
    private var followUpJob: Job? = null

    /**
     * 一轮同步只跑一份，后来的人等它的结果。
     *
     * 升级完第一次打开应用时最挤：bootstrap、回到前台的补偿同步、
     * 用户的下拉刷新几乎同时到达。以前抢不到锁就直接回 false，
     * 界面于是报「云端这次没对上」。
     */
    private val round = SingleFlight<Boolean>()

    /**
     * 本机改动的计数器。同步开始时记一份，结束时对一下：
     * 中途要是又改了，就别把「已同步」的结论盖到那些新改动上。
     */
    @Volatile
    private var localChanges = 0L

    /** 连续失败了几次。推成功或者又有新改动时归零。 */
    @Volatile
    private var retryAttempt = 0

    val signedIn: Boolean get() = auth.token.isNotBlank()

    /** 应用启动时调用：已经登录就顺手对一次账。 */
    fun start() {
        if (!signedIn) return
        scope.launch { bootstrap() }
    }

    suspend fun bootstrap() {
        if (!signedIn) return
        try {
            state.bind(auth.account.id)
            _ui.update { it.copy(signedIn = true, account = auth.account, lastSync = auth.lastSync) }
            val me = api.me(settings.serverUrl, auth.token)
            auth.updateAccount(me.user.toAccount())
            _ui.update { it.copy(account = auth.account) }
            syncNow()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 令牌过期：清掉登录态，回到「本地模式」，别拿一个永远失败的凭据反复试
            if (error is ApiException && error.status == 401) {
                forgetSession()
            } else {
                failed(error)
            }
        }
    }

    suspend fun login(email: String, password: String): Result<LoginOutcome> = runCatching {
        val response = api.login(settings.serverUrl, email.trim(), password)
        if (response.twoFactor) {
            if (response.ticket.isBlank()) throw ApiException(401, "服务器要求两步验证，再试一次")
            LoginOutcome.NeedCode(response.ticket)
        } else {
            accept(response.token, response.user)
            LoginOutcome.Done
        }
    }

    /** 两步验证的第二步。验证码可以是认证器上的六位数，也可以是恢复码。 */
    suspend fun loginTwoFactor(ticket: String, code: String): Result<Unit> = runCatching {
        val response = api.loginTwoFactor(settings.serverUrl, ticket, code.trim())
        accept(response.token, response.user)
    }

    suspend fun register(email: String, password: String, name: String): Result<Unit> = runCatching {
        val response = api.register(settings.serverUrl, email.trim(), password, name.trim())
        accept(response.token, response.user)
    }

    private suspend fun accept(token: String, dto: AccountDto) {
        val account = dto.toAccount()
        auth.save(token, account)
        state.bind(account.id)
        _ui.update {
            it.copy(signedIn = true, account = account, status = Status.SYNCING, error = null, lastSync = "")
        }
        // 登录完立刻对齐一次；失败也不把登录踢掉，下次编辑还会再试
        syncNow()
    }

    /* ------------------------------ 账号资料 ------------------------------ */

    /** 改完立刻记到本地：设置页和账号页读的都是这份。 */
    private fun adopt(dto: AccountDto) {
        val account = dto.toAccount()
        auth.updateAccount(account)
        _ui.update { it.copy(account = account) }
    }

    suspend fun updateName(name: String): Result<Unit> = runCatching {
        adopt(api.updateName(settings.serverUrl, auth.token, name).user)
    }

    suspend fun changePassword(current: String, next: String): Result<Unit> = runCatching {
        adopt(api.changePassword(settings.serverUrl, auth.token, current, next).user)
    }

    suspend fun uploadAvatar(dataUrl: String): Result<Unit> = runCatching {
        adopt(api.putAvatar(settings.serverUrl, auth.token, dataUrl).user)
    }

    suspend fun removeAvatar(): Result<Unit> = runCatching {
        adopt(api.deleteAvatar(settings.serverUrl, auth.token).user)
    }

    suspend fun avatarBytes(): ByteArray? {
        if (!signedIn) return null
        return runCatching { api.avatarBytes(settings.serverUrl, auth.token) }.getOrNull()
    }

    /** 第一步：拿密钥和二维码，这时候还没生效。 */
    suspend fun setupTwoFactor(): Result<TwoFactorSetup> = runCatching {
        api.setupTwoFactor(settings.serverUrl, auth.token)
    }

    /** 第二步：校验六位码，成功才会拿到一组只露一次的恢复码。 */
    suspend fun enableTwoFactor(code: String): Result<List<String>> = runCatching {
        val enabled = api.enableTwoFactor(settings.serverUrl, auth.token, code.trim())
        adopt(enabled.user)
        enabled.recoveryCodes
    }

    suspend fun disableTwoFactor(password: String): Result<Unit> = runCatching {
        adopt(api.disableTwoFactor(settings.serverUrl, auth.token, password).user)
    }

    /**
     * 立刻同步一次。
     *
     * 已经有一轮在跑时不排队再来一轮，而是等那一轮——返回的就是它真实的结果。
     * 这样「正在同步」不会被当成「同步失败」，下拉刷新的文案也就不会撒谎。
     */
    suspend fun syncNow(): Boolean {
        if (!signedIn) return false
        return try {
            round.run { runRound() }
        } catch (error: CancellationException) {
            // 页面没了/用户退出，取消照常往外抛，别把 CoroutineScope 的取消吞掉
            throw error
        } catch (error: SingleFlight.Abandoned) {
            // 领跑的那一轮被取消了：这一轮确实没完成，但不必弹错误文案
            false
        } catch (error: Exception) {
            failed(error)
            false
        }
    }

    /**
     * 真正干活的那一轮：拉下来 → 合并 → 推上去，撞车了就再来一次。
     *
     * 整轮都放到 IO 线程：合并和「整份账本变成 JSON」是纯 CPU 活，五万条账的时候
     * 加起来一百毫秒上下。下拉刷新是从界面的 viewModelScope 里调的，不切线程
     * 就会卡在主线程上——转圈动画一顿一顿，正是「刷新要等很久」的来源。
     */
    private suspend fun runRound(): Boolean = withContext<Boolean>(Dispatchers.IO) {
        _ui.update { it.copy(status = Status.SYNCING, error = null) }
        val stamp = localChanges
        var doc = api.pull(settings.serverUrl, auth.token)
        var attempt = 0
        var synced = false
        while (!synced) {
            val merged = mergeWith(doc)
            if (merged != store.data.value) {
                store.replaceAll(merged, fromRemote = true)
            }
            val payload = LedgerPush(
                baseRev = doc.rev,
                ledger = store.json.encodeToJsonElement(Ledger.serializer(), merged) as JsonObject,
                deleted = state.deleted(),
            )
            when (val result = api.push(settings.serverUrl, auth.token, payload)) {
                is PushResult.Ok -> {
                    settle(result.doc, stamp)
                    synced = true
                }
                is PushResult.Conflict -> {
                    // 别的设备先写过：拿回云端那份，合完再推。给它两次机会，够了。
                    if (++attempt > 2) throw ApiException(409, "云端刚被别的设备改过，稍后再试")
                    doc = result.doc
                }
            }
        }
        true
    }

    /**
     * 云端那份和本地合起来。
     *
     * 本机自上次同步后没动过（[AuthStore.dirty] 为 false）就以云端为准，
     * 否则以本机为准——刚记的一笔不该被一份旧快照盖回去。
     */
    private fun mergeWith(doc: LedgerDoc): Ledger {
        state.mergeDeleted(doc.deleted)
        val local = store.data.value
        val remote = doc.ledger?.let { element ->
            store.parseJson(element.toString())
        } ?: return local
        return LedgerMerge.merge(
            local = local,
            remote = remote,
            preferRemote = !auth.dirty,
            deleted = state.deleted(),
        )
    }

    private fun settle(doc: LedgerDoc, stamp: Long) {
        retryAttempt = 0
        val syncedAt = doc.updatedAt.orEmpty()
        state.onSynced(doc.rev, syncedAt)
        // 同步途中又记了一笔：账已经推上去了（那笔在 payload 里），但别急着清脏标记。
        //
        // 这里必须补排一次：这一轮的 pushJob 已经跑完，不会再醒；而「等下一次编辑」
        // 不靠谱——用户记完最后一笔就放下手机了。以前靠下一次 tryLock 失败 + 退避重试
        // 兜住这种情况，现在同步改成排队了，得显式接上。
        if (localChanges == stamp) {
            auth.dirty = false
        } else {
            scheduleFollowUp()
        }
        auth.lastSync = syncedAt
        _ui.update {
            it.copy(
                signedIn = true,
                account = auth.account,
                status = Status.OK,
                error = null,
                lastSync = syncedAt,
            )
        }
    }

    /**
     * 一轮同步跑完时本机又改过，就再补一次。
     *
     * 单独一条协程，不动 [pushJob]：这里常常是在 pushJob 自己里面被调到的，
     * 取消它等于把自己掐掉。
     */
    private fun scheduleFollowUp() {
        followUpJob?.cancel()
        followUpJob = scope.launch {
            delay(FOLLOW_UP_DELAY)
            if (signedIn && auth.dirty) syncNow()
        }
    }

    private fun failed(error: Exception) {
        if (error is ApiException && error.status == 401) {
            forgetSession()
            return
        }
        _ui.update {
            it.copy(
                status = Status.ERROR,
                error = error.message ?: error::class.java.simpleName,
            )
        }
    }

    /**
     * 本地改动之后调：记墓碑、标脏、4 秒后推一次。
     *
     * 墓碑不管有没有登录都记——不然"没登录时删掉的账"，登录后会被云端那份合回来。
     */
    fun onLocalChange(before: Ledger, after: Ledger) {
        localChanges++
        retryAttempt = 0
        state.recordRemovals(before, after)
        if (!signedIn) return
        auth.dirty = true
        schedulePush()
    }

    fun schedulePush(delayMs: Long = PUSH_DELAY) {
        if (!signedIn) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(delayMs)
            val ok = syncNow()
            // 推失败就按 8s / 30s / 2min 退避再试。本地改动不能因为一次断网
            // 就一直躺在机器里，等用户「下次编辑」是不靠谱的
            if (!ok && signedIn && retryAttempt < SyncBackoff.maxAttempts) {
                val wait = SyncBackoff.next(++retryAttempt)
                if (wait != null) {
                    log("push 失败，${wait / 1000}s 后重试（第 $retryAttempt 次）")
                    schedulePush(wait)
                }
            }
        }
    }

    private fun log(message: String) {
        android.util.Log.d("StockpotSync", message)
    }

    /** 清空云端那份（本地不动）。 */
    suspend fun clearRemote(): Result<Unit> = runCatching {
        if (!signedIn) return@runCatching
        val doc = api.clearRemote(settings.serverUrl, auth.token)
        val syncedAt = doc.updatedAt.orEmpty()
        state.onSynced(doc.rev, syncedAt)
        auth.dirty = false
        auth.lastSync = syncedAt
        _ui.update { it.copy(status = Status.OK, error = null, lastSync = syncedAt) }
    }

    fun logout() {
        forgetSession()
    }

    private fun forgetSession() {
        pushJob?.cancel()
        followUpJob?.cancel()
        auth.clear()
        state.reset()
        _ui.value = SyncUi()
    }

    fun setServerUrl(url: String) = settings.setServerUrl(url)

    val serverUrl: String get() = settings.serverUrl

    private companion object {
        /** 打字的时候别一直推：停下来 4 秒再同步。 */
        const val PUSH_DELAY = 4_000L

        /** 补推不用再等 4 秒：改动已经在手里了，1.2 秒够把连点的那几笔并进来。 */
        const val FOLLOW_UP_DELAY = 1_200L
    }
}

/** 服务端的账号 → 本地那份。昵称、头像、两步验证都跟着一起存下来。 */
private fun AccountDto.toAccount() = Account(
    id = id,
    email = email,
    name = name,
    role = role,
    hasAvatar = hasAvatar,
    avatarAt = avatarAt,
    twoFactor = twoFactor,
)
