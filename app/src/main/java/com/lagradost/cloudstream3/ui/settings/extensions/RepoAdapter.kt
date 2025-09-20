package com.lagradost.cloudstream3.ui.settings.extensions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.RepositoryItemBinding
import com.lagradost.cloudstream3.databinding.RepositoryItemTvBinding
import com.lagradost.cloudstream3.plugins.RepositoryManager.PREBUILT_REPOSITORIES
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.getImageFromDrawable

class RepoAdapter(
    val isSetup: Boolean,
    val clickCallback: RepoAdapter.(RepositoryData) -> Unit,
    val imageClickCallback: RepoAdapter.(RepositoryData) -> Unit,
    /** In setup mode the trash icons will be replaced with download icons */
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val repositories: MutableList<RepositoryData> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (isLayout(TV)) RepositoryItemTvBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        ) else RepositoryItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )  //R.layout.repository_item_tv else R.layout.repository_item
        return RepoViewHolder(
            layout
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is RepoViewHolder -> {
                holder.bind(repositories[position])
            }
        }
    }

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

    // Clear coil image because setImageResource doesn't override
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is RepoViewHolder) {
            when(holder.binding){
                is RepositoryItemBinding -> holder.binding.entryIcon.loadImage(R.drawable.ic_github_logo)
                is RepositoryItemTvBinding -> holder.binding.entryIcon.loadImage(R.drawable.ic_github_logo)
            }
        }
        super.onViewRecycled(holder)
    }

    inner class RepoViewHolder(
        val binding: ViewBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            repositoryData: RepositoryData
        ) {
            val isPrebuilt = PREBUILT_REPOSITORIES.contains(repositoryData)
            val drawable =
                if (isSetup) R.drawable.netflix_download else R.drawable.ic_baseline_delete_outline_24
            when (binding) {
                is RepositoryItemTvBinding -> {
                    binding.apply {
                        // Only shows icon if on setup or if it isn't a prebuilt repo.
                        // No delete buttons on prebuilt repos.
                        if (!isPrebuilt || isSetup) {
                            actionButton.setImageResource(drawable)
                        }

                        actionButton.setOnClickListener {
                            imageClickCallback(repositoryData)
                        }

                        repositoryItemRoot.setOnClickListener {
                            clickCallback(repositoryData)
                        }
                        mainText.text = repositoryData.name
                        subText.text = repositoryData.url
                        if(!repositoryData.iconUrl.isNullOrEmpty()){
                            entryIcon.loadImage(repositoryData.iconUrl){
                                error(getImageFromDrawable(itemView.context,R.drawable.ic_github_logo))
                            }
                        }
                    }
                }

                is RepositoryItemBinding -> {
                    binding.apply {
                        // Only shows icon if on setup or if it isn't a prebuilt repo.
                        // No delete buttons on prebuilt repos.
                        if (!isPrebuilt || isSetup) {
                            actionButton.setImageResource(drawable)
                        }

                        actionButton.setOnClickListener {
                            imageClickCallback(repositoryData)
                        }

                        repositoryItemRoot.setOnClickListener {
                            clickCallback(repositoryData)
                        }

                        repositoryItemRoot.setOnLongClickListener {
                            val shareableRepoData = "${repositoryData.name}$SHAREABLE_REPO_SEPARATOR\n ${repositoryData.url}"
                            clipboardHelper(txt(R.string.repo_copy_label), shareableRepoData)
                            true
                        }

                        mainText.text = repositoryData.name
                        subText.text = repositoryData.url
                        if(!repositoryData.iconUrl.isNullOrEmpty()){
                            entryIcon.loadImage(repositoryData.iconUrl){
                                error(getImageFromDrawable(itemView.context,R.drawable.ic_github_logo))
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val SHAREABLE_REPO_SEPARATOR = " : "
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