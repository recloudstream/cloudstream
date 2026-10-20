/*
 * JNI bindings for LibassNative.kt
 *
 * Handles are pointers passed as jlong. There is no locking here, LibassSubtitleSupport makes sure
 * calls on the same ASS_Library never run at the same time.
 */

#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "ass.h"

#define TAG "libass"
#define JNI_FN(name) Java_com_lagradost_cloudstream3_ui_player_LibassNative_##name

static void log_callback(int level, const char *fmt, va_list args, void *data) {
    (void) data;
    // 0 is fatal and 7 is debug, only log errors and warnings
    if (level > 2) return;
    __android_log_vprint(level <= 1 ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, TAG, fmt, args);
}

static inline uint32_t div255(uint32_t x) {
    return (x + 1 + (x >> 8)) >> 8;
}

/* Part of the bitmap that was drawn on, so only that gets cleared */
typedef struct {
    int left, top, right, bottom;
} Rect;

static void clear_rect(uint8_t *pixels, uint32_t stride, Rect rect) {
    if (rect.right <= rect.left || rect.bottom <= rect.top) return;
    size_t row_bytes = (size_t) (rect.right - rect.left) * 4;
    for (int y = rect.top; y < rect.bottom; y++) {
        memset(pixels + (size_t) y * stride + (size_t) rect.left * 4, 0, row_bytes);
    }
}

/* Blends one libass image (an alpha mask with a color) onto a premultiplied RGBA bitmap */
static void blend_image(const ASS_Image *img, uint8_t *pixels, uint32_t stride,
                        int width, int height, Rect *dirty) {
    uint32_t color = img->color;
    uint32_t opacity = 255 - (color & 0xFF);
    if (opacity == 0 || img->w <= 0 || img->h <= 0) return;
    uint32_t r = color >> 24;
    uint32_t g = (color >> 16) & 0xFF;
    uint32_t b = (color >> 8) & 0xFF;

    int x0 = img->dst_x < 0 ? 0 : img->dst_x;
    int y0 = img->dst_y < 0 ? 0 : img->dst_y;
    int x1 = img->dst_x + img->w > width ? width : img->dst_x + img->w;
    int y1 = img->dst_y + img->h > height ? height : img->dst_y + img->h;
    if (x1 <= x0 || y1 <= y0) return;

    for (int y = y0; y < y1; y++) {
        const uint8_t *src = img->bitmap + (size_t) (y - img->dst_y) * img->stride
                             + (x0 - img->dst_x);
        uint8_t *dst = pixels + (size_t) y * stride + (size_t) x0 * 4;
        for (int x = x0; x < x1; x++, src++, dst += 4) {
            uint32_t mask = *src;
            if (mask == 0) continue;
            uint32_t alpha = div255(mask * opacity);
            uint32_t inverse = 255 - alpha;
            dst[0] = (uint8_t) div255(r * alpha + dst[0] * inverse);
            dst[1] = (uint8_t) div255(g * alpha + dst[1] * inverse);
            dst[2] = (uint8_t) div255(b * alpha + dst[2] * inverse);
            dst[3] = (uint8_t) (alpha + div255(dst[3] * inverse));
        }
    }

    if (dirty->right <= dirty->left || dirty->bottom <= dirty->top) {
        *dirty = (Rect) {x0, y0, x1, y1};
    } else {
        if (x0 < dirty->left) dirty->left = x0;
        if (y0 < dirty->top) dirty->top = y0;
        if (x1 > dirty->right) dirty->right = x1;
        if (y1 > dirty->bottom) dirty->bottom = y1;
    }
}

JNIEXPORT jlong JNICALL
JNI_FN(libraryInit)(JNIEnv *env, jobject thiz) {
    ASS_Library *library = ass_library_init();
    if (!library) return 0;
    ass_set_message_cb(library, log_callback, NULL);
    // Fonts in the [Fonts] section
    ass_set_extract_fonts(library, 1);
    return (jlong) (intptr_t) library;
}

JNIEXPORT void JNICALL
JNI_FN(libraryDone)(JNIEnv *env, jobject thiz, jlong library) {
    if (library) ass_library_done((ASS_Library *) (intptr_t) library);
}

