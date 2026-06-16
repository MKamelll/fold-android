package com.mkamelll.fold

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun Uri.fileName(context: Context): String? {
    return context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        cursor.getString(nameIndex)
    }
}

fun String.toPageIndices(): Set<Int> {
    if (this.isEmpty()) return emptySet()
    val result = mutableSetOf<Int>()
    val parts = this.split(",")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val (start, end) = trimmed.split("-").map { it.trim().toIntOrNull() }
            if (start != null && end != null) {
                result.addAll(start..end)
            }
        } else {
            trimmed.toIntOrNull()?.let { result.add(it) }
        }
    }
    return result.map { it - 1 }.toSet()
}