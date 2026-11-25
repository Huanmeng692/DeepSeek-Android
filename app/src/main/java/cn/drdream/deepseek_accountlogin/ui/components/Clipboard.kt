package cn.drdream.deepseek_accountlogin.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun copyText(context: Context, text: String): Boolean {
    return runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("DeepSeek", text))
        true
    }.getOrDefault(false)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
