package com.sozolab.zampa.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sozolab.zampa.data.model.User
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pantalla que se muestra cuando un usuario inicia sesión y tiene la cuenta
 * marcada como pendiente de eliminación. Le permite recuperar la cuenta o
 * cerrar sesión sin deshacer el borrado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDeletionRecoveryScreen(
    user: User,
    onRecover: (onError: (String) -> Unit) -> Unit,
    onLogout: () -> Unit,
) {
    var isRecovering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val purgeDateText = remember(user.scheduledPurgeAt) {
        val date = user.scheduledPurgeAt?.toDate()
        if (date != null) {
            SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "ES"))
                .format(date)
        } else {
            "pronto"
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(72.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Cuenta pendiente\nde eliminación",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Tu cuenta se eliminará el",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = purgeDateText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Si quieres conservarla, pulsa Recuperar cuenta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (isRecovering) return@Button
                    isRecovering = true
                    onRecover { err ->
                        isRecovering = false
                        errorMessage = err
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRecovering
            ) {
                if (isRecovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Recuperar cuenta", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onLogout,
                enabled = !isRecovering
            ) {
                Text("Cerrar sesión")
            }

            Spacer(Modifier.height(32.dp))
        }

        // Toast/snackbar de error
        LaunchedEffect(errorMessage) {
            val msg = errorMessage
            if (msg != null) {
                snackbarHostState.showSnackbar(msg)
                errorMessage = null
            }
        }
    }
}
