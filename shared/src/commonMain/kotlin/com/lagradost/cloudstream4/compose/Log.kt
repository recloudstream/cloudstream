package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.state.LogLevel


@Composable
fun LogBoxWhite(text: String) {
    Text(
        text,
        modifier = Modifier
            .padding(2.dp)
            .rounded()
            .background(MaterialTheme.colorScheme.onBackground)
            .padding(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun LogBoxBlack(text: String) {
    Text(
        text,
        modifier = Modifier
            .padding(2.dp)
            .rounded()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun LogText(
    level: LogLevel?,
    message: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = level?.color ?: Color.Transparent
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .fillMaxWidth()
            .rounded()
            .clickable(onClick = onClick)
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
            message,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            lineHeight = 15.sp,
        )
    }
}
