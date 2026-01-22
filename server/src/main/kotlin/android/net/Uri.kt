package android.net

class Uri private constructor(private val value: String) {
    override fun toString(): String = value

    companion object {
        @JvmStatic
        fun parse(uri: String): Uri = Uri(uri)
    }
}
