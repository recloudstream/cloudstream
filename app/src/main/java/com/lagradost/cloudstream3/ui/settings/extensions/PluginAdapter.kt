package com.lagradost.cloudstream3.ui.settings.extensions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import kotlinx.android.synthetic.main.repository_item.view.*

class PluginAdapter(
    var plugins: List<SitePlugin>,
    val iconClickCallback: PluginAdapter.(plugin: SitePlugin, isDownloaded: Boolean) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
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

    private var storedPlugins: Array<PluginData> = reloadStoredPlugins()

    fun reloadStoredPlugins(): Array<PluginData> {
        return PluginManager.getPluginsOnline().also { storedPlugins = it }
    }

    inner class PluginViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        fun bind(
            plugin: SitePlugin
        ) {
            val isDownloaded = storedPlugins.any { it.url == plugin.url }

            val drawableInt = if (isDownloaded)
                R.drawable.ic_baseline_delete_outline_24
            else R.drawable.netflix_download

            itemView.nsfw_marker?.isVisible = plugin.isAdult == true
            itemView.action_button?.setImageResource(drawableInt)

            itemView.action_button?.setOnClickListener {
                iconClickCallback.invoke(this@PluginAdapter, plugin, isDownloaded)
            }

            itemView.main_text?.text = plugin.name
            itemView.sub_text?.text = plugin.description
        }
    }
}