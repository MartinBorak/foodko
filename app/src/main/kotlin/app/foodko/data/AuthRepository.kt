package app.foodko.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed interface SignInResult {
    data object Success : SignInResult
    data object Cancelled : SignInResult
    data object NoGoogleAccount : SignInResult
    data class Failed(val error: Throwable) : SignInResult
}

class AuthRepository(private val auth: FirebaseAuth = Firebase.auth) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits the signed-in user, or null, now and on every change. */
    val user: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Google sign-in via Credential Manager. [activityContext] must be an Activity context,
     * [webClientId] the OAuth web client id from google-services.json (R.string.default_web_client_id).
     */
    suspend fun signInWithGoogle(activityContext: Context, webClientId: String): SignInResult {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                SignInResult.Success
            } else {
                SignInResult.Failed(IllegalStateException("Unexpected credential type ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.NoGoogleAccount
        } catch (e: Exception) {
            SignInResult.Failed(e)
        }
    }

    /**
     * Mock build only: the Auth emulator accepts a plain JSON "ID token" for the Google provider,
     * so no Google account or password is needed. Never reaches production Firebase.
     */
    suspend fun signInMock(email: String, displayName: String): SignInResult = try {
        val token = org.json.JSONObject()
            .put("sub", "mock-" + email.substringBefore('@').replace(Regex("[^a-z0-9]"), ""))
            .put("email", email)
            .put("email_verified", true)
            .put("name", displayName)
            .toString()
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        SignInResult.Success
    } catch (e: Exception) {
        SignInResult.Failed(e)
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
