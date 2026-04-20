package com.stugram.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stugram.app.R
import com.stugram.app.ui.auth.components.*
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5)) // Rasmdagi ochiq kulrang fon
    ) {
        // --- 1. SETKA (GRID LINE) FON ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStep = 50.dp.toPx()
            val gridColor = Color(0xFFE2E8F0)
            
            // Vertikal chiziqlar
            for (x in 0..size.width.toInt() step gridStep.toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            // Gorizontal chiziqlar
            for (y in 0..size.height.toInt() step gridStep.toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
        }

        // --- 2. ASOSIY OQ SURFACE (KATTA RADIUSLI) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp)
                .background(
                    color = Color.White.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(topStart = 80.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 80.dp)
                )
        )

        // --- 3. DEKORATIV YUMSHOQ DOG'LAR ---
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-50).dp)
                .blur(80.dp)
                .background(Color(0xFFD1D5DB).copy(alpha = 0.3f), CircleShape)
        )

        // --- CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Logo qo'shish (Circle olib tashlandi)
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(60.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Yuqoridagi kichik "+" belgilari
            Text(
                text = "+",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.Black.copy(alpha = 0.15f)
                ),
                modifier = Modifier.offset(x = (-10).dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Matn animatsiyasi
            AnimatedVisibility(
                visible = startAnimation,
                enter = fadeIn(tween(1000)) + slideInHorizontally(tween(1000)) { -40 }
            ) {
                Column {
                    Text(
                        text = "Liquid",
                        style = TextStyle(
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(0xFF1A1C1E),
                            letterSpacing = (-2).sp
                        )
                    )
                    Text(
                        text = "glass",
                        style = TextStyle(
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF64748B),
                            letterSpacing = (-1).sp
                        ),
                        modifier = Modifier.offset(y = (-15).dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // --- LIQUID GLASS PLUS BUTTON ---
            AnimatedVisibility(
                visible = startAnimation,
                enter = scaleIn(tween(800, delayMillis = 500)) + fadeIn()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidGlassPlusButton(
                        onClick = {
                            isLoading = true
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }

        // --- LOADING STATE ---
        if (isLoading) {
            LoadingOverlay()
            LaunchedEffect(Unit) {
                delay(2000)
                onLoginClick()
            }
        }
    }
}
