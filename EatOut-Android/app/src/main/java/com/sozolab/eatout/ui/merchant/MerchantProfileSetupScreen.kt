package com.sozolab.eatout.ui.merchant

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantProfileSetupScreen(
    viewModel: MerchantProfileSetupViewModel,
    onSuccess: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var addressText by remember { mutableStateOf("") }
    var acceptsReservations by remember { mutableStateOf(false) }
    val selectedCuisines = remember { mutableStateListOf<String>() }
    
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageData by remember { mutableStateOf<ByteArray?>(null) }
    
    val isLoading by viewModel.isLoading.collectAsState()
    val isSuccess by viewModel.isSuccess.collectAsState()
    val error by viewModel.error.collectAsState()
    val schedule by viewModel.schedule.collectAsState()
    
    var availableCuisines by remember { mutableStateOf<List<String>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            imageData = context.contentResolver.openInputStream(it)?.readBytes()
        }
    }

    LaunchedEffect(isSuccess) {
        if (isSuccess) onSuccess()
    }

    LaunchedEffect(Unit) {
        availableCuisines = com.sozolab.eatout.data.FirebaseService().fetchCuisineTypes().map { it.name }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Configurar Restaurante", fontWeight = FontWeight.Bold) })
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
                Text("Completa tu perfil", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Configura tu restaurante para empezar a publicar menús del día.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Cover Photo
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Foto de portada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(32.dp))
                            Text("Añadir foto", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Information
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Información de contacto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre del restaurante *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Teléfono *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = addressText, onValueChange = { addressText = it },
                    label = { Text("Dirección *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Description
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Descripción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Cuéntanos sobre tu restaurante") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3
                )
            }

            // Cuisines
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tipo de cocina", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Text("Horario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Text("Acepta reservas", modifier = Modifier.weight(1f))
                Switch(checked = acceptsReservations, onCheckedChange = { acceptsReservations = it })
            }

            // Submit
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            
            Button(
                onClick = { viewModel.saveProfile(name, phone, description, addressText, selectedCuisines, acceptsReservations, imageData) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading && name.isNotBlank() && phone.isNotBlank() && addressText.isNotBlank()
            ) {
                if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                else Text("Guardar y continuar")
            }

            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Completar más tarde", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

