package com.lagradost.cloudstream3.ui.player

import android.graphics.Bitmap

/**
 * JNI bindings for libass, see src/main/cpp/libass_jni.c
 *
 * Handles are native pointers, 0 is null. Calls using the same library (or anything created
 * from it) must not run at the same time.
 */
internal object LibassNative {
    init {
        System.loadLibrary("cs3ass")
    }

    external fun libraryInit(): Long
    external fun libraryDone(library: Long)

    /** libass makes its own copy of [data] */
    external fun addFont(library: Long, name: String, data: ByteArray)

    external fun rendererInit(library: Long, defaultFont: String?): Long
    external fun rendererDone(renderer: Long)

    /** Frame size is the size of the bitmap, storage size is the video size */
    external fun setSize(
        renderer: Long,
        frameWidth: Int,
        frameHeight: Int,
        storageWidth: Int,
        storageHeight: Int
    )

    external fun newTrack(library: Long): Long

    /** A whole .ass/.ssa file in UTF-8 */
    external fun readMemory(library: Long, data: ByteArray): Long

    /** ASS header of an embedded track (Matroska CodecPrivate) */
    external fun processCodecPrivate(track: Long, data: ByteArray)

    /** One embedded event in the Matroska format: "ReadOrder,Layer,Style,...,Text" */
    external fun processChunk(
        track: Long,
        data: ByteArray,
        offset: Int,
        length: Int,
        startMs: Long,
        durationMs: Long
    )

    external fun freeTrack(track: Long)

    /**
     * Renders the subtitles at [timeMs] into [bitmap] (ARGB_8888, same size as the frame).
     * [dirty] is the area that was drawn the last time this bitmap was used, it gets cleared
     * and replaced with the new area.
     * @return 1 if redrawn, 0 if nothing changed and the bitmap was not touched, -1 on error
     */
    external fun renderFrame(
        renderer: Long,
        track: Long,
        timeMs: Long,
        bitmap: Bitmap,
        dirty: IntArray,
        force: Boolean
    ): Int
}
