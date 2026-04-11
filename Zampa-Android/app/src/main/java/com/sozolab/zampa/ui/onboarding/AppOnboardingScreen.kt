package com.sozolab.zampa.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sozolab.zampa.ui.theme.Primary
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color
)

private val clientPages = listOf(
    OnboardingPage(
        Icons.Default.Restaurant,
        "Descubre el menú del día",
        "Explora la oferta diaria cerca de ti.",
        Primary
    ),
    OnboardingPage(
        Icons.Default.Tune,
        "Filtra a tu medida",
        "Por distancia, tipo de cocina o si el local está abierto ahora mismo.",
        Color(0xFF2196F3)
    ),
    OnboardingPage(
        Icons.Default.Favorite,
        "Guarda tus favoritos",
        "Marca los restaurantes que más te gustan para seguirlos fácilmente.",
        Color(0xFFE91E63)
    ),
    OnboardingPage(
        Icons.Default.Notifications,
        "No te pierdas nada",
        "Activa las notificaciones y entérate de las nuevas ofertas al instante.",
        Color(0xFF9C27B0)
    )
)

private val merchantPages = listOf(
    OnboardingPage(
        Icons.Default.AddCircle,
        "Publica tu oferta del día",
        "Crea tu menú diario en segundos y llega a clientes cerca de tu local.",
        Primary
    ),
    OnboardingPage(
        Icons.Default.LocationOn,
        "Visibilidad local",
        "Aparece en el feed de usuarios cercanos exactamente cuando más te necesitan.",
        Color(0xFF2196F3)
    ),
    OnboardingPage(
        Icons.Default.BarChart,
        "Consulta tus estadísticas",
        "Revisa vistas, clics y favoritos diarios desde tu perfil de restaurante.",
        Color(0xFF4CAF50)
    ),
    OnboardingPage(
        Icons.Default.Star,
        "Hazte Pro",
        "Con Zampa Pro publica varios menús al día y destaca con el badge «Destacado».",
        Color(0xFFFFC107)
    )
)

@Composable
fun AppOnboardingScreen(
    isMerchant: Boolean,
    onFinish: () -> Unit
) {
    val pages = if (isMerchant) merchantPages else clientPages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (pagerState.currentPage < pages.size - 1) {
                    TextButton(onClick = onFinish) {
                        Text(
                            "Saltar",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    Spacer(Modifier.height(40.dp))
                }
            }

            // Swipeable pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { index ->
                val page = pages[index]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(page.color.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            tint = page.color,
                            modifier = Modifier.size(72.dp)
                        )
                    }

                    Spacer(Modifier.height(40.dp))

                    Text(
                        text = page.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = page.description,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp
                    )
                }
            }

            // Dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 28.dp)
            ) {
                repeat(pages.size) { i ->
                    val width by animateDpAsState(
                        targetValue = if (i == pagerState.currentPage) 24.dp else 8.dp,
                        label = "dot_$i"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .background(
                                color = if (i == pagerState.currentPage) Primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            // CTA button
            Button(
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(
                    text = if (pagerState.currentPage < pages.size - 1) "Siguiente" else "¡Empezar!",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