JNIEXPORT void JNICALL
JNI_FN(addFont)(JNIEnv *env, jobject thiz, jlong library, jstring name, jbyteArray data) {
    if (!library || !name || !data) return;
    const char *name_chars = (*env)->GetStringUTFChars(env, name, NULL);
    if (!name_chars) return;
    jsize size = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (bytes) {
        ass_add_font((ASS_Library *) (intptr_t) library, name_chars, (const char *) bytes, size);
        (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    }
    (*env)->ReleaseStringUTFChars(env, name, name_chars);
}

JNIEXPORT jlong JNICALL
JNI_FN(rendererInit)(JNIEnv *env, jobject thiz, jlong library, jstring default_font) {
    if (!library) return 0;
    ASS_Renderer *renderer = ass_renderer_init((ASS_Library *) (intptr_t) library);
    if (!renderer) return 0;
    const char *font = default_font ? (*env)->GetStringUTFChars(env, default_font, NULL) : NULL;
    // No fontconfig on Android, fonts come from the subtitle or the default font
    ass_set_fonts(renderer, font, "sans-serif", ASS_FONTPROVIDER_NONE, NULL, 0);
    if (font) (*env)->ReleaseStringUTFChars(env, default_font, font);
    return (jlong) (intptr_t) renderer;
}

JNIEXPORT void JNICALL
JNI_FN(rendererDone)(JNIEnv *env, jobject thiz, jlong renderer) {
    if (renderer) ass_renderer_done((ASS_Renderer *) (intptr_t) renderer);
}

JNIEXPORT void JNICALL
JNI_FN(setSize)(JNIEnv *env, jobject thiz, jlong renderer,
                jint frame_width, jint frame_height, jint storage_width, jint storage_height) {
    if (!renderer) return;
    ASS_Renderer *r = (ASS_Renderer *) (intptr_t) renderer;
    ass_set_frame_size(r, frame_width, frame_height);
    if (storage_width > 0 && storage_height > 0) {
        ass_set_storage_size(r, storage_width, storage_height);
    }
}

JNIEXPORT jlong JNICALL
JNI_FN(newTrack)(JNIEnv *env, jobject thiz, jlong library) {
    if (!library) return 0;
    return (jlong) (intptr_t) ass_new_track((ASS_Library *) (intptr_t) library);
}

JNIEXPORT jlong JNICALL
JNI_FN(readMemory)(JNIEnv *env, jobject thiz, jlong library, jbyteArray data) {
    if (!library || !data) return 0;
    jsize size = (*env)->GetArrayLength(env, data);
    // ass_read_memory may modify the buffer
    char *buffer = malloc((size_t) size + 1);
    if (!buffer) return 0;
    (*env)->GetByteArrayRegion(env, data, 0, size, (jbyte *) buffer);
    buffer[size] = '\0';
    ASS_Track *track = ass_read_memory((ASS_Library *) (intptr_t) library, buffer, size, NULL);
    free(buffer);
    return (jlong) (intptr_t) track;
}

JNIEXPORT void JNICALL
JNI_FN(processCodecPrivate)(JNIEnv *env, jobject thiz, jlong track, jbyteArray data) {
    if (!track || !data) return;
    jsize size = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) return;
    ass_process_codec_private((ASS_Track *) (intptr_t) track, (char *) bytes, size);
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
}

JNIEXPORT void JNICALL
JNI_FN(processChunk)(JNIEnv *env, jobject thiz, jlong track, jbyteArray data,
                     jint offset, jint length, jlong start_ms, jlong duration_ms) {
    if (!track || !data || offset < 0 || length <= 0) return;
    if (offset + length > (*env)->GetArrayLength(env, data)) return;
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) return;
    ass_process_chunk((ASS_Track *) (intptr_t) track, (char *) bytes + offset, length,
                      start_ms, duration_ms);
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
}

JNIEXPORT void JNICALL
JNI_FN(freeTrack)(JNIEnv *env, jobject thiz, jlong track) {
    if (track) ass_free_track((ASS_Track *) (intptr_t) track);
}

/* See LibassNative.renderFrame, dirty is (left, top, right, bottom) */
JNIEXPORT jint JNICALL
JNI_FN(renderFrame)(JNIEnv *env, jobject thiz, jlong renderer, jlong track, jlong time_ms,
                    jobject bitmap, jintArray dirty, jboolean force) {
    if (!renderer || !track || !bitmap || !dirty) return -1;
    if ((*env)->GetArrayLength(env, dirty) < 4) return -1;

    int change = 0;
    ASS_Image *images = ass_render_frame((ASS_Renderer *) (intptr_t) renderer,
                                         (ASS_Track *) (intptr_t) track, time_ms, &change);
    if (change == 0 && !force) return 0;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return -1;
    }
    void *pixels = NULL;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        !pixels) {
        return -1;
    }

    jint rect[4];
    (*env)->GetIntArrayRegion(env, dirty, 0, 4, rect);
    Rect previous = {rect[0], rect[1], rect[2], rect[3]};
    int width = (int) info.width;
    int height = (int) info.height;
    if (previous.left < 0) previous.left = 0;
    if (previous.top < 0) previous.top = 0;
    if (previous.right > width) previous.right = width;
    if (previous.bottom > height) previous.bottom = height;
    clear_rect(pixels, info.stride, previous);

    Rect drawn = {0, 0, 0, 0};
    for (const ASS_Image *img = images; img; img = img->next) {
        blend_image(img, pixels, info.stride, width, height, &drawn);
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    rect[0] = drawn.left;
    rect[1] = drawn.top;
    rect[2] = drawn.right;
    rect[3] = drawn.bottom;
    (*env)->SetIntArrayRegion(env, dirty, 0, 4, rect);
    return 1;
}
