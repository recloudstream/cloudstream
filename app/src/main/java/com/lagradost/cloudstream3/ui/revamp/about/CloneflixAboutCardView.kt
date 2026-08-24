package com.lagradost.cloudstream3.ui.revamp.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import com.lagradost.cloudstream3.databinding.RevampViewAboutCardBinding

class CloneflixAboutCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val binding: RevampViewAboutCardBinding =
        RevampViewAboutCardBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        binding.btnConnectX.setOnClickListener {
            openUrl("https://x.com/ivannaheraskina")
        }

        binding.btnFeedback.setOnClickListener {
            Toast.makeText(
                context,
                "Feedback submitted! Thank you for using Cloneflix Design System 2024.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open $url", Toast.LENGTH_SHORT).show()
        }
    }
}
