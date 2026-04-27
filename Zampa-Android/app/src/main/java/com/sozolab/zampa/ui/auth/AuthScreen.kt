package com.sozolab.zampa.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sozolab.zampa.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sozolab.zampa.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {}
) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(User.UserRole.CLIENTE) }
    var passwordVisible by remember { mutableStateOf(false) }

    val passwordErrorRes = remember(isLogin, password) {
        if (isLogin || password.isEmpty()) null
        else when {
            password.length < 8 -> R.string.auth_password_min
            !password.any { it.isLetter() } -> R.string.auth_password_letter
            !password.any { it.isDigit() } -> R.string.auth_password_number
            else -> null
        }
    }

    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.error.collectAsState()
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val pendingSocialUser by viewModel.pendingSocialUser.collectAsState()

    // Google Sign-In launcher
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || result.data != null) {
            viewModel.handleGoogleSignInResult(result.data)
        }
    }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) onAuthSuccess()
    }

    // Role selection dialog for new Google users
    pendingSocialUser?.let { socialUser ->
        var roleDialogSelected by remember { mutableStateOf(User.UserRole.CLIENTE) }
        var displayName by remember { mutableStateOf(socialUser.name) }
        AlertDialog(
            onDismissRequest = { viewModel.cancelSocialRegistration() },
            title = { Text(stringResource(R.string.auth_welcome)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.auth_welcome_subtitle), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(stringResource(R.string.auth_your_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(stringResource(R.string.auth_how_use), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = roleDialogSelected == User.UserRole.CLIENTE,
                            onClick = { roleDialogSelected = User.UserRole.CLIENTE },
                            label = { Text(stringResource(R.string.auth_diner)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = roleDialogSelected == User.UserRole.COMERCIO,
                            onClick = { roleDialogSelected = User.UserRole.COMERCIO },
                            label = { Text(stringResource(R.string.auth_restaurant)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.finalizeSocialRegistration(roleDialogSelected, displayName.ifBlank { socialUser.name }) },
                    enabled = !isLoading
                ) { Text(stringResource(R.string.auth_start)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelSocialRegistration() }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))

        // Logo
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_zampa),
                contentDescription = "Zampa",
                modifier = Modifier.size(88.dp),
                colorFilter = ColorFilter.tint(Color.White)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Zampa", style = MaterialTheme.typography.headlineLarge)
        Text(
            if (isLogin) stringResource(R.string.auth_login_subtitle) else stringResource(R.string.auth_register_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        // Toggle Login/Register
        Row(modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = isLogin, onClick = { isLogin = true },
                label = { Text(stringResource(R.string.auth_login)) },
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            )
            FilterChip(
                selected = !isLogin, onClick = { isLogin = false },
                label = { Text(stringResource(R.string.auth_register)) },
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        // Register-only fields
        if (!isLogin) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.auth_full_name)) },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text(stringResource(R.string.auth_phone_short)) },
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            // Role Selector
            Text(stringResource(R.string.auth_i_am), style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedRole == User.UserRole.CLIENTE,
                    onClick = { selectedRole = User.UserRole.CLIENTE },
                    label = { Text(stringResource(R.string.auth_client)) },
                    leadingIcon = { if (selectedRole == User.UserRole.CLIENTE) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedRole == User.UserRole.COMERCIO,
                    onClick = { selectedRole = User.UserRole.COMERCIO },
                    label = { Text(stringResource(R.string.auth_restaurant)) },
                    leadingIcon = { if (selectedRole == User.UserRole.COMERCIO) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Google Sign-In
        OutlinedButton(
            onClick = {
                val intent = viewModel.getGoogleSignInIntent(context)
                googleLauncher.launch(intent)
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                painter = painterResource(id = com.sozolab.zampa.R.drawable.ic_google),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.auth_continue_google))
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Email
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text(stringResource(R.string.auth_email)) },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        // Password
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            isError = passwordErrorRes != null,
            supportingText = passwordErrorRes?.let { resId -> { Text(stringResource(resId)) } }
        )

        // Error de autenticación
        errorMsg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))

        // Submit
        Button(
            onClick = {
                if (isLogin) viewModel.login(email, password)
                else viewModel.register(email, password, name, selectedRole, phone.ifBlank { null })
            },
            enabled = !isLoading && email.isNotBlank() && password.isNotEmpty() &&
                (isLogin || passwordErrorRes == null),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(if (isLogin) stringResource(R.string.auth_login) else stringResource(R.string.auth_create_account))
        }

        if (!isLogin) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.auth_accept_terms_prefix) + " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.auth_terms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = androidx.compose.ui.Modifier.clickable { onNavigateToTerms() }
                )
                Text(
                    " " + stringResource(R.string.auth_and) + " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.auth_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = androidx.compose.ui.Modifier.clickable { onNavigateToPrivacyPolicy() }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { isLogin = !isLogin }) {
            Text(if (isLogin) stringResource(R.string.auth_no_account) else stringResource(R.string.auth_has_account))
        }
    }
}
