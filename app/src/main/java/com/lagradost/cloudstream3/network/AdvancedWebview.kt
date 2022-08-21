package com.lagradost.cloudstream3.network


import android.app.Dialog
import android.net.http.SslError
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.RelativeLayout
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI


enum class WebViewActions {
    VISIT_ADDRESS,
    WAIT_FOR_PAGE_LOAD,
    WAIT_FOR_X_SECONDS,
    WAIT_FOR_NETWORK_CALL,
    WAIT_FOR_NETWORK_IDLE,
    WAIT_FOR_ELEMENT,
    WAIT_FOR_ELEMENT_GONE,
    EXECUTE_JAVASCRIPT,
    WAIT_FOR_ELEMENT_TO_BE_CLICKABLE,
    CAPTURE_REQUESTS_THAT_MATCH_REGEX,
//    SEND_KEYS_TO_ELEMENT,
    RETURN
}

data class WebViewAction(val actionType: WebViewActions, val parameter: Any = "", val callback: (AdvancedWebView) -> Unit = {  })

class AdvancedWebView private constructor(
    val url: String,
    val actions: ArrayList<WebViewAction>,
    val referer: String?,
    val method: String,
    val callback: (AdvancedWebView) -> Unit = {  },
    val debug: Boolean = false
) {
    companion object {
        const val TAG = "AdvancedWebViewTag"
    }
    val headers = mapOf<String, String>()
    var webView: WebView? = null
    val remainingActions: ArrayList<WebViewAction> = actions
    var currentHTML: String = ""

    // Made this a getter, because `currentHTML` changes on the fly
    val document: Document?
        get() = try { Jsoup.parse(currentHTML) } catch (e: Exception) { null }

    private val Instance = this

    data class Builder(
        var url: String = "",
        var actions: ArrayList<WebViewAction> = arrayListOf(),
        var referer: String? = null,
        var method: String = "GET",
        var debug: Boolean = false
    ) {
        fun visitAddress(url: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            if (this.url != "") {
                addAction(WebViewAction(WebViewActions.VISIT_ADDRESS, url, cb))
            } else this.url = url
        }
        fun setReferer(referer: String) = apply { this.referer = referer }
        fun setMethod(method: String) = apply { this.method = method }

        private fun addAction(action: WebViewAction) = apply { this.actions.add(action) }

        fun waitForElement(selector: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_ELEMENT, selector, cb))
        }
        fun waitForElementGone(selector: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_ELEMENT, selector, cb))
        }
        fun waitForElementToBeClickable(selector: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_ELEMENT_TO_BE_CLICKABLE, selector, cb))
        }
        fun waitForSeconds(seconds: Long, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_X_SECONDS, seconds, cb))
        }
        fun waitForPageLoad(cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_PAGE_LOAD, "", cb))
        }
        fun waitForNetworkIdle(cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_NETWORK_IDLE, "", cb))
        }
        fun waitForNetworkCall(targetResource: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_NETWORK_CALL, targetResource, cb))
        }
        fun executeJavaScript(code: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.EXECUTE_JAVASCRIPT, code, cb))
        }
        fun captureReqsThatMatchRegex(regex: Regex, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.CAPTURE_REQUESTS_THAT_MATCH_REGEX, regex, cb))
        }
