package cn.neeto.stockpot.core.net

import android.content.Context
import android.util.Log
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.model.Ledger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 同步用的辅助状态：墓碑、云端版本号、上次同步时间。
 *
 * 单独存一个文件，不塞进 `ledger.json`——桌面端只认那份账本，
 * 多出来的字段虽然会被忽略，但没必要让两边的格式为了同步而互相牵制。
 */
@Serializable
data class SyncState(
    /** 这份状态属于哪个账号；换账号就整份重来 */
    val accountId: String = "",
    /** `"entries:e_xxx" -> 删除时间` */
    val deleted: Map<String, String> = emptyMap(),
    /** 手上这份对应云端的第几版 */
    val lastRev: Int = 0,
    val lastSync: String = "",
)

class SyncStateStore(context: Context) {

    private val file = File(context.filesDir, "sync-state.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var saveJob: Job? = null

    private var state: SyncState = read()

    val current: SyncState get() = synchronized(lock) { state }

    /** 墓碑只保留最近这么久；再老的记录基本不会有人还留着那份旧账本。 */
    private val keepMillis = 180L * 24 * 60 * 60 * 1000

    fun deleted(): Map<String, String> = current.deleted

    fun bind(accountId: String) {
        synchronized(lock) {
            if (state.accountId == accountId) return
            // 从没登录过（accountId 为空）时，登录前删掉的那些要留着：
            // 否则登录后云端那份会把这些账又合回来。换账号才整份清空。
            val keep = if (state.accountId.isEmpty()) state.deleted else emptyMap()
            state = SyncState(accountId = accountId, deleted = keep)
        }
        scheduleSave()
    }

    fun reset() {
        synchronized(lock) {
            state = SyncState()
            scheduleSave()
        }
    }

    fun onSynced(rev: Int, updatedAt: String) {
        synchronized(lock) { state = state.copy(lastRev = rev, lastSync = updatedAt) }
        scheduleSave()
    }

    /** 云端带回来的墓碑一律收下；本地已有的那些推上去时一起带过去。 */
    fun mergeDeleted(remote: Map<String, String>) {
        if (remote.isEmpty()) return
        synchronized(lock) {
            val next = state.deleted.toMutableMap()
            var changed = false
            remote.forEach { (key, ts) ->
                val old = next[key]
                if (old == null || ts > old) {
                    next[key] = ts
                    changed = true
                }
            }
            if (changed) state = state.copy(deleted = next)
        }
        if (state.deleted.isNotEmpty()) scheduleSave()
    }

    /**
     * 每次本地改动后，比对前后两份账本，把"消失了的记录"记成墓碑。
     *
     * 这样删除逻辑一行都不用改：删除账目、删钱包、删项目、清空流水、
     * 载入示例数据，全都自动记上，也不会有哪个入口漏掉。
     */
    fun recordRemovals(before: Ledger, after: Ledger) {
        val stamp = Fmt.stamp()
        val fresh = LinkedHashMap<String, String>()
        collect(before.entries, after.entries, LedgerMerge.ENTRIES) { it.id }.forEach { fresh[it] = stamp }
        collect(before.projects, after.projects, LedgerMerge.PROJECTS) { it.id }.forEach { fresh[it] = stamp }
        collect(before.wallets, after.wallets, LedgerMerge.WALLETS) { it.id }.forEach { fresh[it] = stamp }
        collect(before.stockAccounts, after.stockAccounts, LedgerMerge.STOCK_ACCOUNTS) { it.id }
            .forEach { fresh[it] = stamp }
        collect(before.stocks, after.stocks, LedgerMerge.STOCKS) { it.id }.forEach { fresh[it] = stamp }
        collect(before.imports, after.imports, LedgerMerge.IMPORTS) { it.id }.forEach { fresh[it] = stamp }
        if (fresh.isEmpty()) return
        synchronized(lock) {
            state = state.copy(deleted = prune(state.deleted + fresh))
        }
        scheduleSave()
    }

    private fun <T> collect(before: List<T>, after: List<T>, collection: String, id: (T) -> String): List<String> {
        if (before.isEmpty()) return emptyList()
        val alive = after.mapTo(HashSet(after.size)) { id(it) }
        return before.asSequence()
            .map { id(it) }
            .filter { it.isNotEmpty() && it !in alive }
            .map { "$collection:$it" }
            .toList()
    }

    private fun prune(map: Map<String, String>): Map<String, String> {
        val cutoff = java.time.Instant.now().minusMillis(keepMillis).toString()
        val kept = map.filterValues { it >= cutoff }
        if (kept.size <= MAX_TOMBSTONES) return kept
        // 实在太多就留最新的那些，删掉的记录不会因为几十万条墓碑而变慢
        return kept.entries
            .sortedByDescending { it.value }
            .take(MAX_TOMBSTONES)
            .associate { it.key to it.value }
    }

    private fun read(): SyncState {
        if (!file.exists()) return SyncState()
        return runCatching { json.decodeFromString<SyncState>(file.readText()) }
            .onFailure { Log.w(TAG, "读取同步状态失败", it) }
            .getOrDefault(SyncState())
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            persist()
        }
    }

    fun persist() {
        val body = synchronized(lock) {
            runCatching { json.encodeToString(SyncState.serializer(), state) }.getOrNull()
        } ?: return
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(body)
            if (!temp.renameTo(file)) {
                file.writeText(body)
                temp.delete()
            }
        }.onFailure { Log.e(TAG, "保存同步状态失败", it) }
    }

    private companion object {
        const val TAG = "SyncState"
        const val MAX_TOMBSTONES = 5000
    }
}
