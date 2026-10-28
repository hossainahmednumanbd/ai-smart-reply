package bd.app.aismartreply

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/** Google ID tokens are scoped to the backend's WEB OAuth client ID. No Gemini key in APK. */
object GoogleAuth {
    const val REQUEST_CODE = 8401
    const val WEB_CLIENT_ID = "656908487206-i6u8rt2jck0a5k9cbbc9v6pbr5ee2cab.apps.googleusercontent.com"
    fun client(ctx: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestIdToken(WEB_CLIENT_ID).build()
        return GoogleSignIn.getClient(ctx, options)
    }
    fun account(ctx: Context): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(ctx)
    fun signIn(activity: Activity) { DriveBackups.disconnect(activity); activity.startActivityForResult(client(activity).signInIntent, REQUEST_CODE) }
    fun handleResult(data: Intent?): Result<GoogleSignInAccount> = runCatching {
        GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            ?: error("Google account not available")
    }
    fun signOut(ctx: Context, complete: () -> Unit) {
        DriveBackups.disconnect(ctx)
        client(ctx).signOut().addOnCompleteListener { complete() }
    }
    /** Silent sign-in refreshes expired Google ID tokens when possible. */
    fun withIdToken(ctx: Context, callback: (Result<String>) -> Unit) {
        client(ctx).silentSignIn().addOnCompleteListener { task ->
            val result = runCatching {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.takeIf { it.isNotBlank() }
                    ?: error("Google ID token unavailable. Sign in again.")
            }
            callback(result)
        }
    }
}
