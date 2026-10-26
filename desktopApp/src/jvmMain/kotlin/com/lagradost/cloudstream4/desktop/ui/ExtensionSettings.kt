package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.DesktopContext
import com.lagradost.cloudstream4.desktop.generated.resources.Res
import com.lagradost.cloudstream4.desktop.generated.resources.arrow_back
import com.lagradost.cloudstream4.desktop.generated.resources.delete
import org.jetbrains.compose.resources.painterResource

/**
 * On Android extensions have their own settings screens, which cannot run on desktop. Their settings
 * are plain key-value stores though, so they can be edited here (for example a login token).
 */
@Composable
fun ExtensionSettingsScreen(onBack: () -> Unit) {
    // Bumped after every edit so the list re-reads the stores
    var version by remember { mutableStateOf(0) }
    val names = remember(version) { Context.preferenceNames() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(Res.drawable.arrow_back), "Back") }
            Column {
                Text("Extension settings", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Values extensions saved through their settings screen. Restart the app after changing them.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (names.isEmpty()) {
            MessageBox("No extension has saved settings yet")
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(names, key = { it }) { name ->
                PreferenceStoreCard(name, version) { version++ }
            }
        }
    }
}

@Composable
private fun PreferenceStoreCard(name: String, version: Int, onChanged: () -> Unit) {
    val prefs = remember(name) { DesktopContext.getSharedPreferences(name, Context.MODE_PRIVATE) }
    val entries = remember(name, version) { prefs.getAll().toSortedMap() }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        entries.forEach { (key, value) ->
            var text by remember(name, key, version) { mutableStateOf(value?.toString().orEmpty()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        // Keep the stored type for numbers and booleans so extensions can still read them
                        val editor = prefs.edit()
                        when (value) {
                            is Boolean -> it.toBooleanStrictOrNull()?.let { b -> editor.putBoolean(key, b) }
                            is Int -> it.toIntOrNull()?.let { n -> editor.putInt(key, n) }
                            is Long -> it.toLongOrNull()?.let { n -> editor.putLong(key, n) }
                            is Number -> it.toFloatOrNull()?.let { n -> editor.putFloat(key, n) }
                            else -> editor.putString(key, it)
                        }
                        editor.apply()
                    },
                    label = { Text(key) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    prefs.edit().remove(key).apply()
                    onChanged()
                }) { Icon(painterResource(Res.drawable.delete), "Remove $key") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newKey, { newKey = it }, label = { Text("New key, e.g. token") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(newValue, { newValue = it }, label = { Text("Value") }, singleLine = true, modifier = Modifier.weight(2f))
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    prefs.edit().putString(newKey.trim(), newValue.trim()).apply()
                    newKey = ""
                    newValue = ""
                    onChanged()
                },
                enabled = newKey.isNotBlank(),
            ) { Text("Add") }
        }
    }
}
