package com.mihon.presentation.settings.widget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.cancel
import com.lagradost.cloudstream4.generated.resources.error
import com.lagradost.cloudstream4.generated.resources.ok
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditTextPreferenceWidget(
    title: String,
    subtitle: String?,
    icon: Painter?,
    value: String,
    onConfirm: suspend (String) -> Boolean,
) {
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = title,
        subtitle = subtitle?.format(value),
        icon = icon,
        onPreferenceClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        val scope = rememberCoroutineScope()
        val onDismissRequest = { isDialogShown = false }
        var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(value))
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = title) },
            text = {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    trailingIcon = {
                        if (textFieldValue.text.isBlank()) {
                            Icon(painter = painterResource(Res.drawable.error), contentDescription = null)
                        } else {
                            IconButton(onClick = { textFieldValue = TextFieldValue("") }) {
                                Icon(painter = painterResource(Res.drawable.cancel), contentDescription = null)
                            }
                        }
                    },
                    isError = textFieldValue.text.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    enabled = textFieldValue.text != value && textFieldValue.text.isNotBlank(),
                    onClick = {
                        scope.launch {
                            if (onConfirm(textFieldValue.text)) {
                                onDismissRequest()
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(Res.string.cancel))
                }
            },
        )
    }
}
