package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.mihon.common.preference.DataPreferenceStore
import java.util.Locale

/**
 * Đọc phụ đề bằng Android TextToSpeech, ngôn ngữ và giọng đọc chọn trong cài đặt (mặc định tiếng Việt).
 *
 * Các câu được xếp hàng (không câu nào bị cắt ngang). Khi phụ đề dày hơn tốc độ đọc có 3 cách xử lý:
 *  1. Tự tăng tốc: càng trễ thì đọc câu kế tiếp càng nhanh, tối đa [PREF_MAX_RATE] %.
 *  2. Bỏ câu trễ quá [PREF_DROP_SECONDS] giây so với lúc nó hiện trên màn hình.
 *  3. Chế độ chờ ([PREF_PAUSE_MODE]): tạm dừng phim đến khi đọc xong, không tăng tốc, không bỏ câu.
 *
 * Khi đang đọc, âm lượng gốc của video hạ xuống [PREF_ORIGINAL_VOLUME] % rồi trả lại khi hết hàng đợi.
 * Mọi trạng thái chỉ được đụng tới trên main thread.
 */
object SubtitleTts {
    private const val TAG = "SubtitleTts"

    const val PREF_ENABLED = "subtitle_tts_enabled"
    const val PREF_VOICE = "subtitle_tts_voice"
    const val PREF_LANGUAGE = "subtitle_tts_language"
    const val PREF_ORIGINAL_VOLUME = "subtitle_tts_original_volume"
    const val PREF_SPEECH_RATE = "subtitle_tts_speech_rate"
    const val PREF_AUTO_BOOST = "subtitle_tts_auto_boost"
    const val PREF_MAX_RATE = "subtitle_tts_max_rate"
    const val PREF_DROP_SECONDS = "subtitle_tts_drop_seconds"
    const val PREF_PAUSE_MODE = "subtitle_tts_pause_mode"
    const val PREF_SHOW_SUBTITLE = "subtitle_tts_show_subtitle"

    /** Ngôn ngữ đọc mặc định (tag BCP-47). */
    const val DEFAULT_LANGUAGE = "vi-VN"

    /** Giá trị đặc biệt của [PREF_VOICE]: để hệ thống chọn giọng mặc định của ngôn ngữ. */
    const val VOICE_DEFAULT = ""

    const val DEFAULT_ORIGINAL_VOLUME = 20 // %
    const val DEFAULT_SPEECH_RATE = 100 // %
    const val DEFAULT_MAX_RATE = 200 // %
    const val DEFAULT_DROP_SECONDS = 5

    /** Độ trễ (giây) được chấp nhận trước khi bắt đầu tăng tốc. */
    private const val LAG_TOLERANCE_SEC = 1.0f

    /** Số ký tự đọc được mỗi giây ở tốc độ 1.0x (giá trị khởi đầu, sẽ tự học lại). */
    private const val INITIAL_CPS = 13f

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Các thao tác cần dùng tới player, do CS3IPlayer cung cấp. */
    class Host(
        val getVolume: () -> Float,
        val setVolume: (Float) -> Unit,
        /** true nếu người dùng đang muốn phát (playWhenReady), kể cả lúc đang buffer. */
        val isPlayRequested: () -> Boolean,
        val setPlaying: (Boolean) -> Unit,
    )

    private class Item(val id: String, val text: String, val enqueuedAt: Long)

    private var tts: TextToSpeech? = null
    private var ready = false
    private var appContext: Context? = null
    private var host: Host? = null

    private val queue = ArrayDeque<Item>()
    private var current: Item? = null
    private var currentStartMs = 0L
    private var currentRate = 1f
    private var utteranceCounter = 0L
    private var lastText: String? = null

    /** Số ký tự đọc được mỗi giây ở 1.0x, học dần từ thời gian đọc thực tế. */
    private var cps = INITIAL_CPS

    // Hạ âm lượng
    private var ducked = false
    private var savedVolume = 1f

    // Chế độ chờ: phim đang bị tạm dừng bởi bộ đọc (khác với người dùng tự bấm tạm dừng)
    private var pausedByTts = false

    private var appliedKey: String? = null

    private val watchdog = Runnable {
        Log.w(TAG, "Utterance timed out, moving on")
        val item = current
        if (item != null) {
            try {
                tts?.stop()
            } catch (t: Throwable) {
                Log.e(TAG, "stop failed", t)
            }
            current = null
            pump()
        }
    }

    private fun store(context: Context) = DataPreferenceStore(context)

