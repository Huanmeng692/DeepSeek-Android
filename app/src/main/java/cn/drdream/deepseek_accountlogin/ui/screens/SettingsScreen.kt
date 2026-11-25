package cn.drdream.deepseek_accountlogin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.drdream.deepseek_accountlogin.BuildConfig
import cn.drdream.deepseek_accountlogin.data.AccountState
import cn.drdream.deepseek_accountlogin.data.AppLanguage
import cn.drdream.deepseek_accountlogin.data.AppSettings
import cn.drdream.deepseek_accountlogin.data.ThemeMode
import cn.drdream.deepseek_accountlogin.data.model.KeySource
import cn.drdream.deepseek_accountlogin.data.model.Models
import cn.drdream.deepseek_accountlogin.ui.AppViewModel
import cn.drdream.deepseek_accountlogin.ui.components.AccountAvatar
import cn.drdream.deepseek_accountlogin.ui.components.SoftBadge
import cn.drdream.deepseek_accountlogin.ui.components.bonusText
import cn.drdream.deepseek_accountlogin.ui.components.formatWallet
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import cn.drdream.deepseek_accountlogin.ui.theme.LocalDeepSeekColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    account: AccountState,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    var showSignOut by remember { mutableStateOf(false) }
    var showDeleteAll by remember { mutableStateOf(false) }
    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(strings.settings) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SectionTitle(strings.account) }
            item {
                AccountCard(
                    account = account,
                    onRefresh = viewModel::refreshAccount,
                    onTopUp = {
                        onOpenWeb(
                            "https://platform.deepseek.com/top_up",
                            strings.topUpTitle,
                        )
                    },
                    onUsage = {
                        onOpenWeb(
                            "https://platform.deepseek.com/usage",
                            strings.usageDetails,
                        )
                    },
                    onSignOut = { showSignOut = true },
                )
            }

            item { SectionTitle(strings.chatSettings) }
            item {
                CardContainer {
                    Models.all.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setModel(option.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.model == option.id,
                                onClick = { viewModel.setModel(option.id) },
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (settings.language == AppLanguage.ZH) {
                                        option.labelZh
                                    } else {
                                        option.labelEn
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = if (settings.language == AppLanguage.ZH) {
                                        option.descZh
                                    } else {
                                        option.descEn
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.mutedText,
                                )
                            }
                        }
                        if (index != Models.all.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = colors.divider,
                                thickness = 0.7.dp,
                            )
                        }
                    }
                    HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                    SwitchRow(
                        title = strings.defaultThinking,
                        subtitle = strings.defaultThinkingDesc,
                        checked = settings.thinkingByDefault,
                        onCheckedChange = viewModel::setThinking,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = colors.divider,
                        thickness = 0.7.dp,
                    )
                    SwitchRow(
                        title = strings.defaultSearch,
                        subtitle = strings.defaultSearchDesc,
                        checked = settings.searchByDefault,
                        onCheckedChange = viewModel::setSearch,
                    )
                }
            }

            item { SectionTitle(strings.appearance) }
            item {
                CardContainer {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = strings.theme,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.theme == mode,
                                    onClick = { viewModel.setTheme(mode) },
                                    label = {
                                        Text(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> strings.themeSystem
                                                ThemeMode.LIGHT -> strings.themeLight
                                                ThemeMode.DARK -> strings.themeDark
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = strings.language,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.language == AppLanguage.ZH,
                                onClick = { viewModel.setLanguage(AppLanguage.ZH) },
                                label = { Text(strings.chinese) },
                            )
                            FilterChip(
                                selected = settings.language == AppLanguage.EN,
                                onClick = { viewModel.setLanguage(AppLanguage.EN) },
                                label = { Text(strings.english) },
                            )
                        }
                    }
                }
            }

            item { SectionTitle(strings.data) }
            item {
                CardContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteAll = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.deleteAllChats,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item { SectionTitle(strings.about) }
            item {
                CardContainer {
                    SettingsLinkRow(
                        leading = Icons.Filled.Info,
                        title = strings.officialPlatform,
                        subtitle = "platform.deepseek.com",
                        onClick = {
                            onOpenWeb(
                                "https://platform.deepseek.com",
                                strings.platformTitle,
                            )
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = colors.divider,
                        thickness = 0.7.dp,
                    )
                    SettingsLinkRow(
                        leading = Icons.Filled.Key,
                        title = strings.apiKeysTitle,
                        subtitle = strings.manageApiKeys,
                        onClick = {
                            onOpenWeb(
                                "https://platform.deepseek.com/api_keys",
                                strings.apiKeysTitle,
                            )
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = colors.divider,
                        thickness = 0.7.dp,
                    )
                    SettingsLinkRow(
                        leading = Icons.Filled.Language,
                        title = strings.apiDocs,
                        subtitle = "api-docs.deepseek.com",
                        onClick = {
                            onOpenWeb(
                                "https://api-docs.deepseek.com/zh-cn/",
                                strings.docsTitle,
                            )
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = colors.divider,
                        thickness = 0.7.dp,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.version,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.mutedText,
                        )
                    }
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = strings.disclaimerTitle,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = strings.disclaimerBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.mutedText,
                        )
                    }
                }
            }
        }
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            title = { Text(strings.signOutTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.signOutMessage)
                    if (account.keySource == KeySource.MANAGED) {
                        Text(
                            text = strings.signOutManagedNote,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOut = false
                        viewModel.signOut(true)
                    },
                ) {
                    Text(strings.signOut, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOut = false }) { Text(strings.cancel) }
            },
        )
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text(strings.deleteAllChats) },
            text = { Text(strings.deleteAllMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAll = false
                        viewModel.deleteAllSessions()
                    },
                ) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
private fun AccountCard(
    account: AccountState,
    onRefresh: () -> Unit,
    onTopUp: () -> Unit,
    onUsage: () -> Unit,
    onSignOut: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalDeepSeekColors.current
    val bonus = bonusText(account.wallets, strings)
    CardContainer {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(name = account.profile?.displayName, size = 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.profile?.displayName ?: strings.accountNotSignedIn,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (account.keySource == KeySource.MANAGED) {
                            Spacer(modifier = Modifier.width(6.dp))
                            SoftBadge(text = strings.appManaged)
                        }
                    }
                    Text(
                        text = account.profile?.contact ?: account.keyMasked.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (account.keySource == KeySource.MANAGED) {
                            strings.managedKey
                        } else {
                            strings.manualKey
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                    )
                    Text(
                        text = account.keyMasked ?: "—",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = strings.keyOnlyMasked,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.balance,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (account.balanceLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                    )
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = strings.refreshBalance,
                            tint = colors.mutedText,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (account.wallets.isEmpty()) {
                Text(
                    text = strings.noBalance,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
            } else {
                account.wallets.forEach { wallet ->
                    Text(
                        text = formatWallet(wallet),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (bonus != null) {
                    Text(
                        text = "${strings.bonus} $bonus",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                    )
                }
            }
            if (account.balanceError != null) {
                Text(
                    text = account.balanceError,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onTopUp,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.recharge)
                }
                OutlinedButton(
                    onClick = onUsage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.usageDetails)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.signOut, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CardContainer(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column { content() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 30.dp, end = 30.dp, top = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun SettingsLinkRow(
    leading: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = leading,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
