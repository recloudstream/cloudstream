package com.lagradost.cloudstream3

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.lagradost.api.setContext
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.mvvm.safeAsync
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.openBrowser
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.ImageLoader
import kotlinx.coroutines.runBlocking
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.PrintStream
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class CustomReportSender : ReportSender {
    // Sends all your crashes to google forms
    override fun send(context: Context, errorContent: CrashReportData) {
        /*println("Sending report")
        val url =
            "https://docs.google.com/forms/d/e/$id/formResponse"
        val data = mapOf(
            "entry.$entry" to errorContent.toJSON()
        )

        thread { // to not run it on main thread
            runBlocking {
                safeAsync {
                    app.post(url, data = data)
                    //println("Report response: $post")
                }
            }
        }

        runOnMainThread { // to run it on main looper
            safe {
                Toast.makeText(context, R.string.acra_report_toast, Toast.LENGTH_SHORT).show()
            }
        }*/
    }
}

class CustomSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender {
        return CustomReportSender()
    }

    override fun enabled(config: CoreConfiguration): Boolean {
        return true
    }
}

class ExceptionHandler(val errorFile: File, val onError: (() -> Unit)) :
    Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        ACRA.errorReporter.handleException(error)
        try {
            PrintStream(errorFile).use { ps ->
                ps.println("Currently loading extension: ${PluginManager.currentlyLoading ?: "none"}")
                ps.println("Fatal exception on thread ${thread.name} (${thread.id})")
                error.printStackTrace(ps)
            }
        } catch (ignored: FileNotFoundException) {
        }
        try {
            onError.invoke()
        } catch (ignored: Exception) {
        }
        exitProcess(1)
    }

}

class AcraApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // if we want to initialise coil at earliest
        // (maybe when loading an image or gif using in splash screen activity)
        //ImageLoader.buildImageLoader(applicationContext)

        ExceptionHandler(filesDir.resolve("last_error")) {
            val intent = context!!.packageManager.getLaunchIntentForPackage(context!!.packageName)
            startActivity(Intent.makeRestartActivityTask(intent!!.component))
        }.also {
            exceptionHandler = it
            Thread.setDefaultUncaughtExceptionHandler(it)
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        context = base

        initAcra {
            //core configuration:
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON

            reportContent = listOf(
                ReportField.BUILD_CONFIG, ReportField.USER_CRASH_DATE,
                ReportField.ANDROID_VERSION, ReportField.PHONE_MODEL,
                ReportField.STACK_TRACE,
            )

            // removed this due to bug when starting the app, moved it to when it actually crashes
            //each plugin you chose above can be configured in a block like this:
            /*toast {
                text = getString(R.string.acra_report_toast)
                //opening this block automatically enables the plugin.
            }*/
        }
    }

    override fun newImageLoader(context: PlatformContext): coil3.ImageLoader {
        // Coil Module will be initialized & setSafe globally when first loadImage() is invoked
        return ImageLoader.buildImageLoader(applicationContext)
    }

    companion object {
        var exceptionHandler: ExceptionHandler? = null

        /** Use to get activity from Context */
        tailrec fun Context.getActivity(): Activity? {
            return when (this) {
                is Activity -> this
                is ContextWrapper -> baseContext.getActivity()
                else -> null
            }
        }

        private var _context: WeakReference<Context>? = null
        var context
            get() = _context?.get()
            private set(value) {
                _context = WeakReference(value)
                setContext(WeakReference(value))
            }

        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? {
            return context?.getKey(path, valueType)
        }

        fun <T : Any> setKeyClass(path: String, value: T) {
            context?.setKey(path, value)
        }

        fun removeKeys(folder: String): Int? {
            return context?.removeKeys(folder)
        }

        fun <T> setKey(path: String, value: T) {
            context?.setKey(path, value)
        }

        fun <T> setKey(folder: String, path: String, value: T) {
            context?.setKey(folder, path, value)
        }

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
            return context?.getKey(path, defVal)
        }

        inline fun <reified T : Any> getKey(path: String): T? {
            return context?.getKey(path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String): T? {
            return context?.getKey(folder, path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
            return context?.getKey(folder, path, defVal)
        }

        fun getKeys(folder: String): List<String>? {
            return context?.getKeys(folder)
        }

        fun removeKey(folder: String, path: String) {
            context?.removeKey(folder, path)
        }

        fun removeKey(path: String) {
            context?.removeKey(path)
        }

        /**
         * If fallbackWebview is true and a fragment is supplied then it will open a webview with the url if the browser fails.
         * */
        fun openBrowser(url: String, fallbackWebview: Boolean = false, fragment: Fragment? = null) {
            context?.openBrowser(url, fallbackWebview, fragment)
        }

        /** Will fallback to webview if in TV layout */
        fun openBrowser(url: String, activity: FragmentActivity?) {
            openBrowser(
                url,
                isLayout(TV or EMULATOR),
                activity?.supportFragmentManager?.fragments?.lastOrNull()
            )
        }
    }
}