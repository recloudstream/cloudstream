package com.mihon.common.preference

interface PreferenceStore {

    fun getString(key: String, defaultValue: String = ""): PreferenceData<String>

    fun getLong(key: String, defaultValue: Long = 0): PreferenceData<Long>

    fun getInt(key: String, defaultValue: Int = 0): PreferenceData<Int>

    fun getFloat(key: String, defaultValue: Float = 0f): PreferenceData<Float>

    fun getBoolean(key: String, defaultValue: Boolean = false): PreferenceData<Boolean>

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): PreferenceData<Set<String>>

    fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): PreferenceData<T>

    fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): PreferenceData<T>

    fun <T> getObjectSetFromStringSet(
        key: String,
        defaultValue: Set<T>,
        serializer: (T) -> String,
        deserializer: (String) -> T?,
    ): PreferenceData<Set<T>>

    fun getAll(): Map<String, *>
}

fun PreferenceStore.getLongArray(
    key: String,
    defaultValue: List<Long>,
): PreferenceData<List<Long>> {
    return getObjectFromString(
        key = key,
        defaultValue = defaultValue,
        serializer = { it.joinToString(",") },
        deserializer = { it.split(",").mapNotNull { l -> l.toLongOrNull() } },
    )
}

inline fun <reified T : Enum<T>> PreferenceStore.getEnum(
    key: String,
    defaultValue: T,
): PreferenceData<T> {
    return getObjectFromString(
        key = key,
        defaultValue = defaultValue,
        serializer = { it.name },
        deserializer = {
            try {
                enumValueOf(it)
            } catch (e: IllegalArgumentException) {
                defaultValue
            }
        },
    )
}

inline fun <reified T : Enum<T>> PreferenceStore.getEnumSet(
    key: String,
    defaultValue: Set<T>,
): PreferenceData<Set<T>> {
    return getObjectSetFromStringSet(
        key = key,
        defaultValue = defaultValue,
        serializer = { it.name },
        deserializer = {
            try {
                enumValueOf<T>(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        },
    )
}
