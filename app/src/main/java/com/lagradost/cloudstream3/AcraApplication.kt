package com.lagradost.cloudstream3

import android.app.Application
import android.content.Context
import com.google.auto.service.AutoService
import org.acra.ReportField
import org.acra.config.CoreConfiguration
import org.acra.config.toast
import org.acra.data.CrashReportData
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderFactory
import kotlin.concurrent.thread

class CustomReportSender : ReportSender {
    // Sends all your crashes to google forms
    override fun send(context: Context, errorContent: CrashReportData) {
        try {
            println("Report sent")
            val url =
                "https://docs.google.com/forms/u/0/d/e/1FAIpQLSeFmyBChi6HF3IkhTVWPiDXJtxt8W0Hf4Agljm_0-0_QuEYFg/formResponse"
            val data = mapOf(
                "entry.134906550" to errorContent.toJSON()
            )
            thread {
                val post = khttp.post(url, data = data)
                println("Report response: $post")
            }
        } catch (e: Exception) {
            println("ERROR SENDING BUG")
        }
    }
}

@AutoService(ReportSenderFactory::class)
class CustomSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender {
        return CustomReportSender()
    }

    override fun enabled(config: CoreConfiguration): Boolean {
        return true
    }
}

class AcraApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        initAcra {
            //core configuration:
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON

            reportContent = arrayOf(
                ReportField.BUILD_CONFIG, ReportField.USER_CRASH_DATE,
                ReportField.ANDROID_VERSION, ReportField.PHONE_MODEL,
                ReportField.STACK_TRACE
            )

            //each plugin you chose above can be configured in a block like this:
            toast {
                text = getString(R.string.acra_report_toast)
                //opening this block automatically enables the plugin.
            }

        }
    }
}