package com.lagradost.cloudstream3.utils.resources

import android.annotation.SuppressLint
import android.content.res.*
import android.content.res.loader.ResourcesLoader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.annotation.RequiresApi
import java.io.InputStream

open class ResourcePatch(private val original: Resources) :
    Resources(original.assets, original.displayMetrics, original.configuration) {

    open fun mapId(id: Int): Int = id

    @RequiresApi(Build.VERSION_CODES.R)
    override fun addLoaders(vararg loaders: ResourcesLoader?) = original.addLoaders(*loaders)
    override fun getAnimation(id: Int): XmlResourceParser = original.getAnimation(mapId(id))
    override fun getBoolean(id: Int): Boolean = original.getBoolean(mapId(id))
    @RequiresApi(Build.VERSION_CODES.M)
    override fun getColor(id: Int, theme: Theme?): Int = original.getColor(mapId(id), theme)
    override fun getConfiguration(): Configuration = original.configuration
    override fun getDisplayMetrics(): DisplayMetrics = original.displayMetrics
    @RequiresApi(Build.VERSION_CODES.M)
    override fun getColorStateList(id: Int, theme: Theme?): ColorStateList = original.getColorStateList(mapId(id), theme)
    override fun getLayout(id: Int): XmlResourceParser = original.getLayout(mapId(id))
    override fun getDimension(id: Int): Float = original.getDimension(mapId(id))
    override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int = original.getIdentifier(name, defType, defPackage)
    override fun getDimensionPixelOffset(id: Int): Int = original.getDimensionPixelOffset(mapId(id))
    override fun getDimensionPixelSize(id: Int): Int = original.getDimensionPixelSize(mapId(id))
    @SuppressLint("UseCompatLoadingForDrawables")
    override fun getDrawable(id: Int, theme: Theme?): Drawable = original.getDrawable(mapId(id), theme)
    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?): Drawable? = original.getDrawableForDensity(mapId(id), density, theme)
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getFloat(id: Int): Float = original.getFloat(mapId(id))
    @RequiresApi(Build.VERSION_CODES.O)
    override fun getFont(id: Int): Typeface = original.getFont(mapId(id))
    override fun getIntArray(id: Int): IntArray = original.getIntArray(mapId(id))
    override fun getFraction(id: Int, base: Int, pbase: Int): Float = original.getFraction(mapId(id), base, pbase)
    override fun getInteger(id: Int): Int = original.getInteger(mapId(id))
    override fun getQuantityString(id: Int, quantity: Int): String = original.getQuantityString(mapId(id), quantity)
    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String = original.getQuantityString(mapId(id), quantity, *formatArgs)
    override fun getQuantityText(id: Int, quantity: Int): CharSequence = original.getQuantityText(mapId(id), quantity)
    override fun getResourceEntryName(id: Int): String = original.getResourceEntryName(mapId(id))
    override fun getResourceName(id: Int): String = original.getResourceName(mapId(id))
    override fun getResourcePackageName(id: Int): String = original.getResourcePackageName(mapId(id))
    override fun getResourceTypeName(id: Int): String = original.getResourceTypeName(mapId(id))
    override fun getString(id: Int): String = original.getString(mapId(id))
    override fun getString(id: Int, vararg formatArgs: Any?): String = original.getString(mapId(id), *formatArgs)
    override fun getStringArray(id: Int): Array<String> = original.getStringArray(mapId(id))
    override fun getText(id: Int): CharSequence = original.getText(mapId(id))
    override fun getText(id: Int, def: CharSequence?): CharSequence = original.getText(mapId(id), def)
    override fun getTextArray(id: Int): Array<CharSequence> = original.getTextArray(mapId(id))
    override fun getValue(id: Int, outValue: TypedValue?, resolveRefs: Boolean) = original.getValue(mapId(id), outValue, resolveRefs)
    override fun getValue(name: String?, outValue: TypedValue?, resolveRefs: Boolean) = original.getValue(name, outValue, resolveRefs)
    override fun getValueForDensity(id: Int, density: Int, outValue: TypedValue?, resolveRefs: Boolean) = original.getValueForDensity(mapId(id), density, outValue, resolveRefs)
    override fun getXml(id: Int): XmlResourceParser = original.getXml(mapId(id))
    override fun obtainTypedArray(id: Int): TypedArray = original.obtainTypedArray(mapId(id))
    override fun openRawResource(id: Int): InputStream = original.openRawResource(mapId(id))
    override fun openRawResource(id: Int, value: TypedValue?): InputStream = original.openRawResource(mapId(id), value)
    override fun openRawResourceFd(id: Int): AssetFileDescriptor = original.openRawResourceFd(mapId(id))
    override fun obtainAttributes(set: AttributeSet?, attrs: IntArray?): TypedArray = original.obtainAttributes(set, attrs)
    override fun parseBundleExtra(tagName: String?, attrs: AttributeSet?, outBundle: Bundle?) = original.parseBundleExtra(tagName, attrs, outBundle)
    override fun parseBundleExtras(parser: XmlResourceParser?, outBundle: Bundle?) = original.parseBundleExtras(parser, outBundle)
    @RequiresApi(Build.VERSION_CODES.R)
    override fun removeLoaders(vararg loaders: ResourcesLoader?) = original.removeLoaders(*loaders)
    override fun equals(other: Any?): Boolean = original == other
    override fun hashCode(): Int = original.hashCode()

    override fun getColor(id: Int): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getColor(mapId(id), null) else original.getColor(mapId(id))
    @SuppressLint("UseCompatLoadingForColorStateLists")
    override fun getColorStateList(id: Int): ColorStateList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getColorStateList(mapId(id), null) else original.getColorStateList(mapId(id))
    @SuppressLint("UseCompatLoadingForDrawables")
    override fun getDrawable(id: Int): Drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getDrawable(mapId(id), null) else original.getDrawable(mapId(id))
}
