package com.lagradost.cloudstream3.ui.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentPairTvBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.DataStore.getKey
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

    private fun submitPairingCode(code: String) {
        val ctx = context ?: return
        val email = ctx.getKey<String>("firebase_email")
        val password = ctx.getKey<String>("firebase_password")

        if (email.isNullOrBlank() || password.isNullOrBlank()) {
            Toast.makeText(ctx, "Please log in using email/password first to pair TV.", Toast.LENGTH_LONG).show()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
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

                // Update the document to authorize the TV
                val updateData = hashMapOf(
                    "status" to "authorized",
                    "email" to email,
                    "password" to password
                )
                docRef.update(updateData as Map<String, Any>).await()

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
