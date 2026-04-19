package com.stugram.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import com.stugram.app.R
import com.stugram.app.ui.auth.components.PremiumButton
import com.stugram.app.ui.auth.components.PremiumTextField
import com.stugram.app.ui.auth.components.PremiumSocialButton
import com.stugram.app.ui.theme.PremiumBlue
import com.stugram.app.ui.theme.PremiumTextSecondary

@Composable
fun LoginContent(
    viewModel: LoginViewModel,
    uiState: LoginUiState,
    onNavigateToRegister: () -> Unit,
    contentColor: Color,
    isDarkMode: Boolean
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resetIdentity by remember { mutableStateOf("") }
    var resetToken by remember { mutableStateOf("") }
    var resetNewPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PremiumTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            label = "Username or Email",
            placeholder = "example@gmail.com",
            leadingIcon = Icons.Default.AlternateEmail,
            isError = uiState.error != null
        )

        Spacer(modifier = Modifier.height(16.dp))

        PremiumTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Password",
            placeholder = "Enter your password",
            leadingIcon = Icons.Default.Lock,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = PremiumTextSecondary.copy(0.6f)
                    )
                }
            }
        )

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(
                onClick = { viewModel.togglePasswordResetModal(true) }
            ) {
                Text(
                    "Forgot password?",
                    color = PremiumBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PremiumButton(
            text = "Sign In",
            onClick = viewModel::login,
            isLoading = uiState.isLoading
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = PremiumTextSecondary.copy(0.1f))
            Text(
                text = "or",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = PremiumTextSecondary,
                fontSize = 12.sp
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = PremiumTextSecondary.copy(0.1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        PremiumSocialButton(
            painter = painterResource(id = R.drawable.ic_google_g),
            text = "Continue with Google",
            onClick = {
                scope.launch {
                    try {
                        val credentialManager = CredentialManager.create(context)
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(context.getString(R.string.google_web_client_id))
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()

                        val result = credentialManager.getCredential(context, request)
                        val credential = result.credential
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        viewModel.loginWithGoogleIdToken(idToken)
                    } catch (e: Exception) {
                        viewModel.showInlineError(e.localizedMessage ?: "Google sign-in failed")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onNavigateToRegister) {
            Text(
                text = "Don't have an account? Register",
                color = contentColor.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        uiState.error?.let {
            Text(
                text = it,
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    if (uiState.showPasswordResetModal) {
        AlertDialog(
            onDismissRequest = { viewModel.togglePasswordResetModal(false) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (resetToken.isBlank()) {
                            viewModel.requestPasswordReset(resetIdentity)
                        } else {
                            viewModel.resetPassword(resetToken, resetNewPassword)
                        }
                    }
                ) { Text(if (resetToken.isBlank()) "Send reset" else "Reset password") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.togglePasswordResetModal(false) }) { Text("Cancel") }
            },
            title = { Text("Password reset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumTextField(
                        value = resetIdentity,
                        onValueChange = { resetIdentity = it },
                        label = "Email",
                        placeholder = "example@gmail.com",
                        leadingIcon = Icons.Default.AlternateEmail,
                        isError = false
                    )
                    PremiumTextField(
                        value = resetToken,
                        onValueChange = { resetToken = it },
                        label = "Reset token",
                        placeholder = "From email (or dev token)",
                        leadingIcon = Icons.Default.Lock,
                        isError = false
                    )
                    PremiumTextField(
                        value = resetNewPassword,
                        onValueChange = { resetNewPassword = it },
                        label = "New password",
                        placeholder = "Enter new password",
                        leadingIcon = Icons.Default.Lock,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = false
                    )
                }
            }
        )
    }
}
