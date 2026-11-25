package cn.drdream.deepseek_accountlogin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.drdream.deepseek_accountlogin.data.AccountState
import cn.drdream.deepseek_accountlogin.data.AppLanguage
import cn.drdream.deepseek_accountlogin.data.AppSettings
import cn.drdream.deepseek_accountlogin.data.model.ChatSession
import cn.drdream.deepseek_accountlogin.data.model.Models
import cn.drdream.deepseek_accountlogin.data.model.ReplyBlock
import cn.drdream.deepseek_accountlogin.data.model.Role
import cn.drdream.deepseek_accountlogin.ui.AppViewModel
import cn.drdream.deepseek_accountlogin.ui.components.AssistantMessageItem
import cn.drdream.deepseek_accountlogin.ui.components.ChatInputBar
import cn.drdream.deepseek_accountlogin.ui.components.UserMessageItem
import cn.drdream.deepseek_accountlogin.ui.components.WelcomeContent
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import cn.drdream.deepseek_accountlogin.ui.speech.TtsController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScaffold(
    viewModel: AppViewModel,
    active: ChatSession?,
    account: AccountState,
    settings: AppSettings,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onRename: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val pending by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val attachmentBusy by viewModel.attachmentBusy.collectAsStateWithLifecycle()
    val thinking by viewModel.composerThinking.collectAsStateWithLifecycle()
    val search by viewModel.composerSearch.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val streamingSessionId by viewModel.streamingSessionId.collectAsStateWithLifecycle()

    val messages = active?.messages.orEmpty()
    val streamingHere = streamingSessionId != null && streamingSessionId == active?.id

    val tts = remember { TtsController(context) }
    var readingId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        tts.onFinished = { readingId = null }
        onDispose {
            readingId = null
            tts.shutdown()
        }
    }
    val languageTag = if (settings.language == AppLanguage.ZH) "zh-CN" else "en-US"
    val openLink: (String) -> Unit = { url ->
        if (url.startsWith("https://platform.deepseek.com")) {
            onOpenWeb(url, strings.platformTitle)
        } else {
            onOpenExternal(url)
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    suspend fun scrollToBottom() {
        if (messages.isEmpty()) return
        listState.scrollToItem(messages.lastIndex)
        runCatching { listState.scrollBy(20_000f) }
    }
    var previousMessageCount by remember(active?.id) { mutableStateOf(messages.size) }
    LaunchedEffect(active?.id) {
        previousMessageCount = messages.size
        if (messages.isNotEmpty()) scrollToBottom()
    }
    val lastLength = messages.lastOrNull()?.let {
        it.visibleText().length + it.content.length
    } ?: 0
    LaunchedEffect(messages.size, lastLength, streamingHere) {
        val appended = messages.size > previousMessageCount
        previousMessageCount = messages.size
        if (messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (appended || messages.size <= 2 || lastVisible >= messages.lastIndex - 1) {
                scrollToBottom()
            }
        }
        if (readingId != null) {
            val stillThere = messages.any { it.id == readingId }
            if (!stillThere) {
                tts.stop()
                readingId = null
            }
        }
    }
    val showScrollDown by remember {
        derivedStateOf { listState.canScrollForward && messages.size > 2 }
    }

    var modelMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = active?.title?.ifBlank { strings.untitled } ?: strings.appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { modelMenu = true }) {
                            Text(
                                text = shortModelName(settings.model),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = modelMenu,
                            onDismissRequest = { modelMenu = false },
                        ) {
                            Models.all.forEach { option ->
                                DropdownMenuItem(
                                    text = {
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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        if (settings.model == option.id) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setModel(option.id)
                                        modelMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.startNewChat() }) {
                        Icon(Icons.Filled.Add, contentDescription = strings.newChat)
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = strings.more)
                        }
                        DropdownMenu(
                            expanded = moreMenu,
                            onDismissRequest = { moreMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.settings) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                },
                                onClick = {
                                    moreMenu = false
                                    onOpenSettings()
                                },
                            )
                            if (active != null) {
                                DropdownMenuItem(
                                    text = { Text(strings.rename) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        moreMenu = false
                                        onRename(active)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.delete) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Delete, contentDescription = null)
                                    },
                                    onClick = {
                                        moreMenu = false
                                        onDelete(active)
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            ChatInputBar(
                draft = draft,
                onDraftChange = viewModel::setDraft,
                pendingAttachments = pending,
                attachmentBusy = attachmentBusy,
                thinking = thinking,
                search = search,
                isSending = streamingHere && sending,
                languageTag = languageTag,
                onThinkingChange = viewModel::setThinking,
                onSearchChange = viewModel::setSearch,
                onSend = viewModel::send,
                onStop = viewModel::stop,
                onAddAttachments = viewModel::addAttachments,
                onRemoveAttachment = viewModel::removePendingAttachment,
                onVoiceUnavailable = { viewModel.showToast(strings.voiceUnavailable) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (messages.isEmpty()) {
                WelcomeContent(
                    strings = strings,
                    onSuggestion = { viewModel.setDraft(it) },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { _, message -> message.id },
                        contentType = { _, message -> message.role },
                    ) { index, message ->
                        when (message.role) {
                            Role.USER -> UserMessageItem(message = message)
                            Role.ASSISTANT -> AssistantMessageItem(
                                message = message,
                                isLastAssistant = index == messages.lastIndex,
                                isStreaming = streamingHere && index == messages.lastIndex,
                                isReading = readingId == message.id,
                                onRegenerate = viewModel::regenerate,
                                onReadAloud = {
                                    if (tts.speak(message.visibleText(), languageTag)) {
                                        readingId = message.id
                                    } else {
                                        viewModel.showToast(strings.voiceUnavailable)
                                    }
                                },
                                onStopReading = {
                                    tts.stop()
                                    readingId = null
                                },
                                onOpenUrl = openLink,
                                onOpenSettings = onOpenSettings,
                                onTopUp = {
                                    onOpenWeb(
                                        "https://platform.deepseek.com/top_up",
                                        strings.topUpTitle,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            if (showScrollDown) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { scrollToBottom() } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = strings.scrollToBottom,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun shortModelName(model: String): String = when (model) {
    Models.PRO -> "V4 Pro"
    else -> "Flash"
}
