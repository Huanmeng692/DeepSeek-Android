package cn.drdream.deepseek_accountlogin.data.history

import android.content.Context
import android.net.Uri
import cn.drdream.deepseek_accountlogin.data.AttachmentStore
import cn.drdream.deepseek_accountlogin.data.model.Attachment
import cn.drdream.deepseek_accountlogin.data.model.ChatMessage
import cn.drdream.deepseek_accountlogin.data.model.ChatSession
import cn.drdream.deepseek_accountlogin.data.model.MessageStatus
import cn.drdream.deepseek_accountlogin.data.model.ReplyBlock
import cn.drdream.deepseek_accountlogin.data.model.Role
import cn.drdream.deepseek_accountlogin.data.model.SearchSource
import cn.drdream.deepseek_accountlogin.data.model.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.roundToLong

class HistoryArchiveException(message: String) : Exception(message)

data class HistoryImportResult(
    val sessions: List<ChatSession>,
    val attachmentCount: Int,
    val missingAttachmentCount: Int,
)

data class HistoryExportResult(
    val sessionCount: Int,
    val imageCount: Int,
)

/**
 * DeepSeek official `deepseek_data-*.zip` history format (export_format_version 3).
 * This mirrors the format used by the official web export and chat-demo/DSH import.
 *
 * Import keeps the current branch selected by current_message_id and merges it into
 * this app's linear conversation model. Export writes one linear branch per session.
 */
