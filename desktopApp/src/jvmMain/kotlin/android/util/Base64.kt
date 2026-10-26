package android.util

/** android.util.Base64 on top of java.util.Base64, same flags and signatures */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8
    const val NO_CLOSE = 16

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray = decode(str.toByteArray(Charsets.US_ASCII), flags)

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray {
        // Android ignores whitespace and accepts both alphabets and missing padding
        val clean = String(input, Charsets.US_ASCII).filterNot { it.isWhitespace() }
            .replace('-', '+').replace('_', '/').trimEnd('=')
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        return try {
            java.util.Base64.getDecoder().decode(padded)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("bad base-64", e)
        }
    }

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).toByteArray(Charsets.US_ASCII)

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        var encoder = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) encoder = encoder.withoutPadding()
        val text = encoder.encodeToString(input)
        if (flags and NO_WRAP != 0) return text
        // Android wraps at 76 characters and ends with a newline unless NO_WRAP is set
        val newline = if (flags and CRLF != 0) "\r\n" else "\n"
        return text.chunked(76).joinToString(newline, postfix = newline)
    }
}
