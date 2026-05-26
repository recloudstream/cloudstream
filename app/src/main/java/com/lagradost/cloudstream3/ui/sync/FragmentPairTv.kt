package com.lagradost.cloudstream3.ui.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentPairTvBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

class FragmentPairTv : Fragment() {
    private var _binding: FragmentPairTvBinding? = null
    private val binding get() = _binding!!

    // Stores the pairing code so we can use it after interactive sign-in completes
    private var pendingPairingCode: String? = null

    // Interactive Google Sign-In launcher (fallback when silentSignIn fails)
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val code = pendingPairingCode
                if (code != null && account.idToken != null) {
                    // We got a fresh token, now complete the pairing
                    completePairing(code, account.idToken!!, account.email)
                } else {
                    Toast.makeText(context, "Google sign in did not return a token.", Toast.LENGTH_SHORT).show()
                    setLoading(false)
                }
            } catch (e: ApiException) {
                logError(e)
                Toast.makeText(context, "Google sign in was cancelled or failed.", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }

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

        val email = ctx.getKey<String>("firebase_email")
        val password = ctx.getKey<String>("firebase_password")

        setLoading(true)

        lifecycleScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    Toast.makeText(ctx, "Please log in first to pair TV.", Toast.LENGTH_LONG).show()
                    setLoading(false)
                    return@launch
                }

                // If we have email/password credentials, use those directly
                if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                    completePairingWithCredentials(code, email, password)
                    return@launch
                }

                // Otherwise try to get a Google ID token
                if (user.email != null) {
                    try {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(ctx.getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(ctx, gso)
                        val account = com.google.android.gms.tasks.Tasks.await(googleSignInClient.silentSignIn())
                        completePairing(code, account.idToken!!, account.email)
                    } catch (e: Exception) {
                        // Silent sign-in failed — launch interactive sign-in
                        logError(e)
                        pendingPairingCode = code
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(ctx.getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(ctx, gso)
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    }
                } else {
                    Toast.makeText(ctx, "Please log in using email/password first to pair TV.", Toast.LENGTH_LONG).show()
                    setLoading(false)
                }

            } catch (e: Exception) {
                logError(e)
                Toast.makeText(ctx, "An error occurred during pairing.", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }
    }

    /** Complete pairing using a Google ID token */
    private fun completePairing(code: String, googleIdToken: String, email: String?) {
        lifecycleScope.launch {
            try {
                val ctx = context ?: return@launch
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
                    "status" to "authorized",
                    "googleIdToken" to googleIdToken
                )

                docRef.update(updateData).await()

                Toast.makeText(ctx, "TV paired successfully!", Toast.LENGTH_LONG).show()
                activity?.onBackPressed()

            } catch (e: Exception) {
                logError(e)
                Toast.makeText(context, "An error occurred during pairing.", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    /** Complete pairing using email/password credentials */
    private fun completePairingWithCredentials(code: String, email: String, password: String) {
        lifecycleScope.launch {
            try {
                val ctx = context ?: return@launch
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
                    "status" to "authorized",
                    "email" to email,
                    "password" to password
                )

                docRef.update(updateData).await()

                Toast.makeText(ctx, "TV paired successfully!", Toast.LENGTH_LONG).show()
                activity?.onBackPressed()

            } catch (e: Exception) {
                logError(e)
                Toast.makeText(context, "An error occurred during pairing.", Toast.LENGTH_SHORT).show()
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
