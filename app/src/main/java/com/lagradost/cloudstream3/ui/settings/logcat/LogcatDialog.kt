package com.lagradost.cloudstream3.ui.settings.logcat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream4.compose.BlackButton
import com.lagradost.cloudstream4.compose.LogBoxBlack
import com.lagradost.cloudstream4.compose.LogBoxWhite
import com.lagradost.cloudstream4.compose.LogText
import com.lagradost.cloudstream4.compose.WhiteButton
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun LogcatDialog(dismiss: () -> Unit) {
    val list = remember { mutableStateOf(persistentListOf<LogcatItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(dismiss) {
        try {
            isLoading = true

            // https://developer.android.com/studio/command-line/logcat
            val process = Runtime.getRuntime().exec("logcat --binary -d")
            val items = arrayListOf<LogcatItem>()
            LogcatBinaryParser(process.inputStream).use { parser ->
                while (true) {
                    val item = parser.parseItem() ?: break
                    items.add(item)
                }
            }

            list.value = items.toPersistentList()
        } catch (e: Exception) {
            logError(e) // kinda ironic
        } finally {
            isLoading = false
        }
    }
    val (dismissFocus, confirmFocus) = remember { FocusRequester.createRefs() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = dismiss,
        title = {
            Text(text = stringResource(R.string.log_cat))
        },
        text = {
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onBackground,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.focusProperties {
                    start = dismissFocus
                    end = confirmFocus
                }
            ) {
                items(items = list.value, key = { item -> item.uuid }) { item ->
                    LogcatItem(item, modifier = Modifier.focusProperties {
                        start = dismissFocus
                        end = confirmFocus
                    })
                }
            }
        },
        confirmButton = {
            WhiteButton(
                text = stringResource(R.string.sort_save),
                modifier = Modifier.focusRequester(confirmFocus)
            ) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val date = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.getDefault()).format(
                            Date(currentTimeMillis())
                        )
                        var fileStream: OutputStream?
                        try {
                            fileStream = VideoDownloadManager.setupStream(
                                context,
                                "logcat_${date}",
                                null,
                                "txt",
                                false
                            ).openNew()
                            fileStream.bufferedWriter()
                                .use { writer ->
                                    list.value.forEach {
                                        writer.write(it.toString())
                                        writer.write("\n\n")
                                    }
                                }
                            dismiss()
                        } catch (t: Throwable) {
                            logError(t)
                            showToast(t.message)
                        }
                    }
                }
            }

            WhiteButton(text = stringResource(R.string.sort_copy)) {
                clipboardHelper(
                    txt("Logcat"),
                    list.value.joinToString(separator = "\n\n") { it.toString() }
                )
            }
            WhiteButton(text = stringResource(R.string.sort_clear)) {
                try {
                    Runtime.getRuntime().exec("logcat -c")
                } catch (t: Throwable) {
                    logError(t)
                }
                dismiss()
            }
        },
        dismissButton = {
            BlackButton(
                text = stringResource(R.string.sort_close),
                onClick = dismiss,
                modifier = Modifier.focusRequester(dismissFocus)
            )
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}

@Composable
fun LogcatItem(item: LogcatItem, modifier: Modifier = Modifier) {
    Row(modifier = Modifier.fillMaxWidth()) {
        item.level?.identifier?.let { value ->
            LogBoxWhite(value)
        }
        LogBoxBlack(item.date.toHumanReadable())
        LogBoxBlack(item.tag)
    }

    LogText(level = item.level, message = item.message, modifier = modifier, onClick = {
        clipboardHelper(txt("Logcat"), item.toString())
    })
}