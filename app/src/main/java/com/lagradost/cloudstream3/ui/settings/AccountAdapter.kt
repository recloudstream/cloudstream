package com.lagradost.cloudstream3.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AccountSingleBinding
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.utils.UIHelper.setImage

class AccountClickCallback(val action: Int, val view: View, val card: AuthAPI.LoginInfo)

class AccountAdapter(
    val cardList: List<AuthAPI.LoginInfo>,
    private val clickCallback: (AccountClickCallback) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            AccountSingleBinding.inflate(LayoutInflater.from(parent.context), parent, false), //LayoutInflater.from(parent.context).inflate(layout, parent, false),

            clickCallback
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
    constructor(val binding: AccountSingleBinding, private val clickCallback: (AccountClickCallback) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {
      //  private val pfp: ImageView = itemView.findViewById(R.id.account_profile_picture)!!
       // private val accountName: TextView = itemView.findViewById(R.id.account_name)!!

        fun bind(card: AuthAPI.LoginInfo) {
            // just in case name is null account index will show, should never happened
            binding.accountName.text = card.name ?: "%s %d".format(
                binding.accountName.context.getString(R.string.account),
                card.accountIndex
            )
            binding.accountProfilePicture.isVisible = binding.accountProfilePicture.setImage(card.profilePicture)

            itemView.setOnClickListener {
                clickCallback.invoke(AccountClickCallback(0, itemView, card))
            }
        }
    }
}
