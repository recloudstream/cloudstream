package com.lagradost.cloudstream3.utils.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Downloads a DASH stream (separate video and audio URLs) and muxes them into one mp4.
 * Used when a plugin supplies a video-only URL plus an audio-only URL.
 */
object DashDownloader {

    /** Ranged download of a single URL to a file. Reports progress 0..100 through [onProgress]. */
    fun downloadRanged(
        client: okhttp3.OkHttpClient,
        url: String,
        dest: File,
        userAgent: String,
        onProgress: (Int) -> Unit = {}
    ): Boolean {
        val chunk = 8L * 1024 * 1024
        if (dest.exists()) dest.delete()
        var total = -1L
        client.newCall(
            okhttp3.Request.Builder().url(url).header("User-Agent", userAgent).header("Range", "bytes=0-0").build()
        ).execute().use { r ->
            if (!r.isSuccessful && r.code != 206) return false
            total = r.header("Content-Range")?.substringAfter("/")?.toLongOrNull()
                ?: r.header("Content-Length")?.toLongOrNull()
                ?: -1L
        }
        FileOutputStream(dest).use { fos ->
            if (total <= 0L) {
                client.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", userAgent).build())
                    .execute().use { r ->
                        if (!r.isSuccessful) return false
                        val body = r.body ?: return false
                        body.byteStream().use { ins ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val c = ins.read(buf)
                                if (c < 0) break
                                fos.write(buf, 0, c)
                            }
                        }
                    }
            } else {
                var pos = 0L
                while (pos < total) {
                    val end = minOf(pos + chunk - 1, total - 1)
                    val req = okhttp3.Request.Builder().url(url)
                        .header("User-Agent", userAgent)
                        .header("Range", "bytes=$pos-$end")
                        .build()
                    client.newCall(req).execute().use { r ->
                        if (!r.isSuccessful && r.code != 206) return false
                        val body = r.body ?: return false
                        var got = 0L
                        body.byteStream().use { ins ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val c = ins.read(buf)
                                if (c < 0) break
                                fos.write(buf, 0, c)
                                got += c
                            }
                        }
                        if (got <= 0L) return false
                        pos += got
                        onProgress(((pos * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        }
        return dest.length() > 0L
    }

    private fun trackOf(e: MediaExtractor, prefix: String): Int {
        for (i in 0 until e.trackCount) {
            val m = e.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (m.startsWith(prefix)) return i
        }
        return -1
    }

    private fun copyTrack(e: MediaExtractor, m: MediaMuxer, track: Int) {
        val buf = ByteBuffer.allocate(4 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buf.clear()
            val size = e.readSampleData(buf, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = e.sampleTime
            info.flags = if ((e.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0)
                MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            m.writeSampleData(track, buf, info)
            e.advance()
        }
    }

    /** Muxes a video file and an audio file into one mp4. */
    fun mux(video: File, audio: File, out: File): Boolean {
        val ve = MediaExtractor()
        val ae = MediaExtractor()
        var mx: MediaMuxer? = null
        try {
            ve.setDataSource(video.absolutePath)
            ae.setDataSource(audio.absolutePath)
            val vi = trackOf(ve, "video/")
            val ai = trackOf(ae, "audio/")
            if (vi < 0 || ai < 0) return false
            ve.selectTrack(vi)
            ae.selectTrack(ai)
            if (out.exists()) out.delete()
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mx = m
            val vt = m.addTrack(ve.getTrackFormat(vi))
            val at = m.addTrack(ae.getTrackFormat(ai))
            m.start()
            copyTrack(ve, m, vt)
            copyTrack(ae, m, at)
            m.stop()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            try { mx?.release() } catch (e: Exception) { }
            try { ve.release() } catch (e: Exception) { }
            try { ae.release() } catch (e: Exception) { }
        }
    }

    /** Copies a finished file into Movies/<folder> via MediaStore (Android 10+). Returns the saved location. */
    fun publish(ctx: Context, file: File, name: String, folder: String): String {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues()
                cv.put(MediaStore.Video.Media.DISPLAY_NAME, name)
                cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$folder")
                cv.put(MediaStore.Video.Media.IS_PENDING, 1)
                val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                if (uri != null) {
                    ctx.contentResolver.openOutputStream(uri)?.use { o ->
                        file.inputStream().use { it.copyTo(o) }
                    }
                    cv.clear()
                    cv.put(MediaStore.Video.Media.IS_PENDING, 0)
                    ctx.contentResolver.update(uri, cv, null, null)
                    return "Movies/$folder/$name"
                }
            }
        } catch (e: Exception) {
        }
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, folder)
        d.mkdirs()
        val t = File(d, name)
        file.copyTo(t, true)
        return t.absolutePath
    }
}