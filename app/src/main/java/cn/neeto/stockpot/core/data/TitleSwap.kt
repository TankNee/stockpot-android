package cn.neeto.stockpot.core.data

import cn.neeto.stockpot.core.model.Entry

/**
 * 有些渠道的账单，交易对方写的是平台名（美团、淘宝闪购），真正买了什么写在商品说明里。
 * 列表只显示标题，一屏看下去全是「美团」，等于什么也没说。
 *
 * 这里按关键词把这类条目认出来，再把标题和备注换回来。**只动这两段文字**：
 * 金额、分类、时间、单号一概不碰；换错了再换一次就回来了，不会有副作用。
 */
data class SwapRule(
    val id: String,
    val label: String,
    /** 命中标题（交易对方）的词 */
    val words: List<String>,
    val hint: String,
) {
    companion object {
        val builtIn: List<SwapRule> = listOf(
            SwapRule("meituan", "美团", listOf("美团", "小象超市"), "外卖、闪购、买菜"),
            SwapRule("taobao", "淘宝闪购", listOf("淘宝", "闪购", "淘鲜达"), "闪购、淘鲜达"),
            SwapRule("eleme", "饿了么", listOf("饿了么"), "外卖"),
            SwapRule("jd", "京东", listOf("京东"), "京东、京东到家"),
            SwapRule("douyin", "抖音", listOf("抖音"), "团购、直播下单"),
            SwapRule("pdd", "拼多多", listOf("拼多多", "多多买菜"), "多多买菜"),
        )

        /** 内置规则里的全部词，用来把用户自己加的词挑出来。 */
        val builtInWords: Set<String> = builtIn.flatMap { it.words }.toSet()

        /** 默认全开：扫描本身不改任何数据，真正落库要用户自己点确认。 */
        val defaultWords: Set<String> = builtInWords
    }
}

/** 一条等着交换的账目：换完长什么样，先摆出来给人看。 */
data class SwapHit(
    val entry: Entry,
    /** 认出来是哪一类，界面上给个标签 */
    val ruleLabel: String,
) {
    val oldTitle: String get() = entry.counterparty
    val newTitle: String get() = entry.note
}

object TitleSwap {

    /**
     * 三条同时满足才值得换：标题是平台名、备注里确实有东西、两者不是同一段字。
     * 少一条都别动——比如标题本来就是「蜜雪冰城」，换过来反而变糟。
     */
    fun matches(entry: Entry, words: Collection<String>): Boolean {
        if (entry.counterparty.isBlank() || entry.note.isBlank()) return false
        if (entry.counterparty == entry.note) return false
        return words.any { word ->
            word.isNotBlank() && entry.counterparty.contains(word, ignoreCase = true)
        }
    }

    /** 用户自己加的词：不在任何内置规则里，按字典序排好给界面用。 */
    fun customWords(words: Collection<String>): List<String> =
        words.filter { it.isNotBlank() && it !in SwapRule.builtInWords }.sorted()

    /**
     * 把一个新词规整一下：去掉首尾空白、内部连续空格压成一个、全角空格换半角。
     * 返回 null 表示这个词不能用（空的、太长、或者已经在内置规则里了）。
     */
    fun normalizeWord(raw: String): String? {
        val word = raw.replace('\u3000', ' ').trim().replace(Regex("\\s+"), " ")
        if (word.length < 1 || word.length > 12) return null
        if (word in SwapRule.builtInWords) return null
        return word
    }

    /** 扫一遍账本，按时间倒序给出命中项。已收起的行不参与（列表里根本看不见）。 */
    fun scan(entries: List<Entry>, words: Collection<String>): List<SwapHit> {
        if (words.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { !it.archived }
            .filter { matches(it, words) }
            .map { entry ->
                val label = SwapRule.builtIn.firstOrNull { rule ->
                    rule.words.any { entry.counterparty.contains(it, ignoreCase = true) }
                }?.label ?: "自定义"
                SwapHit(entry, label)
            }
            .sortedWith(
                compareByDescending<SwapHit> { it.entry.date }.thenByDescending { it.entry.time },
            )
            .toList()
    }

    /** 交换标题和备注。备注为空时原样返回——换过去等于把标题弄丢了。 */
    fun swapped(entry: Entry): Entry =
        if (entry.note.isBlank() || entry.counterparty == entry.note) {
            entry
        } else {
            entry.copy(counterparty = entry.note, note = entry.counterparty)
        }
}