class HistoryArchive(
    private val context: Context,
    private val attachments: AttachmentStore,
) {

    suspend fun importArchive(uri: Uri): HistoryImportResult = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("deepseek-import-", ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw HistoryArchiveException("无法读取所选 ZIP 文件")
            if (temp.length() == 0L) throw HistoryArchiveException("ZIP 文件为空")

            ZipFile(temp).use { zip ->
                val manifest = readJson(zip, "manifest.json")
                    ?: throw HistoryArchiveException("不是 DeepSeek 历史 ZIP：缺少 manifest.json")
                val version = manifest.optInt("export_format_version", -1)
                if (version != EXPORT_FORMAT_VERSION) {
                    throw HistoryArchiveException(
                        if (version > EXPORT_FORMAT_VERSION) {
                            "该 ZIP 的格式版本为 $version，当前应用仅支持 $EXPORT_FORMAT_VERSION。"
                        } else {
                            "不支持的历史 ZIP 格式版本：$version"
                        },
                    )
                }

                val entries = java.util.Collections.list(zip.entries())
                val sessionEntries = entries.filter {
                    it.name.startsWith("chat_sessions/") &&
                        it.name.endsWith(".json") &&
                        !it.name.contains("_files/")
                }
                if (sessionEntries.isEmpty()) {
                    throw HistoryArchiveException("ZIP 中没有找到 chat_sessions/*.json")
                }

                val sessions = ArrayList<ChatSession>()
                var attachmentCount = 0
                var missingCount = 0
                for (entry in sessionEntries) {
                    val parsed = runCatching { parseSession(zip, entry.name) }.getOrNull()
                    if (parsed != null) {
                        sessions += parsed.session
                        attachmentCount += parsed.attachmentCount
                        missingCount += parsed.missingAttachmentCount
                    }
                }
                if (sessions.isEmpty()) {
                    throw HistoryArchiveException("ZIP 中没有可导入的对话")
                }
                sessions.sortByDescending { it.updatedAt }
                HistoryImportResult(sessions, attachmentCount, missingCount)
            }
        } finally {
            temp.delete()
        }
    }

    suspend fun exportArchive(uri: Uri, sessions: List<ChatSession>): HistoryExportResult =
        withContext(Dispatchers.IO) {
            val exportable = sessions.filter { it.messages.isNotEmpty() }
            if (exportable.isEmpty()) throw HistoryArchiveException("没有可导出的对话")
            val temp = File.createTempFile("deepseek-export-", ".zip", context.cacheDir)
            try {
                val index = JSONArray()
                val images = ArrayList<ImageExport>()
                ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { zip ->
                    putJson(
                        zip,
                        "manifest.json",
                        JSONObject().put("export_format_version", EXPORT_FORMAT_VERSION),
                    )
                    for (session in exportable.sortedByDescending { it.updatedAt }) {
                        val sessionJson = sessionToJson(session, images)
                        putJson(zip, "chat_sessions/${session.id}.json", sessionJson)
                        index.put(indexEntry(session))
                    }
                    putJson(zip, "chat_session_index.json", index)
                    for (image in images) {
                        zip.putNextEntry(ZipEntry(image.path))
                        image.file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    temp.inputStream().use { input -> input.copyTo(output) }
                } ?: throw HistoryArchiveException("无法写入所选文件")
                HistoryExportResult(exportable.size, images.size)
            } finally {
                temp.delete()
            }
        }

    private fun parseSession(zip: ZipFile, entryName: String): ParsedSession {
        val root = readJson(zip, entryName) ?: throw HistoryArchiveException("会话文件为空")
        val id = root.requiredString("id")
        if (!SAFE_ID.matches(id)) throw HistoryArchiveException("非法会话 ID")
        val insertedAt = parseIso(root.requiredString("inserted_at"))
        val updatedAt = parseIso(root.requiredString("updated_at"))
        val currentMessageId = if (root.has("current_message_id") && !root.isNull("current_message_id")) {
            root.optLong("current_message_id")
        } else {
            0L
        }
        val messagesJson = root.optJSONArray("messages")
            ?: throw HistoryArchiveException("会话缺少 messages")
        val messages = ArrayList<ArchiveMessage>()
        for (i in 0 until messagesJson.length()) {
            val item = messagesJson.optJSONObject(i) ?: continue
            val messageId = item.requiredLong("id")
            val parentId = if (item.has("parent_id") && !item.isNull("parent_id")) {
                item.getLong("parent_id")
            } else {
                null
            }
            messages += ArchiveMessage(
                id = messageId,
                parentId = parentId,
                role = item.requiredString("role"),
                status = item.optString("status", "FINISHED"),
                thinkingEnabled = item.optBoolean("thinking_enabled", false),
                searchEnabled = item.optBoolean("search_enabled", false),
                insertedAt = item.requiredString("inserted_at").let(::parseIso),
                fragments = item.optJSONArray("fragments") ?: JSONArray(),
                accumulatedTokens = item.optLong("accumulated_token_usage", 0),
            )
        }
        if (messages.isEmpty()) throw HistoryArchiveException("会话没有消息")
        val path = choosePath(messages, currentMessageId)
        val importer = AttachmentImporter(zip, id)
        val chatMessages = ArrayList<ChatMessage>()
        for (message in path) {
            toChatMessage(message, importer)?.let(chatMessages::add)
        }
        if (chatMessages.isEmpty()) throw HistoryArchiveException("会话消息无法转换")
        val fallbackTitle = path.firstOrNull { it.role == ROLE_USER }
            ?.let { requestText(it.fragments) }
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(40)
            .orEmpty()
        val title = root.nullableString("title")?.takeIf { it.isNotBlank() } ?: fallbackTitle
        return ParsedSession(
            session = ChatSession(
                id = id,
                title = title,
                createdAt = insertedAt,
                updatedAt = updatedAt,
                messages = chatMessages,
            ),
            attachmentCount = importer.attachmentCount,
            missingAttachmentCount = importer.missingAttachmentCount,
        )
    }

    private fun choosePath(
        messages: List<ArchiveMessage>,
        currentMessageId: Long,
    ): List<ArchiveMessage> {
        val byId = messages.associateBy { it.id }
        var current = byId[currentMessageId] ?: messages.maxByOrNull { it.id }
        val reversePath = ArrayList<ArchiveMessage>()
        val seen = HashSet<Long>()
        while (current != null && seen.add(current.id)) {
            reversePath += current
            current = current.parentId?.let(byId::get)
        }
        val path = reversePath.asReversed()
        if (path.isEmpty()) return messages.sortedBy { it.id }
        if (path.first().role != ROLE_USER) return messages.sortedBy { it.id }
        return path
    }

    private fun toChatMessage(
        message: ArchiveMessage,
        importer: AttachmentImporter,
    ): ChatMessage? {
        return when (message.role) {
            ROLE_USER -> {
                val files = ArrayList<Attachment>()
                for (i in 0 until message.fragments.length()) {
                    val fragment = message.fragments.optJSONObject(i) ?: continue
                    if (fragment.optString("type") != "FILE") continue
                    val array = fragment.optJSONArray("files") ?: continue
                    for (j in 0 until array.length()) {
                        array.optJSONObject(j)?.let { importer.resolve(it) }?.let(files::add)
                    }
                }
                ChatMessage(
                    role = Role.USER,
                    createdAt = message.insertedAt,
                    content = requestText(message.fragments),
                    attachments = files,
                )
            }

            ROLE_ASSISTANT -> {
                val blocks = ArrayList<ReplyBlock>()
                for (i in 0 until message.fragments.length()) {
                    val fragment = message.fragments.optJSONObject(i) ?: continue
                    when (fragment.optString("type")) {
                        "RESPONSE", "TEMPLATE_RESPONSE" -> {
                            val content = fragment.optString("content")
                            if (content.isNotBlank()) blocks += ReplyBlock.Text(content)
                        }

                        "THINK" -> {
                            val content = fragment.optString("content")
                            val elapsed = if (fragment.has("elapsed_secs") &&
                                !fragment.isNull("elapsed_secs")
                            ) {
                                fragment.optDouble("elapsed_secs", 1.0)
                            } else {
                                1.0
                            }
                            blocks += ReplyBlock.Thinking(
                                text = content,
                                startedAt = message.insertedAt,
                                endedAt = message.insertedAt +
                                    (elapsed * 1000).roundToLong().coerceAtLeast(1000),
                            )
                        }

                        "TOOL_SEARCH" -> {
                            val queries = stringArray(fragment.optJSONArray("queries"))
                            val results = fragment.optJSONArray("results") ?: JSONArray()
                            val sources = ArrayList<SearchSource>()
                            for (j in 0 until results.length()) {
                                val result = results.optJSONObject(j) ?: continue
                                sources += SearchSource(
                                    title = result.optString("title", result.optString("url")),
                                    url = result.optString("url"),
                                    snippet = result.nullableString("snippet"),
                                    siteName = result.nullableString("site_name"),
                                    pageAge = result.nullableString("published_at"),
                                )
                            }
                            if (queries.isNotEmpty()) {
                                blocks += ReplyBlock.ToolUse(
                                    kind = "search",
                                    queries = queries,
                                )
                            }
                            if (sources.isNotEmpty()) {
                                blocks += ReplyBlock.SearchResults(sources)
                            }
                        }

                        "TOOL_OPEN" -> {
                            val url = fragment.nullableString("url")
                            if (!url.isNullOrBlank()) {
                                blocks += ReplyBlock.FetchResult(
                                    url = url,
                                    title = fragment.nullableString("title"),
                                )
                            }
                        }

                        "READ_LINK" -> {
                            val results = fragment.optJSONArray("results") ?: JSONArray()
                            for (j in 0 until results.length()) {
                                val result = results.optJSONObject(j) ?: continue
                                val url = result.nullableString("url")
                                if (!url.isNullOrBlank()) {
                                    blocks += ReplyBlock.FetchResult(
                                        url = url,
                                        title = result.nullableString("title"),
                                    )
                                    break
                                }
                            }
                        }
                    }
                }

                val status = when (message.status) {
                    "FINISHED" -> MessageStatus.DONE
                    "INCOMPLETE", "WIP" -> MessageStatus.STOPPED
                    "FAILED", "CONTEXT_LENGTH_EXCEEDED", "TIMEOUT" -> MessageStatus.ERROR
                    else -> MessageStatus.DONE
                }
                val refusal = message.status == "CONTENT_FILTER"
                val visibleBlocks = if (refusal) {
                    blocks.filterNot { it is ReplyBlock.Text || it is ReplyBlock.Thinking }
                } else {
                    blocks
                }
                ChatMessage(
                    role = Role.ASSISTANT,
                    createdAt = message.insertedAt,
                    blocks = visibleBlocks,
                    status = if (refusal) MessageStatus.DONE else status,
                    errorKind = if (status == MessageStatus.ERROR) "IMPORTED" else null,
                    stopReason = if (refusal) "refusal" else null,
                    thinkingEnabled = message.thinkingEnabled,
                    searchEnabled = message.searchEnabled,
                    usage = if (message.accumulatedTokens > 0) {
                        Usage(inputTokens = message.accumulatedTokens.toInt())
                    } else {
                        null
                    },
                )
            }

            else -> null
        }
    }

    private fun sessionToJson(session: ChatSession, images: MutableList<ImageExport>): JSONObject {
        val messages = JSONArray()
        session.messages.forEachIndexed { index, message ->
            val archive = JSONObject()
            archive.put("id", index + 1L)
            archive.put(
                "parent_id",
                if (index == 0) JSONObject.NULL else index.toLong(),
            )
            archive.put("role", if (message.role == Role.USER) ROLE_USER else ROLE_ASSISTANT)
            archive.put("status", archiveStatus(message))
            archive.put(
                "thinking_enabled",
                if (message.role == Role.USER) {
                    session.messages.getOrNull(index + 1)?.thinkingEnabled ?: false
                } else {
                    message.thinkingEnabled
                },
            )
            archive.put(
                "search_enabled",
                if (message.role == Role.USER) {
                    session.messages.getOrNull(index + 1)?.searchEnabled ?: false
                } else {
                    message.searchEnabled
                },
            )
            archive.put(
                "accumulated_token_usage",
                message.usage?.let { it.inputTokens + it.outputTokens } ?: 0,
            )
            archive.put("inserted_at", iso(message.createdAt))
            archive.put(
                "fragments",
                if (message.role == Role.USER) {
                    userFragments(session, message, images)
                } else {
                    assistantFragments(message)
                },
            )
            messages.put(archive)
        }
        return JSONObject().apply {
            put("id", session.id)
            put("title", session.title.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("pinned", false)
            put("current_message_id", session.messages.size.toLong())
            put("inserted_at", iso(session.createdAt))
            put("updated_at", iso(session.updatedAt))
            put("messages", messages)
        }
    }

    private fun userFragments(
        session: ChatSession,
        message: ChatMessage,
        images: MutableList<ImageExport>,
    ): JSONArray {
        val fragments = JSONArray()
        if (message.attachments.isNotEmpty()) {
            val files = JSONArray()
            message.attachments.forEach { attachment ->
                val entry = JSONObject()
                entry.put("id", attachment.id)
                entry.put("file_name", attachment.name)
                entry.put("file_size", attachment.size)
                entry.put("token_usage", JSONObject.NULL)
                entry.put("inserted_at", iso(message.createdAt))
                val exportPath = exportImage(session, attachment, images)
                entry.put("export_path", exportPath ?: JSONObject.NULL)
                entry.put("is_image", attachment.isImage)
                entry.put("width", JSONObject.NULL)
                entry.put("height", JSONObject.NULL)
                files.put(entry)
            }
            fragments.put(JSONObject().put("type", "FILE").put("files", files))
        }
        fragments.put(JSONObject().put("type", "REQUEST").put("content", message.content))
        return fragments
    }

    private fun exportImage(
        session: ChatSession,
        attachment: Attachment,
        images: MutableList<ImageExport>,
    ): String? {
        if (!attachment.isImage || attachment.localOnly || attachment.path.isBlank()) return null
        val file = File(attachment.path)
        if (!file.isFile || file.length() == 0L) return null
        val id = safeSegment(attachment.id).ifBlank { UUID.randomUUID().toString() }
        val name = safeSegment(attachment.name).ifBlank { "image" }
        var path = "chat_sessions/${session.id}_files/${id}_${name}"
        var suffix = 2
        while (images.any { it.path == path }) {
            path = "chat_sessions/${session.id}_files/${id}_${suffix}_${name}"
            suffix++
        }
        images += ImageExport(path, file)
        return path
    }

    private fun assistantFragments(message: ChatMessage): JSONArray {
        val fragments = JSONArray()
        var pendingQueries: List<String> = emptyList()
        var pendingOpenUrl: String? = null
        var pendingOpenTitle: String? = null

        fun flushSearch() {
            if (pendingQueries.isNotEmpty()) {
                fragments.put(
                    JSONObject()
                        .put("type", "TOOL_SEARCH")
                        .put("queries", JSONArray(pendingQueries))
                        .put("results", JSONArray())
                        .put("status", "FINISHED"),
                )
                pendingQueries = emptyList()
            }
        }

        fun flushOpen() {
            if (!pendingOpenUrl.isNullOrBlank()) {
                fragments.put(
                    JSONObject()
                        .put("type", "TOOL_OPEN")
                        .put("status", "FINISHED")
                        .put("url", pendingOpenUrl)
                        .put("title", pendingOpenTitle ?: JSONObject.NULL)
                        .put("published_at", JSONObject.NULL),
                )
                pendingOpenUrl = null
                pendingOpenTitle = null
            }
        }

        for (block in message.blocks) {
            when (block) {
                is ReplyBlock.Text -> {
                    flushSearch()
                    flushOpen()
                    if (block.text.isNotBlank()) {
                        fragments.put(
                            JSONObject().put("type", "RESPONSE").put("content", block.text),
                        )
                    }
                }

                is ReplyBlock.Thinking -> {
                    flushSearch()
                    flushOpen()
                    val elapsed = if (block.endedAt != null) {
                        (block.endedAt - block.startedAt) / 1000.0
                    } else {
                        1.0
                    }
                    fragments.put(
                        JSONObject()
                            .put("type", "THINK")
                            .put("content", block.text)
                            .put("elapsed_secs", elapsed),
                    )
                }

                is ReplyBlock.ToolUse -> {
                    if (block.queries.isNotEmpty() || block.kind == "search") {
                        pendingQueries = block.queries
                    }
                    if (!block.url.isNullOrBlank()) {
                        pendingOpenUrl = block.url
                    }
                }

                is ReplyBlock.SearchResults -> {
                    val results = JSONArray()
                    block.sources.forEach { source ->
                        results.put(
                            JSONObject()
                                .put("url", source.url)
                                .put("title", source.title)
                                .put("snippet", source.snippet ?: "")
                                .put("published_at", isoOrNull(source.pageAge) ?: JSONObject.NULL)
                                .put("site_name", source.siteName ?: JSONObject.NULL),
                        )
                    }
                    fragments.put(
                        JSONObject()
                            .put("type", "TOOL_SEARCH")
                            .put("queries", JSONArray(pendingQueries))
                            .put("results", results)
                            .put("status", "FINISHED"),
                    )
                    pendingQueries = emptyList()
                }

                is ReplyBlock.FetchResult -> {
                    flushSearch()
                    pendingOpenUrl = block.url
                    pendingOpenTitle = block.title
                    flushOpen()
                }
            }
        }
        flushSearch()
        flushOpen()
        if (fragments.length() == 0 && !message.error.isNullOrBlank()) {
            fragments.put(
                JSONObject()
                    .put("type", "TEMPLATE_RESPONSE")
                    .put("content", message.error),
            )
        }
        return fragments
    }

    private fun indexEntry(session: ChatSession): JSONObject = JSONObject().apply {
        put("id", session.id)
        put("title", session.title.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        put("pinned", false)
        put("message_count", session.messages.size)
        put("inserted_at", iso(session.createdAt))
        put("updated_at", iso(session.updatedAt))
    }

    private fun archiveStatus(message: ChatMessage): String = when {
        message.stopReason == "refusal" -> "CONTENT_FILTER"
        message.status == MessageStatus.DONE -> "FINISHED"
        message.status == MessageStatus.STOPPED -> "INCOMPLETE"
        message.status == MessageStatus.ERROR -> "FAILED"
        else -> "FINISHED"
    }

    private fun requestText(fragments: JSONArray): String {
        val builder = StringBuilder()
        for (i in 0 until fragments.length()) {
            val fragment = fragments.optJSONObject(i) ?: continue
            if (fragment.optString("type") == "REQUEST") builder.append(fragment.optString("content"))
        }
        return builder.toString()
    }

    private fun stringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }

    private fun readJson(zip: ZipFile, name: String): JSONObject? {
        val entry = zip.getEntry(name) ?: return null
        return zip.getInputStream(entry).use { input ->
            JSONObject(input.readBytes().toString(Charsets.UTF_8))
        }
    }

    private fun putJson(zip: ZipOutputStream, name: String, json: JSONObject) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(json.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun putJson(zip: ZipOutputStream, name: String, json: JSONArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(json.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun iso(milliseconds: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(milliseconds))
    }

    private fun isoOrNull(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return runCatching { iso(parseIso(value)) }.getOrNull()
    }

    private fun parseIso(value: String): Long {
        val match = ISO.matchEntire(value)
            ?: throw HistoryArchiveException("非法 UTC 时间：$value")
        val fraction = match.groupValues[7].padEnd(3, '0').take(3).ifBlank { "000" }
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        calendar.clear()
        calendar.set(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt() - 1,
            match.groupValues[3].toInt(),
            match.groupValues[4].toInt(),
            match.groupValues[5].toInt(),
            match.groupValues[6].toInt(),
        )
        calendar.set(Calendar.MILLISECOND, fraction.toInt())
        return calendar.timeInMillis
    }

    private fun safeSegment(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(120)

    private fun JSONObject.requiredString(name: String): String {
        if (!has(name) || isNull(name)) throw HistoryArchiveException("缺少字段 $name")
        return getString(name)
    }

    private fun JSONObject.requiredLong(name: String): Long {
        if (!has(name) || isNull(name)) throw HistoryArchiveException("缺少字段 $name")
        return getLong(name)
    }

    private fun JSONObject.nullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() && it != "null" }
    }

    private data class ArchiveMessage(
        val id: Long,
        val parentId: Long?,
        val role: String,
        val status: String,
        val thinkingEnabled: Boolean,
        val searchEnabled: Boolean,
        val insertedAt: Long,
        val fragments: JSONArray,
        val accumulatedTokens: Long,
    )

    private data class ParsedSession(
        val session: ChatSession,
        val attachmentCount: Int,
        val missingAttachmentCount: Int,
    )

    private data class ImageExport(val path: String, val file: File)

    private inner class AttachmentImporter(
        private val zip: ZipFile,
        private val sessionId: String,
    ) {
        private val cache = HashMap<String, Attachment>()
        var attachmentCount = 0
            private set
        var missingAttachmentCount = 0
            private set

        fun resolve(json: JSONObject): Attachment? {
            val name = json.optString("file_name").takeIf { it.isNotBlank() } ?: return null
            val id = json.optString("id").takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()
            cache[id]?.let { return it }
            val isImage = json.optBoolean("is_image", false)
            val fileSize = json.optLong("file_size", 0)
            val mime = attachments.archiveMime(name, isImage)
            val exportPath = json.nullableString("export_path")
                ?.replace('\\', '/')
            val expectedPrefix = "chat_sessions/${sessionId}_files/"
            val entry = if (
                isImage &&
                exportPath != null &&
                exportPath.startsWith(expectedPrefix) &&
                !exportPath.contains("..")
            ) {
                zip.getEntry(exportPath)
            } else {
                null
            }
            val attachment = if (entry != null) {
                runCatching {
                    zip.getInputStream(entry).let { input ->
                        attachments.importArchiveFile(input, name, mime, true)
                    }
                }.getOrNull()
            } else {
                null
            }
            val resolved = attachment ?: Attachment(
                id = id,
                path = "",
                name = name,
                mime = mime,
                size = fileSize,
                isImage = isImage,
                localOnly = true,
            )
            if (attachment == null) missingAttachmentCount++ else attachmentCount++
            cache[id] = resolved
            return resolved
        }
    }

    private companion object {
        const val EXPORT_FORMAT_VERSION = 3
        const val ROLE_USER = "USER"
        const val ROLE_ASSISTANT = "ASSISTANT"
        val SAFE_ID = Regex("[A-Za-z0-9._-]{1,128}")
        val ISO = Regex(
            "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?Z$",
        )
    }
}
