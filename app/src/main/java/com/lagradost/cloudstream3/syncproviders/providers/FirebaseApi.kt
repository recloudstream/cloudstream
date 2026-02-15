package com.lagradost.cloudstream3.syncproviders.providers

import com.google.firebase.auth.FirebaseAuth
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthLoginRequirement
import com.lagradost.cloudstream3.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.utils.FirestoreSyncManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseApi : AuthAPI() {
    override val name = "Firebase"
    override val idPrefix = "firebase"
    override val icon = R.drawable.ic_baseline_sync_24
    override val requiresLogin = true
    override val createAccountUrl = null // We handle registration in-app via login fallback

    override val hasInApp = true
    override val inAppLoginRequirement = AuthLoginRequirement(
        email = true,
        password = true
    )

    override suspend fun login(form: AuthLoginResponse): AuthToken? {
        val email = form.email ?: return null
        val password = form.password ?: return null

        return suspendCancellableCoroutine { cont ->
            val auth = FirestoreSyncManager.getFirebaseAuth()
            
            // Try Login first
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        cont.resume(
                            AuthToken(
                                accessToken = user.uid,
                                refreshToken = null, // Managed by Firebase
                                payload = user.email
                            )
                        )
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { loginErr ->
                    // Fallback to Registration if login fails (simple "Login or Register" flow)
                    // This matches the previous logic which was convenient for users
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result ->
                             val user = result.user
                             if (user != null) {
                                 cont.resume(
                                     AuthToken(
                                         accessToken = user.uid,
                                         refreshToken = null,
                                         payload = user.email
                                     )
                                 )
                             } else {
                                 cont.resume(null)
                             }
                        }
                        .addOnFailureListener { regErr ->
                            // If both fail, throw the login error (usually more relevant if user exists)
                            // or a combined error.
                            cont.resumeWithException(Exception("Login: ${loginErr.message}\nRegister: ${regErr.message}"))
                        }
                }
        }
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        if (token == null) return null
        // We can trust the token payload (email) or get fresh from FirebaseAuth
        val user = FirestoreSyncManager.getFirebaseAuth().currentUser
        return if (user != null && user.uid == token.accessToken) {
             AuthUser(
                 name = user.email,
                 id = user.uid.hashCode(), // Int ID required by AuthUser, hash is imperfect but standard here
                 profilePicture = null
             )
        } else {
            null
        }
    }
    
    override suspend fun invalidateToken(token: AuthToken): Nothing {
        FirestoreSyncManager.getFirebaseAuth().signOut()
        throw NotImplementedError("Firebase tokens cannot be manually invalidated")
    }
}
