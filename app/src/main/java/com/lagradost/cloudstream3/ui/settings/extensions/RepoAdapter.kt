package com.lagradost.cloudstream3.ui.settings.extensions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.AccountClickCallback
import kotlinx.android.synthetic.main.repository_item.view.*

class RepoAdapter(
    private val repositories: Array<RepositoryData>,
    val clickCallback: (RepositoryData) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return RepoViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.repository_item, parent, false)
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

    inner class RepoViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(
            repositoryData: RepositoryData
        ) {
            itemView.setOnClickListener {
                clickCallback(repositoryData)
            }
            itemView.main_text?.text = repositoryData.name
            itemView.sub_text?.text = repositoryData.url
        }
    }
}