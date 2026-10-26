@file:Suppress("unused")

package android.util

/**
 * Minimal stand-in for android.util.Log so extensions compiled against Android can log on desktop.
 */
object Log {
    private fun print(level: String, tag: String?, msg: String?, tr: Throwable? = null): Int {
        println("$level/$tag: $msg")
        tr?.printStackTrace()
        return 0
    }

    @JvmStatic fun v(tag: String?, msg: String?): Int = print("V", tag, msg)
    @JvmStatic fun v(tag: String?, msg: String?, tr: Throwable?): Int = print("V", tag, msg, tr)
    @JvmStatic fun d(tag: String?, msg: String?): Int = print("D", tag, msg)
    @JvmStatic fun d(tag: String?, msg: String?, tr: Throwable?): Int = print("D", tag, msg, tr)
    @JvmStatic fun i(tag: String?, msg: String?): Int = print("I", tag, msg)
    @JvmStatic fun i(tag: String?, msg: String?, tr: Throwable?): Int = print("I", tag, msg, tr)
    @JvmStatic fun w(tag: String?, msg: String?): Int = print("W", tag, msg)
    @JvmStatic fun w(tag: String?, msg: String?, tr: Throwable?): Int = print("W", tag, msg, tr)
    @JvmStatic fun w(tag: String?, tr: Throwable?): Int = print("W", tag, null, tr)
    @JvmStatic fun e(tag: String?, msg: String?): Int = print("E", tag, msg)
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int = print("E", tag, msg, tr)
    @JvmStatic fun wtf(tag: String?, msg: String?): Int = print("WTF", tag, msg)

    @JvmStatic
    fun getStackTraceString(tr: Throwable?): String = tr?.stackTraceToString() ?: ""
}