    fun isEnabled(context: Context): Boolean =
        store(context).getBoolean(PREF_ENABLED, false).get()

    /** False only when reading is on and the user chose to hide the on-screen subtitle. */
    fun shouldShowSubtitle(context: Context): Boolean {
        val appCtx = context.applicationContext
        return !isEnabled(appCtx) || store(appCtx).getBoolean(PREF_SHOW_SUBTITLE, true).get()
    }

    /**
     * Gọi với mỗi nhóm cue mới của phụ đề (có thể gọi từ bất kỳ thread nào).
     */
    fun onSubtitleText(context: Context, text: CharSequence?, host: Host) {
        val appCtx = context.applicationContext
        if (!isEnabled(appCtx)) {
            mainHandler.post { if (tts != null) stopAll() }
            return
        }

        val clean = clean(text)
        if (clean.isEmpty()) {
            // Cue biến mất: để câu đang đọc nói nốt, chỉ cho phép đọc lại câu giống hệt sau này.
            lastText = null
            return
        }
        // Player có thể gửi lại cùng cue nhiều lần (đổi style, seek nhẹ...)
        if (clean == lastText) return
        lastText = clean

        val now = SystemClock.elapsedRealtime()
        mainHandler.post {
            // Không đọc các dòng hiện ra khi người dùng đang tạm dừng (trừ khi chính ta tạm dừng).
            if (!host.isPlayRequested() && !pausedByTts) return@post

            this.host = host
            appContext = appCtx
            queue.addLast(Item("sub_tts_${++utteranceCounter}", clean, now))

            ensureEngine(appCtx)
            if (ready) pump()
        }
    }

    // ---------------------------------------------------------------- engine

    private fun ensureEngine(appCtx: Context) {
        if (tts != null) return
        tts = TextToSpeech(appCtx) { status ->
            mainHandler.post {
                val engine = tts ?: return@post
                if (status != TextToSpeech.SUCCESS) {
                    Log.e(TAG, "TTS init failed: $status")
                    release()
                    return@post
                }
                engine.setOnUtteranceProgressListener(progressListener)
                ready = true
                pump()
            }
        }
    }

