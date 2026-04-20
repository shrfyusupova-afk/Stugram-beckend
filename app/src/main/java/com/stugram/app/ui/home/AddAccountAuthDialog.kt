package com.stugram.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stugram.app.ui.LoginContent
import com.stugram.app.ui.LoginUiState
import com.stugram.app.ui.LoginViewModel
import com.stugram.app.ui.auth.components.LoadingOverlay
import com.stugram.app.ui.auth.register.RegisterContent
import com.stugram.app.ui.auth.register.RegisterUiState
import com.stugram.app.ui.auth.register.RegisterViewModel
import com.stugram.app.ui.theme.PremiumBg

@Composable
fun AddAccountAuthDialog(
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
    loginViewModel: LoginViewModel = viewModel(),
    registerViewModel: RegisterViewModel = viewModel()
) {
    var showRegister by remember { mutableStateOf(false) }
    val loginState by loginViewModel.uiState.collectAsState()
    val registerState by registerViewModel.uiState.collectAsState()

    val loginSuccess = loginState is LoginUiState.Success
    val registerSuccess = registerState is RegisterUiState.Success
    val loginLoading = loginState is LoginUiState.Loading
    val registerLoading = registerState is RegisterUiState.Loading

    LaunchedEffect(loginSuccess, registerSuccess) {
        if (loginSuccess || registerSuccess) {
            onFinished()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumBg)
        ) {
            if (loginLoading || registerLoading) {
                LoadingOverlay(fullScreen = true, message = "Signing you in")
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = showRegister,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn())
                                .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut())
                        } else {
                            (slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn())
                                .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut())
                        }.using(SizeTransform(clip = false))
                    },
                    label = "add_account_auth"
                ) { targetRegister ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        if (targetRegister) {
                            RegisterContent(
                                viewModel = registerViewModel,
                                uiState = registerState,
                                onNavigateToLogin = { showRegister = false }
                            )
                        } else {
                            LoginContent(
                                viewModel = loginViewModel,
                                uiState = loginState,
                                onNavigateToRegister = { showRegister = true },
                                contentColor = Color.White,
                                isDarkMode = isDarkMode
                            )
                        }
                    }
                }
            }
        }
    }
}
