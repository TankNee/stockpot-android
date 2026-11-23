package cn.neeto.stockpot.core.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** SAF 文档读写。导入导出都走系统选择器，不申请任何存储权限。 */
object Documents {

    data class Picked(val name: String, val bytes: ByteArray)

    fun read(context: Context, uri: Uri): Picked? {
        val resolver = context.contentResolver
        val name = displayName(context, uri)
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        return Picked(name, bytes)
    }

    fun readText(context: Context, uri: Uri): String? =
        read(context, uri)?.let { String(it.bytes, Charsets.UTF_8) }

    fun displayName(context: Context, uri: Uri): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "账单"
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
        }.getOrNull() ?: fallback
    }

    fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        } != null
    }.getOrDefault(false)
}
