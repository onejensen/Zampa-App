package com.sozolab.zampa.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sozolab.zampa.R
import com.sozolab.zampa.ui.theme.Sora
import kotlinx.coroutines.delay

/**
 * Splash al estilo iOS: fondo naranja brand + logo blanco centrado + "Zampa"
 * en blanco debajo. Se muestra brevemente al arrancar la app y luego cede paso
 * al NavHost real (Auth o Main).
 *
 * El delay de 700ms es para que el usuario perciba el branding sin que la app
 * se sienta lenta. Si el cold start de Compose es más lento, el splash se
 * mostrará mientras el sistema mismo lo gestiona.
 */
@Composable
fun ZampaSplash(onFinish: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(700)
        onFinish()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFAA1C)), // naranja brand
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.logo_zampa),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
            Text(
                text = "Zampa",
                color = Color.White,
                fontFamily = Sora,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
