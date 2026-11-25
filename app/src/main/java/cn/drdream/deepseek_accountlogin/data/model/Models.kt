package cn.drdream.deepseek_accountlogin.data.model

import java.util.UUID

enum class Role { USER, ASSISTANT }

enum class MessageStatus { STREAMING, DONE, STOPPED, ERROR }

enum class KeySource { NONE, MANAGED, MANUAL }

data class Citation(
    val url: String,
    val title: String?,
    val citedText: String,
)

data class SearchSource(
    val title: String,
    val url: String,
    val snippet: String?,
    val siteName: String?,
    val pageAge: String?,
)

sealed interface ReplyBlock {
    data class Text(
        val text: String,
        val citations: List<Citation> = emptyList(),
    ) : ReplyBlock

    data class Thinking(
        val text: String,
        val startedAt: Long,
        val endedAt: Long? = null,
    ) : ReplyBlock

    data class ToolUse(
        val kind: String,
        val queries: List<String> = emptyList(),
        val url: String? = null,
    ) : ReplyBlock

    data class SearchResults(
        val sources: List<SearchSource>,
    ) : ReplyBlock

    data class FetchResult(
        val url: String,
        val title: String?,
    ) : ReplyBlock
}

data class Usage(
    val inputTokens: Int = 0,
    val cacheCreationInputTokens: Int = 0,
    val cacheReadInputTokens: Int = 0,
    val outputTokens: Int = 0,
)

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val name: String,
    val mime: String,
    val size: Long,
    val isImage: Boolean,
    val serverId: String? = null,
    val uploading: Boolean = false,
    val error: String? = null,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val createdAt: Long = System.currentTimeMillis(),
    /** User prompt text. Assistant text lives in [blocks]. */
    val content: String = "",
    val attachments: List<Attachment> = emptyList(),
    val blocks: List<ReplyBlock> = emptyList(),
    val status: MessageStatus = MessageStatus.DONE,
    val error: String? = null,
    val errorKind: String? = null,
    val stopReason: String? = null,
    val thinkingEnabled: Boolean = false,
    val searchEnabled: Boolean = false,
    val usage: Usage? = null,
) {
    fun visibleText(): String = blocks.filterIsInstance<ReplyBlock.Text>().joinToString("") { it.text }

    fun hasVisibleContent(): Boolean =
        blocks.any { it is ReplyBlock.Text && it.text.isNotBlank() } ||
            blocks.any { it is ReplyBlock.SearchResults && it.sources.isNotEmpty() }
}

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
)

data class ModelOption(
    val id: String,
    val labelZh: String,
    val labelEn: String,
    val descZh: String,
    val descEn: String,
)

object Models {
    const val FLASH = "deepseek-flash"
    const val PRO = "deepseek-v4-pro"

    val all = listOf(
        ModelOption(
            id = FLASH,
            labelZh = "DeepSeek Flash",
            labelEn = "DeepSeek Flash",
            descZh = "快速、均衡，支持深度思考与图片理解",
            descEn = "Fast and balanced, with thinking and image input",
        ),
        ModelOption(
            id = PRO,
            labelZh = "DeepSeek V4 Pro",
            labelEn = "DeepSeek V4 Pro",
            descZh = "更强的推理与创作能力，适合复杂任务",
            descEn = "Stronger reasoning for complex tasks",
        ),
    )

    fun find(id: String): ModelOption = all.firstOrNull { it.id == id } ?: all.first()
}
