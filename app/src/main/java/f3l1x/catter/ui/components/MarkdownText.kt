package f3l1x.catter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(blocks) { block -> MarkdownBlock(block) }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> Text(
            text = inline(block.text),
            style = headingStyle(block.level),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )

        is MdBlock.Paragraph -> Text(
            text = inline(block.text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is MdBlock.Code -> CodeBlock(block.text)

        is MdBlock.Quote -> QuoteBlock(block.text)

        is MdBlock.Bullet -> BulletBlock(block)

        MdBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        is MdBlock.Table -> CodeBlock(block.rows.joinToString("\n"))
    }
}

@Composable
private fun CodeBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun QuoteBlock(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = inline(text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun BulletBlock(block: MdBlock.Bullet) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (block.ordered) "${block.number}." else "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp),
        )
        Text(
            text = inline(block.text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.headlineSmall
    2 -> MaterialTheme.typography.titleLarge
    3 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}

@Composable
private fun inline(text: String): AnnotatedString {
    val code = MaterialTheme.colorScheme.surfaceContainerHighest
    val link = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    return remember(text, code, link, muted) {
        buildAnnotatedString {
            var index = 0

            inlinePattern.findAll(text).forEach { match ->
                if (match.range.first > index) append(text.substring(index, match.range.first))

                val groups = match.groupValues
                when {
                    groups[2].isNotEmpty() || groups[4].isNotEmpty() -> {
                        val label = groups[1].ifEmpty { groups[3] }
                        val url = groups[2].ifEmpty { groups[4] }
                        if (url.isBlank()) {
                            append(label)
                        } else {
                            withLink(LinkAnnotation.Url(url)) {
                                pushStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline))
                                append(label)
                                pop()
                            }
                        }
                    }

                    groups[5].isNotEmpty() -> {
                        pushStyle(SpanStyle(background = code))
                        append(" ${groups[5]} ")
                        pop()
                    }

                    groups[6].isNotEmpty() -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                        append(groups[6])
                        pop()
                    }

                    groups[7].isNotEmpty() || groups[8].isNotEmpty() -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(groups[7].ifEmpty { groups[8] })
                        pop()
                    }

                    groups[9].isNotEmpty() -> {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = muted))
                        append(groups[9])
                        pop()
                    }

                    else -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(groups[10].ifEmpty { groups[11] })
                        pop()
                    }
                }

                index = match.range.last + 1
            }

            if (index < text.length) append(text.substring(index))
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Bullet(val text: String, val ordered: Boolean, val number: Int) : MdBlock
    data object Rule : MdBlock
    data class Table(val rows: List<String>) : MdBlock
}

private val inlinePattern = Regex(
    "!\\[([^\\]]*)]\\(([^)]+)\\)" +
        "|\\[([^\\]]+)]\\(([^)]+)\\)" +
        "|`([^`]+)`" +
        "|\\*\\*\\*([^*]+)\\*\\*\\*" +
        "|\\*\\*([^*]+)\\*\\*" +
        "|__([^_]+)__" +
        "|~~([^~]+)~~" +
        "|\\*([^*]+)\\*" +
        "|_([^_]+)_",
)

private val headingPattern = Regex("^(#{1,6})\\s+(.*)$")
private val htmlBreakPattern = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val htmlTagPattern = Regex("<[^>]+>")
private val bulletPattern = Regex("^[-*+]\\s+(.*)$")
private val numberedPattern = Regex("^(\\d{1,3})[.)]\\s+(.*)$")
private val rulePattern = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val tableRulePattern = Regex("^\\|[\\s:|-]+\\|$")

private fun clean(text: String): String = text
    .replace(htmlBreakPattern, " ")
    .replace(htmlTagPattern, "")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
    val paragraph = StringBuilder()

    fun flush() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(clean(paragraph.toString()))
        paragraph.setLength(0)
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()

        if (line.startsWith("```")) {
            flush()
            val code = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                code.appendLine(lines[index])
                index++
            }
            if (index < lines.size) index++
            blocks += MdBlock.Code(code.toString().trimEnd())
            continue
        }

        if (line.isEmpty()) {
            flush()
            index++
            continue
        }

        if (rulePattern.matches(line)) {
            flush()
            blocks += MdBlock.Rule
            index++
            continue
        }

        val heading = headingPattern.matchEntire(line)
        if (heading != null) {
            flush()
            blocks += MdBlock.Heading(heading.groupValues[1].length, clean(heading.groupValues[2]))
            index++
            continue
        }

        if (line.startsWith(">")) {
            flush()
            val quote = StringBuilder()
            while (index < lines.size && lines[index].trim().startsWith(">")) {
                quote.appendLine(lines[index].trim().removePrefix(">").trim())
                index++
            }
            blocks += MdBlock.Quote(clean(quote.toString()))
            continue
        }

        if (line.startsWith("|")) {
            flush()
            val rows = mutableListOf<String>()
            while (index < lines.size && lines[index].trim().startsWith("|")) {
                val row = lines[index].trim()
                if (!tableRulePattern.matches(row)) rows += row
                index++
            }
            if (rows.isNotEmpty()) blocks += MdBlock.Table(rows)
            continue
        }

        val bullet = bulletPattern.matchEntire(line)
        if (bullet != null) {
            flush()
            blocks += MdBlock.Bullet(clean(bullet.groupValues[1]), ordered = false, number = 0)
            index++
            continue
        }

        val numbered = numberedPattern.matchEntire(line)
        if (numbered != null) {
            flush()
            blocks += MdBlock.Bullet(
                text = clean(numbered.groupValues[2]),
                ordered = true,
                number = numbered.groupValues[1].toIntOrNull() ?: 1,
            )
            index++
            continue
        }

        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(line)
        index++
    }

    flush()
    return blocks
}
