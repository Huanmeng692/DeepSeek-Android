package cn.drdream.deepseek_accountlogin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import cn.drdream.deepseek_accountlogin.data.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

class AttachmentException(message: String) : Exception(message)

/** Copies picked documents into app storage so history survives permission expiry. */
class AttachmentStore(private val context: Context) {

    suspend fun import(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: guessMime(uri)
        val displayName = queryName(uri) ?: "附件"
        val isImage = mime.startsWith("image/")
        val maxBytes = if (isImage) 20L * 1024 * 1024 else 40L * 1024 * 1024

        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: displayName.substringAfterLast('.', "bin")
        val destination = File(directory, "${UUID.randomUUID()}.$extension")

        val copied = resolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
            destination.length()
        } ?: throw AttachmentException("无法读取所选文件")

        if (copied > maxBytes) {
            destination.delete()
            throw AttachmentException("文件过大")
        }

        if (isImage && mime != "image/gif") {
            compressImage(destination)
        }

        val finalFile = if (isImage && mime != "image/gif") {
            File(directory, "${destination.nameWithoutExtension}.jpg")
        } else {
            destination
        }
        if (!finalFile.exists()) {
            destination.delete()
            throw AttachmentException("图片处理失败")
        }

        Attachment(
            id = UUID.randomUUID().toString(),
            path = finalFile.absolutePath,
            name = displayName,
            mime = if (isImage && mime != "image/gif") "image/jpeg" else mime,
            size = finalFile.length(),
            isImage = isImage,
        )
    }

    fun file(attachment: Attachment): File = File(attachment.path)

    /** Copies an original image from an imported official ZIP. */
    fun importArchiveFile(
        input: InputStream,
        name: String,
        mime: String,
        isImage: Boolean,
    ): Attachment {
        val safeName = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "attachment" }
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: safeName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            ?: if (isImage) "img" else "bin"
        val destination = File(directory, "${UUID.randomUUID()}.$extension")
        input.use { source ->
            destination.outputStream().use { output -> source.copyTo(output) }
        }
        if (destination.length() == 0L) {
            destination.delete()
            throw AttachmentException("归档附件为空")
        }
        val resolvedMime = mime.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (isImage) "image/*" else "application/octet-stream"
        return Attachment(
            id = UUID.randomUUID().toString(),
            path = destination.absolutePath,
            name = safeName,
            mime = resolvedMime,
            size = destination.length(),
            isImage = isImage,
        )
    }

    fun archiveMime(name: String, isImage: Boolean): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (isImage) "image/*" else "application/octet-stream"
    }

    fun delete(attachment: Attachment) {
        runCatching { File(attachment.path).delete() }
    }

    private fun compressImage(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return

        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return

        val maxEdge = maxOf(decoded.width, decoded.height)
        val scaled = if (maxEdge > 2048) {
            val ratio = 2048f / maxEdge
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }

        val target = File(file.parentFile, "${file.nameWithoutExtension}.jpg")
        target.outputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)
        }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        if (target != file) file.delete()
    }

    private fun queryName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    private fun guessMime(uri: Uri): String {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}
