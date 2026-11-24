package cn.neeto.stockpot.core.data

import android.content.Context
import android.util.Log
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.emptyLedger
import cn.neeto.stockpot.core.model.normalized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 本地优先的存储：应用私有目录里的一份可读 JSON，原子替换写入，
 * 外加一份滚动备份。没有账号也照常工作，登录之后同步只是在旁边多挂一个观察者。
 *
 * 文件名与文件结构都和桌面端一致，导出的 `ledger.json` 可以直接被桌面端打开。
 */
class LedgerStore(context: Context) {

    private val dir: File = context.filesDir
    private val file = File(dir, "ledger.json")
    private val backup = File(dir, "ledger.bak.json")

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<Ledger> = _data.asStateFlow()

    private var saveJob: Job? = null

    /**
     * 本机改动的回调：把「改之前」和「改之后」两份都交出去，
     * 同步那边靠它记墓碑、安排推送。云端写回来的改动不会触发这里，
     * 否则同步自己会把自己再推一遍。
     */
    var onLocalChange: ((before: Ledger, after: Ledger) -> Unit)? = null

    init {
        if (!file.exists()) scope.launch { persist(_data.value) }
    }

    /** 数据文件路径，设置页展示用。 */
    val path: String get() = file.absolutePath

    private fun load(): Ledger {
        val loaded = read(file) ?: read(backup)
        if (loaded != null) return loaded.normalized()
        if (file.exists()) {
            // 文件存在但读不出来：留一份现场，别让下一次保存把它盖掉
            runCatching { file.renameTo(File(dir, "ledger.corrupt.json")) }
                .onFailure { Log.w(TAG, "无法备份损坏的账本文件", it) }
        }
        return emptyLedger()
    }

    private fun read(source: File): Ledger? {
        if (!source.exists()) return null
        return runCatching {
            json.decodeFromString<Ledger>(source.readText())
        }.onFailure { Log.w(TAG, "读取 ${source.name} 失败", it) }.getOrNull()
    }

    /** 所有修改都走这里：先更新内存快照，再防抖落盘。 */
    fun update(block: (Ledger) -> Ledger) {
        val before = _data.value
        val next = block(before)
        if (next === before) return
        _data.value = next
        scheduleSave()
        onLocalChange?.invoke(before, next)
    }

    /**
     * 整份替换：导入、合并、载入示例、删除撤销都走这里。
     * [fromRemote] 是云端写回来的那份——它不该再被当成"本地改动"推上去。
     */
    fun replaceAll(ledger: Ledger, fromRemote: Boolean = false) {
        val before = _data.value
        val next = ledger.normalized()
        if (next == before) return
        _data.value = next
        scheduleSave()
        if (!fromRemote) onLocalChange?.invoke(before, next)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(180)
            persist(_data.value)
        }
    }

    private suspend fun persist(data: Ledger) = saveMutex.withLock {
        runCatching {
            val body = json.encodeToString(Ledger.serializer(), data)
            val temp = File(dir, "${file.name}.tmp")
            temp.writeText(body)
            if (!temp.renameTo(file)) {
                file.writeText(body)
                temp.delete()
            }
            runCatching { file.copyTo(backup, overwrite = true) }
        }.onFailure { Log.e(TAG, "保存账本失败", it) }
    }

    fun flushAsync() {
        scope.launch { persist(_data.value) }
    }

    /** 切到后台时调用：和后台写盘共用同一把锁，不会互相踩。 */
    fun flushBlocking() {
        kotlinx.coroutines.runBlocking { persist(_data.value) }
    }

    /** 导出用：与磁盘上完全相同的 JSON 文本。 */
    fun exportJson(): String = json.encodeToString(Ledger.serializer(), _data.value)

    fun parseJson(text: String): Ledger? =
        runCatching { json.decodeFromString<Ledger>(text).normalized() }.getOrNull()

    private companion object {
        const val TAG = "LedgerStore"
    }
}
