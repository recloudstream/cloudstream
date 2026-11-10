package com.lagradost.cloudstream3.lab

import android.app.Activity
import androidx.navigation.findNavController
import com.lagradost.cloudstream3.R

object ExtensionUi {
    fun Activity.navigate(destination: Int) {
        findNavController(R.id.nav_host_fragment).navigate(destination)
    }
}
