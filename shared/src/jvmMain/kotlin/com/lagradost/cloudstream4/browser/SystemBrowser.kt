package com.lagradost.cloudstream4.browser

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.network.DesktopWebView
import com.lagradost.cloudstream4.AppDirs
import com.lagradost.cloudstream4.player.HumanChecks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The browser behind extensions' WebViewResolver and CloudflareKiller on desktop: Microsoft Edge,
 * which comes with Windows, or Chrome, run with its own profile in the app's cache folder and driven
 * through the DevTools protocol. It runs headless, without a window, and Cloudflare checks that pass
 * by themselves are passed. A page whose check wants a box ticked fails at once, and its site is
 * [asked]: the player leaves those sites for last, and only when nothing else plays offers to [pass]
 * their checks, in a window. The cookies of a passed check come back here. The browser closes after
 * a few idle minutes, and when the app exits.
 */
class SystemBrowser(
    private val executable: File,
    private val profile: File = AppDirs.cache.resolve("browser").toFile(),
    /** Without a window. False shows the browser, to watch what a page does */
    private val headless: Boolean = true,
) : DesktopWebView, HumanChecks {
    private val json = jacksonObjectMapper()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startLock = Mutex()
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    /** [browser] is the browser's main process, null when it could not be told */
    private class Connection(val browser: ProcessHandle?, val socket: WebSocket)

    @Volatile private var connection: Connection? = null
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonNode>>()
    /** Events of each page, by its DevTools session */
    private val sessions = ConcurrentHashMap<String, Channel<JsonNode>>()
    private val loads = AtomicInteger(0)
    private var idleJob: Job? = null
    /** The hidden tab that keeps the browser's session, see [start] */
    @Volatile private var keeper: String? = null
    /** A check per site at a time in a window: the cookies once passed, else null */
    private val checks = ConcurrentHashMap<String, Deferred<List<JsonNode>?>>()
    /** Sites whose check wants a person, with the page that asked and the user agent it had */
    private val blocked = ConcurrentHashMap<String, Pair<HttpUrl, String?>>()
    private val _asked = MutableSharedFlow<String>(extraBufferCapacity = 32)
    override val asked = _asked.asSharedFlow()

    @Volatile override var userAgent: String? = null
        private set

    /** Called with the browser's process id before its window comes forward, which Windows must allow */
    @Volatile var onShow: ((Long) -> Unit)? = null

    override suspend fun load(
        request: Request,
        userAgent: String?,
        script: String?,
        onScript: ((String) -> Unit)?,
        timeoutMs: Long,
        onRequest: (Request) -> Boolean,
    ) {
        loads.incrementAndGet()
        idleJob?.cancel()
        try {
            start()
            val target = newTab()
            try {
                val session = call("Target.attachToTarget", mapOf("targetId" to target, "flatten" to true)).path("sessionId").asText()
                val events = Channel<JsonNode>(Channel.UNLIMITED)
                sessions[session] = events
                try {
                    browse(request, userAgent, script, onScript, timeoutMs, onRequest, target, session, events)
                } finally {
                    sessions.remove(session)
                    events.close()
                }
            } finally {
                runCatching { call("Target.closeTarget", mapOf("targetId" to target)) }
            }
        } finally {
            if (loads.decrementAndGet() == 0) idleJob = scope.launch {
                delay(IDLE_CLOSE_MS)
                if (loads.get() == 0) stop()
            }
        }
    }

    private suspend fun browse(
        request: Request,
        userAgent: String?,
        script: String?,
        onScript: ((String) -> Unit)?,
        timeoutMs: Long,
        onRequest: (Request) -> Boolean,
        target: String,
        session: String,
        events: Channel<JsonNode>,
    ) {
        call("Network.enable", session = session)
        useAgent(userAgent, session)
        // Pictures, fonts and media are not needed to find links, as on Android
        call("Network.setBlockedURLs", mapOf("urls" to BLOCKED), session)
        val extra = request.headers.toMap().filterKeys { it.lowercase() !in setOf("user-agent", "referer", "cookie") }
        if (extra.isNotEmpty()) call("Network.setExtraHTTPHeaders", mapOf("headers" to extra), session)
        val referer = request.header("Referer")
        call("Page.navigate", buildMap { put("url", request.url.toString()); if (referer != null) put("referrer", referer) }, session)

        val deadline = Deadline(timeoutMs)
        val checker = handleChallenge(request.url, userAgent ?: this.userAgent, session, deadline)
        try {
            untilDeadline(deadline) {
                for (event in events) {
                    if (event.path("method").asText() != "Network.requestWillBeSent") continue
                    val sent = event.path("params").path("request")
                    val seen = toRequest(sent) ?: continue
                    if (script != null) {
                        runCatching { evaluateJson(script, session) }.getOrNull()?.let { onScript?.invoke(it) }
                    }
                    if (onRequest(seen)) break
                }
            }
        } finally {
            checker.cancel()
        }
    }

    /** The extension's user agent, else the browser's own without "Headless", which sites refuse */
    private suspend fun useAgent(agent: String?, session: String) {
        (agent ?: userAgent)?.let { call("Network.setUserAgentOverride", mapOf("userAgent" to it), session) }
    }

    /** When a page gives up waiting: after its time, or earlier, once its site wants a check only a person can pass */
    private class Deadline(val ms: Long) {
        val ended = CompletableDeferred<Unit>()
        fun end() {
            ended.complete(Unit)
        }
    }

    /** Runs [block] until it finishes, its time runs out, or the [deadline] is ended early */
    private suspend fun untilDeadline(deadline: Deadline, block: suspend () -> Unit) = coroutineScope {
        val work = launch { withTimeoutOrNull(deadline.ms) { block() } }
        val stopper = launch {
            deadline.ended.await()
            work.cancel()
        }
        work.join()
        stopper.cancel()
    }

    private suspend fun challenged(session: String): Boolean {
        val title = runCatching { evaluate("document.title", session) }.getOrNull().orEmpty()
        return CHALLENGE_TITLES.any { title.contains(it, ignoreCase = true) }
    }

    /**
     * A page still on Cloudflare's check after a while wants a box ticked, which a browser without a
     * window cannot do. The page stops waiting, like a site that is down, and the site is [asked], so
     * the user can [pass] its check once nothing else plays.
     */
    private fun handleChallenge(url: HttpUrl, agent: String?, session: String, deadline: Deadline) = scope.launch {
        delay(CHALLENGE_WAIT_MS)
        if (!challenged(session)) return@launch
        println("INFO SystemBrowser: ${url.host} wants a human check, its links are left for last")
        blocked[url.host] = url to agent
        _asked.tryEmit(url.host)
        deadline.end()
    }

    override suspend fun pass(host: String): Boolean {
        val (url, agent) = blocked[host] ?: return false
        // Several asking at once share one window
        val check = checks.computeIfAbsent(host) {
            scope.async {
                val window = SystemBrowser(executable, profile.resolveSibling("${profile.name}-check"), headless = false)
                window.onShow = onShow
                window.passInWindow(url, agent)
            }
        }
        val cookies = try {
            check.await()
        } finally {
            checks.remove(host, check)
        } ?: return false
        return runCatching {
            start()
            call("Storage.setCookies", mapOf("cookies" to cookies.map(::cookieParam)))
            blocked.remove(host)
            true
        }.getOrElse {
            println("WARN SystemBrowser: could not use the cookies of $host's check: $it")
            false
        }
    }

    /**
     * Opens [url] in this browser's window for the user to pass the site's check, and closes it after.
     * Returns the site's cookies once passed, or null when the window was closed or the time ran out.
     */
    private suspend fun passInWindow(url: HttpUrl, agent: String?): List<JsonNode>? = try {
        start()
        val target = newTab()
        val session = call("Target.attachToTarget", mapOf("targetId" to target, "flatten" to true)).path("sessionId").asText()
        // The cookie only counts for the user agent that passed
        useAgent(agent, session)
        call("Page.navigate", mapOf("url" to url.toString()), session)
        val window = call("Browser.getWindowForTarget", mapOf("targetId" to target)).path("windowId").asInt()
        call("Browser.setWindowBounds", mapOf("windowId" to window, "bounds" to mapOf("windowState" to "normal")))
        connection?.browser?.pid()?.let { onShow?.invoke(it) }
        call("Target.activateTarget", mapOf("targetId" to target))
        call("Page.bringToFront", session = session)
        withTimeoutOrNull(CHECK_TIMEOUT_MS) {
            while (true) {
                delay(1_000)
                // Throws once the user closes the window
                val title = evaluate("document.title", session).orEmpty()
                if (title.isNotBlank() && CHALLENGE_TITLES.none { title.contains(it, ignoreCase = true) }) break
            }
            val domain = url.host.removePrefix("www.")
            call("Storage.getCookies").path("cookies").filter { domain.endsWith(it.path("domain").asText().trimStart('.')) }
        }.also { println("INFO SystemBrowser: ${url.host}'s check ${if (it == null) "was not passed in time" else "was passed"}") }
    } catch (e: Exception) {
        println("INFO SystemBrowser: ${url.host}'s check was not passed: $e")
        null
    } finally {
        stop()
    }

    /** A cookie as Storage.getCookies gives it, in the form Storage.setCookies takes */
    private fun cookieParam(cookie: JsonNode): Map<String, Any> = buildMap {
        listOf("name", "value", "domain", "path").forEach { put(it, cookie.path(it).asText()) }
        put("secure", cookie.path("secure").asBoolean())
        put("httpOnly", cookie.path("httpOnly").asBoolean())
        cookie.path("sameSite").takeIf { it.isTextual }?.let { put("sameSite", it.asText()) }
        if (!cookie.path("session").asBoolean()) put("expires", cookie.path("expires").asDouble())
    }

    private fun toRequest(sent: JsonNode): Request? = runCatching {
        val url = sent.path("url").asText()
        if (!url.startsWith("http")) return null
        Request.Builder().url(url).apply {
            sent.path("headers").properties().forEach { (name, value) -> header(name, value.asText()) }
            val method = sent.path("method").asText("GET")
            if (method != "GET") {
                val body = sent.path("postData").asText("").toByteArray()
                method(method, if (method == "HEAD") null else body.toRequestBody())
            }
        }.build()
    }.getOrNull()

    private suspend fun evaluate(expression: String, session: String): String? =
        call("Runtime.evaluate", mapOf("expression" to expression, "returnByValue" to true), session).path("result").path("value").takeIf { !it.isMissingNode }?.asText()

    /** The script's result as JSON, like Android's evaluateJavascript */
    private suspend fun evaluateJson(expression: String, session: String): String {
        val value = call("Runtime.evaluate", mapOf("expression" to expression, "returnByValue" to true, "awaitPromise" to true), session).path("result").path("value")
        return if (value.isMissingNode) "null" else json.writeValueAsString(value)
    }

    override suspend fun fetch(request: Request): Response? {
        loads.incrementAndGet()
        idleJob?.cancel()
        try {
            start()
            val target = newTab()
            try {
                val session = call("Target.attachToTarget", mapOf("targetId" to target, "flatten" to true)).path("sessionId").asText()
                val events = Channel<JsonNode>(Channel.UNLIMITED)
                sessions[session] = events
                try {
                    useAgent(null, session)
                    return fetchIn(target, session, events, request)
                } finally {
                    sessions.remove(session)
                    events.close()
                }
            } finally {
                runCatching { call("Target.closeTarget", mapOf("targetId" to target)) }
            }
        } catch (e: Exception) {
            println("WARN SystemBrowser: fetch of ${request.url} failed: $e")
            return null
        } finally {
            if (loads.decrementAndGet() == 0) idleJob = scope.launch {
                delay(IDLE_CLOSE_MS)
                if (loads.get() == 0) stop()
            }
        }
    }

    private suspend fun fetchIn(target: String, session: String, events: Channel<JsonNode>, request: Request): Response? =
        if (request.method == "GET") navigateIn(target, session, events, request) else fetchInPage(session, events, request)

    /** Loads a GET request as the page itself, which Cloudflare trusts more than a script's fetch() */
    private suspend fun navigateIn(target: String, session: String, events: Channel<JsonNode>, request: Request): Response? {
        val url = request.url
        call("Network.enable", session = session)
        call("Page.enable", session = session)
        call("Network.setBlockedURLs", mapOf("urls" to BLOCKED), session)
        val extra = request.headers.toMap().filterKeys { it.lowercase() !in setOf("user-agent", "referer", "cookie") }
        if (extra.isNotEmpty()) call("Network.setExtraHTTPHeaders", mapOf("headers" to extra), session)
        call("Page.navigate", buildMap { put("url", url.toString()); request.header("Referer")?.let { put("referrer", it) } }, session)
        // The page's last document: Cloudflare's check marks its page, and loads the real one once passed
        var document: JsonNode? = null
        var finished = false
        val deadline = Deadline(FETCH_TIMEOUT_MS)
        val checker = handleChallenge(url, userAgent, session, deadline)
        try {
            untilDeadline(deadline) {
                for (event in events) {
                    val params = event.path("params")
                    when (event.path("method").asText()) {
                        "Network.responseReceived" -> if (params.path("type").asText() == "Document") {
                            document = params
                            finished = false
                        }

                        "Network.loadingFinished" -> if (params.path("requestId").asText() == document?.path("requestId")?.asText()) {
                            finished = true
                        }

                        // Once the page is up, it is either the real one or Cloudflare's check, which loads the real one when passed
                        "Page.loadEventFired" -> if (finished) {
                            val marked = (document?.path("response")?.path("headers") as? ObjectNode)?.fields()?.asSequence()
                                ?.any { it.key.equals("cf-mitigated", ignoreCase = true) && it.value.asText() == "challenge" } == true
                            val title = runCatching { evaluate("document.title", session) }.getOrNull().orEmpty()
                            if (!marked && CHALLENGE_TITLES.none { title.contains(it, ignoreCase = true) }) break
                        }
                    }
                }
            }
        } finally {
            checker.cancel()
        }
        val done = document?.takeIf { finished } ?: return null
        val response = done.path("response")
        val body = call("Network.getResponseBody", mapOf("requestId" to done.path("requestId").asText()), session)
        val bytes = if (body.path("base64Encoded").asBoolean()) Base64.getDecoder().decode(body.path("body").asText())
        else body.path("body").asText().toByteArray()
        val headers = Headers.Builder().apply {
            (response.path("headers") as? ObjectNode)?.fields()?.forEach { (name, value) ->
                // Several values of a header come joined by new lines
                value.asText().split('\n').forEach { runCatching { addUnsafeNonAscii(name, it) } }
            }
            // The body is already decoded
            removeAll("Content-Encoding")
            removeAll("Content-Length")
        }.build()
        println("INFO SystemBrowser: loaded $url in the page: ${response.path("status").asInt()}")
        return Response.Builder()
            .request(request.newBuilder().url(response.path("url").asText(url.toString())).build())
            .protocol(Protocol.HTTP_1_1)
            .code(response.path("status").asInt())
            .message(response.path("statusText").asText(""))
            .headers(headers)
            .body(bytes.toResponseBody(headers["Content-Type"]?.toMediaTypeOrNull()))
            .build()
    }

    private suspend fun fetchInPage(session: String, events: Channel<JsonNode>, request: Request): Response? {
        // fetch() only reaches the page's own site: a small file of it is opened first
        val url = request.url
        val origin = "${url.scheme}://${url.host}" + if (url.port != HttpUrl.defaultPort(url.scheme)) ":${url.port}" else ""
        call("Page.enable", session = session)
        call("Page.navigate", mapOf("url" to "$origin/robots.txt"), session)
        withTimeoutOrNull(20_000) {
            for (event in events) if (event.path("method").asText() == "Page.loadEventFired") break
        }
        val init = buildMap<String, Any?> {
            put("method", request.method)
            put("credentials", "include")
            put("headers", request.headers.toMap().filterKeys { it.lowercase() !in FORBIDDEN_FETCH_HEADERS })
            request.header("Referer")?.let { put("referrer", it) }
            request.body?.let { body ->
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                put("body", buffer.readUtf8())
            }
        }
        val script = """
            (async () => {
              const r = await fetch(${json.writeValueAsString(url.toString())}, ${json.writeValueAsString(init)});
              const bytes = new Uint8Array(await r.arrayBuffer());
              let binary = "";
              for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
              return { status: r.status, url: r.url, headers: [...r.headers.entries()], body: btoa(binary) };
            })()
        """.trimIndent()
        val evaluated = call("Runtime.evaluate", mapOf("expression" to script, "returnByValue" to true, "awaitPromise" to true), session)
        val result = evaluated.path("result").path("value")
        if (result.isMissingNode || !result.has("status")) {
            println("WARN SystemBrowser: fetch of $url failed in the page: ${evaluated.path("exceptionDetails").path("exception").path("description").asText(evaluated.toString().take(300))}")
            return null
        }
        println("INFO SystemBrowser: fetched $url in the page: ${result.path("status").asInt()}")
        val headers = Headers.Builder().apply {
            result.path("headers").forEach { pair -> runCatching { addUnsafeNonAscii(pair[0].asText(), pair[1].asText()) } }
        }.build()
        val body = Base64.getDecoder().decode(result.path("body").asText())
        return Response.Builder()
            .request(request.newBuilder().url(result.path("url").asText(url.toString())).build())
            .protocol(Protocol.HTTP_1_1)
            .code(result.path("status").asInt())
            .message("")
            .headers(headers)
            .body(body.toResponseBody(headers["Content-Type"]?.toMediaTypeOrNull()))
            .build()
    }

    override fun cookies(url: String): String? {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return null
        if (connection == null) return null
        val all = runBlocking { withTimeout(10_000) { call("Storage.getCookies") } }.path("cookies")
        val matching = all.filter { cookie ->
            val domain = cookie.path("domain").asText().trimStart('.')
            host == domain || host.endsWith(".$domain")
        }
        if (matching.isEmpty()) return null
        return matching.joinToString("; ") { "${it.path("name").asText()}=${it.path("value").asText()}" }
    }

    /** Sends one DevTools command and waits for its result */
    private suspend fun call(method: String, params: Map<String, Any?> = emptyMap(), session: String? = null): JsonNode {
        val socket = connection?.socket ?: throw IllegalStateException("The browser is not running")
        val id = nextId.getAndIncrement()
        val answer = CompletableDeferred<JsonNode>()
        pending[id] = answer
        val message = buildMap { put("id", id); put("method", method); put("params", params); if (session != null) put("sessionId", session) }
        if (!socket.send(json.writeValueAsString(message))) {
            pending.remove(id)
            throw IllegalStateException("The browser closed")
        }
        val reply = try {
            withTimeout(CALL_TIMEOUT_MS) { answer.await() }
        } finally {
            pending.remove(id)
        }
        reply.path("error").takeIf { !it.isMissingNode }?.let { throw IllegalStateException("$method: ${it.path("message").asText()}") }
        return reply.path("result")
    }

    private suspend fun start() = startLock.withLock {
        connection?.takeIf { it.browser?.isAlive != false }?.let { return }
        connection = null
        profile.mkdirs()
        val portFile = profile.resolve("DevToolsActivePort")
        portFile.delete()
        val launcher = ProcessBuilder(
            executable.absolutePath,
            "--remote-debugging-port=0",
            "--user-data-dir=${profile.absolutePath}",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-extensions",
            "--disable-sync",
            "--hide-crash-restore-bubble",
            "--disable-features=msImplicitSignin,msEdgeWelcomePage",
            if (headless) "--headless=new" else "--window-position=120,80",
            "--window-size=1100,800",
            "about:blank",
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()

        // The browser writes its DevTools port and address once it listens. Headless Edge starts
        // its browser as another process and the one started here exits, so it is not waited on
        val lines = withTimeoutOrNull(START_TIMEOUT_MS) { devToolsAddress(portFile) }
        if (lines == null) {
            launcher.destroyForcibly()
            throw IllegalStateException("${executable.name} did not start")
        }
        val opened = CompletableDeferred<Unit>()
        val socket = http.newWebSocket(
            Request.Builder().url("ws://127.0.0.1:${lines[0].trim()}${lines[1].trim()}").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = runCatching { json.readTree(text) }.getOrNull() ?: return
                    val id = message.path("id")
                    if (!id.isMissingNode) {
                        pending[id.asInt()]?.complete(message)
                    } else if (message.path("method").asText() == "Target.targetCreated") {
                        closePopup(message.path("params").path("targetInfo"))
                    } else {
                        message.path("sessionId").asText(null)?.let { sessions[it]?.trySend(message) }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    opened.completeExceptionally(t)
                    failAll(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    failAll(IllegalStateException("The browser closed"))
                }
            },
        )
        withTimeout(START_TIMEOUT_MS) { opened.await() }
        connection = Connection(launcher.toHandle().takeIf { it.isAlive }, socket)
        // The browser's own process, to close it if it does not close when asked
        runCatching {
            call("SystemInfo.getProcessInfo").path("processInfo").firstOrNull { it.path("type").asText() == "browser" }?.path("id")?.asLong()
        }.getOrNull()?.let(ProcessHandle::of)?.orElse(null)?.let { connection = Connection(it, socket) }
        userAgent = runCatching { call("Browser.getVersion").path("userAgent").asText().replace("Headless", "") }.getOrNull()
        // Windows lists Edge's windows in Alt+Tab, also those of a browser without a window, which can be
        // neither seen nor closed there. Its tabs are hidden ones, which have no window, and the blank
        // tab it starts with is closed. One hidden tab stays open: without any, Edge ends the session
        // and forgets its session cookies. Popups that pages open get a window, so they are closed
        runCatching { call("Target.setDiscoverTargets", mapOf("discover" to true)) }
        if (headless) runCatching {
            val keeper = newTab()
            call("Target.getTargets").path("targetInfos")
                .filter { it.path("type").asText() == "page" && it.path("targetId").asText() != keeper }
                .forEach { call("Target.closeTarget", mapOf("targetId" to it.path("targetId").asText())) }
            this.keeper = keeper
        }
        println("INFO SystemBrowser: started ${executable.name}, $userAgent")
    }

    /** Opens a blank tab, without a window when headless, and returns its target */
    private suspend fun newTab(): String {
        val hidden = if (headless) mapOf("hidden" to true, "background" to true) else emptyMap()
        return call("Target.createTarget", mapOf("url" to "about:blank") + hidden).path("targetId").asText()
    }

    /** The addresses of the open tabs, but for the one kept open */
    internal suspend fun tabs(): List<String> = call("Target.getTargets").path("targetInfos")
        .filter { it.path("type").asText() == "page" && it.path("targetId").asText() != keeper }
        .map { it.path("url").asText() }

    /** Closes a tab a page opened, an ad most often: links are only looked for in the page itself */
    private fun closePopup(target: JsonNode) {
        if (target.path("type").asText() != "page" || target.path("openerId").asText().isEmpty()) return
        scope.launch { runCatching { call("Target.closeTarget", mapOf("targetId" to target.path("targetId").asText())) } }
    }

    private suspend fun devToolsAddress(portFile: File): List<String> {
        while (true) {
            val lines = runCatching { portFile.readLines() }.getOrNull()
            if (lines != null && lines.size >= 2) return lines
            delay(100)
        }
    }

    private fun failAll(error: Throwable) {
        connection = null
        pending.values.forEach { it.completeExceptionally(error) }
        sessions.values.forEach { it.close() }
    }

    /** Closes the browser, also when the app exits */
    fun stop() {
        val current = connection ?: return
        connection = null
        runCatching { current.socket.send("""{"id":0,"method":"Browser.close"}""") }
        current.browser?.let { browser ->
            val closed = runCatching { browser.onExit().get(3, TimeUnit.SECONDS) }.isSuccess
            if (!closed) {
                browser.descendants().forEach { it.destroyForcibly() }
                browser.destroyForcibly()
            }
        }
        current.socket.cancel()
    }

    private fun JsonNode.properties(): List<Pair<String, JsonNode>> =
        (this as? ObjectNode)?.fields()?.asSequence()?.map { it.key to it.value }?.toList().orEmpty()

    companion object {
        /** A check that passes by itself is done by then */
        private const val CHALLENGE_WAIT_MS = 8_000L
        /** How long the user has to pass a check in the window */
        private const val CHECK_TIMEOUT_MS = 3 * 60_000L
        private const val CALL_TIMEOUT_MS = 30_000L
        private const val START_TIMEOUT_MS = 20_000L
        private const val IDLE_CLOSE_MS = 3 * 60_000L
        private const val FETCH_TIMEOUT_MS = 60_000L
        /** Headers a page's fetch() may not set: the browser sends its own */
        private val FORBIDDEN_FETCH_HEADERS = setOf(
            "user-agent", "cookie", "referer", "origin", "host", "content-length", "connection", "accept-encoding",
            "sec-fetch-site", "sec-fetch-mode", "sec-fetch-dest", "sec-fetch-user", "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform",
        )
        private val CHALLENGE_TITLES = listOf("Just a moment", "Attention Required", "Un instant")
        private val BLOCKED = listOf(
            "*.jpg", "*.jpeg", "*.png", "*.webp", "*.gif", "*.gifv", "*.mp4", "*.mp3", "*.webm", "*.mkv",
            "*.avi", "*.mov", "*.flv", "*.ogg", "*.wav", "*.woff", "*.woff2", "*.ttf", "*.ts", "*.vtt", "*.srt",
            "*/favicon.ico",
        )

        /** Edge, which comes with Windows, else Chrome or Chromium. Null when none is installed */
        fun find(): File? {
            val env = System.getenv()
            val windows = listOfNotNull(env["ProgramFiles(x86)"], env["ProgramFiles"], env["LOCALAPPDATA"]).flatMap { base ->
                listOf("Microsoft\\Edge\\Application\\msedge.exe", "Google\\Chrome\\Application\\chrome.exe").map { File(base, it) }
            }
            val others = listOf(
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/usr/bin/microsoft-edge", "/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser",
            ).map(::File)
            return (windows + others).firstOrNull { it.isFile }
        }

        /** The browser for extensions, once installed */
        @Volatile var instance: SystemBrowser? = null
            private set

        /** Installs the browser for extensions, when one is found */
        fun install(): SystemBrowser? {
            val executable = find() ?: return null.also { println("WARN SystemBrowser: no Edge or Chrome found, extensions cannot use a WebView") }
            val browser = SystemBrowser(executable)
            instance = browser
            DesktopWebView.engine = browser
            Runtime.getRuntime().addShutdownHook(Thread { browser.stop() })
            return browser
        }
    }
}
