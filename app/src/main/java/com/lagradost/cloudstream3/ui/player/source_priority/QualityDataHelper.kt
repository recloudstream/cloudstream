package com.lagradost.cloudstream3.ui.player.source_priority

import androidx.annotation.StringRes
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKeys
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlin.math.abs

object QualityDataHelper {
    private const val VIDEO_SOURCE_PRIORITY = "video_source_priority"
    private const val VIDEO_PROFILE_NAME = "video_profile_name"
    private const val VIDEO_QUALITY_PRIORITY = "video_quality_priority"

    // Old key only supporting one type per profile
    @Deprecated("Changed to support multiple types per profile")
    private const val VIDEO_PROFILE_TYPE = "video_profile_type"
    // New key supporting more than one type per profile

    private const val VIDEO_PROFILE_TYPES = "video_profile_types_2"
    private const val DEFAULT_SOURCE_PRIORITY = 1

    /**
     * Automatically skip loading links once this priority is reached
     **/
    const val AUTO_SKIP_PRIORITY = 10

    /**
     * Must be higher than amount of QualityProfileTypes
     **/
    private const val PROFILE_COUNT = 7

    /**
     * Unique guarantees that there will always be one of this type in the profile list.
     **/
    enum class QualityProfileType(@StringRes val stringRes: Int, val unique: Boolean) {
        None(R.string.none, false),
        WiFi(R.string.wifi, true),
        Data(R.string.mobile_data, true),
        Download(R.string.download, true)
    }

    data class QualityProfile(
        val name: UiText,
        val id: Int,
        val types: Set<QualityProfileType>
    )

    fun getSourcePriority(profile: Int, name: String?): Int {
        if (name == null) return DEFAULT_SOURCE_PRIORITY
        return getKey(
            "$currentAccount/$VIDEO_SOURCE_PRIORITY/$profile",
            name,
            DEFAULT_SOURCE_PRIORITY
        ) ?: DEFAULT_SOURCE_PRIORITY
    }

    fun getAllSourcePriorityNames(profile: Int): List<String> {
        val folder = "$currentAccount/$VIDEO_SOURCE_PRIORITY/$profile"
        return getKeys(folder)?.map { key ->
            key.substringAfter("$folder/")
        } ?: emptyList()
    }

    fun setSourcePriority(profile: Int, name: String, priority: Int) {
        val folder = "$currentAccount/$VIDEO_SOURCE_PRIORITY/$profile"
        // Prevent unnecessary keys
        if (priority == DEFAULT_SOURCE_PRIORITY) {
            removeKey(folder, name)
        } else {
            setKey(folder, name, priority)
        }
    }

    fun setProfileName(profile: Int, name: String?) {
        val path = "$currentAccount/$VIDEO_PROFILE_NAME/$profile"
        if (name == null) {
            removeKey(path)
        } else {
            setKey(path, name.trim())
        }
    }

    fun getProfileName(profile: Int): UiText {
        return getKey<String>("$currentAccount/$VIDEO_PROFILE_NAME/$profile")?.let { txt(it) }
            ?: txt(R.string.profile_number, profile)
    }

    fun getQualityPriority(profile: Int, quality: Qualities): Int {
        return getKey(
            "$currentAccount/$VIDEO_QUALITY_PRIORITY/$profile",
            quality.value.toString(),
            quality.defaultPriority
        ) ?: quality.defaultPriority
    }

    fun setQualityPriority(profile: Int, quality: Qualities, priority: Int) {
        setKey(
            "$currentAccount/$VIDEO_QUALITY_PRIORITY/$profile",
            quality.value.toString(),
            priority
        )
    }


    @Suppress("DEPRECATION")
    fun getQualityProfileTypes(profile: Int): Set<QualityProfileType> {
        val newKey = "$currentAccount/$VIDEO_PROFILE_TYPES/$profile"
        // Use arrays for to make with work with setKey properly (weird crashes otherwise)
        val newProfiles = getKey<Array<QualityProfileType>>(newKey)?.toSet()

        // Migrate to new profile key
        if (newProfiles == null) {
            val oldProfile =
                getKey<QualityProfileType>("$currentAccount/$VIDEO_PROFILE_TYPE/$profile")
            val newSet = oldProfile?.let { arrayOf(it) } ?: arrayOf()
            setKey(newKey, newSet)
            return newSet.toSet()
        } else {
            return newProfiles
        }
    }

    fun addQualityProfileType(profile: Int, type: QualityProfileType) {
        val path = "$currentAccount/$VIDEO_PROFILE_TYPES/$profile"
        val currentTypes = getQualityProfileTypes(profile)

        if (type != QualityProfileType.None) {
            setKey(path, (currentTypes + type).toTypedArray())
        }
    }

    fun removeQualityProfileType(profile: Int, type: QualityProfileType) {
        val path = "$currentAccount/$VIDEO_PROFILE_TYPES/$profile"
        val currentTypes = getQualityProfileTypes(profile)

        if (type != QualityProfileType.None) {
            setKey(path, (currentTypes - type).toTypedArray())
        }
    }

    /**
     * Gets all quality profiles, always includes one profile with WiFi and Data
     * Must under all circumstances at least return one profile
     **/
    fun getProfiles(): List<QualityProfile> {
        val availableTypes = QualityProfileType.entries.toMutableList()
        val profiles = (1..PROFILE_COUNT).map { profileNumber ->
            // Get the real type
            val types = getQualityProfileTypes(profileNumber)

            val uniqueTypes = types.mapNotNull { type ->
                // This makes it impossible to get more than one of each type
                if (type.unique && !availableTypes.remove(type)) {
                    null
                } else {
                    type
                }
            }.toSet()

            QualityProfile(
                getProfileName(profileNumber),
                profileNumber,
                uniqueTypes
            )
        }.toMutableList()

        /**
         * If no profile of this type exists: insert it on the earliest profile
         **/
        fun insertType(
            list: MutableList<QualityProfile>,
            type: QualityProfileType
        ) {
            if (list.any { it.types.contains(type) }) return

            synchronized(list) {
                val firstItem = list.firstOrNull() ?: return
                val fixedTypes = firstItem.types + type
                val fixedItem = firstItem.copy(types = fixedTypes)
                list.set(0, fixedItem)
            }
        }

        QualityProfileType.entries.forEach {
            if (it.unique) insertType(profiles, it)
        }

        debugAssert({
            !QualityProfileType.entries.all { type ->
                !type.unique || profiles.any { it.types.contains(type) }
            }
        }, { "All unique quality types do not exist" })

        debugAssert({
            profiles.isEmpty()
        }, { "No profiles!" })

        return profiles
    }

    fun getLinkPriority(
        qualityProfile: Int,
        linkData: ExtractorLink?
    ): Int {
        val qualityPriority = getQualityPriority(
            qualityProfile,
            closestQuality(linkData?.quality)
        )
        val sourcePriority = getSourcePriority(qualityProfile, linkData?.source)

        return qualityPriority + sourcePriority
    }

    private fun closestQuality(target: Int?): Qualities {
        if (target == null) return Qualities.Unknown
        return Qualities.entries.minBy { abs(it.value - target) }
    }
}