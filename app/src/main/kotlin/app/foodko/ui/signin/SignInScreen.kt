package app.foodko.ui.signin

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.foodko.BuildConfig
import app.foodko.Mock
import app.foodko.R
import app.foodko.data.AuthRepository
import app.foodko.data.SignInResult
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(auth: AuthRepository) {
    val context = LocalContext.current
    val webClientId = stringResource(R.string.default_web_client_id)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var noAccount by remember { mutableStateOf(false) }
    val noAccountText = stringResource(R.string.sign_in_no_account)
    val failedText = stringResource(R.string.sign_in_failed)

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.sign_in_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            if (busy) {
                CircularProgressIndicator()
            } else if (BuildConfig.MOCK) {
                Text(stringResource(R.string.mock_banner), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Mock.users.forEach { user ->
                    Button(onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val result = auth.signInMock(user.email, user.name)
                            if (result is SignInResult.Failed) error = failedText + "\n" + (result.error.message ?: "")
                            busy = false
                        }
                    }) { Text(stringResource(R.string.mock_sign_in_as, user.name)) }
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                Button(onClick = {
                    busy = true
                    error = null
                    noAccount = false
                    scope.launch {
                        when (val result = auth.signInWithGoogle(context, webClientId)) {
                            SignInResult.Success, SignInResult.Cancelled -> Unit
                            SignInResult.NoGoogleAccount -> { error = noAccountText; noAccount = true }
                            is SignInResult.Failed -> error = failedText + "\n" + (result.error.message ?: "")
                        }
                        busy = false
                    }
                }) { Text(stringResource(R.string.sign_in_google)) }
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            if (noAccount) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    // Opens the system "Add account" flow filtered to Google accounts.
                    context.startActivity(
                        Intent(Settings.ACTION_ADD_ACCOUNT)
                            .putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.add_google_account)) }
            }
        }
    }
}

/** Shown when Firestore refused access: the Google account isn't on the allowlist. */
@Composable
fun AccessDeniedScreen(email: String?, onSignOut: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.access_denied_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.access_denied_body, email ?: "?"),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
        }
    }
}
