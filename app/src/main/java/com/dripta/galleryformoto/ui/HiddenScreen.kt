package com.dripta.galleryformoto.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dripta.galleryformoto.data.MediaItem

@Composable
fun HiddenGate(onUnlocked: () -> Unit, onCancelled: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var status by remember { mutableStateOf("Locking...") }

    DisposableEffect(Unit) {
        if (activity == null) {
            onCancelled()
            return@DisposableEffect onDispose {}
        }
        val manager = BiometricManager.from(activity)
        val canAuth = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            status = "No screen lock or biometric set up on this device. Set one up in system settings to use the Hidden album."
            return@DisposableEffect onDispose {}
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onCancelled()
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Hidden")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(promptInfo)

        onDispose {}
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = status, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (activity == null || status.startsWith("No screen lock")) {
            Button(onClick = onCancelled, modifier = Modifier.padding(top = 16.dp)) {
                Text("Go back")
            }
        }
    }
}

@Composable
fun HiddenScreen(
    items: List<MediaItem>,
    viewModel: GalleryViewModel,
    onItemClick: (List<MediaItem>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    MediaListScreen(
        title = "Hidden",
        items = items,
        viewModel = viewModel,
        mode = ScreenMode.HIDDEN,
        onItemClick = onItemClick,
        modifier = modifier
    )
}
