package android.util

object Log {
    fun d(tag: String, msg: String): Int = println("D/$tag: $msg").let { 0 }
    fun i(tag: String, msg: String): Int = println("I/$tag: $msg").let { 0 }
    fun w(tag: String, msg: String): Int = println("W/$tag: $msg").let { 0 }
    fun e(tag: String, msg: String): Int = println("E/$tag: $msg").let { 0 }
}
