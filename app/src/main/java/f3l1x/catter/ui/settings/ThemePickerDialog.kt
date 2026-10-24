package f3l1x.catter.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import f3l1x.catter.ui.theme.DEFAULT_THEME_ID
import f3l1x.catter.ui.theme.ThemeFamily
import f3l1x.catter.ui.theme.ThemeVariant
import f3l1x.catter.ui.theme.Themes
import io.tabler.icons.TablerIcons

@Composable
fun ThemePickerDialog(
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var family by remember { mutableStateOf<ThemeFamily?>(null) }
    val current = family

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = current != null,
                        enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.7f),
                        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.7f),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { family = null }
                                .padding(6.dp),
                        ) {
                            Icon(
                                imageVector = TablerIcons["arrow-left"],
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    Text(
                        text = current?.label ?: "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onDismissRequest)
                            .padding(6.dp),
                    ) {
                        Icon(
                            imageVector = TablerIcons.X,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        val direction = if (targetState != null) 1 else -1
                        (slideInHorizontally(tween(280)) { direction * it / 4 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(280)) { -direction * it / 4 } + fadeOut(tween(150)))
                    },
                    label = "theme_picker_page",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(368.dp),
                ) { page ->
                    if (page == null) {
                        FamilyGrid(
                            selectedId = selectedId,
                            onOpen = { family = it },
                        )
                    } else {
                        PresetGrid(
                            family = page,
                            selectedId = selectedId,
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyGrid(selectedId: String?, onOpen: (ThemeFamily) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(Themes.families) { family ->
            val variants = Themes.of(family)
            FamilyCard(
                family = family,
                variants = variants,
                selected = variants.any { it.id == selectedId },
                onClick = { onOpen(family) },
            )
        }
    }
}

@Composable
private fun FamilyCard(
    family: ThemeFamily,
    variants: List<ThemeVariant>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val preview = variants.first()
    val scheme = preview.scheme

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.background,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = family.label,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                variants.take(6).forEach { variant ->
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(variant.scheme.primary),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (variants.any { !it.isDark }) {
                    Tag("Light", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
                }
                if (variants.any { it.isDark }) {
                    Tag("Dark", scheme.onSurfaceVariant, scheme.surfaceContainerHighest)
                }
            }
        }
    }
}

@Composable
private fun PresetGrid(family: ThemeFamily, selectedId: String?, onSelect: (String) -> Unit) {
    val variants = Themes.of(family)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(variants, key = { it.id }) { variant ->
            PresetCard(
                variant = variant,
                selected = variant.id == (selectedId ?: DEFAULT_THEME_ID),
                onClick = { onSelect(variant.id) },
            )
        }
    }
}

@Composable
private fun PresetCard(variant: ThemeVariant, selected: Boolean, onClick: () -> Unit) {
    val scheme = variant.scheme

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.background,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = variant.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = TablerIcons["check"],
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
                Tag(
                    text = if (variant.isDark) "Dark" else "Light",
                    color = scheme.onSurfaceVariant,
                    background = scheme.surfaceContainerHighest,
                )
                if (variant.id == DEFAULT_THEME_ID) {
                    Tag(
                        text = "Default",
                        color = scheme.primary,
                        background = scheme.primary.copy(alpha = 0.18f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color, background: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
