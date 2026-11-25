package cn.drdream.deepseek_accountlogin.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.drdream.deepseek_accountlogin.data.model.ChatMessage
import cn.drdream.deepseek_accountlogin.data.model.MessageStatus
import cn.drdream.deepseek_accountlogin.data.model.ReplyBlock
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import cn.drdream.deepseek_accountlogin.ui.markdown.MarkdownText
import cn.drdream.deepseek_accountlogin.ui.theme.LocalDeepSeekColors
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun AssistantMessageItem(
    message: ChatMessage,
    isLastAssistant: Boolean,
    isStreaming: Boolean,
    isReading: Boolean,
    onRegenerate: () -> Unit,
    onReadAloud: () -> Unit,
    onStopReading: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onTopUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    val context = LocalContext.current
    val text = message.visibleText()
    val hasSources = message.blocks.any {
        it is ReplyBlock.SearchResults && it.sources.isNotEmpty()
    }
    val lastTextIndex = message.blocks.indexOfLast { it is ReplyBlock.Text }
    val lastBlockIndex = message.blocks.lastIndex
    var copied by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strings.assistantName,
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            message.blocks.forEachIndexed { index, block ->
                when (block) {
                    is ReplyBlock.Thinking -> {
                        if (block.text.isNotBlank() || isStreaming) {
                            ThinkingPanel(block = block, streaming = isStreaming)
                        }
                    }

                    is ReplyBlock.ToolUse -> {
                        if (!hasSources) {
                            SearchingRow(
                                block = block,
                                streaming = isStreaming && index == lastBlockIndex,
                            )
                        }
                    }

                    is ReplyBlock.SearchResults -> {
                        if (block.sources.isNotEmpty()) {
                            SourcesSection(sources = block.sources, onOpenUrl = onOpenUrl)
                        }
                    }

                    is ReplyBlock.FetchResult -> {
                        if (block.url.isNotBlank()) {
                            FetchChip(block, onOpenUrl)
                        }
                    }

                    is ReplyBlock.Text -> {
                        if (block.text.isNotBlank() || isStreaming) {
                            MarkdownText(
                                text = block.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.assistantText,
                                showCursor = isStreaming && index == lastTextIndex &&
                                    index == lastBlockIndex,
                                onOpenUrl = onOpenUrl,
                            )
                        }
                    }
                }
            }

            if (isStreaming && message.blocks.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingDots()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.thinkingNow,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedText,
                    )
                }
            }

            if (message.stopReason == "refusal") {
                InfoLine(
                    text = strings.refusal,
                    color = MaterialTheme.colorScheme.error,
                    icon = Icons.Filled.ErrorOutline,
                )
            }

            if (message.status == MessageStatus.ERROR) {
                ErrorCard(
                    message = message,
                    canRetry = isLastAssistant && !isStreaming,
                    onRetry = onRegenerate,
                    onOpenSettings = onOpenSettings,
                    onTopUp = onTopUp,
                )
            } else if (message.status == MessageStatus.DONE &&
                text.isBlank() && !hasSources && message.stopReason != "refusal"
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings.emptyReply,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedText,
                    )
                    if (isLastAssistant && !isStreaming) {
                        TextButton(onClick = onRegenerate) { Text(strings.retry) }
                    }
                }
            }

            if (message.status == MessageStatus.STOPPED) {
                Text(
                    text = strings.stopped,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                )
            }

            if (!isStreaming) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (text.isNotBlank()) {
                        MessageAction(
                            icon = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            description = if (copied) strings.copied else strings.copy,
                            tint = if (copied) MaterialTheme.colorScheme.primary else colors.mutedText,
                            onClick = {
                                copied = copyText(context, text)
                            },
                        )
                        MessageAction(
                            icon = if (isReading) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                            description = if (isReading) strings.stopReading else strings.readAloud,
                            tint = if (isReading) MaterialTheme.colorScheme.primary else colors.mutedText,
                            onClick = { if (isReading) onStopReading() else onReadAloud() },
                        )
                    }
                    if (isLastAssistant) {
                        MessageAction(
                            icon = Icons.Filled.Refresh,
                            description = strings.regenerate,
                            tint = colors.mutedText,
                            onClick = onRegenerate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDeepSeekColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AttachmentRow(attachments = message.attachments)
        if (message.content.isNotBlank()) {
            Surface(
                color = colors.userBubble,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomEnd = 5.dp,
                    bottomStart = 18.dp,
                ),
            ) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.userBubbleText,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingPanel(
    block: ReplyBlock.Thinking,
    streaming: Boolean,
) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    var expanded by remember(block.startedAt) { mutableStateOf(block.endedAt == null) }
    LaunchedEffect(block.endedAt) {
        if (block.endedAt != null) expanded = false
    }
    val elapsed by produceState(
        initialValue = secondsOf(block),
        block.startedAt,
        block.endedAt,
    ) {
        while (block.endedAt == null) {
            value = secondsOf(block)
            delay(1000)
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = colors.thinkingBackground,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = if (block.endedAt != null) {
                        String.format(Locale.getDefault(), strings.thoughtFor, secondsOf(block))
                    } else {
                        "${strings.thinkingNow} ${elapsed}s"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.mutedText,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(visible = expanded && block.text.isNotBlank()) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.width(1.dp))
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        MarkdownText(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.thinkingText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchingRow(
    block: ReplyBlock.ToolUse,
    streaming: Boolean,
) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    val label = when {
        block.queries.isNotEmpty() -> String.format(
            Locale.getDefault(),
            strings.searchNow,
            block.queries.joinToString("、"),
        )

        block.kind == "read_link" && block.url != null -> "${strings.readPage}: ${block.url}"
        else -> strings.thinkingNow
    }
    Surface(
        color = colors.thinkingBackground,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (streaming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.8.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SourcesSection(
    sources: List<cn.drdream.deepseek_accountlogin.data.model.SearchSource>,
    onOpenUrl: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = colors.searchCard,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.getDefault(), strings.searchedCount, sources.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sources.forEach { source ->
                        SourceCard(source, onOpenUrl)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: cn.drdream.deepseek_accountlogin.data.model.SearchSource,
    onOpenUrl: (String) -> Unit,
) {
    val colors = LocalDeepSeekColors.current
    val host = remember(source.url) {
        runCatching { Uri.parse(source.url).host }.getOrNull()?.removePrefix("www.")
            ?: source.url
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpenUrl(source.url) },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = source.title.ifBlank { source.url },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.siteName ?: host,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                )
                if (source.pageAge != null) {
                    Text(
                        text = " · ${source.pageAge}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                    )
                }
            }
            if (!source.snippet.isNullOrBlank()) {
                Text(
                    text = source.snippet,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FetchChip(
    block: ReplyBlock.FetchResult,
    onOpenUrl: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onOpenUrl(block.url) },
        color = colors.thinkingBackground,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${strings.readPage} · ${block.title ?: block.url}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: ChatMessage,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onTopUp: () -> Unit,
) {
    val strings = LocalStrings.current
    val description = when (message.errorKind) {
        "NETWORK" -> strings.errorNetwork
        "RATE_LIMIT" -> strings.errorRateLimit
        "BALANCE" -> strings.insufficientBalance
        "AUTH" -> strings.authExpired
        else -> message.error ?: strings.errorServer
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when (message.errorKind) {
                    "BALANCE" -> TextButton(onClick = onTopUp) { Text(strings.topUp) }
                    "AUTH" -> TextButton(onClick = onOpenSettings) { Text(strings.goSettings) }
                    else -> if (canRetry) {
                        TextButton(onClick = onRetry) { Text(strings.retry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
private fun MessageAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
    }
}

private fun secondsOf(block: ReplyBlock.Thinking): Int {
    val end = block.endedAt ?: System.currentTimeMillis()
    return ((end - block.startedAt) / 1000).toInt().coerceAtLeast(1)
}
