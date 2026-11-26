package com.lagradost.cloudstream4

import androidx.compose.ui.platform.Clipboard

expect suspend fun Clipboard.setPlainText(label: String, text: String)
expect suspend fun Clipboard.getPlainText(): String?
