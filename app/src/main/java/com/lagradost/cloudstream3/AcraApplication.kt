package com.lagradost.cloudstream3

/**
 * Deprecated alias for CloudStreamApp for backwards compatibility with plugins.
 * Use CloudStreamApp instead.
 */
@Deprecated(
    message = "AcraApplication is deprecated, use CloudStreamApp instead",
    replaceWith = ReplaceWith("com.lagradost.cloudstream3.CloudStreamApp"),
    level = DeprecationLevel.ERROR
)
class AcraApplication {
	companion object {

		@Deprecated(
		    message = "AcraApplication is deprecated, use CloudStreamApp instead",
		    replaceWith = ReplaceWith("com.lagradost.cloudstream3.CloudStreamApp.context"),
		    level = DeprecationLevel.ERROR
		)
		val context get() = CloudStreamApp.context

		@Deprecated(
		    message = "AcraApplication is deprecated, use CloudStreamApp instead",
		    replaceWith = ReplaceWith("com.lagradost.cloudstream3.CloudStreamApp.removeKeys(folder)"),
		    level = DeprecationLevel.ERROR
		)
		fun removeKeys(folder: String): Int? =
		    CloudStreamApp.removeKeys(folder)

		@Deprecated(
		    message = "AcraApplication is deprecated, use CloudStreamApp instead",
		    replaceWith = ReplaceWith("com.lagradost.cloudstream3.CloudStreamApp.setKey(path, value)"),
		    level = DeprecationLevel.ERROR
		)
		fun <T> setKey(path: String, value: T) =
			CloudStreamApp.setKey(path, value)

		@Deprecated(
		    message = "AcraApplication is deprecated, use CloudStreamApp instead",
		    replaceWith = ReplaceWith("com.lagradost.cloudstream3.CloudStreamApp.setKey(folder, path, value)"),
		    level = DeprecationLevel.ERROR
		)
		fun <T> setKey(folder: String, path: String, value: T) =
			CloudStreamApp.setKey(folder, path, value)

		@Deprecated(
		    message = "AcraApplication is deprecated, use CloudStreamApp instead",
		    replaceWith = ReplaceWith("com.lagradost.cloudstream3.CloudStreamApp.getKey(path, defVal)"),
		    level = DeprecationLevel.ERROR
		)
		inline fun <reified T : Any> getKey(path: String, defVal: T?): T? =
			CloudStreamApp.getKey(path, defVal)

		@Deprecated(
		    message = "AcraApplication is deprecated, use CloudStreamApp instead",
		    replaceWith = ReplaceWith("com.lagradost.cloudstream3.CloudStreamApp.getKey(path)"),
		    level = DeprecationLevel.ERROR
		)
		inline fun <reified T : Any> getKey(path: String): T? =
			CloudStreamApp.getKey(path)

		@Deprecated(
		    message = "AcraApplication is deprecated, use CloudStreamApp instead",
		    replaceWith = ReplaceWith("com.lagradost.cloudstream3.CloudStreamApp.getKey(folder, path)"),
		    level = DeprecationLevel.ERROR
		)
		inline fun <reified T : Any> getKey(folder: String, path: String): T? =
		    CloudStreamApp.getKey(folder, path)

		@Deprecated(
		    message = "AcraApplication is deprecated, use CloudStreamApp instead",
		    replaceWith = ReplaceWith("com.lagradost.cloudstream3.CloudStreamApp.getKey(folder, path, defVal)"),
		    level = DeprecationLevel.ERROR
		)
		inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? =
			CloudStreamApp.getKey(folder, path, defVal)
	}
}
