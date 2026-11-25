package cn.drdream.deepseek_accountlogin.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.drdream.deepseek_accountlogin.AppContainer
import cn.drdream.deepseek_accountlogin.api.ApiException
import cn.drdream.deepseek_accountlogin.api.Delta
import cn.drdream.deepseek_accountlogin.api.StartBlock
import cn.drdream.deepseek_accountlogin.api.StreamEvent
import cn.drdream.deepseek_accountlogin.data.AppLanguage
import cn.drdream.deepseek_accountlogin.data.ThemeMode
import cn.drdream.deepseek_accountlogin.data.model.Attachment
import cn.drdream.deepseek_accountlogin.data.model.ChatMessage
import cn.drdream.deepseek_accountlogin.data.model.ChatSession
import cn.drdream.deepseek_accountlogin.data.model.MessageStatus
import cn.drdream.deepseek_accountlogin.data.model.Models
import cn.drdream.deepseek_accountlogin.data.model.ReplyBlock
import cn.drdream.deepseek_accountlogin.data.model.Role
import cn.drdream.deepseek_accountlogin.data.model.Usage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.Locale
import java.util.UUID

class AppViewModel(private val container: AppContainer) : ViewModel() {

    val account = container.account.state
    val settings = container.settings.state
    val sessions = container.repo.sessions

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    private val _attachmentBusy = MutableStateFlow(false)
    val attachmentBusy: StateFlow<Boolean> = _attachmentBusy.asStateFlow()

    private val _composerThinking = MutableStateFlow(settings.value.thinkingByDefault)
    val composerThinking: StateFlow<Boolean> = _composerThinking.asStateFlow()

    private val _composerSearch = MutableStateFlow(settings.value.searchByDefault)
    val composerSearch: StateFlow<Boolean> = _composerSearch.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _streamingSessionId = MutableStateFlow<String?>(null)
    val streamingSessionId: StateFlow<String?> = _streamingSessionId.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var streamJob: Job? = null

    fun activeSession(): ChatSession? = container.repo.find(_activeId.value)

    fun startNewChat() {
        _activeId.value = null
        _draft.value = ""
        clearPendingAttachments()
    }

    fun selectSession(sessionId: String) {
        if (_activeId.value == sessionId) return
        _activeId.value = sessionId
        _draft.value = ""
        clearPendingAttachments()
    }

    fun renameSession(sessionId: String, title: String) {
        container.repo.rename(sessionId, title)
    }

    fun deleteSession(sessionId: String) {
        if (_streamingSessionId.value == sessionId) stop()
        container.repo.delete(sessionId)
        if (_activeId.value == sessionId) _activeId.value = null
    }

    fun deleteAllSessions() {
        if (_sending.value) stop()
        container.repo.deleteAll()
        _activeId.value = null
    }

    fun setDraft(value: String) {
        _draft.value = value
    }

    fun setThinking(value: Boolean) {
        _composerThinking.value = value
        container.settings.setThinkingByDefault(value)
    }

    fun setSearch(value: Boolean) {
        _composerSearch.value = value
        container.settings.setSearchByDefault(value)
    }

    fun setModel(modelId: String) {
        container.settings.setModel(modelId)
    }

    fun setTheme(theme: ThemeMode) = container.settings.setTheme(theme)

    fun setLanguage(language: AppLanguage) = container.settings.setLanguage(language)

    fun clearToast() {
        _toast.value = null
    }

