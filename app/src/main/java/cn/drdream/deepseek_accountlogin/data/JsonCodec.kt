package cn.drdream.deepseek_accountlogin.data

import cn.drdream.deepseek_accountlogin.data.model.Attachment
import cn.drdream.deepseek_accountlogin.data.model.ChatMessage
import cn.drdream.deepseek_accountlogin.data.model.ChatSession
import cn.drdream.deepseek_accountlogin.data.model.Citation
import cn.drdream.deepseek_accountlogin.data.model.MessageStatus
import cn.drdream.deepseek_accountlogin.data.model.ReplyBlock
import cn.drdream.deepseek_accountlogin.data.model.Role
import cn.drdream.deepseek_accountlogin.data.model.SearchSource
import cn.drdream.deepseek_accountlogin.data.model.Usage
import org.json.JSONArray
import org.json.JSONObject

/** Durable JSON representation for the single-table history store. */
object JsonCodec {

    fun encodeSession(session: ChatSession): String = sessionToJson(session).toString()

    fun decodeSession(raw: String): ChatSession? = runCatching {
        sessionFromJson(JSONObject(raw))
    }.getOrNull()

    private fun sessionToJson(session: ChatSession): JSONObject = JSONObject().apply {
        put("id", session.id)
        put("title", session.title)
        put("createdAt", session.createdAt)
        put("updatedAt", session.updatedAt)
        put("messages", JSONArray().apply {
            session.messages.forEach { put(messageToJson(it)) }
        })
    }

    private fun sessionFromJson(json: JSONObject): ChatSession {
        val messages = json.optJSONArray("messages") ?: JSONArray()
        return ChatSession(
            id = json.optString("id"),
            title = json.optString("title"),
            createdAt = json.optLong("createdAt"),
            updatedAt = json.optLong("updatedAt"),
            messages = (0 until messages.length()).mapNotNull { index ->
                messages.optJSONObject(index)?.let(::messageFromJson)
            },
        )
    }

    private fun messageToJson(message: ChatMessage): JSONObject = JSONObject().apply {
        put("id", message.id)
        put("role", message.role.name)
        put("createdAt", message.createdAt)
        put("content", message.content)
        put("status", message.status.name)
        message.error?.let { put("error", it) }
        message.errorKind?.let { put("errorKind", it) }
        message.stopReason?.let { put("stopReason", it) }
        put("thinkingEnabled", message.thinkingEnabled)
        put("searchEnabled", message.searchEnabled)
        message.usage?.let {
            put("usage", JSONObject().apply {
                put("inputTokens", it.inputTokens)
                put("cacheCreationInputTokens", it.cacheCreationInputTokens)
                put("cacheReadInputTokens", it.cacheReadInputTokens)
                put("outputTokens", it.outputTokens)
            })
        }
        put("attachments", JSONArray().apply {
            message.attachments.forEach { put(attachmentToJson(it)) }
        })
        put("blocks", JSONArray().apply {
            message.blocks.forEach { put(blockToJson(it)) }
        })
    }

    private fun messageFromJson(json: JSONObject): ChatMessage {
        val attachments = json.optJSONArray("attachments") ?: JSONArray()
        val blocks = json.optJSONArray("blocks") ?: JSONArray()
        return ChatMessage(
            id = json.optString("id"),
            role = runCatching { Role.valueOf(json.optString("role")) }.getOrDefault(Role.USER),
            createdAt = json.optLong("createdAt"),
            content = json.optString("content"),
            attachments = (0 until attachments.length()).mapNotNull { index ->
                attachments.optJSONObject(index)?.let(::attachmentFromJson)
            },
            blocks = (0 until blocks.length()).mapNotNull { index ->
                blocks.optJSONObject(index)?.let(::blockFromJson)
            },
            status = runCatching { MessageStatus.valueOf(json.optString("status")) }
                .getOrDefault(MessageStatus.DONE),
            error = json.optString("error").takeIf { it.isNotBlank() },
            errorKind = json.optString("errorKind").takeIf { it.isNotBlank() },
            stopReason = json.optString("stopReason").takeIf { it.isNotBlank() },
            thinkingEnabled = json.optBoolean("thinkingEnabled"),
            searchEnabled = json.optBoolean("searchEnabled"),
            usage = json.optJSONObject("usage")?.let {
                Usage(
                    inputTokens = it.optInt("inputTokens"),
                    cacheCreationInputTokens = it.optInt("cacheCreationInputTokens"),
                    cacheReadInputTokens = it.optInt("cacheReadInputTokens"),
                    outputTokens = it.optInt("outputTokens"),
                )
            },
        )
    }

