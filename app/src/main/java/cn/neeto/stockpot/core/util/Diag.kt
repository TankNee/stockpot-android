package cn.neeto.stockpot.core.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import cn.neeto.stockpot.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断日志：内存里留最近 200 条操作轨迹，崩溃时连同堆栈写一份到磁盘，
 * 设置页「导出诊断日志」把它打包成一个 txt 分享出去。
 *
 * 为什么要有：界面上的闪退（比如「滑到某处就崩」）在没连电脑时抓不到
 * logcat，普通应用也读不到系统日志。只能自己记：谁在什么时候打开了哪个页面、
 * 列表有几条、有没有重复的 id，最后崩在哪一行。文件名固定、内容里不含任何
 * 账目文本，只有数量统计。
 */
object Diag {

    private const val RING = 200
    private const val CRASH_FILE = "crash-last.txt"

    private val ring = ArrayDeque<String>()
    private val lock = Any()

    /** 由 App 注入：导出时补一句账本规模，方便判断是不是数据量的问题。 */
    var stats: () -> String = { "" }

    fun step(tag: String, detail: String = "") {
        val line = buildString {
            append(stamp())
            append("  ")
            append(tag)
            if (detail.isNotEmpty()) {
                append("  ")
                append(detail)
            }
        }
        synchronized(lock) {
            ring.addLast(line)
            while (ring.size > RING) ring.removeFirst()
        }
    }

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, CRASH_FILE).writeText(dump(app, thread.name, error)) }
            // 交回系统：该弹「应用已停止」还是弹，别把崩溃吞掉
            previous?.uncaughtException(thread, error)
        }
    }

    /** 上一次崩溃的记录；没有就返回空串。 */
    fun lastCrash(context: Context): String {
        val file = File(context.filesDir, CRASH_FILE)
        return if (file.exists()) runCatching { file.readText() }.getOrDefault("") else ""
    }

    fun clearCrash(context: Context) {
        runCatching { File(context.filesDir, CRASH_FILE).delete() }
    }

    /** 完整诊断文本：版本、机型、账本规模、上次崩溃、最近操作。 */
    fun dump(context: Context, threadName: String = "", error: Throwable? = null): String = buildString {
        appendLine("Stockpot ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("设备 ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("时间 ${stamp()}")
        val extra = runCatching { stats() }.getOrDefault("")
        if (extra.isNotBlank()) appendLine("账本 $extra")
        if (error != null) {
            appendLine()
            appendLine("== 崩溃 ==")
            appendLine("thread=$threadName")
            appendLine(error.stackTraceToString())
        } else {
            val last = lastCrash(context)
            if (last.isNotBlank()) {
                appendLine()
                appendLine("== 上次崩溃 ==")
                appendLine(last.trim())
            }
        }
        appendLine()
        appendLine("== 最近操作（新→旧最多 $RING 条）==")
        val snapshot = synchronized(lock) { ring.toList() }
        if (snapshot.isEmpty()) {
            appendLine("(空)")
        } else {
            snapshot.asReversed().forEach { appendLine(it) }
        }
    }

    /** 写到 cache/diag 下，返回那个文件。FileProvider 只放出这一个子目录。 */
    fun export(context: Context): File {
        val dir = File(context.cacheDir, "diag").apply { mkdirs() }
        // 只留最近几份，别把缓存撑大
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
        val name = "stockpot-diag-${SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date())}.txt"
        val file = File(dir, name)
        file.writeText(dump(context))
        return file
    }

    /** 导出并拉起系统分享面板，用户自己选发到哪儿。 */
    fun share(context: Context) {
        step("diag.export")
        val file = export(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Stockpot 诊断日志")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "导出诊断日志").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun stamp(): String =
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
