package com.sozolab.zampa.ui.merchant

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sozolab.zampa.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantProfileSetupScreen(
    viewModel: MerchantProfileSetupViewModel,
    onSuccess: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val existingProfile by viewModel.existingProfile.collectAsState()
    val isEditMode = existingProfile != null
    var profileLoaded by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var taxId by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var addressText by remember { mutableStateOf("") }
    var acceptsReservations by remember { mutableStateOf(false) }
    val selectedCuisines = remember { mutableStateListOf<String>() }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageData by remember { mutableStateOf<ByteArray?>(null) }
    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    val isLoading by viewModel.isLoading.collectAsState()
    val isSuccess by viewModel.isSuccess.collectAsState()
    val error by viewModel.error.collectAsState()
    val schedule by viewModel.schedule.collectAsState()

    var availableCuisines by remember { mutableStateOf<List<String>>(emptyList()) }

    // Pre-populate fields when existing profile loads
    LaunchedEffect(existingProfile) {
        val p = existingProfile
        if (p != null && !profileLoaded) {
            profileLoaded = true
            name = p.name
            phone = p.phone ?: ""
            taxId = p.taxId ?: ""
            description = p.shortDescription ?: ""
            addressText = p.address?.formatted ?: p.addressText ?: ""
            acceptsReservations = p.acceptsReservations
            selectedCuisines.clear()
            selectedCuisines.addAll(p.cuisineTypes ?: emptyList())
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            cameraBitmap = null
            imageData = context.contentResolver.openInputStream(it)?.readBytes()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            cameraBitmap = bitmap
            imageUri = null
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            imageData = stream.toByteArray()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) cameraLauncher.launch(null)
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text(stringResource(R.string.setup_cover_photo)) },
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
                        onClick = {
                            showPhotoSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
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

    LaunchedEffect(isSuccess) {
        if (isSuccess) onSuccess()
    }

    LaunchedEffect(Unit) {
        availableCuisines = com.sozolab.zampa.data.FirebaseService().fetchCuisineTypes().map { it.name }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isEditMode) stringResource(R.string.setup_edit_title) else stringResource(R.string.setup_create_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    // Botón cerrar arriba a la izquierda — sólo en modo edición.
                    // En modo creación no hay back porque es el flow obligatorio post-signup.
                    if (isEditMode) {
                        IconButton(onClick = onSkip) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Intro
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isEditMode) stringResource(R.string.setup_edit_profile) else stringResource(R.string.setup_create_profile), style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (isEditMode) stringResource(R.string.setup_edit_subtitle)
                    else stringResource(R.string.setup_create_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Cover Photo
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.setup_cover_photo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showPhotoSourceDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        cameraBitmap != null -> Image(
                            bitmap = cameraBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        imageUri != null -> AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        existingProfile?.coverPhotoUrl != null -> AsyncImage(
                            model = existingProfile?.coverPhotoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(32.dp))
                            Text(stringResource(R.string.setup_add_photo), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Information
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.setup_contact_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.setup_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.setup_phone)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )

                val taxIdNormalized = taxId.trim().uppercase()
                val taxIdValid = com.sozolab.zampa.data.TaxIdValidator.isValid(taxIdNormalized)
                val taxIdShowError = taxId.isNotBlank() && !taxIdValid
                OutlinedTextField(
                    value = taxId, onValueChange = { taxId = it.uppercase() },
                    label = { Text(stringResource(R.string.setup_tax_id_label)) },
                    supportingText = {
                        Text(stringResource(
                            if (taxIdShowError) R.string.setup_tax_id_invalid
                            else R.string.setup_tax_id_hint
                        ))
                    },
                    isError = taxIdShowError,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = addressText, onValueChange = { addressText = it },
                    label = { Text(stringResource(R.string.setup_address)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Description
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.setup_description), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text(stringResource(R.string.setup_description_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3
                )
            }

            // Cuisines
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.setup_cuisine_type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableCuisines.forEach { cuisine ->
                        FilterChip(
                            selected = cuisine in selectedCuisines,
                            onClick = {
                                if (cuisine in selectedCuisines) selectedCuisines.remove(cuisine)
                                else selectedCuisines.add(cuisine)
                            },
                            label = { Text(cuisine) }
                        )
                    }
                }
            }

            // Schedule
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.setup_schedule), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                schedule.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = entry.isOpen,
                            onCheckedChange = { entry.isOpen = it }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(entry.dayName, modifier = Modifier.width(90.dp))
                        
                        if (entry.isOpen) {
                            OutlinedTextField(
                                value = entry.openTime,
                                onValueChange = { entry.openTime = it },
                                modifier = Modifier.width(70.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = true
                            )
                            Text(" - ", modifier = Modifier.padding(horizontal = 4.dp))
                            OutlinedTextField(
                                value = entry.closeTime,
                                onValueChange = { entry.closeTime = it },
                                modifier = Modifier.width(70.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Reservations
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.setup_reservations), modifier = Modifier.weight(1f))
                Switch(checked = acceptsReservations, onCheckedChange = { acceptsReservations = it })
            }

            // Submit
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            
            Button(
                onClick = { viewModel.saveProfile(name, phone, taxId, description, addressText, selectedCuisines, acceptsReservations, imageData) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading && name.isNotBlank() && phone.isNotBlank() && addressText.isNotBlank()
                    && com.sozolab.zampa.data.TaxIdValidator.isValid(taxId.trim().uppercase())
            ) {
                if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                else Text(if (isEditMode) stringResource(R.string.setup_save_changes) else stringResource(R.string.setup_save_continue))
            }

            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.setup_complete_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

