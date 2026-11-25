package cn.drdream.deepseek_accountlogin.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.drdream.deepseek_accountlogin.BuildConfig
import cn.drdream.deepseek_accountlogin.data.AccountState
import cn.drdream.deepseek_accountlogin.data.model.ChatSession
import cn.drdream.deepseek_accountlogin.data.model.KeySource
import cn.drdream.deepseek_accountlogin.ui.components.AccountAvatar
import cn.drdream.deepseek_accountlogin.ui.components.BrandMark
import cn.drdream.deepseek_accountlogin.ui.components.SoftBadge
import cn.drdream.deepseek_accountlogin.ui.components.formatBalance
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import cn.drdream.deepseek_accountlogin.ui.theme.LocalDeepSeekColors
import java.util.Calendar

private enum class SessionGroup { TODAY, YESTERDAY, WEEK, EARLIER }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionDrawer(
    sessions: List<ChatSession>,
    activeId: String?,
    account: AccountState,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    val grouped = remember(sessions) {
        sessions.groupBy { groupOf(it.updatedAt) }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(310.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(size = 32.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = strings.tagline,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = strings.close,
                        tint = colors.mutedText,
                    )
                }
            }

            Button(
                onClick = onNewChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.newChat)
            }

            Text(
                text = strings.chatHistory,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText,
                fontWeight = FontWeight.Medium,
            )

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = strings.noChats,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedText,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 10.dp,
                        end = 10.dp,
                        bottom = 10.dp,
                    ),
                ) {
                    SessionGroup.entries.forEach { group ->
                        val entries = grouped[group].orEmpty()
                        if (entries.isNotEmpty()) {
                            item(key = "header-${group.name}") {
                                Text(
                                    text = groupLabel(group, strings),
                                    modifier = Modifier.padding(
                                        start = 10.dp,
                                        top = 12.dp,
                                        bottom = 5.dp,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.mutedText,
                                )
                            }
                            items(entries, key = { it.id }) { session ->
                                SessionRow(
                                    session = session,
                                    selected = session.id == activeId,
                                    onSelect = { onSelect(session.id) },
                                    onRename = { onRename(session) },
                                    onDelete = { onDelete(session) },
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(onClick = onOpenSettings, onLongClick = onOpenSettings)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(
                    name = account.profile?.displayName,
                    size = 38.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.profile?.displayName ?: strings.account,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (account.keySource == KeySource.MANAGED) {
                            Spacer(modifier = Modifier.width(6.dp))
                            SoftBadge(text = strings.appManaged)
                        }
                    }
                    Text(
                        text = if (account.wallets.isNotEmpty()) {
                            formatBalance(account.wallets)
                        } else {
                            strings.loginToSeeBalance
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = strings.settings,
                    tint = colors.mutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "${strings.version} ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: ChatSession,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    var menuOpen by remember(session.id) { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    else androidx.compose.ui.graphics.Color.Transparent,
                )
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = { menuOpen = true },
                )
                .padding(start = 11.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = session.title.ifBlank { strings.untitled },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = strings.more,
                        tint = colors.mutedText,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(strings.rename) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(strings.delete) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

private fun groupOf(timestamp: Long): SessionGroup {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val startOfToday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfThen = Calendar.getInstance().apply {
        timeInMillis = then.timeInMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val diff = startOfToday - startOfThen
    return when {
        diff <= 0 -> SessionGroup.TODAY
        diff <= 24L * 60 * 60 * 1000 -> SessionGroup.YESTERDAY
        diff <= 7L * 24 * 60 * 60 * 1000 -> SessionGroup.WEEK
        else -> SessionGroup.EARLIER
    }
}

private fun groupLabel(
    group: SessionGroup,
    strings: cn.drdream.deepseek_accountlogin.ui.i18n.Strings,
): String = when (group) {
    SessionGroup.TODAY -> strings.today
    SessionGroup.YESTERDAY -> strings.yesterday
    SessionGroup.WEEK -> strings.last7Days
    SessionGroup.EARLIER -> strings.earlier
}
