package cn.neeto.stockpot.core.util

import android.app.Application
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把未捕获的异常写到 `filesDir/crash-last.txt`。
 *
 * 没有这个的话闪退只能靠「往下滑就崩」这种描述去猜：手机不在电脑上、logcat
 * 抓不到，就只能靠代码里找可疑点。写一份到应用目录，插上数据线 `adb pull`
 * 或者下次连上就能拿到完整堆栈。
 *
 * 只留最近一次，位置固定，不带任何账目内容。
 */
object CrashLog {

    const val FILE_NAME = "crash-last.txt"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread.name, error) }
            // 交回系统：该弹「应用已停止」还是弹，别把崩溃吞掉
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(app: Application, threadName: String, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            appendLine("$stamp  thread=$threadName")
            appendLine(error.stackTraceToString())
        }
        File(app.filesDir, FILE_NAME).writeText(text)
    }
}
