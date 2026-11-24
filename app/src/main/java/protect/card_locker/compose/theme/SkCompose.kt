package protect.card_locker.compose.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import protect.card_locker.R
import protect.card_locker.shiroikuma.SkIconRole
import protect.card_locker.shiroikuma.SkSlot
import protect.card_locker.shiroikuma.SkTheme

/**
 * shiroikuma-nekokan fork — the black-yellow theme's parts for the Compose screens (About, the
 * image viewer): the pill button, the menu row and the dialog frame, all drawn from the same
 * colour roles as the View-based screens.
 */

@Composable
fun skRole(slot: SkSlot): Color = Color(SkTheme.color(LocalContext.current, slot))

@Composable
fun skRole(role: SkIconRole): Color = Color(SkTheme.color(LocalContext.current, role))

/**
 * The pill: fully round, never filled. The main action of a block has a 3 dp frame and bold
 * text, every other a 1.25 dp frame and normal text.
 */
@Composable
fun SkPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    main: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        border = BorderStroke(if (main) 3.dp else 1.25.dp, skRole(SkSlot.PILL_FRAME)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = skRole(SkSlot.PILL_GROUND),
            contentColor = skRole(SkSlot.BUTTON_TEXT),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(text = text, fontWeight = if (main) FontWeight.Bold else FontWeight.Normal)
    }
}

/**
 * A row of a menu: a full-width outlined pill with its icon inside at the left, coloured by
 * what the row does, and a › at the right when it opens something.
 */
@Composable
fun SkMenuRow(
    text: String,
    icon: Int,
    role: SkIconRole,
    onClick: () -> Unit,
    opens: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(CircleShape)
            .border(1.25.dp, skRole(SkSlot.MENU_ROW_FRAME), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = skRole(role),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, color = skRole(SkSlot.MENU_LABEL), modifier = Modifier.weight(1f))
        if (opens) {
            Icon(
                painter = painterResource(R.drawable.sk_ic_chevron),
                contentDescription = null,
                tint = skRole(SkSlot.MENU_LABEL),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The frame of a dialog or a popup: 1 dp round the panel, in its frame role. */
@Composable
fun Modifier.skPanelFrame(frame: SkSlot, radiusDp: Int): Modifier =
    border(1.dp, skRole(frame), RoundedCornerShape(radiusDp.dp))
