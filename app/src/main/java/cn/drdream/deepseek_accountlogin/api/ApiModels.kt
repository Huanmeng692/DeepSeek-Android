package cn.drdream.deepseek_accountlogin.api

import cn.drdream.deepseek_accountlogin.data.model.Citation
import cn.drdream.deepseek_accountlogin.data.model.SearchSource
import cn.drdream.deepseek_accountlogin.data.model.Usage

class ApiException(
    val kind: Kind,
    override val message: String,
    val status: Int? = null,
) : Exception(message) {
    enum class Kind { AUTH, BALANCE, RATE_LIMIT, HTTP, NETWORK, STREAM, PROTOCOL, PLATFORM }
}

data class BalanceInfo(
    val currency: String,
    val total: String,
    val granted: String,
    val toppedUp: String,
)

data class AccountProfile(
    val id: String?,
    val name: String?,
    val email: String?,
    val mobile: String?,
    val avatarUrl: String?,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: email?.takeIf { it.isNotBlank() }
            ?: mobile?.takeIf { it.isNotBlank() }
            ?: "DeepSeek 用户"

    val contact: String?
        get() = email?.takeIf { it.isNotBlank() } ?: mobile?.takeIf { it.isNotBlank() }
}

data class ManagedKey(
    val secret: String,
    val createdAtSeconds: Long?,
    val trackingId: String?,
    val sensitiveId: String?,
)

data class PlatformKeyInfo(
    val name: String,
    val sensitiveId: String?,
    val trackingId: String?,
    val createdAtSeconds: Long?,
    val keyType: String?,
)

data class UploadedFile(val id: String, val filename: String, val sizeBytes: Long)

sealed interface StreamEvent {
    data class MessageStart(val id: String?, val usage: Usage?) : StreamEvent
    data class BlockStart(val index: Int, val block: StartBlock) : StreamEvent
    data class BlockDelta(val index: Int, val delta: Delta) : StreamEvent
    data class BlockStop(val index: Int) : StreamEvent
    data class MessageDelta(
        val stopReason: String?,
        val title: String?,
        val usage: Usage?,
    ) : StreamEvent

    data object MessageStop : StreamEvent
}

sealed interface StartBlock {
    data class Text(val text: String, val citations: List<Citation>) : StartBlock
    data class Thinking(val text: String, val signature: String?) : StartBlock
    data class ServerToolUse(
        val kind: String,
        val queries: List<String>,
        val url: String?,
    ) : StartBlock

    data class SearchResults(val sources: List<SearchSource>) : StartBlock
    data class FetchResult(val url: String, val title: String?, val signature: String?) : StartBlock
    data class Ignored(val type: String) : StartBlock
}

sealed interface Delta {
    data class Text(val text: String) : Delta
    data class Thinking(val thinking: String) : Delta
    data class Signature(val signature: String) : Delta
    data class CitationDelta(val citation: Citation) : Delta
}
