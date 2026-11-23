package cn.neeto.stockpot.core.importer

/** 引号感知的 CSV 切分：字段里可以有逗号、换行和转义的 `""`。 */
object Csv {

    fun rows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0

        fun flushField() {
            row.add(field.toString().trim())
            field.setLength(0)
        }

        fun flushRow() {
            flushField()
            if (row.any { it.isNotEmpty() }) rows.add(ArrayList(row))
            row.clear()
        }

        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> flushField()
                (ch == '\n' || ch == '\r') && !quoted -> {
                    flushRow()
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) flushRow()
        return rows
    }
}
