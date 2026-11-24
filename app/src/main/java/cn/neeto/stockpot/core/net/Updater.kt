package cn.neeto.stockpot.core.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.neeto.stockpot.BuildConfig
import cn.neeto.stockpot.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * 应用内更新。
 *
 * 服务器上放了安装包（管理台发的），这里只做三件事：问一句有没有新版、
 * 把包装下来校验 sha256、交给系统安装器。整个过程不经过应用商店，
 * 也不要求登录——连账号都没有的人照样能升级。
 *
 * 下载下来的包放在应用自己的外部目录 `updates/` 里，系统安装器读得到，
 * 卸载应用时跟着删掉，不会在用户存储里留垃圾。
 */
class Updater(
    private val context: Context,
    private val api: ApiClient,
    private val settings: SettingsStore,
) {

    enum class Phase {
        /** 没在检查，也没有待处理的版本 */
        IDLE,
        CHECKING,
        /** 有新版本，等着用户点下载 */
        READY,
        DOWNLOADING,
        /** 包已经在本地了，点一下就能装 */
        DOWNLOADED,
        FAILED,
    }

    data class State(
        val phase: Phase = Phase.IDLE,
        val release: AppRelease? = null,
        /** 0..1，拿不到总长度时一直是 0 */
        val progress: Float = 0f,
        val error: String? = null,
        /** ISO 时间，设置页显示「上次检查」 */
        val checkedAt: String = "",
        /** 系统不让装未知来源，得先去设置里放开 */
        val needPermission: Boolean = false,
    ) {
        val currentVersion: String get() = BuildConfig.VERSION_NAME
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /**
     * 问一次服务器。`silent` 是自动检查：网络不通、服务器没配好都不该弹错，
     * 把上一次的结果留着就行。
     */
    fun check(silent: Boolean = true) {
        if (_state.value.phase == Phase.DOWNLOADING) return
        job?.cancel()
        job = scope.launch {
            _state.update { it.copy(phase = Phase.CHECKING, error = null, needPermission = false) }
            try {
                val response = api.latestRelease(settings.serverUrl, BuildConfig.VERSION_CODE)
                val release = response.latest
                val newer = release != null && release.versionCode > BuildConfig.VERSION_CODE
                if (!newer) {
                    _state.value = State(phase = Phase.IDLE, checkedAt = Instant.now().toString())
                    prune(null)
                    return@launch
                }
                val cached = apkFile(release).exists()
                _state.value = State(
                    phase = if (cached) Phase.DOWNLOADED else Phase.READY,
                    release = release,
                    progress = if (cached) 1f else 0f,
                    checkedAt = Instant.now().toString(),
                )
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        phase = if (silent) Phase.IDLE else Phase.FAILED,
                        error = error.message ?: "检查更新失败",
                    )
                }
            }
        }
    }

    /** 下载并校验。校验不过就当没下过，绝不让安装器去装一个可能被换过的包。 */
    fun download() {
        val release = _state.value.release ?: return
        if (_state.value.phase == Phase.DOWNLOADING) return
        job?.cancel()
        job = scope.launch {
            _state.update { it.copy(phase = Phase.DOWNLOADING, progress = 0f, error = null) }
            val target = File(updatesDir(), fileName(release))
            val temp = File(target.parentFile, target.name + ".part")
            try {
                val sha = api.downloadApk(release.downloadUrl, temp) { ratio ->
                    _state.update { it.copy(progress = ratio) }
                }
                if (release.sha256.isNotBlank() && !sha.equals(release.sha256, ignoreCase = true)) {
                    throw IOException("安装包校验不通过，可能传坏了")
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) throw IOException("安装包保存失败")
                prune(target)
                _state.update { it.copy(phase = Phase.DOWNLOADED, progress = 1f) }
            } catch (error: Exception) {
                temp.delete()
                _state.update {
                    it.copy(phase = Phase.FAILED, error = error.message ?: "下载失败")
                }
            }
        }
    }

    /**
     * 拉起系统安装器。返回 false 有两种情况：
     * 系统不允许装未知来源（[State.needPermission] 会置上，并且已经跳去设置页），
     * 或者包不在了 / 安装器打不开。
     */
    fun install(): Boolean {
        val release = _state.value.release ?: return false
        val file = apkFile(release)
        if (!file.exists()) {
            _state.update { it.copy(phase = Phase.READY, error = "安装包不在了，重新下一次") }
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            _state.update { it.copy(needPermission = true, error = "系统里给 Stockpot 打开「安装未知应用」再回来") }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse { error ->
            _state.update { it.copy(error = "打不开安装器：${error.message ?: "未知原因"}") }
            false
        }
    }

    /** 「以后再说」：把结果收起来，下次启动还会再查一遍。 */
    fun dismiss() {
        job?.cancel()
        _state.value = _state.value.copy(phase = Phase.IDLE, release = null, progress = 0f, error = null)
    }

    /* ------------------------------ 文件 ------------------------------ */

    private fun updatesDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "updates").apply { mkdirs() }
    }

    private fun fileName(release: AppRelease): String =
        "stockpot-${release.versionName.ifBlank { release.versionCode.toString() }}.apk"

    private fun apkFile(release: AppRelease): File = File(updatesDir(), fileName(release))

    /** 只留当前这一版，别的（旧版本、下了一半的）都清掉。 */
    private fun prune(keep: File?) {
        runCatching {
            updatesDir().listFiles()?.forEach { file ->
                if (keep == null || file.absolutePath != keep.absolutePath) file.delete()
            }
        }
    }
}
