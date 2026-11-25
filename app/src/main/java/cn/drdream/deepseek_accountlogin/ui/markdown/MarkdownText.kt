package cn.drdream.deepseek_accountlogin.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.drdream.deepseek_accountlogin.ui.components.TypingCursor
import cn.drdream.deepseek_accountlogin.ui.theme.LocalDeepSeekColors
import kotlinx.coroutines.delay

/**
 * Model-output Markdown with paragraphs, headings, lists, quotes, tables, fenced code,
 * inline code, links, emphasis and basic LaTeX. Links invoke [onOpenUrl].
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showCursor: Boolean = false,
    onOpenUrl: (String) -> Unit = {},
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                    when (block) {
                        is MdBlock.Paragraph -> InlineText(
                            text = block.text,
                            style = style,
                            color = color,
                            onOpenUrl = onOpenUrl,
                        )

                        is MdBlock.Heading -> HeadingView(block, color, onOpenUrl)
                        is MdBlock.Code -> CodeBlockView(block)
                        is MdBlock.Quote -> QuoteView(block, style, onOpenUrl)
                        is MdBlock.BulletList -> ListView(
                            ordered = false,
                            start = 1,
                            items = block.items,
                            style = style,
                            color = color,
                            onOpenUrl = onOpenUrl,
                        )

                        is MdBlock.OrderedList -> ListView(
                            ordered = true,
                            start = block.start,
                            items = block.items,
                            style = style,
                            color = color,
                            onOpenUrl = onOpenUrl,
                        )

                        is MdBlock.Table -> TableView(block, style, color, onOpenUrl)
                        MdBlock.Divider -> HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = LocalDeepSeekColors.current.divider,
                        )

                        is MdBlock.Math -> MathBlockView(block)
                    }
                }
            }
        if (showCursor) {
            TypingCursor(modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun HeadingView(
    block: MdBlock.Heading,
    color: Color,
    onOpenUrl: (String) -> Unit,
) {
    val typography = MaterialTheme.typography
    val style = when (block.level) {
        1 -> typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
        2 -> typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        3 -> typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        4 -> typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        else -> typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    }
    InlineText(
        text = block.text,
        style = style,
        color = color,
        onOpenUrl = onOpenUrl,
        modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 2.dp),
    )
}

@Composable
private fun QuoteView(
    block: MdBlock.Quote,
    style: TextStyle,
    onOpenUrl: (String) -> Unit,
) {
    val colors = LocalDeepSeekColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(colors.brand.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        InlineText(
            text = block.text,
            style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onOpenUrl = onOpenUrl,
        )
    }
}

@Composable
private fun ListView(
    ordered: Boolean,
    start: Int,
    items: List<MdItem>,
    style: TextStyle,
    color: Color,
    onOpenUrl: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.padding(start = (item.level * 18).dp)) {
                Text(
                    text = if (ordered) "${start + index}." else "•",
                    modifier = Modifier.width(if (ordered) 26.dp else 16.dp),
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InlineText(
                    text = item.text,
                    style = style,
                    color = color,
                    onOpenUrl = onOpenUrl,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: MdBlock.Code) {
    val colors = LocalDeepSeekColors.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    val highlighted = remember(block.code, block.language, colors.codeText) {
        CodeHighlight.highlight(block.code, block.language, colors)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.codeBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.codeHeader)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        copyToClipboard(context, block.code)
                        copied = true
                    },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy code",
                        tint = if (copied) MaterialTheme.colorScheme.primary else colors.mutedText,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = highlighted,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    ),
                    color = colors.codeText,
                )
            }
        }
    }
}

@Composable
private fun TableView(
    block: MdBlock.Table,
    style: TextStyle,
    color: Color,
    onOpenUrl: (String) -> Unit,
) {
    val colors = LocalDeepSeekColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().background(colors.searchCard)) {
                block.header.forEach { cell ->
                    TableCell(
                        text = cell,
                        style = style,
                        color = color,
                        onOpenUrl = onOpenUrl,
                        bold = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            block.rows.forEachIndexed { rowIndex, row ->
                HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (rowIndex % 2 == 0) Color.Transparent
                            else colors.searchCard.copy(alpha = 0.5f),
                        ),
                ) {
                    row.forEach { cell ->
                        TableCell(
                            text = cell,
                            style = style,
                            color = color,
                            onOpenUrl = onOpenUrl,
                            bold = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    style: TextStyle,
    color: Color,
    onOpenUrl: (String) -> Unit,
    bold: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(0.5.dp, LocalDeepSeekColors.current.divider.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        InlineText(
            text = text,
            style = if (bold) style.copy(fontWeight = FontWeight.SemiBold) else style,
            color = color,
            onOpenUrl = onOpenUrl,
        )
    }
}

@Composable
private fun MathBlockView(block: MdBlock.Math) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(
                LocalDeepSeekColors.current.thinkingBackground,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = Latex.toUnicode(block.latex),
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    color: Color,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDeepSeekColors.current
    val primary = MaterialTheme.colorScheme.primary
    val codeBackground = colors.thinkingBackground
    val annotated = remember(text, color, primary, codeBackground, onOpenUrl) {
        buildAnnotatedString {
            fun link(url: String, linkStyle: SpanStyle, block: () -> Unit) {
                val annotation = LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(style = linkStyle),
                    linkInteractionListener = object : LinkInteractionListener {
                        override fun onClick(link: LinkAnnotation) {
                            onOpenUrl(url)
                        }
                    },
                )
                withLink(annotation) { block() }
            }

            fun appendSegment(segment: String, span: SpanStyle) {
                var i = 0
                while (i < segment.length) {
                    val char = segment[i]
                    when {
                        char == '\\' && i + 1 < segment.length -> {
                            withStyle(span) { append(segment[i + 1]) }
                            i += 2
                        }

                        segment.startsWith("**", i) || segment.startsWith("__", i) -> {
                            val marker = segment.substring(i, i + 2)
                            val end = segment.indexOf(marker, i + 2)
                            if (end > i + 2) {
                                val bold = span.copy(fontWeight = FontWeight.SemiBold)
                                withStyle(bold) {
                                    appendSegment(segment.substring(i + 2, end), bold)
                                }
                                i = end + 2
                            } else {
                                withStyle(span) { append(marker) }
                                i += 2
                            }
                        }

                        segment.startsWith("~~", i) -> {
                            val end = segment.indexOf("~~", i + 2)
                            if (end > i + 2) {
                                val strike = span.copy(textDecoration = TextDecoration.LineThrough)
                                withStyle(strike) {
                                    appendSegment(segment.substring(i + 2, end), strike)
                                }
                                i = end + 2
                            } else {
                                withStyle(span) { append("~~") }
                                i += 2
                            }
                        }

                        segment.startsWith("`", i) -> {
                            val end = segment.indexOf('`', i + 1)
                            if (end > i) {
                                withStyle(
                                    span.copy(
                                        fontFamily = FontFamily.Monospace,
                                        background = codeBackground,
                                        color = colors.codeText,
                                    ),
                                ) {
                                    append(segment.substring(i + 1, end))
                                }
                                i = end + 1
                            } else {
                                withStyle(span) { append('`') }
                                i++
                            }
                        }

                        char == '[' -> {
                            val closeLabel = segment.indexOf(']', i + 1)
                            val openUrl = if (closeLabel > i && closeLabel + 1 < segment.length &&
                                segment[closeLabel + 1] == '('
                            ) {
                                closeLabel + 2
                            } else {
                                -1
                            }
                            val closeUrl = if (openUrl >= 0) segment.indexOf(')', openUrl) else -1
                            if (openUrl >= 0 && closeUrl > openUrl) {
                                val label = segment.substring(i + 1, closeLabel)
                                val url = segment.substring(openUrl, closeUrl).trim()
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    val linkStyle = span.copy(
                                        color = primary,
                                        textDecoration = TextDecoration.Underline,
                                    )
                                    link(url, linkStyle) {
                                        withStyle(linkStyle) { appendSegment(label, linkStyle) }
                                    }
                                } else {
                                    withStyle(span) { append(segment.substring(i, closeUrl + 1)) }
                                }
                                i = closeUrl + 1
                            } else {
                                withStyle(span) { append(char) }
                                i++
                            }
                        }

                        char == '<' -> {
                            val end = segment.indexOf('>', i + 1)
                            val candidate = if (end > i) segment.substring(i + 1, end) else ""
                            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                                val linkStyle = span.copy(
                                    color = primary,
                                    textDecoration = TextDecoration.Underline,
                                )
                                link(candidate, linkStyle) {
                                    withStyle(linkStyle) { append(candidate) }
                                }
                                i = end + 1
                            } else {
                                withStyle(span) { append(char) }
                                i++
                            }
                        }

                        segment.startsWith("http://", i) || segment.startsWith("https://", i) -> {
                            var end = i
                            while (end < segment.length && !segment[end].isWhitespace() &&
                                segment[end] !in ")]}>\"'"
                            ) {
                                end++
                            }
                            var url = segment.substring(i, end)
                            while (url.isNotEmpty() && url.last() in ".,;:!?") {
                                url = url.dropLast(1)
                                end--
                            }
                            val linkStyle = span.copy(
                                color = primary,
                                textDecoration = TextDecoration.Underline,
                            )
                            link(url, linkStyle) {
                                withStyle(linkStyle) { append(url) }
                            }
                            i = end
                        }

                        char == '$' -> {
                            val end = findDollarEnd(segment, i + 1)
                            if (end > i + 1) {
                                val math = Latex.toUnicode(segment.substring(i + 1, end))
                                withStyle(
                                    span.copy(
                                        fontFamily = FontFamily.Serif,
                                        fontStyle = FontStyle.Italic,
                                    ),
                                ) {
                                    append(math)
                                }
                                i = end + 1
                            } else {
                                withStyle(span) { append(char) }
                                i++
                            }
                        }

                        char == '*' || char == '_' -> {
                            val end = segment.indexOf(char, i + 1)
                            if (end > i + 1) {
                                val italic = span.copy(fontStyle = FontStyle.Italic)
                                withStyle(italic) {
                                    appendSegment(segment.substring(i + 1, end), italic)
                                }
                                i = end + 1
                            } else {
                                withStyle(span) { append(char) }
                                i++
                            }
                        }

                        else -> {
                            var end = i + 1
                            while (end < segment.length &&
                                segment[end] !in "\\*_~`[]$<" &&
                                !segment.startsWith("http://", end) &&
                                !segment.startsWith("https://", end)
                            ) {
                                end++
                            }
                            withStyle(span) { append(segment.substring(i, end)) }
                            i = end
                        }
                    }
                }
            }
            appendSegment(text, SpanStyle(color = color))
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
    )
}

private fun findDollarEnd(text: String, start: Int): Int {
    var index = start
    while (index < text.length) {
        if (text[index] == '$' && (index == 0 || text[index - 1] != '\\')) return index
        index++
    }
    return -1
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("DeepSeek", text))
}
