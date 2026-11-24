package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.auth.AdminAuthStatus
import com.example.auth.AdminCloudVerification
import com.example.auth.CloudAccountProfile
import com.example.ui.theme.AdminCoral
import com.example.ui.theme.Cream100
import com.example.ui.theme.Cream200
import com.example.ui.theme.Cream50
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Emerald800
import com.example.ui.theme.Emerald900
import com.example.ui.theme.Emerald950
import com.example.ui.theme.Gold400
import com.example.ui.theme.Ink500
import com.example.ui.theme.Ink700
import com.example.ui.theme.Ink900
import kotlinx.coroutines.launch

@Composable
fun AdminLoginScreen(
    authStatus: AdminAuthStatus,
    authError: String?,
    onBack: () -> Unit,
    onSetupFirstAdmin: (name: String, email: String, passcode: String, confirm: String) -> Unit,
    onLoginAdmin: (passcode: String) -> Unit,
    onClearError: () -> Unit,
    useCloudAuth: Boolean = false,
    cloudAccount: CloudAccountProfile? = null,
    adminCloudVerification: AdminCloudVerification = AdminCloudVerification.NotSignedIn,
    isAdminRoleChecking: Boolean = false,
    onSignInWithGoogleAdmin: (suspend (Context) -> Unit)? = null,
    onReverifyCloudAdmin: () -> Unit = {},
    onSignOutCloudAccount: () -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activityContext = remember(context) { context.findActivity() ?: context }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isSigningInGoogle by remember { mutableStateOf(false) }

    val isFirstTimeSetup = authStatus is AdminAuthStatus.NeedsFirstTimeSetup
    val lockedInfo = authStatus as? AdminAuthStatus.Locked

    var adminName by remember { mutableStateOf("Library Admin") }
    var adminEmail by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }
    var confirmPasscode by remember { mutableStateOf("") }
    var showPasscode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(listOf(Emerald950, Emerald900)))
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("admin_login_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = Cream50,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (useCloudAuth) {
                    "Cloud Admin Sign In"
                } else if (isFirstTimeSetup) {
                    "Admin Access Setup"
                } else {
                    "Administrator Sign In"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Cream50,
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Crest Icon
            Surface(
                color = Emerald800,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, Gold400.copy(alpha = 0.65f)),
                modifier = Modifier.size(76.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            useCloudAuth -> Icons.Rounded.VerifiedUser
                            isFirstTimeSetup -> Icons.Rounded.AdminPanelSettings
                            else -> Icons.Rounded.Lock
                        },
                        contentDescription = null,
                        tint = Gold400,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when {
                    useCloudAuth -> "Firebase Administrator Console"
                    isFirstTimeSetup -> "Claim Administrator Access"
                    else -> "Protected Catalog Console"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Cream50,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when {
                    useCloudAuth -> "Sign in with your Google administrator account. Access is verified on the server by Firestore rules and your admin role record."
                    isFirstTimeSetup -> "Set a personal administrator passcode (6+ characters) to authorize PDF uploads, edits, and category management."
                    else -> {
                        val who = lockedInfo?.adminName ?: "Administrator"
                        "Enter the passcode for $who to unlock catalog management."
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Emerald100.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main Cream Card
            Surface(
                color = Cream50,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (useCloudAuth) {
                        val deniedState = adminCloudVerification as? AdminCloudVerification.Denied
                        val activeProfile = cloudAccount ?: deniedState?.profile
                        val dbId = remember(context) {
                            try {
                                context.getString(R.string.firestore_database_id)
                            } catch (_: Exception) {
                                "ai-studio-android-bookhub-53de5640-7861-496e-8b28-09fea7f6f7e1"
                            }
                        }

                        if (isAdminRoleChecking || isSigningInGoogle) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator(color = Emerald700)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isSigningInGoogle) {
                                        "Connecting Google Account..."
                                    } else {
                                        "Verifying Administrator Role in Firestore..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink700,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else if (deniedState != null && activeProfile != null) {
                            Surface(
                                color = Cream100,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Cream200),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Signed-in Google Account",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Emerald700,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = activeProfile.displayName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Ink900,
                                    )
                                    if (!activeProfile.email.isNullOrBlank()) {
                                        Text(
                                            text = activeProfile.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Ink700,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "UID: ${activeProfile.uid}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = Ink500,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(activeProfile.uid))
                                                onShowMessage("Admin UID copied to clipboard")
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = "Copy UID",
                                                tint = Emerald700,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                color = AdminCoral.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, AdminCoral.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Admin Privilege Required",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = AdminCoral,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = deniedState.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Ink900,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "To authorize this account in Firebase Console:\nOpen Firestore database \"$dbId\" -> Collection \"admins\" -> Create document ID \"${activeProfile.uid}\" with boolean field active = true.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Ink700,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = onReverifyCloudAdmin,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald700,
                                    contentColor = Cream50,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("admin_reverify_button"),
                            ) {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Re-check Admin Status")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = onSignOutCloudAccount,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("admin_switch_account_button"),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = Ink700, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign Out / Switch Account", color = Ink900)
                            }
                        } else {
                            Text(
                                text = "Administrator Authentication",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Ink900,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Use Google Sign-In to authenticate against Firebase and unlock Google Drive uploads and Cloud Firestore catalog publishing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink700,
                            )

                            if (!authError.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = AdminCoral.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AdminCoral.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = authError,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = AdminCoral,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    if (onSignInWithGoogleAdmin != null && !isSigningInGoogle) {
                                        isSigningInGoogle = true
                                        onClearError()
                                        scope.launch {
                                            onSignInWithGoogleAdmin(activityContext)
                                            isSigningInGoogle = false
                                        }
                                    }
                                },
                                enabled = !isSigningInGoogle && onSignInWithGoogleAdmin != null,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald700,
                                    contentColor = Cream50,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("admin_google_signin_button"),
                            ) {
                                Icon(Icons.Rounded.VerifiedUser, null, modifier = Modifier.size(19.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign in with Google",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    } else {
                        if (isFirstTimeSetup) {
                            OutlinedTextField(
                                value = adminName,
                                onValueChange = {
                                    adminName = it
                                    onClearError()
                                },
                                label = { Text("Administrator Name") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Person, null, tint = Emerald700)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = adminFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_setup_name_input"),
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = adminEmail,
                                onValueChange = {
                                    adminEmail = it
                                    onClearError()
                                },
                                label = { Text("Admin Email (Optional)") },
                                placeholder = { Text("Used for administrator profile") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Email, null, tint = Emerald700)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                shape = RoundedCornerShape(14.dp),
                                colors = adminFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_setup_email_input"),
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        } else if (!lockedInfo?.adminEmail.isNullOrBlank()) {
                            Surface(
                                color = Cream100,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Person, null, tint = Emerald700, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = lockedInfo?.adminName ?: "Administrator",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Ink900,
                                        )
                                        Text(
                                            text = lockedInfo?.adminEmail ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Ink500,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        OutlinedTextField(
                            value = passcode,
                            onValueChange = {
                                passcode = it
                                onClearError()
                            },
                            label = { Text(if (isFirstTimeSetup) "Create Passcode (min 6 chars)" else "Administrator Passcode") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Key, null, tint = Emerald700)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPasscode = !showPasscode }) {
                                    Icon(
                                        imageVector = if (showPasscode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showPasscode) "Hide passcode" else "Show passcode",
                                        tint = Ink500,
                                    )
                                }
                            },
                            visualTransformation = if (showPasscode) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(14.dp),
                            colors = adminFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_passcode_input"),
                        )

                        if (isFirstTimeSetup) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = confirmPasscode,
                                onValueChange = {
                                    confirmPasscode = it
                                    onClearError()
                                },
                                label = { Text("Confirm Passcode") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Key, null, tint = Emerald700)
                                },
                                visualTransformation = if (showPasscode) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(14.dp),
                                colors = adminFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_confirm_passcode_input"),
                            )
                        }

                        if (!authError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = AdminCoral.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AdminCoral.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = authError,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = AdminCoral,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (isFirstTimeSetup) {
                                    onSetupFirstAdmin(adminName, adminEmail, passcode, confirmPasscode)
                                } else {
                                    onLoginAdmin(passcode)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald700,
                                contentColor = Cream50,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("admin_login_submit_button"),
                        ) {
                            Text(
                                text = if (isFirstTimeSetup) "Activate Admin Access" else "Unlock Admin Panel",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Transparent Security Architecture Disclosure
            Surface(
                color = Emerald800.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Gold400.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = Gold400,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (useCloudAuth) "Server-Enforced Security" else "Local Security Scope",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Gold400,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (useCloudAuth) {
                                "Administrator writes and draft visibility are enforced by Cloud Firestore Security Rules using Firebase Auth custom claims or /admins/{uid} allowlist documents."
                            } else {
                                "Passcodes are hashed on-device with salted PBKDF2-HMAC-SHA256 and sessions are verified before every mutation."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Emerald100.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun adminFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Cream100,
    unfocusedContainerColor = Cream100,
    focusedBorderColor = Emerald700,
    unfocusedBorderColor = Cream200,
    focusedTextColor = Ink900,
    unfocusedTextColor = Ink900,
    focusedLabelColor = Emerald700,
    unfocusedLabelColor = Ink700,
    cursorColor = Emerald700,
)
