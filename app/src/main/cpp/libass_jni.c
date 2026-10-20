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

static inline int max_int(int a, int b) { return a > b ? a : b; }

static inline int min_int(int a, int b) { return a < b ? a : b; }

static inline int rect_is_empty(Rect rect) {
    return rect.right <= rect.left || rect.bottom <= rect.top;
}

static Rect rect_intersect(Rect a, Rect b) {
    return (Rect) {
        max_int(a.left, b.left), max_int(a.top, b.top),
        min_int(a.right, b.right), min_int(a.bottom, b.bottom)
    };
}

static Rect rect_union(Rect a, Rect b) {
    if (rect_is_empty(a)) return b;
    if (rect_is_empty(b)) return a;
    return (Rect) {
        min_int(a.left, b.left), min_int(a.top, b.top),
        max_int(a.right, b.right), max_int(a.bottom, b.bottom)
    };
}

static void clear_rect(uint8_t *pixels, uint32_t stride, Rect rect) {
    if (rect_is_empty(rect)) return;
    size_t row_bytes = (size_t) (rect.right - rect.left) * 4;
    for (int y = rect.top; y < rect.bottom; y++) {
        memset(pixels + (size_t) y * stride + (size_t) rect.left * 4, 0, row_bytes);
    }
}

static void blend_row(const uint8_t *src, uint8_t *dst, int count,
                      uint32_t color, uint32_t opacity) {
    uint32_t r = color >> 24;
    uint32_t g = (color >> 16) & 0xFF;
    uint32_t b = (color >> 8) & 0xFF;
    for (int x = 0; x < count; x++, dst += 4) {
        uint32_t mask = src[x];
        if (mask == 0) continue;
        uint32_t alpha = div255(mask * opacity);
        uint32_t inverse = 255 - alpha;
        dst[0] = (uint8_t) div255(r * alpha + dst[0] * inverse);
        dst[1] = (uint8_t) div255(g * alpha + dst[1] * inverse);
        dst[2] = (uint8_t) div255(b * alpha + dst[2] * inverse);
        dst[3] = (uint8_t) (alpha + div255(dst[3] * inverse));
    }
}

/*
 * Blends one libass image (an alpha mask with a color) onto a premultiplied RGBA bitmap
 * and returns the area that was drawn
 */
static Rect blend_image(const ASS_Image *img, uint8_t *pixels, uint32_t stride, Rect bounds) {
    Rect empty = {0, 0, 0, 0};
    uint32_t opacity = 255 - (img->color & 0xFF);
    if (opacity == 0) return empty;

    Rect image = {img->dst_x, img->dst_y, img->dst_x + img->w, img->dst_y + img->h};
    Rect area = rect_intersect(image, bounds);
    if (rect_is_empty(area)) return empty;

    for (int y = area.top; y < area.bottom; y++) {
        const uint8_t *src = img->bitmap + (size_t) (y - img->dst_y) * img->stride
                             + (area.left - img->dst_x);
        uint8_t *dst = pixels + (size_t) y * stride + (size_t) area.left * 4;
        blend_row(src, dst, area.right - area.left, img->color, opacity);
    }
    return area;
}

/* Locks an RGBA_8888 bitmap, returns NULL on failure */
static uint8_t *lock_bitmap(JNIEnv *env, jobject bitmap, AndroidBitmapInfo *info) {
    if (AndroidBitmap_getInfo(env, bitmap, info) != ANDROID_BITMAP_RESULT_SUCCESS) return NULL;
    if (info->format != ANDROID_BITMAP_FORMAT_RGBA_8888) return NULL;
    void *pixels = NULL;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return NULL;
    }
    return pixels;
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
    uint8_t *pixels = lock_bitmap(env, bitmap, &info);
    if (!pixels) return -1;
    Rect bounds = {0, 0, (int) info.width, (int) info.height};

    jint rect[4];
    (*env)->GetIntArrayRegion(env, dirty, 0, 4, rect);
    Rect previous = {rect[0], rect[1], rect[2], rect[3]};
    clear_rect(pixels, info.stride, rect_intersect(previous, bounds));

    Rect drawn = {0, 0, 0, 0};
    for (const ASS_Image *img = images; img; img = img->next) {
        drawn = rect_union(drawn, blend_image(img, pixels, info.stride, bounds));
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    rect[0] = drawn.left;
    rect[1] = drawn.top;
    rect[2] = drawn.right;
    rect[3] = drawn.bottom;
    (*env)->SetIntArrayRegion(env, dirty, 0, 4, rect);
    return 1;
}
