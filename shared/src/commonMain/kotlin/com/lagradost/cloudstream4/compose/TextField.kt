package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BlackTextField(
    value: String,
    placeHolder : String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (String) -> Unit,
) {
    TextField(
        modifier = modifier.focusOutline(),
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = keyboardOptions,
        placeholder = {
            Text(text = placeHolder)
        },
        shape = RoundedShape(),
        colors = Colors.blackTextField,
    )
}