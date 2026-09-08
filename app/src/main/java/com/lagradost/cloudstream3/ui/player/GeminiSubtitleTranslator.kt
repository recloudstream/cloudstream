package com.lagradost.cloudstream3.ui.player

import android.content.Context
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object GeminiSubtitleTranslator {
    private const val BATCH_SIZE = 50
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient by lazy {
        app.baseClient.newBuilder()
            .callTimeout(60, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun translateCues(
        cues: List<SubtitleCue>,
        targetLanguage: String,
        key: String
    ): Result<List<SubtitleCue>> = withContext(Dispatchers.IO) {
        runCatching {
            if (cues.isEmpty()) return@runCatching emptyList()

            val translatedCues = mutableListOf<SubtitleCue>()
            val chunks = cues.chunked(BATCH_SIZE)
            var successfulChunks = 0

            for ((chunkIdx, chunk) in chunks.withIndex()) {
                val promptBuilder = StringBuilder()
                promptBuilder.append("Translate each line into $targetLanguage. Output ONLY the translated lines with their exact line numbers [N] so lines match up, with no introductory or concluding text:\n")
                chunk.forEachIndexed { index, cue ->
                    val text = cue.text.joinToString(" ").replace("\n", " ").trim()
                    promptBuilder.append("[${index + 1}] $text\n")
                }

                val lineMap = try {
                    val responseText = callGeminiWithFallback(promptBuilder.toString(), key)
                    val parsed = parseNumberedLines(responseText)
                    if (parsed.isNotEmpty()) successfulChunks++
                    parsed
                } catch (e: Exception) {
                    logError(e)
                    emptyMap()
                }

                chunk.forEachIndexed { index, cue ->
                    val translatedText = lineMap[index + 1] ?: cue.text.joinToString(" ")
                    translatedCues.add(
                        SubtitleCue(
                            startTimeMs = cue.startTimeMs,
                            durationMs = if (cue.durationMs > 0) cue.durationMs else 2500L,
                            text = listOf(translatedText)
                        )
                    )
                }
            }

            if (successfulChunks == 0 && cues.isNotEmpty()) {
                throw Exception("AI translation failed: could not connect to Gemini or invalid API key")
            }

            translatedCues
        }
    }

    private fun callGeminiWithFallback(prompt: String, key: String): String {
        val models = listOf("gemini-2.5-flash", "gemini-3.5-flash", "gemini-flash-latest")
        var lastException: Exception? = null

        for (model in models) {
            try {
                return executeGeminiRequest(model, prompt, key)
            } catch (e: Exception) {
                logError(e)
                lastException = e
                try { Thread.sleep(500) } catch (_: Throwable) {}
            }
        }
        throw lastException ?: Exception("Gemini request failed: all models returned error")
    }

    private fun executeGeminiRequest(model: String, prompt: String, key: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        val jsonPayload = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)

            val genConfig = JSONObject().apply {
                val thinkingConfig = JSONObject().apply {
                    put("thinkingBudget", 0)
                }
                put("thinkingConfig", thinkingConfig)
                put("temperature", 0.2)
            }
            put("generationConfig", genConfig)
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body.string()

        if (!response.isSuccessful) {
            throw Exception("API call to $model failed with code ${response.code}: $responseBody")
        }

        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw Exception("No candidates in response: $responseBody")
        if (candidates.length() == 0) throw Exception("Empty candidates list")

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
            ?: throw Exception("No content in candidate: $responseBody")
        val parts = content.optJSONArray("parts")
            ?: throw Exception("No parts in candidate content: $responseBody")
        if (parts.length() == 0) throw Exception("Empty parts list")

        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val text = part.optString("text", "")
            if (text.isNotBlank()) {
                sb.append(text).append("\n")
            }
        }
        val resultText = sb.toString().trim()
        if (resultText.isEmpty()) throw Exception("Empty text returned from $model")
        return resultText
    }

    private fun parseNumberedLines(response: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        val lineRegex = Regex("""^\[?(\d+)\]?[\s.:\)-]*(.*)$""")

        response.lines().forEach { rawLine ->
            val line = rawLine.replace("*", "").trim()
            val match = lineRegex.find(line)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull()
                val text = match.groupValues[2].trim()
                if (num != null && text.isNotEmpty()) {
                    result[num] = text
                }
            }
        }
        return result
    }

    fun cuesToVttFile(cues: List<SubtitleCue>, targetFile: File): File {
        targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        targetFile.bufferedWriter().use { writer ->
            writer.write("WEBVTT\n\n")
            cues.forEach { cue ->
                val start = formatVttTime(cue.startTimeMs)
                val duration = if (cue.durationMs > 0) cue.durationMs else 2500L
                val end = formatVttTime(cue.startTimeMs + duration)
                val text = cue.text.joinToString("\n").ifBlank { "..." }
                writer.write("$start --> $end\n$text\n\n")
            }
        }
        return targetFile
    }

    fun cuesToVttFile(context: Context, cues: List<SubtitleCue>, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        return cuesToVttFile(cues, file)
    }

    private fun formatVttTime(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0) / 1000
        val millis = ms.coerceAtLeast(0) % 1000
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }
}
