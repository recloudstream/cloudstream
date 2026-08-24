package com.lagradost.cloudstream3.ui.revamp.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lagradost.cloudstream3.ui.revamp.compose.screens.CloneflixShowcaseComposeScreen
import com.lagradost.cloudstream3.ui.revamp.compose.theme.CloneflixTheme

class CloneflixComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CloneflixTheme {
                CloneflixShowcaseComposeScreen()
            }
        }
    }
}
