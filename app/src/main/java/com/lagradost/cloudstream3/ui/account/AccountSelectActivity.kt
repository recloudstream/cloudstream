package com.lagradost.cloudstream3.ui.account

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.databinding.ActivityAccountSelectBinding
import com.lagradost.cloudstream3.ui.account.AccountDialog.showPinInputDialog
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAccounts

class AccountSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recyclerView: RecyclerView = binding.accountRecyclerView

        val accounts = getAccounts(this@AccountSelectActivity)

        val adapter = AccountAdapter(accounts) { selectedAccount ->
            // Handle the selected account
            onAccountSelected(selectedAccount)
        }
        recyclerView.adapter = adapter

        recyclerView.layoutManager = GridLayoutManager(this, 3)
    }

    private fun onAccountSelected(selectedAccount: DataStoreHelper.Account) {
        if (selectedAccount.lockPin != null) {
            // The selected account has a PIN set, prompt the user to enter the PIN
            showPinInputDialog(this@AccountSelectActivity, selectedAccount.lockPin, false) { pin ->
                if (pin == null) return@showPinInputDialog
                // Pin is correct, proceed to main activity
                DataStoreHelper.selectedKeyIndex = selectedAccount.keyIndex
                navigateToMainActivity()
            }
        } else {
            // No PIN set for the selected account, proceed to main activity
            DataStoreHelper.selectedKeyIndex = selectedAccount.keyIndex
            navigateToMainActivity()
        }
    }

    private fun navigateToMainActivity() {
        val mainIntent = Intent(this, MainActivity::class.java)
        startActivity(mainIntent)
        finish() // Finish the account selection activity
    }
}
