package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.PotColors

/**
 * 一点点 Markdown，够更新说明用。
 *
 * 版本说明是管理端手写的一段字，用 `##` 分节、`-` 列条目、`**` 加粗、反引号标代码。
 * 这个地方不值得引一个完整的 Markdown 库（体积、依赖、还有一堆用不上的语法），
 * 所以只认这几种：标题、有序/无序列表、引用、围栏代码、分隔线、段落，
 * 行内认粗体、行内代码和链接（链接只上色不跳转，免得更新到一半被切走）。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) {
                Spacer(
                    Modifier.height(
                        when (block) {
                            is MdBlock.Heading -> 14.dp
                            is MdBlock.Rule -> 10.dp
                            else -> 8.dp
                        },
                    ),
                )
            }
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inline(block.text, colors),
                    fontSize = when (block.level) {
                        1 -> 17.sp
                        2 -> 15.sp
                        3 -> 14.sp
                        else -> 13.sp
                    },
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink,
                )

                is MdBlock.Bullet -> Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                    block.items.forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier
                                    .padding(top = 8.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(colors.ink3),
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = inline(item, colors),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink2,
                                lineHeight = 19.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Ordered -> Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                    block.items.forEachIndexed { order, item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = "${block.start + order}.",
                                fontSize = 12.sp,
                                color = colors.ink3,
                                modifier = Modifier.width(18.dp),
                            )
                            Text(
                                text = inline(item, colors),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink2,
                                lineHeight = 19.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Quote -> Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(colors.brand.copy(alpha = 0.4f)),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = inline(block.text, colors),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink3,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f),
                    )
                }

                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.panel2)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = block.lines.joinToString("\n"),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.ink2,
                    )
                }

                is MdBlock.Para -> Text(
                    text = inline(block.text, colors),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    lineHeight = 19.sp,
                )

                MdBlock.Rule -> Hairline()
            }
        }
    }
}

/* ------------------------------ 解析 ------------------------------ */

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
    data class Ordered(val items: List<String>, val start: Int) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lines: List<String>) : MdBlock
    data object Rule : MdBlock
}

private val orderedLine = Regex("^(\\d{1,2})[.)]\\s+(.*)$")

/**
 * 切成块。空行分段，连续的列表条目并成一组。
 * 认不出来的行就当普通段落，不会丢字——说明写坏了也得让人看得见。
 */
internal fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val raw = lines[i].trimEnd()
        val text = raw.trim()
        when {
            text.isEmpty() -> i++

            text.startsWith("```") -> {
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    body += lines[i].trimEnd()
                    i++
                }
                if (i < lines.size) i++
                blocks += MdBlock.Code(body)
            }

            text == "---" || text == "***" || text == "___" -> {
                blocks += MdBlock.Rule
                i++
            }

            text.startsWith("#") -> {
                val level = text.takeWhile { it == '#' }.length.coerceIn(1, 4)
                val title = text.drop(level).trim()
                blocks += MdBlock.Heading(level, title)
                i++
            }

            text.startsWith("- ") || text.startsWith("* ") || text.startsWith("+ ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val item = lines[i].trim()
                    if (!(item.startsWith("- ") || item.startsWith("* ") || item.startsWith("+"))) break
                    items += item.drop(2).trim()
                    i++
                }
                blocks += MdBlock.Bullet(items)
            }

            orderedLine.matches(text) -> {
                val items = mutableListOf<String>()
                var start = 1
                while (i < lines.size) {
                    val match = orderedLine.find(lines[i].trim()) ?: break
                    if (items.isEmpty()) start = match.groupValues[1].toIntOrNull() ?: 1
                    items += match.groupValues[2].trim()
                    i++
                }
                blocks += MdBlock.Ordered(items, start)
            }

            text.startsWith("> ") -> {
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote += lines[i].trim().removePrefix(">").trim()
                    i++
                }
                blocks += MdBlock.Quote(quote.joinToString(" "))
            }

            else -> {
                val para = mutableListOf<String>()
                while (i < lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("- ") ||
                        line.startsWith("* ") || line.startsWith("> ") || line.startsWith("```") ||
                        orderedLine.matches(line)
                    ) {
                        break
                    }
                    para += line
                    i++
                }
                blocks += MdBlock.Para(para.joinToString(" "))
            }
        }
    }
    return blocks
}

/**
 * 行内：粗体、行内代码、链接。认不出来就原样输出，宁可多几个星号也别丢字。
 */
private fun inline(text: String, colors: PotColors): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end < 0) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Medium, color = colors.ink))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                }
            }

            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) {
                    append(text[i])
                    i++
                } else {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            background = colors.panel2,
                            color = colors.ink,
                        ),
                    )
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                }
            }

            text.startsWith("[", i) -> {
                val close = text.indexOf(']', i + 1)
                val open = if (close > 0) text.indexOf('(', close + 1) else -1
                val end = if (open > 0) text.indexOf(')', open + 1) else -1
                if (close > 0 && open == close + 1 && end > open) {
                    pushStyle(
                        SpanStyle(color = colors.brandInk, textDecoration = TextDecoration.Underline),
                    )
                    append(text.substring(i + 1, close))
                    pop()
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            else -> {
                append(text[i])
                i++
            }
        }
    }
}
