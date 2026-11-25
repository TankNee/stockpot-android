package cn.neeto.stockpot.perf

import java.io.File
import java.util.Locale

/**
 * 极简压测夹子：不做 JMH 那套 fork / 预热 / 统计分析，只要「同一段代码在
 * 1k / 20k / 50k 条账目下大概多少毫秒」这个量级，够用来发现劣化。
 *
 * 每个场景先空跑两遍让 JIT 把热路径编出来，再测若干遍取中位数和 p95：
 * 中位数抗偶发抖动，p95 用来看有没有那种「一百次里总有几次卡住」的毛病。
 */
object Bench {

    data class Stats(val median: Double, val p95: Double, val min: Double, val max: Double)

    /** 一次测量的结果，`budgetMs` 是这条场景在这个量级下的上限。 */
    data class Cell(
        val scenario: String,
        val size: Int,
        val stats: Stats,
        val budgetMs: Double,
    ) {
        val pass: Boolean get() = stats.median <= budgetMs
    }

    fun measure(rounds: Int = 5, warmup: Int = 2, block: () -> Any?): Stats {
        repeat(warmup) { block() }
        val samples = DoubleArray(rounds)
        for (index in 0 until rounds) {
            val started = System.nanoTime()
            block()
            samples[index] = (System.nanoTime() - started) / 1_000_000.0
        }
        samples.sort()
        val median = if (rounds % 2 == 1) {
            samples[rounds / 2]
        } else {
            (samples[rounds / 2 - 1] + samples[rounds / 2]) / 2
        }
        val p95Index = ((rounds - 1) * 0.95).toInt().coerceIn(0, rounds - 1)
        return Stats(median, samples[p95Index], samples.first(), samples.last())
    }

    /** 控制台和报告里都用同一套格式，方便横向比对。 */
    fun row(cell: Cell): String = String.format(
        Locale.ROOT,
        "| %-26s | %6d | %9.1f | %9.1f | %9.1f | %s |",
        cell.scenario,
        cell.size,
        cell.stats.median,
        cell.stats.p95,
        cell.budgetMs,
        if (cell.pass) "ok" else "超标",
    )

    fun header(): String = String.format(
        Locale.ROOT,
        "| %-26s | %6s | %9s | %9s | %9s | %s |",
        "场景", "条数", "中位数ms", "p95ms", "预算ms", "",
    )

    /** 写一份 Markdown 进构建目录，CI 直接贴进 job summary。 */
    fun writeReport(dir: File, cells: List<Cell>) {
        dir.mkdirs()
        val worst = cells.maxByOrNull { it.stats.median / it.budgetMs.coerceAtLeast(0.001) }
        val body = buildString {
            appendLine("# 账本性能压测")
            appendLine()
            appendLine("量级：1k（日常）· 20k（重度）· 50k（极端，五年账单）")
            appendLine()
            appendLine(header())
            appendLine("|" + "---|".repeat(6))
            cells.forEach { appendLine(row(it)) }
            appendLine()
            if (worst != null) {
                appendLine(
                    String.format(
                        Locale.ROOT,
                        "最接近预算：%s @ %d 条，中位数 %.1fms / 预算 %.1fms",
                        worst.scenario, worst.size, worst.stats.median, worst.budgetMs,
                    ),
                )
            }
            appendLine()
            appendLine("超标场景：${cells.count { !it.pass }} / ${cells.size}")
        }
        File(dir, "ledger-perf.md").writeText(body)

        val json = buildString {
            appendLine("[")
            cells.forEachIndexed { index, cell ->
                append("  {")
                append("\"scenario\":\"${cell.scenario}\",")
                append("\"size\":${cell.size},")
                append("\"medianMs\":${round(cell.stats.median)},")
                append("\"p95Ms\":${round(cell.stats.p95)},")
                append("\"budgetMs\":${cell.budgetMs},")
                append("\"pass\":${cell.pass}")
                append("}")
                appendLine(if (index == cells.lastIndex) "" else ",")
            }
            appendLine("]")
        }
        File(dir, "ledger-perf.json").writeText(json)
    }

    private fun round(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}