    fun showToast(message: String) {
        _toast.value = message
    }

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val room = 8 - _pendingAttachments.value.size
            if (room <= 0) {
                _toast.value = "最多添加 8 个附件"
                return@launch
            }
            _attachmentBusy.value = true
            val imported = ArrayList<Attachment>()
            var failure: String? = null
            for (uri in uris.take(room)) {
                try {
                    imported += container.attachments.import(uri)
                } catch (error: Exception) {
                    failure = error.message ?: "附件读取失败"
                }
            }
            _pendingAttachments.update { (it + imported).take(8) }
            _attachmentBusy.value = false
            if (failure != null) _toast.value = failure
        }
    }

    fun removePendingAttachment(attachment: Attachment) {
        _pendingAttachments.update { it.filterNot { item -> item.id == attachment.id } }
        container.attachments.delete(attachment)
    }

    fun send() {
        val text = _draft.value.trim()
        val attachments = _pendingAttachments.value
        if (text.isEmpty() && attachments.isEmpty()) return
        if (_sending.value) {
            _toast.value = "当前回复尚未结束"
            return
        }
        val apiKey = container.account.apiKey()
        if (apiKey.isNullOrBlank()) {
            _toast.value = "请先登录或填写 API Key"
            return
        }

        val existing = container.repo.find(_activeId.value)
        val session = existing ?: container.repo.createSession().also { _activeId.value = it.id }
        val generateTitle = session.title.isBlank()
        val fallbackTitle = makeTitle(text, attachments)
        val userMessage = ChatMessage(
            role = Role.USER,
            content = text,
            attachments = attachments,
        )
        val assistant = ChatMessage(
            role = Role.ASSISTANT,
            status = MessageStatus.STREAMING,
            thinkingEnabled = _composerThinking.value,
            searchEnabled = _composerSearch.value,
        )
        val updated = session.copy(
            title = session.title.ifBlank { fallbackTitle },
            messages = session.messages + userMessage + assistant,
        )
        container.repo.save(updated)
        _draft.value = ""
        _pendingAttachments.value = emptyList()
        startStream(session.id, assistant.id, userMessage.id, generateTitle)
    }

    fun stop() {
        streamJob?.cancel()
    }

    fun regenerate() {
        if (_sending.value) {
            _toast.value = "当前回复尚未结束"
            return
        }
        val session = container.repo.find(_activeId.value) ?: return
        val lastAssistantIndex = session.messages.indexOfLast { it.role == Role.ASSISTANT }
        if (lastAssistantIndex < 0) return
        val previous = session.messages[lastAssistantIndex]
        val assistant = ChatMessage(
            role = Role.ASSISTANT,
            status = MessageStatus.STREAMING,
            thinkingEnabled = previous.thinkingEnabled,
            searchEnabled = previous.searchEnabled,
        )
        val messages = session.messages.take(lastAssistantIndex) + assistant
        container.repo.save(session.copy(messages = messages))
        val userMessage = messages.lastOrNull { it.role == Role.USER } ?: return
        startStream(
            sessionId = session.id,
            assistantId = assistant.id,
            userMessageId = userMessage.id,
            generateTitle = session.title.isBlank(),
        )
    }

    fun refreshAccount() = container.account.refresh()

    fun signOut(revokeManagedKey: Boolean) {
        if (_sending.value) stop()
        container.account.signOut(revokeManagedKey)
        _activeId.value = null
    }

    fun loginWithPlatformToken(token: String, cookie: String?) {
        viewModelScope.launch {
            container.account.loginWithPlatformToken(token, cookie)
        }
    }

    fun loginWithManualKey(key: String) {
        viewModelScope.launch {
            container.account.loginWithManualKey(key)
        }
    }

    fun clearAccountError() = container.account.clearError()

    private fun startStream(
        sessionId: String,
        assistantId: String,
        userMessageId: String,
        generateTitle: Boolean,
    ) {
        _sending.value = true
        _streamingSessionId.value = sessionId
        streamJob = viewModelScope.launch {
            val apiKey = container.account.apiKey().orEmpty()
            var assistant = container.repo.find(sessionId)
                ?.messages?.firstOrNull { it.id == assistantId }
                ?: return@launch
            val blockIndexes = HashMap<Int, Int>()
            var lastPersist = 0L
            try {
                uploadAttachments(sessionId, userMessageId, apiKey)
                val current = container.repo.find(sessionId) ?: return@launch
                val request = buildRequest(current, assistantId, generateTitle)
                container.api.streamMessage(apiKey, request).collect { event ->
                    assistant = applyEvent(assistant, event, blockIndexes)
                    if (event is StreamEvent.MessageDelta && generateTitle) {
                        val title = event.title
                        if (!title.isNullOrBlank()) {
                            container.repo.update(sessionId, touch = false) {
                                it.copy(title = title.trim().take(80))
                            }
                        }
                    }
                    publishAssistant(sessionId, assistant)
                    val now = System.currentTimeMillis()
                    if (now - lastPersist > 1000) {
                        container.repo.find(sessionId)?.let { snapshot ->
                            container.repo.save(snapshot, touch = false)
                        }
                        lastPersist = now
                    }
                }
                finalize(sessionId, assistantId, MessageStatus.DONE, null, null)
            } catch (error: CancellationException) {
                finalize(sessionId, assistantId, MessageStatus.STOPPED, null, null)
                throw error
            } catch (error: ApiException) {
                val streamed = container.repo.find(sessionId)
                    ?.messages?.firstOrNull { it.id == assistantId }
                if (error.kind == ApiException.Kind.STREAM && streamed?.stopReason != null) {
                    finalize(sessionId, assistantId, MessageStatus.DONE, null, null)
                } else {
                    val kind = when (error.kind) {
                        ApiException.Kind.AUTH -> "AUTH"
                        ApiException.Kind.BALANCE -> "BALANCE"
                        ApiException.Kind.RATE_LIMIT -> "RATE_LIMIT"
                        ApiException.Kind.NETWORK -> "NETWORK"
                        else -> "ERROR"
                    }
                    finalize(sessionId, assistantId, MessageStatus.ERROR, error.message, kind)
                }
            } catch (error: Exception) {
                finalize(
                    sessionId,
                    assistantId,
                    MessageStatus.ERROR,
                    error.message ?: "生成失败",
                    "ERROR",
                )
            } finally {
                _sending.value = false
                _streamingSessionId.value = null
                streamJob = null
                container.repo.find(sessionId)?.let { container.repo.save(it, touch = true) }
            }
        }
    }

    private suspend fun uploadAttachments(
        sessionId: String,
        userMessageId: String,
        apiKey: String,
    ) {
        val session = container.repo.find(sessionId) ?: return
        val message = session.messages.firstOrNull { it.id == userMessageId } ?: return
        if (message.attachments.none { it.serverId.isNullOrBlank() }) return
        val updatedAttachments = ArrayList<Attachment>()
        for (attachment in message.attachments) {
            if (!attachment.serverId.isNullOrBlank()) {
                updatedAttachments += attachment
                continue
            }
            updatedAttachments += attachment.copy(uploading = true, error = null)
            publishUserAttachments(sessionId, userMessageId, updatedAttachments.toList())
            try {
                val uploaded = container.api.uploadFile(
                    apiKey = apiKey,
                    file = container.attachments.file(attachment),
                    mime = attachment.mime,
                    isImage = attachment.isImage,
                )
                updatedAttachments[updatedAttachments.lastIndex] =
                    attachment.copy(serverId = uploaded.id, uploading = false, error = null)
            } catch (error: Exception) {
                updatedAttachments[updatedAttachments.lastIndex] =
                    attachment.copy(uploading = false, error = error.message ?: "上传失败")
                publishUserAttachments(sessionId, userMessageId, updatedAttachments.toList())
                throw ApiException(
                    ApiException.Kind.PROTOCOL,
                    "附件上传失败：${error.message ?: "未知错误"}",
                )
            }
            publishUserAttachments(sessionId, userMessageId, updatedAttachments.toList())
        }
    }

    private fun publishUserAttachments(
        sessionId: String,
        userMessageId: String,
        attachments: List<Attachment>,
    ) {
        val session = container.repo.find(sessionId) ?: return
        container.repo.publish(
            session.copy(
                messages = session.messages.map {
                    if (it.id == userMessageId) it.copy(attachments = attachments) else it
                },
            ),
        )
    }

    private fun publishAssistant(sessionId: String, assistant: ChatMessage) {
        val session = container.repo.find(sessionId) ?: return
        container.repo.publish(
            session.copy(
                messages = session.messages.map { if (it.id == assistant.id) assistant else it },
            ),
        )
    }

    private fun finalize(
        sessionId: String,
        assistantId: String,
        status: MessageStatus,
        error: String?,
        errorKind: String?,
    ) {
        val session = container.repo.find(sessionId) ?: return
        val now = System.currentTimeMillis()
        val messages = session.messages.map { message ->
            if (message.id != assistantId || message.role != Role.ASSISTANT) {
                message
            } else {
                var blocks = message.blocks.map { block ->
                    if (block is ReplyBlock.Thinking && block.endedAt == null) {
                        block.copy(endedAt = now)
                    } else {
                        block
                    }
                }
                if (message.stopReason == "refusal") {
                    blocks = blocks.filterNot {
                        it is ReplyBlock.Text || it is ReplyBlock.Thinking
                    }
                }
                message.copy(
                    status = status,
                    error = error,
                    errorKind = errorKind,
                    blocks = blocks,
                )
            }
        }
        val title = session.title.ifBlank { "新对话" }
        container.repo.save(session.copy(title = title, messages = messages), touch = true)
    }

    private fun applyEvent(
        reply: ChatMessage,
        event: StreamEvent,
        blockIndexes: MutableMap<Int, Int>,
    ): ChatMessage {
        val next = if (reply.status == MessageStatus.STREAMING) {
            reply
        } else {
            reply.copy(status = MessageStatus.STREAMING)
        }
        return when (event) {
            is StreamEvent.MessageStart -> next.copy(usage = event.usage ?: next.usage)
            is StreamEvent.BlockStart -> {
                blockIndexes.remove(event.index)
                val block = when (val start = event.block) {
                    is StartBlock.Text -> ReplyBlock.Text(start.text, start.citations)
                    is StartBlock.Thinking -> ReplyBlock.Thinking(
                        text = start.text,
                        startedAt = System.currentTimeMillis(),
                    )

                    is StartBlock.ServerToolUse -> ReplyBlock.ToolUse(
                        kind = start.kind,
                        queries = start.queries,
                        url = start.url,
                    )

                    is StartBlock.SearchResults -> ReplyBlock.SearchResults(start.sources)
                    is StartBlock.FetchResult -> ReplyBlock.FetchResult(start.url, start.title)
                    is StartBlock.Ignored -> null
                }
                if (block == null) {
                    next
                } else {
                    blockIndexes[event.index] = next.blocks.size
                    next.copy(blocks = next.blocks + block)
                }
            }

            is StreamEvent.BlockDelta -> {
                val index = blockIndexes[event.index] ?: return next
                val blocks = next.blocks.toMutableList()
                val block = blocks.getOrNull(index) ?: return next
                blocks[index] = when {
                    block is ReplyBlock.Text && event.delta is Delta.Text ->
                        block.copy(text = block.text + event.delta.text)

                    block is ReplyBlock.Thinking && event.delta is Delta.Thinking ->
                        block.copy(text = block.text + event.delta.thinking)

                    block is ReplyBlock.Text && event.delta is Delta.CitationDelta ->
                        block.copy(citations = block.citations + event.delta.citation)

                    else -> block
                }
                next.copy(blocks = blocks)
            }

            is StreamEvent.BlockStop -> {
                val index = blockIndexes.remove(event.index) ?: return next
                val blocks = next.blocks.toMutableList()
                val block = blocks.getOrNull(index)
                if (block is ReplyBlock.Thinking && block.endedAt == null) {
                    blocks[index] = block.copy(endedAt = System.currentTimeMillis())
                }
                next.copy(blocks = blocks)
            }

            is StreamEvent.MessageDelta -> next.copy(
                stopReason = event.stopReason ?: next.stopReason,
                usage = event.usage ?: next.usage,
            )

            StreamEvent.MessageStop -> next
        }
    }

    private fun buildRequest(
        session: ChatSession,
        assistantId: String,
        generateTitle: Boolean,
    ): JSONObject {
        val assistantIndex = session.messages.indexOfFirst { it.id == assistantId }
        val currentUserIndex = if (assistantIndex > 0) assistantIndex - 1 else -1
        val requestMessage = session.messages.getOrNull(assistantIndex)
        val messages = JSONArray()
        val reminder = latestReminder()

        if (currentUserIndex > 0) {
            messages.put(JSONObject().put("role", "latest_reminder").put("content", reminder))
        }
        var index = 0
        while (index < currentUserIndex) {
            val user = session.messages[index]
            if (user.role != Role.USER) {
                index++
                continue
            }
            val reply = session.messages.getOrNull(index + 1)
            if (reply == null || reply.role != Role.ASSISTANT ||
                reply.status !in setOf(MessageStatus.DONE, MessageStatus.STOPPED) ||
                reply.visibleText().isBlank()
            ) {
                index++
                continue
            }
            messages.put(userParam(user))
            messages.put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", reply.visibleText()),
            )
            index += 2
        }

        if (currentUserIndex >= 0) {
            val current = session.messages[currentUserIndex]
            if (currentUserIndex == 0) {
                messages.put(JSONObject().put("role", "latest_reminder").put("content", reminder))
            }
            messages.put(userParam(current))
        }

        val tools = JSONArray().put(
            JSONObject().put("type", "deepseek_read_url").put("name", "deepseek_read_url"),
        )
        if (requestMessage?.searchEnabled == true) {
            tools.put(
                JSONObject().put("type", "deepseek_web_search").put("name", "deepseek_web_search"),
            )
        }

        return JSONObject().apply {
            put("model", Models.find(container.settings.state.value.model).id)
            put("system", SYSTEM_PROMPT)
            put("messages", messages)
            put(
                "thinking",
                JSONObject().put(
                    "type",
                    if (requestMessage?.thinkingEnabled == true) {
                        "enabled"
                    } else {
                        "disabled"
                    },
                ),
            )
            put("tools", tools)
            put("safety_check", JSONObject().put("level", "high"))
            put("stream", true)
            if (generateTitle) put("title", JSONObject().put("enabled", true))
        }
    }

    private fun userParam(message: ChatMessage): JSONObject {
        val files = JSONArray()
        message.attachments.forEach { attachment ->
            val serverId = attachment.serverId
            if (!serverId.isNullOrBlank()) {
                files.put(
                    JSONObject()
                        .put("type", if (attachment.isImage) "image" else "document")
                        .put(
                            "source",
                            JSONObject().put("type", "file").put("file_id", serverId),
                        ),
                )
            }
        }
        val json = JSONObject().put("role", "user")
        if (files.length() == 0) {
            json.put("content", message.content)
        } else {
            if (message.content.isNotBlank()) {
                files.put(JSONObject().put("type", "text").put("text", message.content))
            }
            json.put("content", files)
        }
        return json
    }

    private fun latestReminder(): String {
        val settings = container.settings.state.value
        val locale = if (settings.language == AppLanguage.ZH) {
            Locale.SIMPLIFIED_CHINESE
        } else {
            Locale.US
        }
        val now = Date()
        val region = Locale.getDefault().let {
            if (it.country.equals("CN", ignoreCase = true)) {
                "中国"
            } else {
                it.displayCountry.ifBlank { "未知" }
            }
        }
        val language = if (settings.language == AppLanguage.ZH) "中文" else "English"
        return LatestReminder.build(now, locale, region, language)
    }

    private fun makeTitle(text: String, attachments: List<Attachment>): String {
        val source = text.ifBlank {
            attachments.firstOrNull()?.name ?: "新对话"
        }
        return source.replace(Regex("\\s+"), " ").trim().take(40).ifBlank { "新对话" }
    }

    private fun clearPendingAttachments() {
        _pendingAttachments.value.forEach { container.attachments.delete(it) }
        _pendingAttachments.value = emptyList()
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppViewModel(container) as T
        }
    }

    private companion object {
        const val SYSTEM_PROMPT = "该助手为DeepSeek，由深度求索公司创造。"
    }
}
