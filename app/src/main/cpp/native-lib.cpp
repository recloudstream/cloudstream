#include <jni.h>
#include <csignal>
#include <android/log.h>

#define TAG "CloudStream Crash Handler"
volatile sig_atomic_t gSignalStatus = 0;
void handleNativeCrash(int signal) {
    gSignalStatus = signal;
}

extern "C" JNIEXPORT void JNICALL
Java_com_lagradost_cloudstream3_NativeCrashHandler_initNativeCrashHandler(JNIEnv *env, jobject) {
    #define REGISTER_SIGNAL(X) signal(X, handleNativeCrash);
    REGISTER_SIGNAL(SIGSEGV)
    #undef REGISTER_SIGNAL
}

//extern "C" JNIEXPORT void JNICALL
//Java_com_lagradost_cloudstream3_NativeCrashHandler_triggerNativeCrash(JNIEnv *env, jobject thiz) {
//    int *p = nullptr;
//    *p = 0;
//}

extern "C" JNIEXPORT int JNICALL
Java_com_lagradost_cloudstream3_NativeCrashHandler_getSignalStatus(JNIEnv *env, jobject) {
    //__android_log_print(ANDROID_LOG_INFO, TAG, "Got signal status %d", gSignalStatus);
    return gSignalStatus;
}