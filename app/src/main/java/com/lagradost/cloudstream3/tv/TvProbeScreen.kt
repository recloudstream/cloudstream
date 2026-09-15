package com.lagradost.cloudstream3.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClassicCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import androidx.tv.material3.WideButtonDefaults

/**
 * Minimal Compose-for-TV focus probe: title + small row of focusable cards/buttons.
 * Obvious focus scale, D-pad friendly, wide spacing. No carousel / lazy rails / catalog.
 */
@Composable
fun TvProbeScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(
                text = "CloudStream TV Compose Probe",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Phase 1 — D-pad focus / scale smoke test (no catalog, no player)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Focusable cards",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ProbeClassicCard(title = "Card A", accent = MaterialTheme.colorScheme.primary)
                ProbeClassicCard(title = "Card B", accent = MaterialTheme.colorScheme.secondary)
                ProbeClassicCard(title = "Card C", accent = MaterialTheme.colorScheme.tertiary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Focusable buttons",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {},
                    scale = ButtonDefaults.scale(focusedScale = 1.12f),
                ) {
                    Text("Continue")
                }
                Button(
                    onClick = {},
                    scale = ButtonDefaults.scale(focusedScale = 1.12f),
                ) {
                    Text("Details")
                }
            }

            WideButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth(0.55f),
                scale = WideButtonDefaults.scale(focusedScale = 1.05f),
            ) {
                Text("Wide action (focus me with D-pad)")
            }
        }
    }
}

@Composable
private fun ProbeClassicCard(
    title: String,
    accent: Color,
) {
    ClassicCard(
        onClick = {},
        modifier = Modifier.width(180.dp),
        image = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.takeLast(1),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        },
        subtitle = {
            Text(
                text = "Focus scales up",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        scale = CardDefaults.scale(focusedScale = 1.12f),
        contentPadding = PaddingValues(12.dp),
    )
}
