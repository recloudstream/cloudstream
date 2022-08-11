package com.lagradost.cloudstream3.ui.settings.extensions

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.PROVIDER_STATUS_DOWN
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.utils.GlideApp
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import kotlinx.android.synthetic.main.repository_item.view.*


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
        return PluginViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.repository_item, parent, false)
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
        holder.itemView.entry_icon?.let { pluginIcon ->
            GlideApp.with(pluginIcon).clear(pluginIcon)
        }
        super.onViewRecycled(holder)
    }

    inner class PluginViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        fun bind(
            data: PluginViewData,
        ) {
            val metadata = data.plugin.second
            val alpha = if (metadata.status == PROVIDER_STATUS_DOWN) 0.6f else 1f
            itemView.main_text?.alpha = alpha
            itemView.sub_text?.alpha = alpha

            val drawableInt = if (data.isDownloaded)
                R.drawable.ic_baseline_delete_outline_24
            else R.drawable.netflix_download

            itemView.nsfw_marker?.isVisible = metadata.adult == true
            itemView.action_button?.setImageResource(drawableInt)

            itemView.action_button?.setOnClickListener {
                iconClickCallback.invoke(data.plugin)
            }

            if (data.isDownloaded) {
                val plugin = PluginManager.urlPlugins[metadata.url]
                if (plugin?.openSettings != null) {
                    itemView.action_settings?.isVisible = true
                    itemView.action_settings.setOnClickListener {
                        try {
                            plugin.openSettings!!.invoke(itemView.context)
                        } catch (e: Throwable) {
                            Log.e("PluginAdapter", "Failed to open ${metadata.name} settings: ${Log.getStackTraceString(e)}")
                        }

                    }
                }
            }

            if (itemView.entry_icon?.setImage(metadata.iconUrl, null) != true) {
                itemView.entry_icon?.setImageResource(R.drawable.ic_baseline_extension_24)
            }

            itemView.ext_version?.isVisible = true
            itemView.ext_version?.text = "v${metadata.version}"

            itemView.main_text?.text = metadata.name
            itemView.sub_text?.text = metadata.description
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