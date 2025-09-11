package com.lagradost.cloudstream3.utils.newpipe

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject

class PoTokenWebView private constructor(
    context: Context,
    // to be used exactly once only during initialization!
    private val generatorContinuation: Continuation<PoTokenWebView>,
) {
    private val webView = WebView(context)
    private val poTokenContinuations = mutableMapOf<String, Continuation<String>>()
    private val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        onInitializationError(exception)
    }
    private lateinit var expirationInstant: Instant

    //region Initialization
    init {
        webView.settings.apply {
            //noinspection SetJavaScriptEnabled we want to use JavaScript!
            javaScriptEnabled = true
            if (Build.VERSION.SDK_INT >= 26) {
                safeBrowsingEnabled = false
            }
            userAgentString = USER_AGENT
            blockNetworkLoads = true // the WebView does not need internet access
        }

        // so that we can run async functions and get back the result
        webView.addJavascriptInterface(this, JS_INTERFACE)
    }

    /**
     * Must be called right after instantiating [PoTokenWebView] to perform the actual
     * initialization. This will asynchronously go through all the steps needed to load BotGuard,
     * run it, and obtain an `integrityToken`.
     */
    private fun loadHtmlAndObtainBotguard(context: Context) {
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            try {
                val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
                withContext(Dispatchers.Main) {
                    webView.loadDataWithBaseURL(
                        "https://www.youtube.com",
                        html.replaceFirst(
                            "</script>",
                            // calls downloadAndRunBotguard() when the page has finished loading
                            "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"
                        ),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            } catch (e: Exception) {
                onInitializationError(e)
            }
        }
    }

    /**
     * Called during initialization by the JavaScript snippet appended to the HTML page content in
     * [loadHtmlAndObtainBotguard] after the WebView content has been loaded.
     */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            val responseBody = makeBotguardServiceRequest(
                "https://www.youtube.com/api/jnn/v1/Create",
                listOf(REQUEST_KEY)
            )
            val parsedChallengeData = parseChallengeData(responseBody)
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    """try {
                             data = $parsedChallengeData
                             runBotGuard(data).then(function (result) {
                                 this.webPoSignalOutput = result.webPoSignalOutput
                                 $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                             }, function (error) {
                                 $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                             })
                         } catch (error) {
                             $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                         }""",
                    null
                )
            }
        }
    }

    /**
     * Called during initialization by the JavaScript snippets from either
     * [downloadAndRunBotguard] or [onRunBotguardResult].
     */
    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        onInitializationError(Exception(error))
    }

    /**
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard] after
     * obtaining the BotGuard execution output [botguardResponse].
     */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        CoroutineScope(Dispatchers.IO).launch(exceptionHandler) {
            val response = makeBotguardServiceRequest(
                "https://www.youtube.com/api/jnn/v1/GenerateIT",
                listOf(REQUEST_KEY, botguardResponse)
            )
            val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(response)

            // leave 10 minutes of margin just to be sure
            expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds - 600)

            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    "this.integrityToken = $integrityToken"
                ) {
                    generatorContinuation.resume(this@PoTokenWebView)
                }
            }
        }
    }
    //endregion

    //region Obtaining poTokens
    suspend fun generatePoToken(identifier: String): String {
        return suspendCancellableCoroutine { continuation ->
            poTokenContinuations[identifier] = continuation
            val u8Identifier = stringToU8(identifier)

            Handler(Looper.getMainLooper()).post {
                webView.evaluateJavascript(
                    """try {
                        identifier = "$identifier"
                        u8Identifier = $u8Identifier
                        poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                        poTokenU8String = ""
                        for (i = 0; i < poTokenU8.length; i++) {
                            if (i != 0) poTokenU8String += ","
                            poTokenU8String += poTokenU8[i]
                        }
                        $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                    } catch (error) {
                        $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                    }""",
                ) {}
            }
        }
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] when an error occurs in calling the
     * JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        poTokenContinuations.remove(identifier)?.resumeWithException(Exception(error))
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] with the original identifier and the
     * result of the JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        val poToken = try {
            u8ToBase64(poTokenU8)
        } catch (t: Throwable) {
            poTokenContinuations.remove(identifier)?.resumeWithException(t)
            return
        }

        poTokenContinuations.remove(identifier)?.resume(poToken)
    }

    fun isExpired(): Boolean {
        return Instant.now().isAfter(expirationInstant)
    }
    //endregion

    //region Utils
    /**
     * Makes a POST request to [url] with the given [data] by setting the correct headers.
     * This is supposed to be used only during initialization. Returns the  response body
     * as a String if the response is successful.
     */
    private suspend fun makeBotguardServiceRequest(url: String, data: List<String>): String = withContext(Dispatchers.IO) {
        val requestBody = data.toJson().toRequestBody()
        val requestBuilder = okhttp3.Request.Builder()
            .post(requestBody)
            .headers(mapOf(
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json",
                "Content-Type" to "application/json+protobuf",
                "x-goog-api-key" to GOOGLE_API_KEY,
                "x-user-agent" to "grpc-web-javascript/0.1",
            ).toHeaders())
            .url(url)
        val response = withContext(Dispatchers.IO) {
            httpClient.newCall(requestBuilder.build()).execute()
        }
        val httpCode = response.code
        if (httpCode != 200) {
            throw Exception("Invalid response code: $httpCode")
        } else {
            val body = withContext(Dispatchers.IO) {
                response.body.string()
            }
            body
        }
    }

    /**
     * Handles any error happening during initialization, releasing resources and sending the error
     * to [generatorContinuation].
     */
    private fun onInitializationError(error: Throwable) {
        CoroutineScope(Dispatchers.Main).launch {
            close()
            generatorContinuation.resumeWithException(error)
        }
    }

    /**
     * Releases all [webView] resources.
     */
    @MainThread
    fun close() = with(webView) {
        clearHistory()
        // clears RAM cache and disk cache (globally for all WebViews)
        clearCache(true)

        // ensures that the WebView isn't doing anything when destroying it
        loadUrl("about:blank")

        onPause()
        removeAllViews()
        destroy()
    }
    //endregion

    companion object {
        private const val TAG = "PoTokenWebView"
        //libretube api key
        private var GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"

        private val httpClient = OkHttpClient.Builder()
            //.proxy(YouTube.proxy)
            .build()

        suspend fun getNewPoTokenGenerator(context: Context): PoTokenWebView {
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val potWv = PoTokenWebView(context, cont)
                    potWv.loadHtmlAndObtainBotguard(context)
                }
            }
        }
    }

    fun parseChallengeData(rawChallengeData: String): String {
        val objectMapper = ObjectMapper()

        val scrambled = objectMapper.readTree(rawChallengeData) as ArrayNode
        val challengeData = if (scrambled.size() > 1 && scrambled[1].isTextual) {
            val descrambled = descramble(scrambled[1].asText())
            objectMapper.readTree(descrambled) as ArrayNode
        } else {
            scrambled[1] as ArrayNode
        }

        val messageId = challengeData[0].asText()
        val interpreterHash = challengeData[3].asText()
        val program = challengeData[4].asText()
        val globalName = challengeData[5].asText()
        val clientExperimentsStateBlob = challengeData[7].asText()

        val privateDoNotAccessOrElseSafeScriptWrappedValue = challengeData[1]
            .takeIf { !it.isNull }
            ?.let { node ->
                if (node.isArray) {
                    node.find { it.isTextual }
                } else null
            }

        val privateDoNotAccessOrElseTrustedResourceUrlWrappedValue = challengeData[2]
            .takeIf { !it.isNull }
            ?.let { node ->
                if (node.isArray) {
                    node.find { it.isTextual }
                } else null
            }

        val resultNode = objectMapper.createObjectNode().apply {
            put("messageId", messageId)
            set<ObjectNode>("interpreterJavascript", objectMapper.createObjectNode().apply {
                set<JsonNode>("privateDoNotAccessOrElseSafeScriptWrappedValue",
                    privateDoNotAccessOrElseSafeScriptWrappedValue ?: NullNode.instance)
                set<JsonNode>("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue",
                    privateDoNotAccessOrElseTrustedResourceUrlWrappedValue ?: NullNode.instance)
            })
            put("interpreterHash", interpreterHash)
            put("program", program)
            put("globalName", globalName)
            put("clientExperimentsStateBlob", clientExperimentsStateBlob)
        }

        return objectMapper.writeValueAsString(resultNode)
    }

    private fun descramble(scrambledChallenge: String): String {
        return base64DecodeArray(scrambledChallenge)
            .map { (it + 97).toByte() }
            .toByteArray()
            .decodeToString()
    }

    fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
        val integrityTokenData = JSONArray(rawIntegrityTokenData)
//        return base64ToU8(integrityTokenData[0].jsonPrimitive.content) to integrityTokenData[1].jsonPrimitive.long
        return base64ToU8(integrityTokenData
            .getJSONObject(0).getString("jsonPrimitive")) to
                integrityTokenData.getJSONObject(1).getLong("jsonPrimitive")
    }

    private fun base64ToU8(base64: String): String {
        return newUint8Array(base64DecodeArray(base64))
    }
    private fun newUint8Array(contents: ByteArray): String {
        return "new Uint8Array([" + contents.joinToString(separator = ",") { it.toUByte().toString() } + "])"
    }
    private fun u8ToBase64(poToken: String): String {
        return poToken.split(",")
            .map { it.toUByte().toByte() }
            .toByteArray()
            .toByteString()
            .base64()
            .replace("+", "-")
            .replace("/", "_")
    }

    private fun stringToU8(identifier: String): String {
        return newUint8Array(identifier.toByteArray())
    }
}