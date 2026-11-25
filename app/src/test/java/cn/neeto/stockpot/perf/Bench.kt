package cn.neeto.stockpot.perf

import java.io.File
import java.util.Locale

/**
 * 极简压测夹子：不做 JMH 那套 fork / 预热 / 统计分析，只要「同一段代码在
 * 1k / 20k / 50k 条账目下大概多少毫秒」这个量级，够用来发现劣化。
 *
 * 三件让数字站得住的事：
 * 1. 每个场景先热身若干轮，把 JIT 编译成本挤到计时之外；
 * 2. 单次太快的场景自动攒批跑（一批至少 ~5ms），避免被 `nanoTime` 精度和
 *    GC 抖动吃掉——0.2ms 的场景单次计时，两次能差出一倍；
 * 3. 取中位数，同时报 p95 / 极值，方便看出「一百次里总有几次卡住」那种毛病。
 *
 * 计时只在这里做，断言在 LedgerPerfTest 里：预算拦「整体慢了一大截」，
 * 线性度拦「O(n) 写成了 O(n²)」。
 */
object Bench {

    data class Stats(
        val median: Double,
        val p95: Double,
        val min: Double,
        val max: Double,
        /** 每次采样里跑了几遍（快场景会攒批）。 */
        val batch: Int = 1,
    )

    /** 一次测量的结果，`budgetMs` 是这条场景在这个量级下的上限。 */
    data class Cell(
        val scenario: String,
        val size: Int,
        val stats: Stats,
        val budgetMs: Double,
    ) {
        val pass: Boolean get() = stats.median <= budgetMs
    }

    /** 同一场景在「小量级 → 大量级」之间的耗时倍数，用来盯住复杂度。 */
    data class Scaling(val scenario: String, val ratio: Double, val cap: Double) {
        val pass: Boolean get() = ratio <= cap
    }

    /**
     * 跑一组采样。
     *
     * @param samples 计时次数（排序后取中位数）
     * @param warmup 计时前的空跑轮数
     * @param minSampleMs 一次采样至少攒到多少毫秒，太快的场景按倍数补跑
     */
    fun measure(
        samples: Int = 7,
        warmup: Int = 3,
        minSampleMs: Double = 5.0,
        block: () -> Any?,
    ): Stats {
        repeat(warmup) { block() }

        // 先估一次单次耗时，据此决定一批跑几遍。估出来的数只用一次，不影响结果。
        val started = System.nanoTime()
        block()
        val probe = (System.nanoTime() - started) / 1_000_000.0
        val batch = if (probe <= 0.0) 1 else (minSampleMs / probe).toInt().coerceIn(1, 5_000)

        val used = DoubleArray(samples)
        for (index in 0 until samples) {
            val begin = System.nanoTime()
            repeat(batch) { block() }
            used[index] = (System.nanoTime() - begin) / 1_000_000.0 / batch
        }
        used.sort()
        val median = if (samples % 2 == 1) {
            used[samples / 2]
        } else {
            (used[samples / 2 - 1] + used[samples / 2]) / 2
        }
        val p95Index = ((samples - 1) * 0.95).toInt().coerceIn(0, samples - 1)
        return Stats(median, used[p95Index], used.first(), used.last(), batch)
    }

    /**
     * 大量级相对小量级的倍数。
     *
     * `cap` 取「线性应得的倍数 × 3」：50 倍数据如果是 O(n)，就该是 50 倍耗时；
     * 实测大多落在 35~60（超过 50 的那部分是 50k 的数组塞不进 CPU 缓存），
     * 留到 150 够装下这个和抖动。真写成 O(n²) 时这个倍数是上千，一眼就能看出来，
     * 而且这个判断不看机器快慢，CI 上照样成立。
     *
     * 两头太快的不算：小量级不到 20µs、或者大量级不到 0.5ms 时，比值基本是噪声。
     */
    fun scaling(cells: List<Cell>, from: Int, to: Int, cap: Double): List<Scaling> =
        cells.filter { it.size == to }
            .mapNotNull { big ->
                val small = cells.firstOrNull { it.scenario == big.scenario && it.size == from }
                    ?: return@mapNotNull null
                if (small.stats.median < 0.02 || big.stats.median < 0.5) return@mapNotNull null
                Scaling(big.scenario, big.stats.median / small.stats.median, cap)
            }

    fun machine(): String = String.format(
        Locale.ROOT,
        "JVM %s · %s %s · %d 核",
        System.getProperty("java.version"),
        System.getProperty("os.name"),
        System.getProperty("os.arch"),
        Runtime.getRuntime().availableProcessors(),
    )

    /** 控制台和报告里都用同一套格式，方便横向比对。 */
    fun row(cell: Cell): String = String.format(
        Locale.ROOT,
        "| %-26s | %6d | %9s | %9s | %9s | %s |",
        cell.scenario,
        cell.size,
        ms(cell.stats.median),
        ms(cell.stats.p95),
        ms(cell.budgetMs),
        if (cell.pass) "ok" else "超标",
    )

    fun header(): String = String.format(
        Locale.ROOT,
        "| %-26s | %6s | %9s | %9s | %9s | %s |",
        "场景", "条数", "中位数ms", "p95ms", "预算ms", "",
    )

    fun scalingRow(item: Scaling): String = String.format(
        Locale.ROOT,
        "| %-26s | %8.1f | %8.1f | %s |",
        item.scenario, item.ratio, item.cap, if (item.pass) "ok" else "非线性",
    )

    /** 几微秒的东西按一位小数印出来全是 0.0，看不出差别；小的多给两位。 */
    private fun ms(value: Double): String =
        String.format(Locale.ROOT, if (value < 10) "%.2f" else "%.1f", value)

    /** 写一份 Markdown 进构建目录，CI 直接贴进 job summary。 */
    fun writeReport(dir: File, cells: List<Cell>, scaling: List<Scaling>, from: Int, to: Int) {
        dir.mkdirs()
        val worst = cells.maxByOrNull { it.stats.median / it.budgetMs.coerceAtLeast(0.001) }
        val body = buildString {
            appendLine("# 账本性能压测")
            appendLine()
            appendLine("机器：${machine()}")
            appendLine()
            appendLine("量级：1k（日常）· 20k（重度）· 50k（极端，五年账单）")
            appendLine()
            appendLine(header())
            appendLine("|" + "---|".repeat(6))
            cells.forEach { appendLine(row(it)) }
            appendLine()
            appendLine("## 线性度（${to} / ${from} 的耗时倍数）")
            appendLine()
            appendLine("| 场景 | 倍数 | 上限 | |")
            appendLine("|---|---|---|---|")
            scaling.forEach { appendLine(scalingRow(it)) }
            appendLine()
            if (worst != null) {
                appendLine(
                    String.format(
                        Locale.ROOT,
                        "最接近预算：%s @ %d 条，中位数 %sms / 预算 %sms",
                        worst.scenario, worst.size, ms(worst.stats.median), ms(worst.budgetMs),
                    ),
                )
            }
            appendLine()
            appendLine("超标场景：${cells.count { !it.pass }} / ${cells.size}")
            appendLine("非线性场景：${scaling.count { !it.pass }} / ${scaling.size}")
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
                append("\"batch\":${cell.stats.batch},")
                append("\"budgetMs\":${cell.budgetMs},")
                append("\"pass\":${cell.pass}")
                append("}")
                appendLine(if (index == cells.lastIndex) "" else ",")
            }
            appendLine("]")
        }
        File(dir, "ledger-perf.json").writeText(json)
    }

    private fun round(value: Double): String = String.format(Locale.ROOT, "%.4f", value)
}
