package cn.neeto.stockpot.core.importer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 极简 .xlsx 读取：只关心「第一张表的所有单元格文本」。
 *
 * 微信账单和 iCost 导出的都是 xlsx。为了不把一个 10MB 的 Apache POI 拖进
 * APK，这里直接用系统自带的 zip + XmlPullParser 读 `sharedStrings.xml`
 * 和 `worksheets/sheetN.xml`，够用且启动快。
 */
object XlsxReader {

    fun read(bytes: ByteArray): List<List<String>>? {
        val parts = unzip(bytes)
        if (parts.isEmpty()) return null
        val shared = parts["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetKey = pickFirstSheet(parts.keys) ?: return null
        return parseSheet(parts[sheetKey] ?: return null, shared)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        var stream: ZipInputStream? = null
        try {
            stream = ZipInputStream(ByteArrayInputStream(bytes))
            while (true) {
                val entry = stream.nextEntry ?: break
                val name = entry.name
                val wanted = name == "xl/sharedStrings.xml" ||
                    (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                if (wanted) {
                    out[name] = stream.readCurrentEntry()
                }
                stream.closeEntry()
            }
        } catch (_: Exception) {
            return emptyMap()
        } finally {
            runCatching { stream?.close() }
        }
        return out
    }

    /** ZipInputStream 的一个 entry 读到 -1 就结束，不会把整个包吞掉。 */
    private fun InputStream.readCurrentEntry(): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val read = read(chunk)
            if (read <= 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun pickFirstSheet(names: Set<String>): String? {
        val sheets = names.filter { it.startsWith("xl/worksheets/") }
        if (sheets.isEmpty()) return null
        fun index(name: String): Int =
            Regex("sheet(\\d+)\\.xml$").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        return sheets.minByOrNull { index(it) }
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = ArrayList<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), null)
            var event = parser.eventType
            val current = StringBuilder()
            var inString = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "si" -> {
                            current.setLength(0)
                            inString = true
                        }
                        // 富文本会被拆成多个 <r><t>，拼起来才是完整字符串
                        "t" -> if (inString) current.append(parser.nextText())
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "si" && inString) {
                        result.add(current.toString())
                        inString = false
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            return result
        }
        return result
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>>? {
        val rows = ArrayList<List<String>>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), null)
            var event = parser.eventType

            var row: ArrayList<String>? = null
            var cellType = ""
            var cellColumn = 0
            val cellText = StringBuilder()
            var inCell = false
            var readingValue = false

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "row" -> row = ArrayList()
                        "c" -> {
                            inCell = true
                            cellText.setLength(0)
                            cellType = parser.getAttributeValue(null, "t").orEmpty()
                            cellColumn = columnIndex(parser.getAttributeValue(null, "r"))
                        }
                        "v" -> readingValue = inCell
                        "t" -> if (inCell && cellType == "inlineStr") readingValue = true
                    }
                    XmlPullParser.TEXT -> if (readingValue) cellText.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "v" -> readingValue = false
                        "t" -> if (cellType == "inlineStr") readingValue = false
                        "c" -> {
                            inCell = false
                            val raw = cellText.toString()
                            val value = when (cellType) {
                                "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                                "inlineStr", "str" -> raw
                                "b" -> if (raw.trim() == "1") "true" else "false"
                                else -> trimNumber(raw)
                            }
                            val target = row
                            if (target != null) {
                                // 少数导出工具会省略 `r`，按出现顺序往后放
                                val column = if (cellColumn >= 0) cellColumn else target.size
                                while (target.size <= column) target.add("")
                                target[column] = value
                            }
                            cellText.setLength(0)
                        }
                        "row" -> {
                            row?.let { rows.add(it) }
                            row = null
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            return if (rows.isEmpty()) null else rows
        }
        return rows
    }

    /** `AB12` → 27（0 基）。 */
    private fun columnIndex(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var value = 0
        var seen = false
        for (ch in ref) {
            if (ch.isLetter()) {
                value = value * 26 + (ch.uppercaseChar() - 'A' + 1)
                seen = true
            } else {
                break
            }
        }
        return if (seen) value - 1 else -1
    }

    /** Excel 把整数写成 `15`，把日期写成 `46348.4575`，都按原样交给解析层。 */
    private fun trimNumber(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        return if (text.endsWith(".0")) text.dropLast(2) else text
    }
}
