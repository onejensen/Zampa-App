package com.sozolab.zampa.ui.profile

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.sozolab.zampa.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User?,
    pendingPhotoBitmap: Bitmap? = null,
    isMerchant: Boolean,
    onLogout: () -> Unit,
    onUserNameUpdated: (String) -> Unit = {},
    onProfilePhotoUpdated: (Bitmap, ByteArray) -> Unit = { _, _ -> },
    onNavigateToDietaryPreferences: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToNotificationPreferences: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf("") }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun uploadBitmap(bitmap: Bitmap) {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        onProfilePhotoUpdated(bitmap, stream.toByteArray())
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> if (bitmap != null) rawBitmap = bitmap }

    // Requests CAMERA permission at runtime; launches camera only if granted
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) cameraLauncher.launch(null)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bmp = if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(context.contentResolver, it)
                )
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            }
            rawBitmap = bmp
        }
    }

    // Show crop view fullscreen when a raw image is picked
    if (rawBitmap != null) {
        Dialog(
            onDismissRequest = { rawBitmap = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            CropImageView(
                sourceBitmap = rawBitmap!!,
                onConfirm = { cropped ->
                    rawBitmap = null
                    uploadBitmap(cropped)
                },
                onCancel = { rawBitmap = null }
            )
        }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text("Cambiar foto de perfil") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cámara") }
                    TextButton(
                        onClick = { galleryLauncher.launch("image/*"); showPhotoSourceDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Galería") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Editar nombre") },
            text = {
                OutlinedTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    label = { Text("Nombre a mostrar") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = editNameText.trim()
                        if (trimmed.isNotEmpty()) {
                            onUserNameUpdated(trimmed)
                            showEditNameDialog = false
                        }
                    },
                    enabled = editNameText.trim().isNotEmpty()
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancelar") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Header: Avatar + User Info
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar with camera overlay
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.clickable { showPhotoSourceDialog = true }
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            pendingPhotoBitmap != null -> Image(
                                bitmap = pendingPhotoBitmap.asImageBitmap(),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            user?.photoUrl != null -> AsyncImage(
                                model = user.photoUrl,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            else -> Icon(
                                Icons.Default.Person,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = { showPhotoSourceDialog = true },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Cambiar foto",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                    user?.name ?: "Usuario",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    user?.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    editNameText = user?.name ?: ""
                    showEditNameDialog = true
                }) {
                    Text("Editar nombre", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(4.dp))
                
                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (isMerchant) "Restaurante" else "Cliente")
                    },
                    leadingIcon = {
                        Icon(
                            if (isMerchant) Icons.Default.Storefront else Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
        
        // Section: Mi Actividad
        item {
            SectionHeader("Mi Actividad")
        }
        item {
            ProfileMenuItem(
                icon = Icons.Default.Favorite,
                title = "Favoritos",
                iconTint = MaterialTheme.colorScheme.error,
                onClick = {}
            )
        }
        item {
            ProfileMenuItem(
                icon = Icons.Default.History,
                title = "Historial",
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToHistory
            )
        }
        
        // Section: Preferencias
        item {
            SectionHeader("Preferencias")
        }
        item {
            ProfileMenuItem(
                icon = Icons.Default.Eco,
                title = "Preferencias Alimentarias",
                iconTint = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToDietaryPreferences
            )
        }
        item {
            ProfileMenuItem(
                icon = Icons.Default.Notifications,
                title = "Notificaciones",
                iconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToNotificationPreferences
            )
        }
        if (isMerchant) {
            item { SectionHeader("Mi Restaurante") }
            item {
                ProfileMenuItem(
                    icon = Icons.Default.BarChart,
                    title = "Estadísticas",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToStats
                )
            }
            item {
                ProfileMenuItem(
                    icon = Icons.Default.Edit,
                    title = "Editar perfil del restaurante",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToEditProfile
                )
            }
            item {
                ProfileMenuItem(
                    icon = Icons.Default.WorkspacePremium,
                    title = "Zampa Pro",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToSubscription
                )
            }
        }
        
        // Section: Más
        item {
            SectionHeader("Más")
        }
        item {
            ProfileMenuItem(
                icon = Icons.Outlined.HelpOutline,
                title = "Ayuda y Soporte",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {}
            )
        }
        item {
            ProfileMenuItem(
                icon = Icons.Outlined.Description,
                title = "Términos y Privacidad",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {}
            )
        }
        
        // Logout
        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar Sesión")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = {
            Icon(icon, contentDescription = title, tint = iconTint)
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