    private fun applyVoice(appCtx: Context, engine: TextToSpeech) {
        val settings = store(appCtx)
        val languageTag = settings.getString(PREF_LANGUAGE, DEFAULT_LANGUAGE).get()
            .ifBlank { DEFAULT_LANGUAGE }
        val voiceName = settings.getString(PREF_VOICE, VOICE_DEFAULT).get()
        val key = "$languageTag|$voiceName"
        if (key == appliedKey) return
        appliedKey = key

        val locale = Locale.forLanguageTag(languageTag)
        val voice = if (voiceName.isEmpty()) null else
            engine.voices?.firstOrNull { it.name == voiceName && it.locale.language == locale.language }
        if (voice != null) {
            engine.setVoice(voice)
        } else {
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language not available on this device: $languageTag")
            }
        }
    }

    // ---------------------------------------------------------------- hàng đợi

    /** Lấy câu tiếp theo trong hàng đợi và đọc, hoặc kết thúc nếu hết. */
    private fun pump() {
        val appCtx = appContext ?: return
        val engine = tts ?: return
        if (!ready) return

        val settings = store(appCtx)
        val pauseMode = settings.getBoolean(PREF_PAUSE_MODE, false).get()

        if (current != null) {
            // Đang đọc mà có câu mới tới: chế độ chờ thì tạm dừng phim cho tới khi đọc xong.
            if (pauseMode && queue.isNotEmpty()) pauseVideo()
            return
        }

        val dropMs = settings.getInt(PREF_DROP_SECONDS, DEFAULT_DROP_SECONDS).get()
            .coerceIn(1, 60) * 1000L
        val now = SystemClock.elapsedRealtime()

        // Bỏ các câu đã chờ quá lâu (trừ chế độ chờ), rồi lấy câu đầu tiên còn lại
        if (!pauseMode) {
            while (queue.isNotEmpty() && now - queue.first().enqueuedAt > dropMs) {
                Log.d(TAG, "Dropped stale line: ${queue.removeFirst().text}")
            }
        }
        val item = queue.removeFirstOrNull()
        if (item == null) {
            onIdle()
            return
        }

        applyVoice(appCtx, engine)

        // --- tốc độ đọc câu này
        val baseRate = settings.getInt(PREF_SPEECH_RATE, DEFAULT_SPEECH_RATE).get()
            .coerceIn(50, 200) / 100f
        var rate = baseRate
        val autoBoost = settings.getBoolean(PREF_AUTO_BOOST, true).get()
        if (!pauseMode && autoBoost) {
            val maxRate = settings.getInt(PREF_MAX_RATE, DEFAULT_MAX_RATE).get()
                .coerceIn(100, 200) / 100f
            val waited = (now - item.enqueuedAt) / 1000f
            val pendingChars = queue.sumOf { it.text.length }
            val lag = waited + pendingChars / (cps * baseRate)
            if (lag > LAG_TOLERANCE_SEC) {
                val boost = 1f + (lag - LAG_TOLERANCE_SEC) / 2f
                rate = (baseRate * boost).coerceAtMost(maxOf(maxRate, baseRate))
            }
        }
        engine.setSpeechRate(rate)

        duck(appCtx)

        current = item
        currentRate = rate
        currentStartMs = now

        val params = Bundle()
        val result = engine.speak(item.text, TextToSpeech.QUEUE_FLUSH, params, item.id)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "speak failed: $result")
            current = null
            pump()
            return
        }

        // Giới hạn thời gian phòng khi engine không bao giờ báo xong, tránh treo phim ở chế độ chờ.
        mainHandler.removeCallbacks(watchdog)
        val expectedMs = (item.text.length / (cps * rate) * 1000f).toLong()
        mainHandler.postDelayed(watchdog, expectedMs * 2 + 3000L)

        // Chế độ chờ: câu cuối cùng đã bắt đầu đọc thì cho phim chạy tiếp, vừa xem vừa nghe.
        if (queue.isEmpty()) resumeVideo() else if (pauseMode) pauseVideo()
    }

    private fun onIdle() {
        mainHandler.removeCallbacks(watchdog)
        restoreVolume()
        resumeVideo()
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            mainHandler.post {
                if (current?.id == utteranceId) currentStartMs = SystemClock.elapsedRealtime()
            }
        }

        override fun onDone(utteranceId: String?) = finished(utteranceId, true)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId, false)

        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId, false)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId, false)

        private fun finished(utteranceId: String?, success: Boolean) {
            mainHandler.post {
                val item = current ?: return@post
                if (item.id != utteranceId) return@post // câu cũ đã bị dừng

                if (success) learnSpeed(item)
                current = null
                pump()
            }
        }
    }

    /** Học tốc độ đọc thực tế để ước tính chính xác độ trễ của hàng đợi. */
    private fun learnSpeed(item: Item) {
        val seconds = (SystemClock.elapsedRealtime() - currentStartMs) / 1000f
        if (seconds < 0.3f || item.text.length < 8) return
        val sample = item.text.length / (seconds * currentRate)
        cps = (cps * 0.7f + sample * 0.3f).coerceIn(5f, 40f)
    }

    // ---------------------------------------------------------------- âm lượng & phim

    private fun duck(appCtx: Context) {
        val h = host ?: return
        if (!ducked) {
            val currentVolume = h.getVolume()
            if (currentVolume <= 0f) return // người dùng đang tắt tiếng
            savedVolume = currentVolume
            ducked = true
        }
        val percent = store(appCtx).getInt(PREF_ORIGINAL_VOLUME, DEFAULT_ORIGINAL_VOLUME).get()
            .coerceIn(0, 100)
        h.setVolume(savedVolume * percent / 100f)
    }

    private fun restoreVolume() {
        if (!ducked) return
        ducked = false
        host?.setVolume(savedVolume)
    }

    private fun pauseVideo() {
        val h = host ?: return
        if (pausedByTts || !h.isPlayRequested()) return
        pausedByTts = true
        h.setPlaying(false)
    }

    private fun resumeVideo() {
        if (!pausedByTts) return
        pausedByTts = false
        host?.setPlaying(true)
    }

    /** CS3IPlayer gọi khi video bắt đầu phát: người dùng tự bấm phát thì bỏ trạng thái "bị ta tạm dừng". */
    fun onPlaying() {
        mainHandler.post { pausedByTts = false }
    }

    // ---------------------------------------------------------------- dừng / giải phóng

    /**
     * Dừng đọc (tạm dừng video, tua...). Bỏ qua nếu video đang bị chính bộ đọc tạm dừng
     * ở chế độ chờ.
     */
    fun stop() {
        mainHandler.post {
            if (pausedByTts) return@post
            stopAll()
        }
    }

    private fun stopAll() {
        lastText = null
        queue.clear()
        current = null
        mainHandler.removeCallbacks(watchdog)
        try {
            tts?.stop()
        } catch (t: Throwable) {
            Log.e(TAG, "stop failed", t)
        }
        restoreVolume()
        resumeVideo()
    }

    /** Giải phóng engine, gọi khi player bị hủy. */
    fun release() {
        mainHandler.post {
            pausedByTts = false // player sắp bị hủy, không cần phát lại
            stopAll()
            try {
                tts?.shutdown()
            } catch (t: Throwable) {
                Log.e(TAG, "shutdown failed", t)
            }
            tts = null
            ready = false
            appliedKey = null
            host = null
        }
    }

    // ---------------------------------------------------------------- xử lý chữ

    private val tagRegex = Regex("<[^>]*>")
    private val assRegex = Regex("[{][^}]*[}]")
    private val noteRegex = Regex("[♪♫♬#]+")
    private val spaceRegex = Regex("\\s+")

    /** Bỏ thẻ HTML / ASS, ký hiệu nhạc, khoảng trắng thừa. */
    private fun clean(text: CharSequence?): String {
        if (text.isNullOrBlank()) return ""
        return try {
            text.toString()
                .replace(tagRegex, " ")
                .replace(assRegex, " ")
                .replace(noteRegex, " ")
                .replace(spaceRegex, " ")
                .trim()
        } catch (t: Throwable) {
            Log.e(TAG, "clean failed", t)
            text.toString().trim()
        }
    }

    // ---------------------------------------------------------------- ngôn ngữ & giọng đọc

    class VoiceInfo(val name: String, val languageTag: String, val needsNetwork: Boolean)

    /** Ngôn ngữ (tag -> tên hiển thị) và giọng đọc mà engine TTS trên máy hỗ trợ. */
    class EngineInfo(val languages: Map<String, String>, val voices: List<VoiceInfo>)

    private fun languageName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        val name = locale.getDisplayName(locale)
        return if (name.isNullOrBlank()) tag
        else name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /** Hỏi engine TTS trên máy: có những ngôn ngữ và giọng nào. Kết quả trả về trên main thread. */
    fun loadEngineInfo(context: Context, onResult: (EngineInfo) -> Unit) {
        var probe: TextToSpeech? = null
        probe = TextToSpeech(context.applicationContext) { status ->
            var languages: Map<String, String> = emptyMap()
            var voices: List<VoiceInfo> = emptyList()
            val engine = probe
            if (status == TextToSpeech.SUCCESS && engine != null) {
                try {
                    languages = (engine.availableLanguages ?: emptySet<Locale>())
                        .filter { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
                        .map { it.toLanguageTag() to languageName(it.toLanguageTag()) }
                        .distinctBy { it.first }
                        .sortedBy { it.second }
                        .toMap()
                } catch (t: Throwable) {
                    Log.e(TAG, "list languages failed", t)
                }
                try {
                    voices = (engine.voices ?: emptySet<Voice>())
                        .filter { !it.isNotInstalled() }
                        .sortedBy { it.name }
                        .map {
                            VoiceInfo(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired)
                        }
                } catch (t: Throwable) {
                    Log.e(TAG, "list voices failed", t)
                }
            }
            try {
                engine?.shutdown()
            } catch (t: Throwable) {
                Log.e(TAG, "shutdown failed", t)
            }
            mainHandler.post { onResult(EngineInfo(languages, voices)) }
        }
    }

    /** Danh sách ngôn ngữ cho menu; luôn chứa ngôn ngữ đang chọn dù engine chưa báo về. */
    fun languageOptions(info: EngineInfo?, selectedTag: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        info?.languages?.let { result.putAll(it) }
        if (!result.containsKey(selectedTag)) result[selectedTag] = languageName(selectedTag)
        return result
    }

    /** Danh sách giọng của ngôn ngữ đã chọn. Luôn có mục mặc định ở đầu. */
    fun voiceOptions(info: EngineInfo?, languageTag: String, defaultLabel: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        result[VOICE_DEFAULT] = defaultLabel
        val language = Locale.forLanguageTag(languageTag).language
        info?.voices
            ?.filter { Locale.forLanguageTag(it.languageTag).language == language }
            ?.forEachIndexed { index, voice ->
                val net = if (voice.needsNetwork) " (online)" else ""
                result[voice.name] = "Giọng ${index + 1} - ${voice.name}$net"
            }
        return result
    }

    private fun Voice.isNotInstalled(): Boolean =
        features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
}
