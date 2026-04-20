package com.stugram.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stugram.app.ui.LoginUiState
import com.stugram.app.ui.LoginContent
import com.stugram.app.ui.LoginViewModel
import com.stugram.app.ui.auth.components.*
import com.stugram.app.ui.auth.register.RegisterUiState
import com.stugram.app.ui.auth.register.RegisterContent
import com.stugram.app.ui.auth.register.RegisterViewModel
import com.stugram.app.ui.theme.*

@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(1) } // 0: Login, 1: Register
    
    val loginViewModel: LoginViewModel = viewModel()
    val registerViewModel: RegisterViewModel = viewModel()
    
    val loginState by loginViewModel.uiState.collectAsState()
    val registerState by registerViewModel.uiState.collectAsState()
    val loginSuccess = loginState is LoginUiState.Success
    val registerSuccess = registerState is RegisterUiState.Success
    val loginLoading = loginState is LoginUiState.Loading
    val registerLoading = registerState is RegisterUiState.Loading

    // Navigate to home on success
    LaunchedEffect(loginSuccess, registerSuccess) {
        if (loginSuccess || registerSuccess) {
            onNavigateToHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBg)
    ) {
        // Global Loading Overlay
        if (loginLoading || registerLoading) {
            LoadingOverlay()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // 1. Premium Logo with Glow
            PremiumLogo()

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Welcome Header
            Text(
                text = "Xush kelibsiz",
                color = PremiumTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            
            Text(
                text = "Talabalar uchun zamonaviy fintech-style\nijtimoiy tarmoq platformasi",
                color = PremiumTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 3. Login / Register Tab Toggle
            TabSwitch(
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Smooth Content Switching
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(500)) { it } + fadeIn(animationSpec = tween(500)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(500)) { -it } + fadeOut(animationSpec = tween(500)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(500)) { -it } + fadeIn(animationSpec = tween(500)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(500)) { it } + fadeOut(animationSpec = tween(500)))
                    }.using(SizeTransform(clip = false))
                },
                label = "auth_content"
            ) { targetTab ->
                if (targetTab == 0) {
                    LoginContent(
                        viewModel = loginViewModel,
                        uiState = loginState,
                        onNavigateToRegister = { selectedTab = 1 },
                        contentColor = PremiumTextPrimary,
                        isDarkMode = true
                    )
                } else {
                    RegisterContent(
                        viewModel = registerViewModel,
                        uiState = registerState,
                        onNavigateToLogin = { selectedTab = 0 }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