    private fun attachmentToJson(attachment: Attachment): JSONObject = JSONObject().apply {
        put("id", attachment.id)
        put("path", attachment.path)
        put("name", attachment.name)
        put("mime", attachment.mime)
        put("size", attachment.size)
        put("isImage", attachment.isImage)
        put("localOnly", attachment.localOnly)
        attachment.serverId?.let { put("serverId", it) }
        put("uploading", attachment.uploading)
        attachment.error?.let { put("error", it) }
    }

    private fun attachmentFromJson(json: JSONObject): Attachment = Attachment(
        id = json.optString("id"),
        path = json.optString("path"),
        name = json.optString("name"),
        mime = json.optString("mime"),
        size = json.optLong("size"),
        isImage = json.optBoolean("isImage"),
        localOnly = json.optBoolean("localOnly"),
        serverId = json.optString("serverId").takeIf { it.isNotBlank() },
        uploading = json.optBoolean("uploading"),
        error = json.optString("error").takeIf { it.isNotBlank() },
    )

    private fun blockToJson(block: ReplyBlock): JSONObject = when (block) {
        is ReplyBlock.Text -> JSONObject().apply {
            put("type", "text")
            put("text", block.text)
            put("citations", JSONArray().apply {
                block.citations.forEach { citation ->
                    put(JSONObject().apply {
                        put("url", citation.url)
                        citation.title?.let { put("title", it) }
                        put("citedText", citation.citedText)
                    })
                }
            })
        }

        is ReplyBlock.Thinking -> JSONObject().apply {
            put("type", "thinking")
            put("text", block.text)
            put("startedAt", block.startedAt)
            block.endedAt?.let { put("endedAt", it) }
        }

        is ReplyBlock.ToolUse -> JSONObject().apply {
            put("type", "tool")
            put("kind", block.kind)
            put("queries", JSONArray(block.queries))
            block.url?.let { put("url", it) }
        }

        is ReplyBlock.SearchResults -> JSONObject().apply {
            put("type", "search")
            put("sources", JSONArray().apply {
                block.sources.forEach { source ->
                    put(JSONObject().apply {
                        put("title", source.title)
                        put("url", source.url)
                        source.snippet?.let { put("snippet", it) }
                        source.siteName?.let { put("siteName", it) }
                        source.pageAge?.let { put("pageAge", it) }
                    })
                }
            })
        }

        is ReplyBlock.FetchResult -> JSONObject().apply {
            put("type", "fetch")
            put("url", block.url)
            block.title?.let { put("title", it) }
        }
    }

    private fun blockFromJson(json: JSONObject): ReplyBlock? = when (json.optString("type")) {
        "text" -> {
            val citations = json.optJSONArray("citations") ?: JSONArray()
            ReplyBlock.Text(
                text = json.optString("text"),
                citations = (0 until citations.length()).mapNotNull { index ->
                    citations.optJSONObject(index)?.let {
                        Citation(
                            url = it.optString("url"),
                            title = it.optString("title").takeIf { value -> value.isNotBlank() },
                            citedText = it.optString("citedText"),
                        )
                    }
                },
            )
        }

        "thinking" -> ReplyBlock.Thinking(
            text = json.optString("text"),
            startedAt = json.optLong("startedAt"),
            endedAt = if (json.has("endedAt")) json.optLong("endedAt") else null,
        )

        "tool" -> ReplyBlock.ToolUse(
            kind = json.optString("kind"),
            queries = json.optJSONArray("queries")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it) }
            }.orEmpty(),
            url = json.optString("url").takeIf { it.isNotBlank() },
        )

        "search" -> {
            val sources = json.optJSONArray("sources") ?: JSONArray()
            ReplyBlock.SearchResults(
                sources = (0 until sources.length()).mapNotNull { index ->
                    sources.optJSONObject(index)?.let {
                        SearchSource(
                            title = it.optString("title"),
                            url = it.optString("url"),
                            snippet = it.optString("snippet").takeIf { value -> value.isNotBlank() },
                            siteName = it.optString("siteName").takeIf { value -> value.isNotBlank() },
                            pageAge = it.optString("pageAge").takeIf { value -> value.isNotBlank() },
                        )
                    }
                },
            )
        }

        "fetch" -> ReplyBlock.FetchResult(
            url = json.optString("url"),
            title = json.optString("title").takeIf { it.isNotBlank() },
        )

        else -> null
    }
}
