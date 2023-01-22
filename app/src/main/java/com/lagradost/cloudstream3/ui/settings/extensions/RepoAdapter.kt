package com.lagradost.cloudstream3.ui.settings.extensions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.RepositoryManager.PREBUILT_REPOSITORIES
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import kotlinx.android.synthetic.main.repository_item.view.*

class RepoAdapter(
    val isSetup: Boolean,
    val clickCallback: RepoAdapter.(RepositoryData) -> Unit,
    val imageClickCallback: RepoAdapter.(RepositoryData) -> Unit,
    /** In setup mode the trash icons will be replaced with download icons */
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val repositories: MutableList<RepositoryData> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if(isTrueTvSettings()) R.layout.repository_item_tv else R.layout.repository_item
        return RepoViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is RepoViewHolder -> {
                holder.bind(repositories[position])
            }
        }
    }

    // Clear glide image because setImageResource doesn't override
//    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
//        holder.itemView.entry_icon?.let { repoIcon ->
//            GlideApp.with(repoIcon).clear(repoIcon)
//        }
//        super.onViewRecycled(holder)
//    }

    override fun getItemCount(): Int {
        return repositories.size
    }

    fun updateList(newList: Array<RepositoryData>) {
        val diffResult = DiffUtil.calculateDiff(
            RepoDiffCallback(this.repositories, newList)
        )

        repositories.clear()
        repositories.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }

    inner class RepoViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(
            repositoryData: RepositoryData
        ) {
            val isPrebuilt = PREBUILT_REPOSITORIES.contains(repositoryData)
            val drawable =
                if (isSetup) R.drawable.netflix_download else R.drawable.ic_baseline_delete_outline_24

            // Only shows icon if on setup or if it isn't a prebuilt repo.
            // No delete buttons on prebuilt repos.
            if (!isPrebuilt || isSetup) {
                itemView.action_button?.setImageResource(drawable)
            }

            itemView.action_button?.setOnClickListener {
                imageClickCallback(repositoryData)
            }

            itemView.repository_item_root?.setOnClickListener {
                clickCallback(repositoryData)
            }
            itemView.main_text?.text = repositoryData.name
            itemView.sub_text?.text = repositoryData.url
        }
    }
}

class RepoDiffCallback(
    private val oldList: List<RepositoryData>,
    private val newList: Array<RepositoryData>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].url == newList[newItemPosition].url

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}