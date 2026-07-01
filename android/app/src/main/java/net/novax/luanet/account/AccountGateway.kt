package net.novax.luanet.account

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.tasks.await
import net.novax.luanet.BuildConfig

data class AccountSession(
    val available: Boolean,
    val signedIn: Boolean,
    val uid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val emailVerified: Boolean = false,
)

class AccountGateway(private val context: Context) {
    private val firebaseAvailable: Boolean by lazy {
        FirebaseApp.initializeApp(context) != null || FirebaseApp.getApps(context).isNotEmpty()
    }

    private val auth: FirebaseAuth?
        get() = if (firebaseAvailable) FirebaseAuth.getInstance() else null

    fun currentSession(): AccountSession {
        val user = auth?.currentUser ?: return AccountSession(available = firebaseAvailable, signedIn = false)
        return AccountSession(
            available = firebaseAvailable,
            signedIn = true,
            uid = user.uid,
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl?.toString(),
            emailVerified = user.isEmailVerified || user.hasTrustedOAuthProvider(),
        )
    }

    suspend fun freshIdToken(): String {
        val user = requireNotNull(auth?.currentUser) { "Sign in before using NovaX public tunnels" }
        user.reload().await()
        val refreshed = requireNotNull(auth?.currentUser) { "Sign in before using NovaX public tunnels" }
        if (!refreshed.isEmailVerified && !refreshed.hasTrustedOAuthProvider()) {
            throw IllegalStateException("Verify your email before using NovaX public tunnels")
        }
        return requireNotNull(refreshed.getIdToken(true).await().token) { "Firebase did not return an ID token" }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        val firebaseAuth = requireFirebase()
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
        firebaseAuth.currentUser?.reload()?.await()
    }

    suspend fun createEmailAccount(email: String, password: String) {
        val firebaseAuth = requireFirebase()
        firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
        firebaseAuth.currentUser?.sendEmailVerification()?.await()
    }

    suspend fun sendVerificationEmail() {
        val user = requireNotNull(auth?.currentUser) { "Sign in before requesting email verification" }
        user.sendEmailVerification().await()
    }

    suspend fun signInWithGoogle(activity: Activity) {
        val firebaseAuth = requireFirebase()
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        require(webClientId.isNotBlank()) {
            "Google Sign-In is not configured. Set LUANET_GOOGLE_WEB_CLIENT_ID for this build."
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val response = CredentialManager.create(context).getCredential(activity, request)
        val googleCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        firebaseAuth.signInWithCredential(firebaseCredential).await()
    }

    suspend fun signInWithGitHub(activity: Activity) {
        val firebaseAuth = requireFirebase()
        val provider = OAuthProvider.newBuilder("github.com")
            .setScopes(listOf("read:user", "user:email"))
            .build()
        firebaseAuth.startActivityForSignInWithProvider(activity, provider).await()
    }

    fun signOut() {
        auth?.signOut()
    }

    private fun requireFirebase(): FirebaseAuth =
        auth ?: error("Firebase is not configured. Add google-services.json.")

    private fun com.google.firebase.auth.FirebaseUser.hasTrustedOAuthProvider(): Boolean =
        providerData.any { it.providerId == "google.com" || it.providerId == "github.com" }
}
