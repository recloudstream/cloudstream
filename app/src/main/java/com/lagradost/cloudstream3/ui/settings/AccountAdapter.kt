package com.lagradost.cloudstream3.ui.settings

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AccountSingleBinding
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class AccountClickCallback(val action: Int, val view: View, val card: AuthData)

class AccountAdapter(
    private val cardList: Array<AuthData>,
    private val clickCallback: (AccountClickCallback) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            AccountSingleBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), //LayoutInflater.from(parent.context).inflate(layout, parent, false),

            clickCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList[position], position)
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    override fun getItemId(position: Int): Long {
        return cardList[position].user.id.toLong()
    }

    class CardViewHolder(
        val binding: AccountSingleBinding?,
        private val clickCallback: (AccountClickCallback) -> Unit
    ) :
        RecyclerView.ViewHolder(binding?.root!!) {

        @SuppressLint("StringFormatInvalid")
        fun bind(card: AuthData, position: Int) {
            // just in case name is null account index will show, should never happened
            binding?.apply {
                accountName.text = card.user.name ?: "%s %d".format(
                    binding.accountName.context.getString(R.string.account),
                    position + 1
                )
                accountProfilePicture.isVisible = true
                accountProfilePicture.loadImage(
                    card.user.profilePicture,
                    headers = card.user.profilePictureHeaders
                )

                itemView.setOnClickListener {
                    clickCallback.invoke(AccountClickCallback(0, itemView, card))
                }
            }
        }
    }
}
