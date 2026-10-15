package com.lagradost.cloudstream4

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

actual suspend fun Clipboard.getPlainText(): String? =
    getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()


actual suspend fun Clipboard.setPlainText(
    label: String,
    text: String
) {
    setClipEntry(
        ClipEntry(
            ClipData.newPlainText(
                label,
                text
            )
        )
    )
}