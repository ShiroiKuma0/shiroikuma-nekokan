package protect.card_locker.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import protect.card_locker.shiroikuma.SkSlot
import protect.card_locker.shiroikuma.SkTheme

@Composable
fun CatimaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current

    // shiroikuma-nekokan fork: Compose screens (About, image viewer) read the same colour
    // roles as every other screen instead of dynamic/light/dark schemes. Every entry of the
    // scheme is a role; nothing here is a literal.
    fun role(slot: SkSlot) = Color(SkTheme.color(context, slot))

    val colorScheme = darkColorScheme(
        primary = role(SkSlot.ACCENT),
        onPrimary = role(SkSlot.BACKGROUND),
        primaryContainer = role(SkSlot.LIST_SELECTED),
        onPrimaryContainer = role(SkSlot.LIST_SELECTED_TEXT),
        inversePrimary = role(SkSlot.SNACKBAR_ACTION),
        secondary = role(SkSlot.ACCENT),
        onSecondary = role(SkSlot.BACKGROUND),
        secondaryContainer = role(SkSlot.LIST_SELECTED),
        onSecondaryContainer = role(SkSlot.LIST_SELECTED_TEXT),
        tertiary = role(SkSlot.ACCENT),
        onTertiary = role(SkSlot.BACKGROUND),
        tertiaryContainer = role(SkSlot.LIST_SELECTED),
        onTertiaryContainer = role(SkSlot.LIST_SELECTED_TEXT),
        background = role(SkSlot.BACKGROUND),
        onBackground = role(SkSlot.TEXT),
        surface = role(SkSlot.BACKGROUND),
        onSurface = role(SkSlot.TEXT),
        surfaceVariant = role(SkSlot.BACKGROUND),
        onSurfaceVariant = role(SkSlot.TEXT_SECONDARY),
        surfaceTint = role(SkSlot.BACKGROUND),
        inverseSurface = role(SkSlot.SNACKBAR_GROUND),
        inverseOnSurface = role(SkSlot.SNACKBAR_TEXT),
        error = role(SkSlot.DANGER),
        onError = role(SkSlot.BACKGROUND),
        errorContainer = role(SkSlot.BACKGROUND),
        onErrorContainer = role(SkSlot.DANGER),
        outline = role(SkSlot.FIELD_FRAME_IDLE),
        outlineVariant = role(SkSlot.LIST_DIVIDER),
        surfaceBright = role(SkSlot.BACKGROUND),
        surfaceDim = role(SkSlot.BACKGROUND),
        surfaceContainer = role(SkSlot.BACKGROUND),
        surfaceContainerHigh = role(SkSlot.BACKGROUND),
        surfaceContainerHighest = role(SkSlot.BACKGROUND),
        surfaceContainerLow = role(SkSlot.BACKGROUND),
        surfaceContainerLowest = role(SkSlot.BACKGROUND),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
