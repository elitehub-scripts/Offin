package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SimCardAlert
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AppPreferences
import com.example.ui.OnboardingUiState
import com.example.ui.UsernameAvailability
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSignal

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    appPreferences: AppPreferences,
    onUsernameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onToggleRecoveryMode: (Boolean) -> Unit,
    onRecoveryUsernameChange: (String) -> Unit,
    onRecoveryKeyChange: (String) -> Unit,
    onSubmitRegister: () -> Unit,
    onSubmitRecover: () -> Unit,
    onSaveServerConfig: (String, String, String, String, String) -> Unit,
    onOpenOfflineNearby: () -> Unit
) {
    var showServerDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_worldlink_hero),
                    contentDescription = "WorldLink Global Mesh",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { showServerDialog = true },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.testTag("onboarding_server_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Dns,
                                contentDescription = "Server Config",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Server", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "WORLDLINK",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Zero-PII Guarantee Strip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.SimCardAlert,
                        contentDescription = null,
                        tint = EmeraldSignal,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.onboarding_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Registration or Recovery Form Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!state.isRecoveryMode) {
                        Text(
                            text = "Claim Your Global Identity",
                            style = MaterialTheme.typography.titleLarge
                        )
                        OutlinedTextField(
                            value = state.usernameInput,
                            onValueChange = onUsernameChange,
                            label = { Text(stringResource(R.string.username_label)) },
                            placeholder = { Text(stringResource(R.string.username_hint)) },
                            leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                            trailingIcon = {
                                when (state.availability) {
                                    UsernameAvailability.CHECKING -> CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    UsernameAvailability.AVAILABLE -> Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Available",
                                        tint = EmeraldSignal
                                    )
                                    UsernameAvailability.TAKEN,
                                    UsernameAvailability.INVALID,
                                    UsernameAvailability.SERVER_ERROR -> Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = "Unavailable",
                                        tint = CoralAlert
                                    )
                                    UsernameAvailability.IDLE -> Unit
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_username_input")
                        )

                        AnimatedVisibility(visible = !state.availabilityMessage.isNullOrBlank()) {
                            val color = when (state.availability) {
                                UsernameAvailability.AVAILABLE -> EmeraldSignal
                                UsernameAvailability.CHECKING -> MaterialTheme.colorScheme.primary
                                else -> CoralAlert
                            }
                            Text(
                                text = state.availabilityMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = color
                            )
                        }

                        OutlinedTextField(
                            value = state.displayNameInput,
                            onValueChange = onDisplayNameChange,
                            label = { Text(stringResource(R.string.display_name_label)) },
                            placeholder = { Text(stringResource(R.string.display_name_hint)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_displayname_input")
                        )

                        if (!state.errorMessage.isNullOrBlank()) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = CoralAlert
                            )
                        }

                        Button(
                            onClick = onSubmitRegister,
                            enabled = !state.isSubmitting && state.availability == UsernameAvailability.AVAILABLE,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("onboarding_register_button")
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(stringResource(R.string.create_identity_cta))
                            }
                        }

                        TextButton(
                            onClick = { onToggleRecoveryMode(true) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.restore_identity_cta))
                        }
                    } else {
                        Text(
                            text = "Recover Existing @username",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Enter your permanent @username and the 24-character Recovery Key generated when you first registered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = state.recoveryUsernameInput,
                            onValueChange = onRecoveryUsernameChange,
                            label = { Text("Your @username") },
                            leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("recover_username_input")
                        )
                        OutlinedTextField(
                            value = state.recoveryKeyInput,
                            onValueChange = onRecoveryKeyChange,
                            label = { Text("Device Recovery Key") },
                            placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX-XXXX") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("recover_key_input")
                        )
                        if (!state.errorMessage.isNullOrBlank()) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = CoralAlert
                            )
                        }
                        Button(
                            onClick = onSubmitRecover,
                            enabled = !state.isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("recover_submit_button")
                        ) {
                            Text("Restore Identity")
                        }
                        TextButton(
                            onClick = { onToggleRecoveryMode(false) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Create a New @username Instead")
                        }
                    }
                }
            }

            // Offline Nearby Escape Hatch
            OutlinedButton(
                onClick = onOpenOfflineNearby,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("onboarding_nearby_button")
            ) {
                Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use Offline Nearby Mode (No Internet Needed)")
            }

            Text(
                text = "Active endpoint: ${appPreferences.serverBaseUrl}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (showServerDialog) {
        ServerConfigDialog(
            currentPrefs = appPreferences,
            statusNote = state.serverCheckStatus,
            onDismiss = { showServerDialog = false },
            onSaveAndTest = { srv, stun, turn, user, pass ->
                onSaveServerConfig(srv, stun, turn, user, pass)
            }
        )
    }
}
