package cn.drdream.deepseek_accountlogin.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.drdream.deepseek_accountlogin.data.model.Attachment
import cn.drdream.deepseek_accountlogin.ui.i18n.LocalStrings
import cn.drdream.deepseek_accountlogin.ui.theme.LocalDeepSeekColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AttachmentPreview(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    showName: Boolean = true,
) {
    val colors = LocalDeepSeekColors.current
    val strings = LocalStrings.current
    val hasFile = !attachment.localOnly &&
        attachment.path.isNotBlank() &&
        File(attachment.path).isFile
    if (attachment.isImage) {
        Box(
            modifier = modifier
                .widthIn(max = 280.dp)
                .heightIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            val bitmap by produceState<Bitmap?>(null, attachment.path, hasFile) {
                value = if (hasFile) {
                    withContext(Dispatchers.IO) { decodeSampled(attachment.path, 1400) }
                } else {
                    null
                }
            }
            val current = bitmap
            if (current != null) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = attachment.name,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp)
                        .background(colors.thinkingBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!hasFile) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = null,
                                tint = colors.mutedText,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = attachment.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = strings.attachmentMetadataOnly,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.mutedText,
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (attachment.uploading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = strings.remove,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    } else {
        Surface(
            modifier = modifier.widthIn(max = 300.dp),
            color = colors.searchCard,
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (showName) {
                        Text(
                            text = attachment.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = when {
                            attachment.localOnly -> strings.attachmentMetadataOnly
                            attachment.uploading -> "上传中 · ${formatBytes(attachment.size)}"
                            else -> formatBytes(attachment.size)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                    )
                }
                if (attachment.uploading) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 1.8.dp,
                    )
                }
            }
        }
    }
    if (attachment.error != null) {
        Text(
            text = attachment.error,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
fun AttachmentRow(
    attachments: List<Attachment>,
    modifier: Modifier = Modifier,
    onRemove: ((Attachment) -> Unit)? = null,
) {
    if (attachments.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        attachments.forEach { attachment ->
            AttachmentPreview(
                attachment = attachment,
                onRemove = onRemove?.let { callback -> { callback(attachment) } },
            )
        }
    }
}

private fun decodeSampled(path: String, maxEdge: Int): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        },
    )
}
