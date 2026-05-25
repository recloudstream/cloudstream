package com.lagradost.cloudstream3.ui.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentPairTvBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.DataStore.getKey
import kotlinx.coroutines.tasks.await
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

class FragmentPairTv : Fragment() {
    private var _binding: FragmentPairTvBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPairTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.pairBackBtn.setOnClickListener {
            activity?.onBackPressed()
        }

        binding.pairSubmitButton.setOnClickListener {
            val code = binding.pairCodeInput.text.toString().trim().uppercase()
            if (code.length != 6) {
                Toast.makeText(context, "Pairing code must be exactly 6 characters.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            submitPairingCode(code)
        }
    }

    private fun submitPairingCode(rawCode: String) {
        val ctx = context ?: return
        
        // Parse URI if it's a deep link
        val code = try {
            val uri = android.net.Uri.parse(rawCode)
            uri.getQueryParameter("code") ?: rawCode
        } catch (e: Exception) {
            rawCode
        }

        var email = ctx.getKey<String>("firebase_email")
        var password = ctx.getKey<String>("firebase_password")
        var googleIdToken: String? = null

        setLoading(true)

        lifecycleScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    Toast.makeText(ctx, "Please log in first to pair TV.", Toast.LENGTH_LONG).show()
                    setLoading(false)
                    return@launch
                }

                if (password.isNullOrBlank() && user.email != null) {
                    try {
                        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(ctx.getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build()
                        val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(ctx, gso)
                        val account = com.google.android.gms.tasks.Tasks.await(googleSignInClient.silentSignIn())
                        googleIdToken = account.idToken
                        email = account.email
                    } catch (e: Exception) {
                        logError(e)
                        Toast.makeText(ctx, "Please log out and log back in to pair TV (Recent login required).", Toast.LENGTH_LONG).show()
                        setLoading(false)
                        return@launch
                    }
                }

                if (googleIdToken.isNullOrBlank() && (email.isNullOrBlank() || password.isNullOrBlank())) {
                    Toast.makeText(ctx, "Please log in using email/password first to pair TV.", Toast.LENGTH_LONG).show()
                    setLoading(false)
                    return@launch
                }

                val firestore = FirebaseFirestore.getInstance()
                val docRef = firestore.collection("pairing_codes").document(code)
                val snapshot = docRef.get().await()

                if (!snapshot.exists()) {
                    Toast.makeText(ctx, "Invalid or expired pairing code.", Toast.LENGTH_SHORT).show()
                    setLoading(false)
                    return@launch
                }

                val createdAt = snapshot.getLong("createdAt") ?: 0L
                val status = snapshot.getString("status")

                if (status != "pending" || System.currentTimeMillis() - createdAt > 5 * 60 * 1000) {
                    Toast.makeText(ctx, "Pairing code has expired or is already paired.", Toast.LENGTH_SHORT).show()
                    setLoading(false)
                    return@launch
                }

                val updateData = hashMapOf<String, Any>(
                    "status" to "authorized"
                )
                if (!googleIdToken.isNullOrBlank()) {
                    updateData["googleIdToken"] = googleIdToken!!
                } else {
                    updateData["email"] = email!!
                    updateData["password"] = password!!
                }

                docRef.update(updateData).await()

                Toast.makeText(ctx, "TV paired successfully!", Toast.LENGTH_LONG).show()
                activity?.onBackPressed()

            } catch (e: Exception) {
                logError(e)
                Toast.makeText(ctx, "An error occurred during pairing.", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.pairLoading.isVisible = isLoading
        binding.pairCodeInput.isEnabled = !isLoading
        binding.pairSubmitButton.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