//        fun sendKeysToElement(selector: String, text: String, delayInMsPerKeyPress: Long = 50, cb: (AdvancedWebView) -> Unit = {  }) = apply {
//            addAction(WebViewAction(WebViewActions.SEND_KEYS_TO_ELEMENT, "$selector(__++`__||__`++__)$text(__++`__||__`++__)$delayInMsPerKeyPress", cb))
//        }
        fun debug() = apply { debug = true }
        fun close() = apply { addAction(WebViewAction(WebViewActions.RETURN, "")) }

        fun build(callback: (AdvancedWebView) -> Unit = { }) = AdvancedWebView(this.url, this.actions, this.referer, this.method, callback, debug)
        fun buildAndStart(callback: (AdvancedWebView) -> Unit = { }) = build(callback).apply { this.start() }
    }

    private var actionExecutionsPaused = false
    private var networkIdleTimestamp = -1;
    private var pageHasLoaded = false;
    private var isInSleep = false
    private var isSendingKeys = false
    private var actionStartTimestamp = -1;

    private fun onActionEnded() {
        actionExecutionsPaused = false
        isSendingKeys = false
        actionStartTimestamp = -1
    }

    var Error = ""

    private suspend fun tryExecuteAction() {
        if (actionExecutionsPaused || remainingActions.size == 0) return
        actionExecutionsPaused = true
        actionStartTimestamp = (System.currentTimeMillis() / 1000).toInt()

        main {
            if (remainingActions.size > 0) {
                val action = remainingActions[0]
                when (action.actionType){
                    WebViewActions.WAIT_FOR_ELEMENT -> {
                        webView?.evaluateJavascript("document.querySelector(\"${action.parameter}\")") {
                            Log.i(TAG, "WAIT_FOR_ELEMENT:: <$it>")
                            if (it == "{}") {
                                updateCurrentHtmlAndRun(action.callback)
                                remainingActions.remove(action)
                            }
                            onActionEnded()
                        }
                    }

                    WebViewActions.VISIT_ADDRESS -> {
                        webView?.loadUrl(action.parameter as String)

                        updateCurrentHtmlAndRun(action.callback)
                        remainingActions.remove(action)
                        onActionEnded()
                    }

//                    WebViewActions.SEND_KEYS_TO_ELEMENT -> {
//                        isSendingKeys = true
//                        val (element, characters, timing) = (action.parameter as String).split("(__++`__||__`++__)") // discriminator
//                        val msPerKey: Long = timing.toLongOrNull() ?: return@main
//
//                        Log.i(TAG, "SEND_KEYS_TO_ELEMENT:: start")
//                        webView?.evaluateJavascript("document.querySelector(`$element`)?.click()") {
//                            main {
//                                delay(300)
//                                for (character in characters) {
//                                    Log.i(TAG, "SEND_KEYS_TO_ELEMENT:: character :: $character")
//
//                                    webView?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, character.code))
//                                    delay(70)
//                                    webView?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, character.code))
//                                    delay(msPerKey)
//                                }
//
//                                updateCurrentHtmlAndRun(action.callback)
//                                remainingActions.remove(action)
//                                onActionEnded()
//                            }
//                        }
//                    }

                    WebViewActions.WAIT_FOR_ELEMENT_TO_BE_CLICKABLE -> {
                        webView?.evaluateJavascript(
                            """
                                ((selector) => {
                                    const elem = document.querySelector(selector)
                                    if (elem == undefined) return
                                    const attribute = elem.getAttribute("disabled")
                                    if (attribute === "true" || attribute === '') return
    
                                    return "" + (!elem.disabled || true)
                                })(`${action.parameter}`);
                            """.trimIndent()) {
                            if (it == "\"true\""){
                                updateCurrentHtmlAndRun(action.callback)
                                remainingActions.remove(action)
                            }
                            onActionEnded()
                        }
                    }

                    WebViewActions.WAIT_FOR_ELEMENT_GONE -> {
                        webView?.evaluateJavascript("\"\"+ (document.querySelector(\"${action.parameter}\") == undefined)") {
                            if (it == "\"true\"") {
                                updateCurrentHtmlAndRun(action.callback)
                                remainingActions.remove(action)
                            }
                            onActionEnded()
                        }
                    }

                    WebViewActions.WAIT_FOR_NETWORK_IDLE -> {
//                        if (!pageHasLoaded || ((System.currentTimeMillis() / 1000L) - networkIdleTimestamp) < 10) return@main
                        // we need at least 10 seconds of no network calls being done in order to be in an "IDLE" state

                        updateCurrentHtmlAndRun(action.callback)
                        remainingActions.remove(action)

                        onActionEnded()
                    }

                    WebViewActions.WAIT_FOR_X_SECONDS -> {
                        Log.i(TAG, "Waiting for ${remainingActions[0].parameter} seconds...")
                        isInSleep = true
                        delay(action.parameter as Long * 1000)
                        isInSleep = false
                        Log.i(TAG, "Finished waiting!")
                        updateCurrentHtmlAndRun(action.callback)
                        remainingActions.remove(action)

                        onActionEnded()
                    }

                    WebViewActions.EXECUTE_JAVASCRIPT -> {
                        Log.i(TAG, "Executing javascript from action...")
                        webView?.evaluateJavascript(action.parameter as String) {
                            Log.i(TAG, "JavaScript Execution done! Result: <$it>")
                            updateCurrentHtmlAndRun(action.callback)
                            remainingActions.remove(action)

                            onActionEnded()
                        }
                    }

                    WebViewActions.RETURN -> {
                        updateCurrentHtmlAndRun() { /* Do nothing, we only want to update the html */ }
                        destroyWebView()
                        remainingActions.clear()
                        actionStartTimestamp = -1
                    }

                    else -> {
                        onActionEnded()
                    }
                }
            }
        }
    }

    private fun destroyWebView() {
        main {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            Log.i(TAG, "Destroyed the WebView!")
        }
    }

    private fun updateCurrentHtmlAndRun(cb: (AdvancedWebView) -> Unit) {
        if (webView == null) {
            Instance.run(cb)
            return
        }

        main {
            webView?.evaluateJavascript("document.documentElement.outerHTML") {
                currentHTML = it
                    .replace("\\u003C", "<")
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .trimStart('"').trimEnd('"')

                Instance.run(cb)
            }
        }
    }

    var initialized = false

    suspend fun waitUntilDone() = apply {
        while (!initialized) {
            delay(100)
        }
        while (webView != null) {
            delay(100)
        }
    }

    val capturedRequests = arrayListOf<WebResourceResponse>()

    private var dialog: Dialog? = null

    fun start() {
        main {
            try {
                webView = WebView(
                    AcraApplication.context
                        ?: throw RuntimeException("No base context in WebViewResolver")
                ).apply {
                    // Bare minimum to bypass captcha
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = USER_AGENT
                    settings.blockNetworkImage = true
                }
            } catch (e: Exception) {
                Error = "Error: Failed to create an Advanced WebView, reason: <${e.message}>"
                Log.e(TAG, Error)
                Log.e(TAG, e.toString())
                destroyWebView()
                callback(this)
            }

            if (debug) {
                webView!!.visibility = View.VISIBLE
                val layout = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                )
                dialog = Dialog(MainActivity.context!!, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                dialog!!.addContentView(webView as View, layout)
                dialog!!.show()
            }

            try {
                webView?.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        pageHasLoaded = true
                        networkIdleTimestamp = (System.currentTimeMillis() / 1000).toInt();

                        if (remainingActions.size > 0 && remainingActions[0].actionType == WebViewActions.WAIT_FOR_PAGE_LOAD) {
                            Log.i(TAG, "PAGE FINISHED!")
                            val action = remainingActions[0]
                            updateCurrentHtmlAndRun(action.callback)
                            remainingActions.remove(action)
                        }
                    }

                    override fun onLoadResource(view: WebView?, url: String?) {
                        super.onLoadResource(view, url)
                        networkIdleTimestamp = (System.currentTimeMillis() / 1000L).toInt();
                        if (remainingActions.size > 0) {
                            val action = remainingActions[0]
                            when (action.actionType) {
                                WebViewActions.WAIT_FOR_NETWORK_CALL -> {
                                    if (URI(url) == URI(action.parameter as String)) {
                                        updateCurrentHtmlAndRun(action.callback)
                                        remainingActions.remove(action)
                                    }
                                }
                                else -> { /* nothing */ }
                            }
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = runBlocking {
                        networkIdleTimestamp = (System.currentTimeMillis() / 1000L).toInt();
                        val webViewUrl = request.url.toString()

                        val blacklistedFiles = listOf(
                            ".jpg", ".png", ".webp", ".mpg",
                            ".mpeg", ".jpeg", ".webm", ".mp4",
                            ".mp3", ".gifv", ".flv", ".asf",
                            ".mov", ".mng", ".mkv", ".ogg",
                            ".avi", ".wav", ".woff2", ".woff",
                            ".ttf", ".vtt", ".srt",
                            ".ts", ".gif",
                            // Warning, this might fuck some future sites, but it's used to make Sflix work.
                            "wss://"
                        )

                        val response = try {
                            when {
                                blacklistedFiles.any { URI(webViewUrl).path.contains(it) } || webViewUrl.endsWith(
                                    "/favicon.ico"
                                ) -> WebResourceResponse(
                                    "image/png",
                                    null,
                                    null
                                )

                                request.method == "GET" -> app.get(
                                    webViewUrl,
                                    headers = request.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                request.method == "POST" -> app.post(
                                    webViewUrl,
                                    headers = request.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                else -> return@runBlocking super.shouldInterceptRequest(
                                    view,
                                    request
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }

                        if (remainingActions.size > 0){
                            val action = remainingActions[0]

                            when (action.actionType) {
                                WebViewActions.CAPTURE_REQUESTS_THAT_MATCH_REGEX -> {
                                    if ((action.parameter as Regex).containsMatchIn(webViewUrl)) {
                                        if (response != null) capturedRequests.add(response)
                                        updateCurrentHtmlAndRun(action.callback)
                                        remainingActions.remove(action)
                                    }
                                }
                                else -> { /* nothing */ }
                            }
                        }

                        return@runBlocking response
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed() // Ignore ssl issues
                    }
                }
                webView?.loadUrl(url, headers.toMap())
            } catch (e: Exception){
                Error = "Failed to create a WebView client!"
                Log.e(TAG, Error)
                destroyWebView()
                Instance.run(callback)
                return@main
            }
            initialized = true

            while (remainingActions.size > 0 && webView != null) {
                if (!isInSleep && !isSendingKeys && actionStartTimestamp != -1 && ((System.currentTimeMillis()/1000) - actionStartTimestamp > 20)) {
                    Log.e(TAG, "AdvancedWebview:: Timeout, an action failed to end in under 20 seconds...")
                    Error = "ActionTimeout"
                    break
                }

                delay(300)
                if (!actionExecutionsPaused) tryExecuteAction()
            }
            try {
                updateCurrentHtmlAndRun(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Err: $e")
            }
            if (debug) dialog!!.hide()
            destroyWebView()
        }
    }
    fun Response.toWebResourceResponse(): WebResourceResponse {
        val contentTypeValue = this.header("Content-Type")
        // 1. contentType. 2. charset
        val typeRegex = Regex("""(.*);(?:.*charset=(.*)(?:|;)|)""")
        return if (contentTypeValue != null) {
            val found = typeRegex.find(contentTypeValue)
            val contentType = found?.groupValues?.getOrNull(1)?.ifBlank { null } ?: contentTypeValue
            val charset = found?.groupValues?.getOrNull(2)?.ifBlank { null }
            WebResourceResponse(contentType, charset, this.body?.byteStream())
        } else {
            WebResourceResponse("application/octet-stream", null, this.body?.byteStream())
        }
    }
}
