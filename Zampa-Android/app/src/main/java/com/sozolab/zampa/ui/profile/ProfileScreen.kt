package com.sozolab.zampa.ui.profile

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.sozolab.zampa.R
import com.sozolab.zampa.data.ThemeManager
import com.sozolab.zampa.data.model.User
import com.sozolab.zampa.ui.theme.LocalThemeManager

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
    onNavigateToCurrencyPreference: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToLegal: () -> Unit = {},
    onRequestAccountDeletion: ((onError: (String) -> Unit) -> Unit)? = null,
    onRestartTour: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf("") }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteTypedConfirmation by remember { mutableStateOf("") }
    var deleteIsSubmitting by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }

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
            title = { Text(stringResource(R.string.profile_change_photo)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.profile_camera)) }
                    TextButton(
                        onClick = { galleryLauncher.launch("image/*"); showPhotoSourceDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.profile_gallery)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(stringResource(R.string.profile_edit_name)) },
            text = {
                OutlinedTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    label = { Text(stringResource(R.string.profile_display_name)) },
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
                ) { Text(stringResource(R.string.profile_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!deleteIsSubmitting) {
                    showDeleteAccountDialog = false
                    deleteTypedConfirmation = ""
                    deleteErrorMessage = null
                }
            },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.profile_delete_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.profile_delete_item_profile), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.profile_delete_item_favorites), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.profile_delete_item_history), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.profile_delete_confirm_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteTypedConfirmation,
                        onValueChange = { deleteTypedConfirmation = it },
                        placeholder = { Text(stringResource(R.string.profile_delete_confirm_word)) },
                        singleLine = true,
                        enabled = !deleteIsSubmitting,
                        isError = deleteErrorMessage != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters
                        )
                    )
                    val err = deleteErrorMessage
                    if (err != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteIsSubmitting) return@TextButton
                        val handler = onRequestAccountDeletion ?: return@TextButton
                        deleteIsSubmitting = true
                        deleteErrorMessage = null
                        handler { err ->
                            deleteIsSubmitting = false
                            deleteErrorMessage = err
                        }
                        // En éxito NO necesitamos cerrar el diálogo manualmente:
                        // el NavHost observa pendingDeletionUser y navega fuera de
                        // ProfileScreen, desmontando este dialog.
                    },
                    enabled = deleteTypedConfirmation == "ELIMINAR" && !deleteIsSubmitting,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (deleteIsSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.profile_delete_button))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        deleteTypedConfirmation = ""
                        deleteErrorMessage = null
                    },
                    enabled = !deleteIsSubmitting
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 16.dp)
    ) {
        // Header: Avatar + User Info
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with camera overlay
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.clickable { showPhotoSourceDialog = true }
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(3.dp, Color.White, CircleShape),
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
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = { showPhotoSourceDialog = true },
                        modifier = Modifier.size(24.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.profile_change_photo),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        user?.name ?: stringResource(R.string.profile_user),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        user?.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            editNameText = user?.name ?: ""
                            showEditNameDialog = true
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(stringResource(R.string.profile_edit_name), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        
        // Section: Preferencias
        item {
            SectionHeader(stringResource(R.string.profile_section_preferences))
        }
        item {
            ProfileMenuItem(
                icon = Icons.Default.Eco,
                title = stringResource(R.string.profile_dietary),
                iconTint = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToDietaryPreferences
            )
        }
        item {
            ProfileMenuItem(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.profile_notifications),
                iconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToNotificationPreferences
            )
        }
        item {
            val code = user?.currencyPreference ?: "EUR"
            val symbol = when (code) {
                "EUR" -> "€"
                "USD" -> "$"
                "GBP" -> "£"
                "JPY" -> "¥"
                "CHF" -> "CHF"
                "SEK", "NOK", "DKK" -> "kr"
                "CAD" -> "C$"
                "AUD" -> "A$"
                else -> code
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_currency)) },
                supportingContent = { Text("$code ($symbol)") },
                leadingContent = {
                    Icon(
                        Icons.Default.AttachMoney,
                        contentDescription = stringResource(R.string.profile_currency),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable(onClick = onNavigateToCurrencyPreference)
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_language)) },
                supportingContent = {
                    val langName = com.sozolab.zampa.data.LocalizationManager.supportedLanguages
                        .find { it.code == (user?.languagePreference ?: "auto") }?.nativeName ?: stringResource(R.string.language_auto)
                    Text(langName)
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = stringResource(R.string.profile_language),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable(onClick = onNavigateToLanguage)
            )
        }
        item {
            val themeManager = LocalThemeManager.current
            if (themeManager != null) {
                val theme by themeManager.theme.collectAsState()
                val options = listOf(
                    ThemeManager.SYSTEM to stringResource(R.string.theme_system),
                    ThemeManager.LIGHT to stringResource(R.string.theme_light),
                    ThemeManager.DARK to stringResource(R.string.theme_dark),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profile_theme)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.DarkMode,
                            contentDescription = stringResource(R.string.profile_theme),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    supportingContent = {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            options.forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    selected = theme == value,
                                    onClick = { themeManager.setTheme(value) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.primary,
                                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                        activeBorderColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    label = {
                                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
        item {
            ProfileMenuItem(
                icon = Icons.Default.HelpOutline,
                title = stringResource(R.string.profile_restart_tour),
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = { onRestartTour?.invoke() }
            )
        }
        if (isMerchant) {
            item { SectionHeader(stringResource(R.string.profile_section_restaurant)) }
            item {
                ProfileMenuItem(
                    icon = Icons.Default.BarChart,
                    title = stringResource(R.string.profile_stats),
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToStats
                )
            }
            item {
                ProfileMenuItem(
                    icon = Icons.Default.Edit,
                    title = stringResource(R.string.profile_edit_restaurant),
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToEditProfile
                )
            }
            item {
                ProfileMenuItem(
                    icon = Icons.Default.WorkspacePremium,
                    title = stringResource(R.string.profile_zampa_pro),
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToSubscription
                )
            }
        }
        
        // Section: Más
        item {
            SectionHeader(stringResource(R.string.profile_section_more))
        }
        item {
            ProfileMenuItem(
                icon = Icons.Outlined.HelpOutline,
                title = stringResource(R.string.profile_help),
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.getzampa.com/#faq")
                    )
                    context.startActivity(intent)
                }
            )
        }
        item {
            ProfileMenuItem(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.profile_terms),
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onNavigateToLegal
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
                Text(stringResource(R.string.profile_logout))
            }
        }

        // Delete account (solo clientes)
        if (!isMerchant && onRequestAccountDeletion != null) {
            item {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                    )
                ) {
                    Text(stringResource(R.string.profile_delete_account))
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
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
