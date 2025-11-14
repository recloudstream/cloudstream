package com.lagradost.cloudstream3.ui.settings.extensions

import android.annotation.SuppressLint
import android.text.format.Formatter.formatShortFileSize
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.PROVIDER_STATUS_DOWN
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.RepositoryItemBinding
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.VotingApi.getVotes
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.getImageFromDrawable
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.txt
import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class PluginViewData(
    val plugin: Plugin,
    val isDownloaded: Boolean,
)

class RepositoryViewHolderState(view: ViewBinding) : ViewHolderState<Any>(view) {
    // Store how many times this has called recycled, this is used to correctly sync text in jobs
    var recycleCount = 0
}

class PluginAdapter(
    val iconClickCallback: (Plugin) -> Unit
) : NoStateAdapter<PluginViewData>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    a.plugin.second.internalName == b.plugin.second.internalName && a.plugin.first == b.plugin.first
})) {
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val layout = if (isLayout(TV)) R.layout.repository_item_tv else R.layout.repository_item
        val inflated = LayoutInflater.from(parent.context).inflate(layout, parent, false)

        return RepositoryViewHolderState(
            RepositoryItemBinding.bind(inflated) // may crash
        )
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        if (holder is RepositoryViewHolderState) {
            holder.recycleCount += 1
        }
        when (val binding = holder.view) {
            is RepositoryItemBinding -> {
                clearImage(binding.entryIcon)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindContent(holder: ViewHolderState<Any>, item: PluginViewData, position: Int) {
        val binding = holder.view as? RepositoryItemBinding ?: return
        val itemView = holder.itemView

        val metadata = item.plugin.second
        val disabled = metadata.status == PROVIDER_STATUS_DOWN
        val name = metadata.name.removeSuffix("Provider")
        val alpha = if (disabled) 0.6f else 1f
        val isLocal = !item.plugin.second.url.startsWith("http")
        binding.mainText.alpha = alpha
        binding.subText.alpha = alpha

        val drawableInt = if (item.isDownloaded)
            R.drawable.ic_baseline_delete_outline_24
        else R.drawable.netflix_download

        binding.nsfwMarker.isVisible = metadata.tvTypes?.contains(TvType.NSFW.name) ?: false
        binding.actionButton.setImageResource(drawableInt)

        binding.actionButton.setOnClickListener {
            iconClickCallback.invoke(item.plugin)
        }
        itemView.setOnClickListener {
            if (isLocal) return@setOnClickListener

            val sheet = PluginDetailsFragment(item)
            val activity = itemView.context.getActivity() as AppCompatActivity
            sheet.show(activity.supportFragmentManager, "PluginDetails")
        }
        //if (itemView.context?.isTrueTvSettings() == false) {
        //    val siteUrl = metadata.repositoryUrl
        //    if (siteUrl != null && siteUrl.isNotBlank() && siteUrl != "NONE") {
        //        itemView.setOnClickListener {
        //            openBrowser(siteUrl)
        //        }
        //    }
        //}

        if (item.isDownloaded) {
            // On local plugins page the filepath is provided instead of url.
            val plugin =
                (PluginManager.urlPlugins[metadata.url]
                    ?: (PluginManager.plugins[metadata.url])) as? com.lagradost.cloudstream3.plugins.Plugin

            if (plugin?.openSettings != null) {
                binding.actionSettings.isVisible = true
                binding.actionSettings.setOnClickListener {
                    try {
                        plugin.openSettings?.invoke(itemView.context)
                    } catch (e: Throwable) {
                        Log.e(
                            "PluginAdapter",
                            "Failed to open $name settings: ${
                                Log.getStackTraceString(e)
                            }"
                        )
                    }
                }
            } else {
                binding.actionSettings.isVisible = false
            }
        } else {
            binding.actionSettings.isVisible = false
        }

        val url = metadata.iconUrl?.replace(
            "%size%",
            "$iconSize"
        )?.replace(
            "%exact_size%",
            "$iconSizeExact"
        )

        if (url.isNullOrBlank()) {
            binding.entryIcon.loadImage(R.drawable.ic_baseline_extension_24)
        } else {
            binding.entryIcon.loadImage(
                url
            ) { error(getImageFromDrawable(itemView.context, R.drawable.ic_baseline_extension_24)) }
        }

        binding.extVersion.isVisible = true
        binding.extVersion.text = "v${metadata.version}"

        if (metadata.language.isNullOrBlank()) {
            binding.langIcon.isVisible = false
        } else {
            binding.langIcon.isVisible = true
            binding.langIcon.text = getNameNextToFlagEmoji(metadata.language) ?: metadata.language
        }

        //val oldRecycleCount = (holder as? RepositoryViewHolderState)?.recycleCount

        binding.extVotes.isVisible = false

        // Disable this for now as the vote api is down, this will also significantly improve the lag
        // from doing all these network requests
        /*if (!isLocal) {
            ioSafe {
                metadata.getVotes().main { votes ->
                    val currentRecycleCount = (holder as? RepositoryViewHolderState)?.recycleCount

                    // Only set the text if the view is correctly rendered
                    if (currentRecycleCount == oldRecycleCount) {
                        binding.extVotes.setText(txt(R.string.extension_rating, prettyCount(votes)))
                        binding.extVotes.isVisible = true
                    }
                }
            }
        }*/

        if (metadata.fileSize != null) {
            binding.extFilesize.isVisible = true
            binding.extFilesize.text = formatShortFileSize(itemView.context, metadata.fileSize)
        } else {
            binding.extFilesize.isVisible = false
        }

        binding.mainText.setText(
            if (disabled) txt(
                R.string.single_plugin_disabled,
                name
            ) else txt(name)
        )

        binding.subText.isGone = metadata.description.isNullOrBlank()
        binding.subText.text = metadata.description.html()
    }

    companion object {
        // A high count as we can render in the entire list as the same time
        val sharedPool =
            RecyclerView.RecycledViewPool().apply { this.setMaxRecycledViews(CONTENT, 15) }

        private tailrec fun findClosestBase2(target: Int, current: Int = 16, max: Int = 512): Int {
            if (current >= max) return max
            if (current >= target) return current
            return findClosestBase2(target, current * 2, max)
        }

        // DO NOT MOVE, as running this test will result in ExceptionInInitializerError on prerelease due to static variables using Resources.getSystem()
        // this test function is only to show how the function works
        /*@Test
        fun testFindClosestBase2() {
            Assert.assertEquals(16, findClosestBase2(0))
            Assert.assertEquals(256, findClosestBase2(170))
            Assert.assertEquals(256, findClosestBase2(256))
            Assert.assertEquals(512, findClosestBase2(257))
            Assert.assertEquals(512, findClosestBase2(700))
        }*/

        private val iconSizeExact = 32.toPx
        private val iconSize by lazy {
            findClosestBase2(iconSizeExact, 16, 512)
        }

        fun prettyCount(number: Number): String? {
            val suffix = charArrayOf(' ', 'k', 'M', 'B', 'T', 'P', 'E')
            val numValue = number.toLong()
            val value = floor(log10(numValue.toDouble())).toInt()
            val base = value / 3
            return if (value >= 3 && base < suffix.size) {
                DecimalFormat("#0.00").format(
                    numValue / 10.0.pow((base * 3).toDouble())
                ) + suffix[base]
            } else {
                DecimalFormat().format(numValue)
            }
        }
    }
}