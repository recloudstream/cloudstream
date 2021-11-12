package com.lagradost.cloudstream3.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.utils.UIHelper.setImage

class AccountClickCallback(val action: Int, val view : View, val card: OAuth2API.LoginInfo)

class AccountAdapter(
    val cardList: List<OAuth2API.LoginInfo>,
    val layout: Int = R.layout.account_single,
    private val clickCallback: (AccountClickCallback) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false), clickCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    override fun getItemId(position: Int): Long {
        return cardList[position].accountIndex.toLong()
    }

    class CardViewHolder
    constructor(itemView: View, private val clickCallback: (AccountClickCallback) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val pfp: ImageView = itemView.findViewById(R.id.account_profile_picture)!!
        private val accountName: TextView = itemView.findViewById(R.id.account_name)!!

        fun bind(card: OAuth2API.LoginInfo) {
            // just in case name is null account index will show, should never happened
            accountName.text = card.name ?: "%s %d".format(accountName.context.getString(R.string.account), card.accountIndex)
            if(card.profilePicture.isNullOrEmpty()) {
                pfp.isVisible = false
            } else {
                pfp.isVisible = true
                pfp.setImage(card.profilePicture)
            }

            itemView.setOnClickListener {
                clickCallback.invoke(AccountClickCallback(0, itemView, card))
            }
        }
    }
}
