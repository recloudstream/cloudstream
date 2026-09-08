package com.lagradost.cloudstream3.ui.settings.logcat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream4.compose.Colors
import com.lagradost.cloudstream4.compose.circle
import com.lagradost.cloudstream4.compose.ripple
import com.lagradost.cloudstream4.compose.rounded
import com.lagradost.cloudstream4.theme.CloudStreamTheme.colors
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
    LaunchedEffect(dismiss) {
        try {
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
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        containerColor = colors.background,
        onDismissRequest = dismiss,
        title = {
            Text(text = stringResource(R.string.log_cat))
        },
        text = {
            LazyColumn {
                items(items = list.value) { item ->
                    LogcatItem(item)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val date = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.getDefault()).format(Date(currentTimeMillis()))
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
                            /*try {
                                val date = SimpleDateFormat(
                                    "yyyy_MM_dd_HH_mm",
                                    Locale.getDefault()
                                ).format(
                                    Date(System.currentTimeMillis())
                                )

                                val file = FileHelper.logcat.createFile(context, "logcat_${date}")
                                    ?: throw ErrorLoadingException("Unable to create file")
                                val stream = file.openOutputStream(append = false)
                                    ?: throw ErrorLoadingException("Unable to create stream")

                                stream.bufferedWriter()
                                    .use { writer ->
                                        list.value.forEach {
                                            writer.write(it.toString())
                                            writer.write("\n\n")
                                        }
                                    }
                                dismiss()
                                showToast(
                                    txt(
                                        R.string.logcat_success,
                                        file.absolutePath ?: file.uri.toString()
                                    ),
                                    Toast.LENGTH_LONG
                                )
                            } catch (t: Throwable) {
                                logError(t)
                                showToast(t.message)
                            }*/
                        }
                    }
                }, colors = Colors.whiteButton
            ) { Text(text = stringResource(R.string.sort_save)) }
            Button(
                onClick = {
                    clipboardHelper(
                        txt("Logcat"),
                        list.value.joinToString(separator = "\n\n") { it.toString() }
                    )
                }, colors = Colors.whiteButton
            ) { Text(text = stringResource(R.string.sort_copy)) }
            Button(
                onClick = {
                    try {
                        Runtime.getRuntime().exec("logcat -c")
                    } catch (t: Throwable) {
                        logError(t)
                    }
                    dismiss()
                }, colors = Colors.whiteButton
            ) { Text(text = stringResource(R.string.sort_clear)) }
        },
        dismissButton = {
            Button(
                onClick = dismiss, colors = Colors.blackButton
            ) { Text(text = stringResource(R.string.sort_close)) }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}


@Composable
fun LogcatItem(item: LogcatItem) {
    val interactionSource = remember { MutableInteractionSource() }

    val color = when (item.level) {
        LogcatLevel.Fatal -> Color.Magenta
        LogcatLevel.Error -> Color.Red
        LogcatLevel.Warning -> Color.Yellow
        LogcatLevel.Info -> Color.White
        LogcatLevel.Debug -> Color.Green
        LogcatLevel.Verbose -> Color.Gray
        null -> Color.Transparent
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        item.level?.identifier?.let { value ->
            Text(
                value,
                modifier = Modifier
                    .padding(2.dp)
                    .rounded()
                    .background(colors.onBackground)
                    .padding(4.dp),
                color = colors.surfaceVariant
            )
        }
        Text(
            item.date.toHumanReadable(),
            modifier = Modifier
                .padding(2.dp)
                .rounded()
                .background(colors.surfaceVariant)
                .padding(4.dp),
            color = colors.onBackground
        )
        Text(
            item.tag,
            modifier = Modifier
                .padding(2.dp)
                .rounded()
                .background(colors.surfaceVariant)
                .padding(4.dp),
            color = colors.onBackground
        )
    }
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    clipboardHelper(txt("Logcat"), item.toString())
                })
            .rounded()
            .ripple(interactionSource)
            .padding(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .circle()
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            item.message,
            color = colors.onBackground,
            fontSize = 14.sp,
            lineHeight = 15.sp,
        )
    }
}