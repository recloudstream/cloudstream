package com.lagradost.cloudstream4

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.asAwtTransferable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.getPlainText(): String? = withContext(Dispatchers.IO) {
    val transferable = this@getPlainText.getClipEntry()?.asAwtTransferable
    if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor))
        transferable.getTransferData(DataFlavor.stringFlavor) as? String
    else null
}

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.setPlainText(
    label: String,
    text: String
) {
    this.setClipEntry(ClipEntry(StringSelection(text)))
}