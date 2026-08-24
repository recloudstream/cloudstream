package com.lagradost.cloudstream3.ui.revamp.theme

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object CloneflixTheme {

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label: $text", Toast.LENGTH_SHORT).show()
    }
}
