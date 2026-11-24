package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.cloud.OperationType
import com.example.cloud.handleFirestoreError
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class CloudAccountProfile(
    val uid: String,
    val displayName: String,
    val email: String?,
    val isEmailVerified: Boolean,
)

sealed interface AdminCloudVerification {
    data object NotSignedIn : AdminCloudVerification

    data class Verified(
        val profile: CloudAccountProfile,
        val sourceLabel: String,
    ) : AdminCloudVerification

    data class Denied(
        val profile: CloudAccountProfile,
        val reason: String,
    ) : AdminCloudVerification
}

sealed interface GoogleSignInOutcome {
    data class Success(val profile: CloudAccountProfile) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Failure(val message: String) : GoogleSignInOutcome
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun FirebaseUser.toCloudAccountProfile(): CloudAccountProfile = CloudAccountProfile(
    uid = uid,
    displayName = displayName?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore('@')
        ?: "BookHub User",
    email = email,
    isEmailVerified = isEmailVerified,
)

object FirebaseAuthManager {
    private const val TAG = "BookHubAuth"

    suspend fun attemptSilentSignIn(
        context: Context,
        auth: FirebaseAuth = Firebase.auth,
    ): CloudAccountProfile? {
        auth.currentUser?.let { return it.toCloudAccountProfile() }
        val clientId = resolveWebClientId(context) ?: return null
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val response = credentialManager.getCredential(context, request)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null),
                ).await()
                authResult.user?.toCloudAccountProfile()
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signInInteractiveWithGoogle(
        activityContext: Context,
        auth: FirebaseAuth = Firebase.auth,
    ): GoogleSignInOutcome {
        val clientId = resolveWebClientId(activityContext)
            ?: return GoogleSignInOutcome.Failure(
                "Google Sign-In configuration missing (default_web_client_id not generated).",
            )
        val credentialManager = CredentialManager.create(activityContext)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = (activityContext as? Activity) ?: activityContext,
                request = request,
            )
            val cred = response.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null),
                ).await()
                val user = authResult.user
                    ?: return GoogleSignInOutcome.Failure("Firebase sign-in returned no user.")
                GoogleSignInOutcome.Success(user.toCloudAccountProfile())
            } else {
                GoogleSignInOutcome.Failure("Unexpected credential type received.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleSignInOutcome.Failure(
                e.localizedMessage ?: "Google Sign-In failed. Check SHA-1 fingerprint & network.",
            )
        }
    }

    suspend fun signOut(
        context: Context,
        auth: FirebaseAuth = Firebase.auth,
    ) {
        withContext(NonCancellable) {
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    /**
     * Verifies server-trusted admin privilege without assuming any signed-in user is admin.
     * Supports Firebase Auth custom claim (`admin == true`) or server-provisioned `/admins/{uid}`.
     */
    suspend fun verifyAdminAuthorization(
        db: FirebaseFirestore,
        auth: FirebaseAuth = Firebase.auth,
        forceTokenRefresh: Boolean = true,
    ): AdminCloudVerification {
        val user = auth.currentUser ?: return AdminCloudVerification.NotSignedIn
        val profile = user.toCloudAccountProfile()

        return try {
            val tokenResult = user.getIdToken(forceTokenRefresh).await()
            val hasAdminClaim = (tokenResult.claims["admin"] as? Boolean) == true
            if (hasAdminClaim) {
                return AdminCloudVerification.Verified(
                    profile = profile,
                    sourceLabel = "Firebase Custom Claim (admin)",
                )
            }

            val adminDocPath = "admins/${user.uid}"
            val snapshot = try {
                db.collection("admins").document(user.uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, adminDocPath)
                return AdminCloudVerification.Denied(
                    profile = profile,
                    reason = e.localizedMessage
                        ?: "Could not verify administrator permissions with Firestore.",
                )
            }

            if (snapshot.exists()) {
                val activeFlag = snapshot.getBoolean("active")
                if (activeFlag == false) {
                    AdminCloudVerification.Denied(
                        profile = profile,
                        reason = "Your administrator record in Firestore is currently disabled (active = false).",
                    )
                } else {
                    AdminCloudVerification.Verified(
                        profile = profile,
                        sourceLabel = "Firestore Admin Allowlist (/admins/${user.uid})",
                    )
                }
            } else {
                AdminCloudVerification.Denied(
                    profile = profile,
                    reason = "This Google account is signed in, but does not have an admin role assigned yet.",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed verifying admin token", e)
            AdminCloudVerification.Denied(
                profile = profile,
                reason = e.localizedMessage ?: "Failed to verify administrator session.",
            )
        }
    }

    private fun resolveWebClientId(context: Context): String? = try {
        context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}
