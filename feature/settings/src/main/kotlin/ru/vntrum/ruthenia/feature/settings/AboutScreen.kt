/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.vntrum.ruthenia.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.Morph
import com.vntrum.ruthenia.core.designsystem.component.AnimatedEntrance
import com.vntrum.ruthenia.core.designsystem.component.RutheniaBrandMark
import com.vntrum.ruthenia.core.designsystem.haptics.rememberRutheniaHaptics
import kotlinx.coroutines.launch

private const val HERO_SIZE_DP = 148
private const val HERO_LOGO_DP = 112
private const val HERO_PRESS_SCALE = 0.88f
private const val HERO_ROTATION_STEP_DEG = 45f

/**
 * "About" screen in the expressive style: a morphing brand blob, a version chip
 * that copies build details, grouped legal facts, and a footer with the
 * application id.
 *
 * Opens from the logo on the Apps page and from Settings. All actions are
 * internal — the app does not send the user to a browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onPrivacyClick: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember { context.appVersionInfo() }
    var showLicenseDialog by remember { mutableStateOf(false) }

    val appLabel = stringResource(R.string.settings_about_name)
    val appId = context.packageName
    val versionShort = version.name?.let { stringResource(R.string.about_version_short, it) }
    val versionDetails = buildString {
        append(appLabel)
        version.name?.let { append(" v").append(it) }
        version.code?.let { append(" (").append(it).append(')') }
        append(" • ").append(appId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AboutHero()

            if (versionShort != null) {
                VersionChip(
                    versionShort = versionShort,
                    versionDetails = versionDetails,
                )
            }

            val legalItems = listOf(
                AboutLinkItem(
                    icon = painterResource(R.drawable.ic_privacy),
                    title = stringResource(R.string.about_privacy),
                    subtitle = stringResource(R.string.about_privacy_desc),
                    onClick = onPrivacyClick,
                ),
                AboutLinkItem(
                    icon = painterResource(R.drawable.ic_license),
                    title = stringResource(R.string.about_license),
                    subtitle = stringResource(R.string.about_license_desc),
                    onClick = { showLicenseDialog = true },
                ),
            )
            AnimatedEntrance(index = 1) {
                AboutLinkGroup(
                    title = stringResource(R.string.about_section_legal),
                    items = legalItems,
                )
            }

            AnimatedEntrance(index = 2) {
                AboutFooter(
                    versionDetails = versionDetails,
                    appId = appId,
                )
            }
        }
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(stringResource(R.string.about_license)) },
            text = {
                Text(
                    text = stringResource(R.string.about_license_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/**
 * Brand hero: a tappable blob that morphs between [MaterialShapes] and rotates
 * with a haptic tick, the tinted mark on top, the name and the one-line
 * description.
 */
@Composable
private fun AboutHero() {
    val haptics = rememberRutheniaHaptics()
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme

    val heroShapes = remember {
        listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.SoftBurst,
            MaterialShapes.Gem,
            MaterialShapes.Flower,
        )
    }
    var shapeIndex by remember { mutableIntStateOf(0) }
    var startShape by remember { mutableStateOf(heroShapes.first()) }
    var endShape by remember { mutableStateOf(heroShapes.first()) }
    val morph = remember(startShape, endShape) { Morph(startShape, endShape) }
    val morphProgress = remember { Animatable(1f) }
    val blobRotation = remember { Animatable(0f) }

    val entranceScale = remember { Animatable(0.4f) }
    val entranceAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { entranceScale.animateTo(1f, motionScheme.slowSpatialSpec()) }
        launch { entranceAlpha.animateTo(1f, motionScheme.defaultEffectsSpec()) }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue = if (isPressed) 1f else 0f,
            animationSpec = if (isPressed) motionScheme.fastSpatialSpec() else motionScheme.defaultSpatialSpec(),
        )
    }

    val blobColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
            .graphicsLayer {
                scaleX = entranceScale.value
                scaleY = entranceScale.value
                alpha = entranceAlpha.value
            },
    ) {
        Box(
            modifier = Modifier
                .size(HERO_SIZE_DP.dp)
                .graphicsLayer {
                    val pressScale = lerp(1f, HERO_PRESS_SCALE, pressProgress.value)
                    scaleX = pressScale
                    scaleY = pressScale
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = blobRotation.value }
                    .background(blobColor, MorphShape(morph, morphProgress.value))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.about_logo),
                    ) {
                        haptics.longPress()
                        shapeIndex = (shapeIndex + 1) % heroShapes.size
                        startShape = endShape
                        endShape = heroShapes[shapeIndex]
                        scope.launch {
                            morphProgress.snapTo(0f)
                            morphProgress.animateTo(1f, motionScheme.slowSpatialSpec())
                        }
                        scope.launch {
                            blobRotation.animateTo(
                                blobRotation.value + HERO_ROTATION_STEP_DEG,
                                motionScheme.slowSpatialSpec(),
                            )
                        }
                    },
            )
            RutheniaBrandMark(
                size = HERO_LOGO_DP.dp,
                tint = iconTint,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.settings_about_name),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** Pill with the short version; tapping copies the full build details. */
@Composable
private fun VersionChip(
    versionShort: String,
    versionDetails: String,
) {
    val context = LocalContext.current
    val haptics = rememberRutheniaHaptics()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraExtraLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable {
                    haptics.confirm()
                    context.copyToClipboard(versionDetails)
                    Toast.makeText(context, R.string.about_copied, Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = versionShort,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private data class AboutLinkItem(
    val icon: Painter,
    val title: String,
    val subtitle: String?,
    val onClick: () -> Unit,
    val iconSize: Dp = 24.dp,
)

/** Rotating accent colors of the icon containers inside a group. */
private val iconContainerColors: List<Pair<Color, Color>>
    @Composable
    get() = with(MaterialTheme.colorScheme) {
        listOf(
            primaryContainer to onPrimaryContainer,
            secondaryContainer to onSecondaryContainer,
            tertiaryContainer to onTertiaryContainer,
        )
    }

@Composable
private fun AboutSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** A section of [SegmentedListItem]s with colored icon containers. */
@Composable
private fun AboutLinkGroup(
    title: String,
    items: List<AboutLinkItem>,
    modifier: Modifier = Modifier,
) {
    val containerColors = iconContainerColors

    Column(modifier = modifier.fillMaxWidth()) {
        AboutSectionHeader(title = title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            items.forEachIndexed { index, item ->
                val (containerColor, contentColor) = containerColors[index % containerColors.size]
                SegmentedListItem(
                    onClick = item.onClick,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size),
                    colors = ListItemDefaults.segmentedColors(),
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(containerColor, MaterialTheme.shapes.largeIncreased),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = item.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(item.iconSize),
                            )
                        }
                    },
                    supportingContent = item.subtitle?.let { subtitle -> { Text(subtitle) } },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    content = { Text(item.title) },
                )
            }
        }
    }
}

/** Build details and application id; the id is clickable to copy. */
@Composable
private fun AboutFooter(
    versionDetails: String,
    appId: String,
) {
    val context = LocalContext.current
    val haptics = rememberRutheniaHaptics()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Text(
            text = versionDetails,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = appId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable {
                    haptics.confirm()
                    context.copyToClipboard(appId)
                    Toast.makeText(context, R.string.about_copied, Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.settings_about_name), text))
}

/** An arbitrary [Shape] built from a [Morph] at the given progress. */
private class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f))
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}
