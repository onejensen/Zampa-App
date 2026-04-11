package com.sozolab.zampa.ui.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class LegalType { PRIVACY_POLICY, TERMS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(type: LegalType, onBack: () -> Unit) {
    val title = if (type == LegalType.PRIVACY_POLICY) "Política de Privacidad" else "Términos y Condiciones"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val content = if (type == LegalType.PRIVACY_POLICY) privacyPolicyText else termsText
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content.forEach { (heading, body) ->
                if (heading != null) {
                    Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Contenido legal ──

private val privacyPolicyText: List<Pair<String?, String>> = listOf(
    null to "Última actualización: enero 2025",
    "1. Responsable del tratamiento" to
        "SOZOLAB S.L. es la entidad responsable del tratamiento de los datos personales recogidos a través de la aplicación Zampa.",
    "2. Datos que recogemos" to
        "Recogemos los datos que nos proporcionas al registrarte (nombre, correo electrónico, teléfono), datos de uso de la aplicación, " +
        "tu ubicación aproximada para mostrar restaurantes cercanos (solo cuando la app está en uso y con tu permiso), " +
        "y el token del dispositivo para el envío de notificaciones push.",
    "3. Finalidad del tratamiento" to
        "Utilizamos tus datos para:\n" +
        "• Gestionar tu cuenta y autenticación.\n" +
        "• Mostrarte ofertas gastronómicas cercanas.\n" +
        "• Enviarte notificaciones sobre nuevos menús de tus restaurantes favoritos (solo si lo permites).\n" +
        "• Mejorar el servicio mediante análisis agregado y anónimo.",
    "4. Base legal" to
        "El tratamiento se basa en la ejecución del contrato de uso de la aplicación (art. 6.1.b RGPD) y, " +
        "en el caso de las notificaciones push, en tu consentimiento explícito (art. 6.1.a RGPD).",
    "5. Conservación de datos" to
        "Conservamos tus datos mientras mantengas la cuenta activa. Puedes solicitar la eliminación de tu cuenta y datos en cualquier momento.",
    "6. Tus derechos" to
        "Tienes derecho a acceder, rectificar, suprimir, limitar u oponerte al tratamiento de tus datos, " +
        "así como a la portabilidad de los mismos. Puedes ejercerlos escribiendo a privacidad@zampa.app.",
    "7. Contacto" to
        "Para cualquier consulta sobre privacidad, escríbenos a privacidad@zampa.app."
)

private val termsText: List<Pair<String?, String>> = listOf(
    null to "Última actualización: enero 2025",
    "1. Aceptación de los términos" to
        "Al usar Zampa aceptas estos Términos y Condiciones. Si no estás de acuerdo, no uses la aplicación.",
    "2. Descripción del servicio" to
        "Zampa es una plataforma que conecta a establecimientos de hostelería (\"comercios\") con clientes que buscan " +
        "ofertas de menús del día y platos especiales. Los comercios son responsables de la veracidad de sus publicaciones.",
    "3. Registro y cuentas" to
        "Debes tener al menos 16 años para registrarte. Eres responsable de mantener la confidencialidad de tu contraseña. " +
        "Notifícanos inmediatamente si detectas un uso no autorizado de tu cuenta.",
    "4. Contenido de los comercios" to
        "Los precios, descripciones, horarios y disponibilidad de los menús son responsabilidad exclusiva de cada comercio. " +
        "Zampa no garantiza la disponibilidad de ninguna oferta publicada.",
    "5. Uso aceptable" to
        "Queda prohibido:\n" +
        "• Publicar información falsa o engañosa.\n" +
        "• Usar la plataforma para actividades ilegales.\n" +
        "• Intentar acceder a datos de otros usuarios.\n" +
        "• Realizar scraping o automatizar el acceso masivo a la plataforma.",
    "6. Propiedad intelectual" to
        "El diseño, código y marca de Zampa son propiedad de SOZOLAB S.L. El contenido publicado por los comercios " +
        "(fotos, descripciones) es propiedad de los respectivos titulares.",
    "7. Limitación de responsabilidad" to
        "Zampa actúa como intermediario. No somos responsables de la calidad de los alimentos, " +
        "errores en los precios publicados por los comercios, ni de problemas derivados del uso de servicios de terceros.",
    "8. Modificaciones" to
        "Podemos actualizar estos términos en cualquier momento. Te notificaremos los cambios significativos a través de la app.",
    "9. Contacto" to
        "Para cualquier consulta escríbenos a legal@zampa.app."
)
