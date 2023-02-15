package com.lagradost.cloudstream3.ui.settings.testing

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.getAllMessages
import com.lagradost.cloudstream3.mvvm.getStackTracePretty
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.TestingUtils
import kotlinx.android.synthetic.main.provider_test_item.view.*

class TestResultAdapter(override val items: MutableList<Pair<MainAPI, TestingUtils.TestResultProvider>>) :
    AppUtils.DiffAdapter<Pair<MainAPI, TestingUtils.TestResultProvider>>(items) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ProviderTestViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.provider_test_item, parent, false),
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ProviderTestViewHolder -> {
                val item = items[position]
                holder.bind(item.first, item.second)
            }
        }
    }

    inner class ProviderTestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val languageText: TextView = itemView.lang_icon
        private val providerTitle: TextView = itemView.main_text
        private val statusText: TextView = itemView.passed_failed_marker
        private val failDescription: TextView = itemView.fail_description
        private val logButton: ImageView = itemView.action_button

        private fun String.lastLine(): String? {
            return this.lines().lastOrNull { it.isNotBlank() }
        }

        fun bind(api: MainAPI, result: TestingUtils.TestResultProvider) {
            languageText.text = getFlagFromIso(api.lang)
            providerTitle.text = api.name

            val (resultText, resultColor) = if (result.success) {
                R.string.test_passed to R.color.colorTestPass
            } else {
                R.string.test_failed to R.color.colorTestFail
            }

            statusText.setText(resultText)
            statusText.setTextColor(ContextCompat.getColor(itemView.context, resultColor))

            val stackTrace = result.exception?.getStackTracePretty(false)?.ifBlank { null }
            val messages = result.exception?.getAllMessages()?.ifBlank { null }
            val fullLog =
                result.log + (messages?.let { "\n\n$it" } ?: "") + (stackTrace?.let { "\n\n$it" } ?: "")

            failDescription.text = messages?.lastLine() ?: result.log.lastLine()

            logButton.setOnClickListener {
                val builder: AlertDialog.Builder =
                    AlertDialog.Builder(it.context, R.style.AlertDialogCustom)
                builder.setMessage(fullLog)
                    .setTitle(R.string.test_log)
                    .show()
            }
        }
    }


}