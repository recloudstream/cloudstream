package com.lagradost.cloudstream3.utils.resources

import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

typealias Generator<T> = () -> T?
typealias GeneratorWithTheme<T> = (Resources.Theme?) -> T?

open class MapResourcePatch(private val original: Resources): ResourcePatch(original) {
    val mapping: HashMap<Int, Any> = hashMapOf()
    val idMapping: HashMap<Int, Int> = hashMapOf()

    override fun mapId(id: Int): Int =
        idMapping[id] ?: super.mapId(id)

    override fun getLayout(id: Int): XmlResourceParser =
        mapping.getMappingKA(id, null) ?: super.getLayout(id)

    override fun getDrawable(id: Int, theme: Theme?): Drawable =
        mapping.getMappingKA<Int, Drawable>(id, theme) ?: super.getDrawable(id, theme)

    @RequiresApi(Build.VERSION_CODES.M)
    override fun getColorStateList(id: Int, theme: Theme?): ColorStateList =
        mapping.getMappingKA(id, theme) ?: super.getColorStateList(id, theme)

    override fun getXml(id: Int): XmlResourceParser =
        mapping.getMappingKA(id, null) ?: super.getXml(id)

    @RequiresApi(Build.VERSION_CODES.M)
    override fun getColor(id: Int, theme: Theme?): Int =
        mapping.getMappingKA(id, theme) ?: super.getColor(id, theme)
}

private inline fun <K, reified V> HashMap<K, Any>.getMappingKA(id: K?, theme: Resources.Theme?): V? {
    return when (val res = this[id]) {
        equals(null) -> null
        is V -> res
        is Function0<*> -> (res as? Generator<V>)?.invoke()
        is Function1<*, *> -> (res as? GeneratorWithTheme<V>)?.invoke(theme)
        else -> null
    }
}