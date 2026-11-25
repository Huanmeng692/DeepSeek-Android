package cn.drdream.deepseek_accountlogin.api

import cn.drdream.deepseek_accountlogin.data.model.Citation
import cn.drdream.deepseek_accountlogin.data.model.SearchSource
import cn.drdream.deepseek_accountlogin.data.model.Usage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Thin client for the two DeepSeek surfaces used by this app:
 * - api.deepseek.com (Anthropic-compatible Messages/Files, balance)
 * - platform.deepseek.com (account profile, wallet, managed API keys)
 */
class DeepSeekApi {

    /** Optional Platform session cookies captured at WebView login, used only for platform.deepseek.com. */
    @Volatile
    var platformCookie: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val streamClient = httpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun verifyKey(apiKey: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(MODELS_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw errorFromResponse(response)
        }
    }

    suspend fun getApiBalance(apiKey: String): List<BalanceInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BALANCE_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw errorFromResponse(response)
            val root = JSONObject(response.body?.string().orEmpty())
            val infos = root.optJSONArray("balance_infos") ?: JSONArray()
            (0 until infos.length()).mapNotNull { index ->
                infos.optJSONObject(index)?.let { wallet ->
                    BalanceInfo(
                        currency = wallet.optString("currency", "CNY"),
                        total = wallet.optString("total_balance", "0"),
                        granted = wallet.optString("granted_balance", "0"),
                        toppedUp = wallet.optString("topped_up_balance", "0"),
                    )
                }
            }
        }
    }

    /**
     * Streams one Messages API response. The flow ends after message_stop; a connection
     * that closes earlier raises a STREAM error so the UI can settle the reply.
     */
    fun streamMessage(apiKey: String, body: JSONObject): Flow<StreamEvent> = flow {
        val request = Request.Builder()
            .url(MESSAGES_URL)
            .header("x-api-key", apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .apply {
                if (body.hasFiles()) {
                    header("anthropic-beta", FILES_BETA)
                }
            }
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = streamClient.newCall(request)
        val job = currentCoroutineContext().job
        val cancelOnCompletion = job.invokeOnCompletion { call.cancel() }
        var sawStop = false
        try {
            val response = try {
                call.execute()
            } catch (error: IOException) {
                if (!job.isActive) throw CancellationException("stopped")
                throw ApiException(ApiException.Kind.NETWORK, error.message ?: "网络连接失败")
            }
            response.use { result ->
                if (!result.isSuccessful) throw errorFromResponse(result)
                val contentType = result.header("Content-Type").orEmpty()
                if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                    throw ApiException(
                        ApiException.Kind.PROTOCOL,
                        "服务返回了非流式响应（${contentType.ifBlank { "unknown" }}）",
                    )
                }
                val source = result.body?.source()
                    ?: throw ApiException(ApiException.Kind.PROTOCOL, "响应为空")
                var eventName: String? = null
                val data = StringBuilder()
                while (job.isActive) {
                    val line = try {
                        source.readUtf8Line()
                    } catch (error: IOException) {
                        if (!job.isActive) throw CancellationException("stopped")
                        throw ApiException(ApiException.Kind.NETWORK, error.message ?: "网络连接中断")
                    } ?: break
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) {
                                val event = parseEvent(eventName, data.toString())
                                data.setLength(0)
                                eventName = null
                                if (event != null) {
                                    emit(event)
                                    if (event is StreamEvent.MessageStop) {
                                        sawStop = true
                                        break
                                    }
                                }
                            }
                        }

                        line.startsWith("event:") -> eventName = line.substring(6).trim()
                        line.startsWith("data:") -> data.append(line.substring(5).trim())
                        line.startsWith(":") -> Unit
                    }
                }
                if (job.isActive && !sawStop) {
                    throw ApiException(ApiException.Kind.STREAM, "连接提前结束")
                }
            }
        } finally {
            cancelOnCompletion.dispose()
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun uploadFile(
        apiKey: String,
        file: File,
        mime: String,
        isImage: Boolean,
    ): UploadedFile = withContext(Dispatchers.IO) {
        val purpose = when {
            isImage -> null
            mime in RICH_LAYOUT_MIME -> "rich_layout_recognition"
            else -> "user_data"
        }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                if (purpose != null) addFormDataPart("purpose", purpose)
                addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody(mime.toMediaTypeOrNull()),
                )
            }
            .build()
        val request = Request.Builder()
            .url(FILES_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-beta", FILES_BETA)
            .header("Accept", "application/json")
            .post(multipart)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw errorFromResponse(response)
            val json = JSONObject(response.body?.string().orEmpty())
            UploadedFile(
                id = json.optString("id"),
                filename = json.optString("filename"),
                sizeBytes = json.optLong("size_bytes"),
            )
        }
    }

    suspend fun getPlatformProfile(token: String): AccountProfile = withContext(Dispatchers.IO) {
        val payload = platformRequest(
            path = "/auth-api/v0/users/current",
            method = "GET",
            token = token,
        )
        val user = payload.optJSONObject("user") ?: payload
        val identity = user.optJSONObject("id_profile")
        AccountProfile(
            id = user.textOrNull("id"),
            name = identity?.textOrNull("name"),
            email = user.textOrNull("email"),
            mobile = user.textOrNull("mobile") ?: user.textOrNull("mobile_number"),
            avatarUrl = identity?.textOrNull("picture"),
        )
    }

    suspend fun getPlatformBalance(token: String): List<BalanceInfo> = withContext(Dispatchers.IO) {
        val payload = platformRequest(
            path = "/api/v0/users/get_user_summary",
            method = "GET",
            token = token,
        )
        val wallets = ArrayList<BalanceInfo>()
        val normal = payload.optJSONArray("normal_wallets") ?: JSONArray()
        for (index in 0 until normal.length()) {
            normal.optJSONObject(index)?.let { wallet ->
                wallets += BalanceInfo(
                    currency = wallet.optString("currency", "CNY"),
                    total = wallet.optString("balance", "0"),
                    granted = "0",
                    toppedUp = wallet.optString("balance", "0"),
                )
            }
        }
        val bonus = payload.optJSONArray("bonus_wallets") ?: JSONArray()
        for (index in 0 until bonus.length()) {
            bonus.optJSONObject(index)?.let { wallet ->
                val currency = wallet.optString("currency", "CNY")
                val amount = wallet.optString("balance", "0")
                val existing = wallets.indexOfFirst { it.currency == currency }
                if (existing >= 0) {
                    wallets[existing] = wallets[existing].copy(granted = amount)
                } else {
                    wallets += BalanceInfo(
                        currency = currency,
                        total = "0",
                        granted = amount,
                        toppedUp = "0",
                    )
                }
            }
        }
        wallets
    }

    /**
     * Creates the app-managed key through Platform's API key editor. The response carries the
     * one-time full secret in `sensitive_id`; it is never logged by this client.
     */
    suspend fun createManagedKey(token: String, name: String): ManagedKey = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("action", "create")
            put("name", name)
            put("redacted_key", JSONObject.NULL)
            put("created_at", JSONObject.NULL)
            put("tracking_id", JSONObject.NULL)
        }
        val payload = platformRequest(
            path = "/api/v0/users/edit_api_keys",
            method = "POST",
            token = token,
            body = body,
        )
        val apiKey = payload.optJSONObject("api_key")
            ?: throw ApiException(ApiException.Kind.PLATFORM, "平台未返回 API Key")
        val secret = apiKey.textOrNull("sensitive_id")
        if (secret.isNullOrBlank()) {
            throw ApiException(ApiException.Kind.PLATFORM, "平台未返回完整的 API Key")
        }
        ManagedKey(
            secret = secret,
            createdAtSeconds = apiKey.optLong("created_at").takeIf { it > 0 },
            trackingId = apiKey.textOrNull("tracking_id"),
            sensitiveId = secret,
        )
    }

    suspend fun deleteManagedKey(
        token: String,
        key: ManagedKey,
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("action", "delete")
            put("name", JSONObject.NULL)
            put("redacted_key", key.sensitiveId ?: key.secret)
            put("created_at", key.createdAtSeconds ?: JSONObject.NULL)
            put("tracking_id", key.trackingId ?: JSONObject.NULL)
        }
        platformRequest(
            path = "/api/v0/users/edit_api_keys",
            method = "POST",
            token = token,
            body = body,
        )
        Unit
    }

    suspend fun listPlatformKeys(token: String): List<PlatformKeyInfo> = withContext(Dispatchers.IO) {
        val payload = platformRequest(
            path = "/api/v0/users/get_api_keys",
            method = "GET",
            token = token,
        )
        val keys = payload.optJSONArray("api_keys") ?: JSONArray()
        (0 until keys.length()).mapNotNull { index ->
            keys.optJSONObject(index)?.let { key ->
                PlatformKeyInfo(
                    name = key.textOrNull("name").orEmpty(),
                    sensitiveId = key.textOrNull("sensitive_id"),
                    trackingId = key.textOrNull("tracking_id"),
                    createdAtSeconds = key.optLong("created_at").takeIf { it > 0 },
                    keyType = key.textOrNull("key_type"),
                )
            }
        }
    }

    private suspend fun platformRequest(
        path: String,
        method: String,
        token: String,
        body: JSONObject? = null,
    ): JSONObject {
        val builder = Request.Builder()
            .url(PLATFORM_BASE + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("x-client-bundle-id", "")
            .header("x-client-platform", "web")
            .header("x-client-version", CLIENT_VERSION)
            .header("x-client-locale", "zh_CN")
            .header(
                "x-client-timezone-offset",
                (TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000).toString(),
            )
            .header("Referer", "$PLATFORM_BASE/api_keys")
            .header("Origin", PLATFORM_BASE)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("x-ds-trace-id", UUID.randomUUID().toString())
        platformCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
            builder.header("Cookie", cookie)
        }
        when (method) {
            "POST" -> builder.post(
                (body ?: JSONObject()).toString().toRequestBody(JSON_MEDIA_TYPE),
            )

            else -> builder.get()
        }
        httpClient.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 401) {
                throw ApiException(ApiException.Kind.AUTH, "登录状态已过期", 401)
            }
            if (!response.isSuccessful) {
                throw ApiException(
                    ApiException.Kind.PLATFORM,
                    platformErrorMessage(raw) ?: "平台请求失败（HTTP ${response.code}）",
                    response.code,
                )
            }
            val root = runCatching { JSONObject(raw) }.getOrElse {
                throw ApiException(ApiException.Kind.PROTOCOL, "平台返回格式异常")
            }
            val code = root.optInt("code", -1)
            if (code != 0) {
                val message = root.optString("msg")
                    .takeIf { it.isNotBlank() }
                    ?: "平台返回错误（code $code）"
                if (code == 40003) throw ApiException(ApiException.Kind.AUTH, "登录状态已过期")
                throw ApiException(ApiException.Kind.PLATFORM, message)
            }
            val data = root.optJSONObject("data")
                ?: throw ApiException(ApiException.Kind.PROTOCOL, "平台返回缺少 data")
            val bizCode = data.optInt("biz_code", 0)
            if (bizCode != 0) {
                val message = data.optString("biz_msg")
                    .takeIf { it.isNotBlank() }
                    ?: "平台返回错误（biz_code $bizCode）"
                if (bizCode == 40003) throw ApiException(ApiException.Kind.AUTH, "登录状态已过期")
                throw ApiException(ApiException.Kind.PLATFORM, message)
            }
            return data.optJSONObject("biz_data") ?: JSONObject()
        }
    }

    private fun platformErrorMessage(raw: String): String? = runCatching {
        val root = JSONObject(raw)
        root.optString("msg").takeIf { it.isNotBlank() }
            ?: root.optJSONObject("data")?.optString("biz_msg")?.takeIf { it.isNotBlank() }
            ?: root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun parseEvent(eventName: String?, raw: String): StreamEvent? {
        val json = try {
            JSONObject(raw)
        } catch (_: Exception) {
            if (eventName == "error") {
                throw ApiException(ApiException.Kind.STREAM, raw.ifBlank { "生成失败" })
            }
            return null
        }
        json.optJSONObject("error")?.let { error ->
            throw ApiException(
                ApiException.Kind.STREAM,
                error.optString("message").ifBlank { "生成失败" },
            )
        }
        if (eventName == "error") {
            throw ApiException(ApiException.Kind.STREAM, raw.ifBlank { "生成失败" })
        }
        return when (json.optString("type")) {
            "message_start" -> {
                val message = json.optJSONObject("message")
                StreamEvent.MessageStart(
                    id = message?.optString("id"),
                    usage = message?.optJSONObject("usage")?.toUsage(),
                )
            }

            "content_block_start" -> {
                val block = json.optJSONObject("content_block")?.toStartBlock()
                if (block == null) null else StreamEvent.BlockStart(
                    index = json.optInt("index"),
                    block = block,
                )
            }

            "content_block_delta" -> {
                val delta = json.optJSONObject("delta")?.toDelta()
                if (delta == null) null else StreamEvent.BlockDelta(
                    index = json.optInt("index"),
                    delta = delta,
                )
            }

            "content_block_stop" -> StreamEvent.BlockStop(json.optInt("index"))

            "message_delta" -> {
                val delta = json.optJSONObject("delta")
                StreamEvent.MessageDelta(
                    stopReason = delta?.optString("stop_reason")?.takeIf { it.isNotBlank() },
                    title = delta?.textOrNull("title"),
                    usage = json.optJSONObject("usage")?.toUsage(),
                )
            }

            "message_stop" -> StreamEvent.MessageStop
            "ping" -> null
            else -> null
        }
    }

    private fun JSONObject.toStartBlock(): StartBlock? = when (optString("type")) {
        "text" -> StartBlock.Text(
            text = optString("text"),
            citations = optJSONArray("citations")?.toCitations().orEmpty(),
        )

        "thinking" -> StartBlock.Thinking(
            text = optString("thinking"),
            signature = textOrNull("signature"),
        )

        "server_tool_use" -> {
            val input = optJSONObject("input")
            val action = input?.optString("action")
            val queries = if (action == "search") {
                input.optJSONArray("queries")?.let { array ->
                    (0 until array.length()).mapNotNull {
                        array.optString(it).takeIf { value ->
                            value.isNotBlank() && value != "null"
                        }
                    }
                }.orEmpty()
            } else {
                emptyList()
            }
            StartBlock.ServerToolUse(
                kind = optString("kind"),
                queries = queries,
                url = input?.textOrNull("url"),
            )
        }

        "web_search_tool_result" -> {
            val content = optJSONArray("content") ?: JSONArray()
            val sources = (0 until content.length()).mapNotNull { index ->
                content.optJSONObject(index)?.let { item ->
                    if (item.optString("type") != "web_search_result") return@let null
                    SearchSource(
                        title = item.textOrNull("title") ?: item.optString("url"),
                        url = item.optString("url"),
                        snippet = item.textOrNull("snippet"),
                        siteName = item.textOrNull("site_name"),
                        pageAge = item.textOrNull("page_age"),
                    )
                }
            }
            StartBlock.SearchResults(sources)
        }

        "web_fetch_tool_result" -> {
            val content = optJSONObject("content")
            if (content?.optString("type") == "web_fetch_result") {
                StartBlock.FetchResult(
                    url = content.optString("url"),
                    title = content.optJSONObject("content")
                        ?.textOrNull("title"),
                    signature = textOrNull("signature"),
                )
            } else {
                StartBlock.FetchResult(
                    url = content?.optString("url").orEmpty(),
                    title = null,
                    signature = null,
                )
            }
        }

        else -> StartBlock.Ignored(optString("type"))
    }

    private fun JSONObject.toDelta(): Delta? = when (optString("type")) {
        "text_delta" -> Delta.Text(optString("text"))
        "thinking_delta" -> Delta.Thinking(optString("thinking"))
        "signature_delta" -> Delta.Signature(optString("signature"))
        "citations_delta" -> {
            val citation = optJSONObject("citation")
            if (citation == null) {
                null
            } else {
                Delta.CitationDelta(
                    Citation(
                        url = citation.optString("url"),
                        title = citation.textOrNull("title"),
                        citedText = citation.optString("cited_text"),
                    ),
                )
            }
        }

        else -> null
    }

    private fun JSONArray.toCitations(): List<Citation> = (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.let {
            Citation(
                url = it.optString("url"),
                title = it.textOrNull("title"),
                citedText = it.optString("cited_text"),
            )
        }
    }

    private fun JSONObject.toUsage(): Usage = Usage(
        inputTokens = optInt("input_tokens"),
        cacheCreationInputTokens = optInt("cache_creation_input_tokens"),
        cacheReadInputTokens = optInt("cache_read_input_tokens"),
        outputTokens = optInt("output_tokens"),
    )

    /** org.json renders JSON null as the string "null"; treat that as absent. */
    private fun JSONObject.textOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.hasFiles(): Boolean {
        val messages = optJSONArray("messages") ?: return false
        for (index in 0 until messages.length()) {
            val content = messages.optJSONObject(index)?.opt("content") as? JSONArray ?: continue
            for (blockIndex in 0 until content.length()) {
                val type = content.optJSONObject(blockIndex)?.optString("type")
                if (type == "image" || type == "document") return true
            }
        }
        return false
    }

    private fun errorFromResponse(response: okhttp3.Response): ApiException {
        val raw = runCatching { response.body?.string() }.getOrNull().orEmpty()
        val message = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching {
                JSONObject(raw).optString("message")
            }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "请求失败（HTTP ${response.code}）"
        val kind = when (response.code) {
            401, 403 -> ApiException.Kind.AUTH
            402 -> ApiException.Kind.BALANCE
            429 -> ApiException.Kind.RATE_LIMIT
            else -> ApiException.Kind.HTTP
        }
        return ApiException(kind, message, response.code)
    }

    companion object {
        private const val API_BASE = "https://api.deepseek.com"
        private const val PLATFORM_BASE = "https://platform.deepseek.com"
        private const val MESSAGES_URL = "$API_BASE/anthropic/v1/messages"
        private const val FILES_URL = "$API_BASE/anthropic/v1/files"
        private const val MODELS_URL = "$API_BASE/models"
        private const val BALANCE_URL = "$API_BASE/user/balance"
        private const val FILES_BETA = "files-api-2025-04-14"
        private const val CLIENT_VERSION = "1.0.0"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val RICH_LAYOUT_MIME = setOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        )
    }
}
