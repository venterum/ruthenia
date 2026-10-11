/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vntrum.ruthenia.core.data.settings.AccentPalette
import com.vntrum.ruthenia.core.data.settings.AppIconShape
import com.vntrum.ruthenia.core.data.settings.AppLanguage
import com.vntrum.ruthenia.core.data.settings.AppSettings
import com.vntrum.ruthenia.core.data.settings.InstallMethod
import com.vntrum.ruthenia.core.data.settings.NavBarStyle
import com.vntrum.ruthenia.core.data.settings.ThemeMode
import com.vntrum.ruthenia.core.designsystem.component.LocalRutheniaBottomContentPadding
import com.vntrum.ruthenia.core.designsystem.component.RutheniaWavyProgress
import com.vntrum.ruthenia.core.designsystem.component.SectionHeader
import com.vntrum.ruthenia.core.designsystem.component.animatedEntrance
import com.vntrum.ruthenia.core.designsystem.component.progressSemantics
import com.vntrum.ruthenia.core.designsystem.component.rutheniaContentWidth
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaAccent
import com.vntrum.ruthenia.core.designsystem.theme.primaryColor
import com.vntrum.ruthenia.core.installer.shizuku.ShizukuInstaller
import com.vntrum.ruthenia.core.installer.shizuku.ShizukuStatus
import com.vntrum.ruthenia.core.security.PinSource
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPrivacyClick: () -> Unit,
    onAboutClick: () -> Unit,
    appIconPreviews: List<AppIconPreview> = emptyList(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val certificateStatus by viewModel.certificateStatus.collectAsStateWithLifecycle()
    val certificateBusy by viewModel.certificateBusy.collectAsStateWithLifecycle()
    val collectionsBusy by viewModel.collectionsBusy.collectAsStateWithLifecycle()
    val collectionsProgress by viewModel.collectionsProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember { context.appVersionInfo() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Disabling signature verification requires confirmation: it weakens protection.
    var showSignatureWarning by rememberSaveable { mutableStateOf(false) }

    // Enabling silent install through Shizuku requires an honest warning:
    // the app receives shell-level privileges.
    var showShizukuWarning by rememberSaveable { mutableStateOf(false) }

    // Choose a folder for saving APKs via the Storage Access Framework.
    val exportFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.setApkExportTreeUri(uri.toString())
    }

    // We request notifications at a sensible moment — when background checks
    // are enabled, not on the app's first cold start.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* user's decision */ }

    // Library and settings backup: JSON export/import via SAF.
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportBackup(uri)
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importBackup(uri)
    }

    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .rutheniaContentWidth()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            Spacer(modifier = Modifier.height(4.dp))

            SettingsGroup(
                title = stringResource(R.string.settings_section_appearance),
                modifier = Modifier.animatedEntrance(index = 0),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_theme)) },
                    supportingContent = {
                        ThemeModeSelector(
                            selected = settings.themeMode,
                            onSelect = viewModel::setThemeMode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    },
                    colors = transparentListItemColors(),
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_accent)) },
                    supportingContent = {
                        AccentPicker(
                            selected = settings.accent,
                            enabled = !settings.dynamicColor,
                            onSelect = viewModel::setAccent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    },
                    colors = transparentListItemColors(),
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    supportingContent = {
                        LanguageSelector(
                            selected = settings.language,
                            onSelect = viewModel::setLanguage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    },
                    colors = transparentListItemColors(),
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_navbar_style)) },
                    supportingContent = {
                        NavBarStyleSelector(
                            selected = settings.navBarStyle,
                            onSelect = viewModel::setNavBarStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    },
                    colors = transparentListItemColors(),
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_icon_shape)) },
                    supportingContent = {
                        IconShapeSelector(
                            selected = settings.appIconShape,
                            onSelect = viewModel::setAppIconShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    },
                    colors = transparentListItemColors(),
                )

                if (appIconPreviews.isNotEmpty()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_app_icon)) },
                        supportingContent = {
                            AppIconPicker(
                                previews = appIconPreviews,
                                selected = settings.appIconVariant,
                                onSelect = viewModel::setAppIconVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        },
                        colors = transparentListItemColors(),
                    )
                }

                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_desc),
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            SettingsGroup(
                title = stringResource(R.string.settings_section_catalog),
                modifier = Modifier.animatedEntrance(index = 1),
            ) {
                SwitchRow(
                    title = stringResource(R.string.settings_games_page),
                    subtitle = stringResource(R.string.settings_games_page_desc),
                    checked = settings.gamesEnabled,
                    onCheckedChange = viewModel::setGamesEnabled,
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_reload_collections)) },
                    supportingContent = {
                        // While reloading, the percent and the bar show that the
                        // collections are actually being resolved.
                        if (collectionsBusy) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    stringResource(
                                        R.string.settings_collections_loading,
                                        collectionsProgress.percent,
                                    ),
                                )
                                RutheniaWavyProgress(
                                    progress = { collectionsProgress.percent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .progressSemantics(collectionsProgress.percent / 100f),
                                )
                            }
                        } else {
                            Text(stringResource(R.string.settings_reload_collections_desc))
                        }
                    },
                    trailingContent = {
                        if (!collectionsBusy) {
                            TextButton(onClick = viewModel::reloadCollections) {
                                Text(stringResource(R.string.settings_reload_collections_action))
                            }
                        }
                    },
                    colors = transparentListItemColors(),
                )
            }

            SettingsGroup(
                title = stringResource(R.string.settings_section_updates),
                modifier = Modifier.animatedEntrance(index = 2),
            ) {
                SwitchRow(
                    title = stringResource(R.string.settings_background_checks),
                    subtitle = stringResource(R.string.settings_background_checks_desc),
                    checked = settings.backgroundChecksEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setBackgroundChecks(enabled)
                        if (enabled) {
                            requestNotificationPermission(context, notificationPermissionLauncher)
                        }
                    },
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_interval)) },
                    supportingContent = {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            AppSettings.INTERVAL_OPTIONS.forEachIndexed { index, hours ->
                                SegmentedButton(
                                    selected = settings.updateCheckIntervalHours == hours,
                                    onClick = { viewModel.setIntervalHours(hours) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AppSettings.INTERVAL_OPTIONS.size,
                                    ),
                                    enabled = settings.backgroundChecksEnabled,
                                    label = {
                                        Text(stringResource(R.string.settings_interval_hours, hours))
                                    },
                                )
                            }
                        }
                    },
                    colors = transparentListItemColors(),
                )

                SwitchRow(
                    title = stringResource(R.string.settings_wifi_only),
                    subtitle = stringResource(R.string.settings_wifi_only_desc),
                    checked = settings.unmeteredOnly,
                    onCheckedChange = viewModel::setUnmeteredOnly,
                    enabled = settings.backgroundChecksEnabled,
                )
            }

            SettingsGroup(
                title = stringResource(R.string.settings_section_sources),
                modifier = Modifier.animatedEntrance(index = 3),
            ) {
                SwitchRow(
                    title = stringResource(R.string.settings_show_gplay),
                    subtitle = stringResource(R.string.settings_show_gplay_desc),
                    checked = settings.showGooglePlayApps,
                    onCheckedChange = viewModel::setShowGooglePlayApps,
                )

                SwitchRow(
                    title = stringResource(R.string.settings_show_sideload),
                    subtitle = stringResource(R.string.settings_show_sideload_desc),
                    checked = settings.showSideloadedApps,
                    onCheckedChange = viewModel::setShowSideloadedApps,
                )
            }

            SettingsGroup(
                title = stringResource(R.string.settings_section_downloads),
                modifier = Modifier.animatedEntrance(index = 4),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_export_folder)) },
                    supportingContent = {
                        val folder = settings.apkExportTreeUri?.let(::treeDisplayName)
                        Text(
                            if (folder != null) {
                                stringResource(R.string.settings_export_folder_selected, folder)
                            } else {
                                stringResource(R.string.settings_export_folder_none)
                            },
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { exportFolderLauncher.launch(null) }) {
                            Text(stringResource(R.string.settings_choose_folder))
                        }
                    },
                    colors = transparentListItemColors(),
                )
            }

            SettingsGroup(
                title = stringResource(R.string.settings_section_backup),
                modifier = Modifier.animatedEntrance(index = 5),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_backup_export)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_backup_export_desc))
                    },
                    trailingContent = {
                        TextButton(
                            onClick = { exportBackupLauncher.launch(BACKUP_FILE_NAME) },
                        ) {
                            Text(stringResource(R.string.settings_backup_export_action))
                        }
                    },
                    colors = transparentListItemColors(),
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_backup_import)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_backup_import_desc))
                    },
                    trailingContent = {
                        TextButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json")) },
                        ) {
                            Text(stringResource(R.string.settings_backup_import_action))
                        }
                    },
                    colors = transparentListItemColors(),
                )
            }

            SettingsGroup(
                title = stringResource(R.string.settings_section_install),
                modifier = Modifier.animatedEntrance(index = 6),
            ) {
                SwitchRow(
                    title = stringResource(R.string.settings_auto_install),
                    subtitle = stringResource(R.string.settings_auto_install_desc),
                    checked = settings.autoInstall,
                    onCheckedChange = viewModel::setAutoInstall,
                )

                SwitchRow(
                    title = stringResource(R.string.settings_verify_signature),
                    subtitle = stringResource(R.string.settings_verify_signature_desc),
                    checked = settings.verifySignatures,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            viewModel.setVerifySignatures(true)
                        } else {
                            showSignatureWarning = true
                        }
                    },
                )

                SwitchRow(
                    title = stringResource(R.string.settings_shizuku),
                    subtitle = stringResource(R.string.settings_shizuku_desc),
                    checked = settings.installMethod == InstallMethod.SHIZUKU,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showShizukuWarning = true
                        } else {
                            viewModel.setInstallMethod(InstallMethod.SYSTEM)
                        }
                    },
                )

                if (settings.installMethod == InstallMethod.SHIZUKU) {
                    ShizukuStatusRow(
                        status = shizukuStatus,
                        onGrant = viewModel::requestShizukuPermission,
                        onOpenShizuku = { openShizuku(context) },
                    )
                }
            }

            SettingsGroup(
                title = stringResource(R.string.settings_section_security),
                modifier = Modifier.animatedEntrance(index = 7),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_certs)) },
                    supportingContent = {
                        Text(
                            when (certificateStatus.source) {
                                PinSource.BUNDLED ->
                                    stringResource(R.string.settings_certs_source_bundled)

                                PinSource.REFRESHED -> stringResource(
                                    R.string.settings_certs_source_refreshed,
                                    formatTimestamp(certificateStatus.updatedAt),
                                )
                            },
                        )
                    },
                    trailingContent = {
                        if (certificateBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(onClick = viewModel::refreshCertificates) {
                                Text(stringResource(R.string.settings_certs_update))
                            }
                        }
                    },
                    colors = transparentListItemColors(),
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_certs_reset)) },
                    supportingContent = { Text(stringResource(R.string.settings_certs_reset_desc)) },
                    trailingContent = {
                        TextButton(
                            onClick = viewModel::resetCertificates,
                            enabled = certificateStatus.source == PinSource.REFRESHED &&
                                !certificateBusy,
                        ) {
                            Text(stringResource(R.string.settings_certs_reset_action))
                        }
                    },
                    colors = transparentListItemColors(),
                )
            }

            SettingsGroup(
                title = stringResource(R.string.settings_section_privacy),
                modifier = Modifier.animatedEntrance(index = 8),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy_what)) },
                    supportingContent = { Text(stringResource(R.string.settings_privacy_what_desc)) },
                    trailingContent = {
                        TextButton(onClick = onPrivacyClick) {
                            Text(stringResource(R.string.settings_open))
                        }
                    },
                    colors = transparentListItemColors(),
                )

                SwitchRow(
                    title = stringResource(R.string.settings_diagnostics),
                    subtitle = stringResource(R.string.settings_diagnostics_desc),
                    checked = settings.diagnosticsEnabled,
                    onCheckedChange = viewModel::setDiagnostics,
                )
            }

            SettingsGroup(
                title = stringResource(R.string.settings_section_about),
                modifier = Modifier.animatedEntrance(index = 9),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_about_name)) },
                    supportingContent = { Text(stringResource(R.string.settings_about_desc)) },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            version.name?.let { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = transparentListItemColors(),
                    modifier = Modifier.clickable(onClick = onAboutClick),
                )
            }

            Spacer(modifier = Modifier.height(LocalRutheniaBottomContentPadding.current))
        }
        }
    }

    if (showSignatureWarning) {
        AlertDialog(
            onDismissRequest = { showSignatureWarning = false },
            title = { Text(stringResource(R.string.settings_verify_signature_off_title)) },
            text = { Text(stringResource(R.string.settings_verify_signature_off_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setVerifySignatures(false)
                        showSignatureWarning = false
                    },
                ) {
                    Text(stringResource(R.string.settings_verify_signature_off_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignatureWarning = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showShizukuWarning) {
        AlertDialog(
            onDismissRequest = { showShizukuWarning = false },
            title = { Text(stringResource(R.string.settings_shizuku_warning_title)) },
            text = { Text(stringResource(R.string.settings_shizuku_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShizukuWarning = false
                        viewModel.setInstallMethod(InstallMethod.SHIZUKU)
                        // The request is a no-op when the service is not
                        // running: the status row then guides the user.
                        viewModel.requestShizukuPermission()
                    },
                ) {
                    Text(stringResource(R.string.settings_shizuku_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShizukuWarning = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * Status and action for the optional Shizuku install backend.
 */
@Composable
private fun ShizukuStatusRow(
    status: ShizukuStatus,
    onGrant: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_shizuku_status)) },
        supportingContent = { Text(status.label()) },
        trailingContent = {
            when (status) {
                ShizukuStatus.READY -> Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )

                ShizukuStatus.PERMISSION_REQUIRED -> TextButton(onClick = onGrant) {
                    Text(stringResource(R.string.settings_shizuku_grant))
                }

                ShizukuStatus.NOT_RUNNING -> TextButton(onClick = onOpenShizuku) {
                    Text(stringResource(R.string.settings_shizuku_open))
                }

                // No action: the user has to install Shizuku themselves, and
                // the app deliberately does not open external links.
                ShizukuStatus.NOT_INSTALLED -> Unit
            }
        },
        colors = transparentListItemColors(),
    )
}

@Composable
private fun ShizukuStatus.label(): String = stringResource(
    when (this) {
        ShizukuStatus.NOT_INSTALLED -> R.string.settings_shizuku_status_not_installed
        ShizukuStatus.NOT_RUNNING -> R.string.settings_shizuku_status_not_running
        ShizukuStatus.PERMISSION_REQUIRED -> R.string.settings_shizuku_status_permission
        ShizukuStatus.READY -> R.string.settings_shizuku_status_ready
    },
)

/** Opens the Shizuku app so the user can start the service or grant access. */
private fun openShizuku(context: Context) {
    val intent = context.packageManager
        .getLaunchIntentForPackage(ShizukuInstaller.SHIZUKU_PACKAGE)
        ?: return
    runCatching { context.startActivity(intent) }
}

/**
 * A settings section: a header and a surface card whose rows are grouped
 * without dividers. The hierarchy is conveyed by the
 * `surfaceContainerLow` tone, not by lines.
 */
@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            text = title,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun transparentListItemColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
)

private const val BACKUP_FILE_NAME = "ruthenia-backup.json"

/** Requests notification permission where it is actually needed. */
private fun requestNotificationPermission(
    context: Context,
    launcher: ActivityResultLauncher<String>,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
}

/**
 * Theme selection on an expressive `ButtonGroup`: when pressed, the selected
 * item expands and its neighbors shrink — that "lively" group response.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberRutheniaHaptics()
    val modes = ThemeMode.entries
    val labels = modes.map { it.label() }

    ButtonGroup(
        modifier = modifier,
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
    ) {
        modes.forEachIndexed { index, mode ->
            toggleableItem(
                checked = selected == mode,
                label = labels[index],
                onCheckedChange = {
                    haptics.tick()
                    onSelect(mode)
                },
                weight = 1f,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val haptics = rememberRutheniaHaptics()
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { value ->
                    haptics.toggle(value)
                    onCheckedChange(value)
                },
                enabled = enabled,
            )
        },
        colors = transparentListItemColors(),
    )
}

@Composable
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

/**
 * Accent palette dots. The choice applies when Material You dynamic colors are
 * disabled; otherwise a hint explains that the system controls the color.
 */
@Composable
private fun AccentPicker(
    selected: AccentPalette,
    enabled: Boolean,
    onSelect: (AccentPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentPalette.entries.forEach { palette ->
                val accent = RutheniaAccent.entries
                    .firstOrNull { it.name == palette.name }
                    ?: RutheniaAccent.TEAL
                val isSelected = palette == selected
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.primaryColor().copy(alpha = if (enabled) 1f else 0.4f))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .clickable(enabled = enabled) { onSelect(palette) },
                )
            }
        }
        if (!enabled) {
            Text(
                text = stringResource(R.string.settings_accent_dynamic_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Interface language selection: system, Russian, English. */
@Composable
private fun LanguageSelector(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberRutheniaHaptics()
    val options = listOf(
        AppLanguage.SYSTEM to R.string.settings_language_system,
        AppLanguage.RU to R.string.settings_language_ru,
        AppLanguage.EN to R.string.settings_language_en,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (language, labelRes) ->
            SegmentedButton(
                selected = selected == language,
                onClick = {
                    haptics.tick()
                    onSelect(language)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

/**
 * Bottom navigation bar style: the floating pill (current look) or the
 * standard docked Material 3 bar.
 */
@Composable
private fun NavBarStyleSelector(
    selected: NavBarStyle,
    onSelect: (NavBarStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberRutheniaHaptics()
    val options = listOf(
        NavBarStyle.FLOATING to R.string.settings_navbar_floating,
        NavBarStyle.NORMAL to R.string.settings_navbar_normal,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (style, labelRes) ->
            SegmentedButton(
                selected = selected == style,
                onClick = {
                    haptics.tick()
                    onSelect(style)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

/**
 * Shape of app icons in lists, cards, and on the details screen. The app's own
 * brand mark in the Apps header is always round.
 */
@Composable
private fun IconShapeSelector(
    selected: AppIconShape,
    onSelect: (AppIconShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberRutheniaHaptics()
    val options = listOf(
        AppIconShape.CIRCLE to R.string.settings_icon_shape_circle,
        AppIconShape.ROUNDED_SQUARE to R.string.settings_icon_shape_rounded,
        AppIconShape.SQUARE to R.string.settings_icon_shape_square,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (shape, labelRes) ->
            SegmentedButton(
                selected = selected == shape,
                onClick = {
                    haptics.tick()
                    onSelect(shape)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

/** Human-readable folder name from a SAF tree: the last segment of the document id. */
private fun treeDisplayName(treeUri: String): String {
    val documentId = runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
    }.getOrNull() ?: return treeUri
    return documentId.substringAfterLast(':', documentId)
        .substringAfterLast('/')
        .trim()
        .ifEmpty { documentId }
}

/** Locale-aware date and time for the "pins refreshed at" line. */
private fun formatTimestamp(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
