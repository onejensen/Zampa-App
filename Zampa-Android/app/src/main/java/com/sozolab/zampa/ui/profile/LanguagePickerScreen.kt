package com.sozolab.zampa.ui.profile

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sozolab.zampa.R
import com.sozolab.zampa.data.LocalizationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerScreen(
    localizationManager: LocalizationManager,
    userId: String?,
    onBack: () -> Unit
) {
    val currentLanguage by localizationManager.currentLanguage.collectAsState()
    val context = LocalContext.current
    val localizedContext = localizationManager.createLocalizedContext()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(localizedContext.getString(R.string.language_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = localizedContext.getString(R.string.common_back))
                    }
                },
                colors = com.sozolab.zampa.ui.theme.brandTopAppBarColors()
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(LocalizationManager.supportedLanguages) { lang ->
                ListItem(
                    headlineContent = {
                        if (lang.code == "auto") {
                            Column {
                                Text(
                                    localizedContext.getString(R.string.language_auto),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    localizationManager.resolvedLanguageNativeName.substringAfter("(").substringBefore(")"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                lang.nativeName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    },
                    trailingContent = {
                        if (currentLanguage == lang.code) {
                            Text(
                                "✓",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        localizationManager.setLanguage(lang.code, userId)
                        (context as? Activity)?.recreate()
                    }
                )
                if (lang.code == "auto") {
                    HorizontalDivider()
                }
            }
        }
    }
}
