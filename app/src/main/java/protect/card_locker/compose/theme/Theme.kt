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

    // shiroikuma-nekokan fork: Compose screens (About, image viewer) follow the
    // 白い熊 猫管 UI foundation colors instead of dynamic/light/dark schemes.
    val background = Color(SkTheme.color(context, SkSlot.BACKGROUND))
    val text = Color(SkTheme.color(context, SkSlot.TEXT))
    val textSecondary = Color(SkTheme.color(context, SkSlot.TEXT_SECONDARY))
    val accent = Color(SkTheme.color(context, SkSlot.ACCENT))
    val onAccent = Color(SkTheme.contrastColor(SkTheme.color(context, SkSlot.ACCENT)))

    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = background,
        onSecondaryContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = background,
        onSurfaceVariant = textSecondary,
        outline = accent,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
