package com.lagradost.cloudstream3.ui.settings.extensions

import android.text.format.Formatter.formatShortFileSize
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.PROVIDER_STATUS_DOWN
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.RepositoryItemBinding
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.VotingApi.getVotes
import com.lagradost.cloudstream3.ui.result.setText
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.GlideApp
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTwoLettersToLanguage
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import org.junit.Assert
import org.junit.Test
import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.log10


data class PluginViewData(
    val plugin: Plugin,
    val isDownloaded: Boolean,
)

class PluginAdapter(
    val iconClickCallback: (Plugin) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val plugins: MutableList<PluginViewData> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if(isTrueTvSettings()) R.layout.repository_item_tv else R.layout.repository_item
        val inflated = LayoutInflater.from(parent.context).inflate(layout, parent, false)

        return PluginViewHolder(
            RepositoryItemBinding.bind(inflated) // may crash
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PluginViewHolder -> {
                holder.bind(plugins[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return plugins.size
    }

    fun updateList(newList: List<PluginViewData>) {
        val diffResult = DiffUtil.calculateDiff(
            PluginDiffCallback(this.plugins, newList)
        )

        plugins.clear()
        plugins.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }

    /*
    private var storedPlugins: Array<PluginData> = reloadStoredPlugins()

    private fun reloadStoredPlugins(): Array<PluginData> {
        return PluginManager.getPluginsOnline().also { storedPlugins = it }
    }*/

    // Clear glide image because setImageResource doesn't override
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is PluginViewHolder) {
            holder.binding.entryIcon.let { pluginIcon ->
                GlideApp.with(pluginIcon).clear(pluginIcon)
            }
        }
        super.onViewRecycled(holder)
    }

    companion object {
        private tailrec fun findClosestBase2(target: Int, current: Int = 16, max: Int = 512): Int {
            if (current >= max) return max
            if (current >= target) return current
            return findClosestBase2(target, current * 2, max)
        }

        @Test
        fun testFindClosestBase2() {
            Assert.assertEquals(16, findClosestBase2(0))
            Assert.assertEquals(256, findClosestBase2(170))
            Assert.assertEquals(256, findClosestBase2(256))
            Assert.assertEquals(512, findClosestBase2(257))
            Assert.assertEquals(512, findClosestBase2(700))
        }

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
                    numValue / Math.pow(
                        10.0,
                        (base * 3).toDouble()
                    )
                ) + suffix[base]
            } else {
                DecimalFormat().format(numValue)
            }
        }
    }

    inner class PluginViewHolder(val binding: RepositoryItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            data: PluginViewData,
        ) {
            val metadata = data.plugin.second
            val disabled = metadata.status == PROVIDER_STATUS_DOWN
            val name = metadata.name.removeSuffix("Provider")
            val alpha = if (disabled) 0.6f else 1f
            val isLocal = !data.plugin.second.url.startsWith("http")
            binding.mainText.alpha = alpha
            binding.subText.alpha = alpha

            val drawableInt = if (data.isDownloaded)
                R.drawable.ic_baseline_delete_outline_24
            else R.drawable.netflix_download

            binding.nsfwMarker.isVisible = metadata.tvTypes?.contains("NSFW") ?: false
            binding.actionButton.setImageResource(drawableInt)

            binding.actionButton.setOnClickListener {
                iconClickCallback.invoke(data.plugin)
            }
            itemView.setOnClickListener {
                if (isLocal) return@setOnClickListener

                val sheet = PluginDetailsFragment(data)
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

            if (data.isDownloaded) {
                // On local plugins page the filepath is provided instead of url.
                val plugin =
                    PluginManager.urlPlugins[metadata.url] ?: PluginManager.plugins[metadata.url]
                if (plugin?.openSettings != null) {
                    binding.actionSettings.isVisible = true
                    binding.actionSettings.setOnClickListener {
                        try {
                            plugin.openSettings!!.invoke(itemView.context)
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

            if (!binding.entryIcon.setImage(//itemView.entry_icon?.height ?:
                    metadata.iconUrl?.replace(
                        "%size%",
                        "$iconSize"
                    )?.replace(
                        "%exact_size%",
                        "$iconSizeExact"
                    ),
                    null,
                    errorImageDrawable = R.drawable.ic_baseline_extension_24
                )
            ) {
                binding.entryIcon.setImageResource(R.drawable.ic_baseline_extension_24)
            }

            binding.extVersion.isVisible = true
            binding.extVersion.text = "v${metadata.version}"

            if (metadata.language.isNullOrBlank()) {
                binding.langIcon.isVisible = false
            } else {
                binding.langIcon.isVisible = true
                binding.langIcon.text =
                    "${getFlagFromIso(metadata.language)} ${fromTwoLettersToLanguage(metadata.language)}"
            }

            binding.extVotes.isVisible = false
            if (!isLocal) {
                ioSafe {
                    metadata.getVotes().main {
                        binding.extVotes.setText(txt(R.string.extension_rating, prettyCount(it)))
                        binding.extVotes.isVisible = true
                    }
                }
            }


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
    }
}

class PluginDiffCallback(
    private val oldList: List<PluginViewData>,
    private val newList: List<PluginViewData>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].plugin.second.internalName == newList[newItemPosition].plugin.second.internalName && oldList[oldItemPosition].plugin.first == newList[newItemPosition].plugin.first

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}