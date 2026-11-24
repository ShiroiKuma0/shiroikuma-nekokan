package protect.card_locker.compose

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import protect.card_locker.OpenWebLinkHandler
import protect.card_locker.R
import protect.card_locker.compose.theme.SkPillButton
import protect.card_locker.compose.theme.skPanelFrame
import protect.card_locker.compose.theme.skRole
import protect.card_locker.shiroikuma.SkIconRole
import protect.card_locker.shiroikuma.SkSlot

@Composable
fun CatimaAboutSection(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onClickUrl: String? = null,
    onClickDialogText: AnnotatedString? = null,
) {
    val activity = LocalActivity.current

    val openDialog = remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable {
                if (onClickDialogText != null) {
                    openDialog.value = true
                } else if (onClickUrl != null) {
                    OpenWebLinkHandler().openBrowser(activity, onClickUrl)
                }
            }
            .semantics(mergeDescendants = true) {}
    ) {
        // shiroikuma-nekokan fork: every row has an icon, coloured by what the row does — a
        // row that leaves for the browser green, one that shows a text here the accent.
        val opensBrowser = onClickDialogText == null && onClickUrl != null
        Icon(
            painter = painterResource(
                if (opensBrowser) R.drawable.sk_ic_open_in_new else R.drawable.ic_baseline_info_24
            ),
            contentDescription = null,
            tint = skRole(if (opensBrowser) SkIconRole.OPEN else SkIconRole.VIEW),
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 12.dp).size(24.dp),
        )
        Column(modifier = Modifier.weight(1F)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = message)
        }
        Icon(
            painter = painterResource(R.drawable.sk_ic_chevron),
            contentDescription = null,
            tint = skRole(SkSlot.TEXT),
            modifier = Modifier.align(Alignment.CenterVertically).size(20.dp),
        )
    }
    if (openDialog.value && onClickDialogText != null) {
        AlertDialog(
            icon = {},
            title = {
                Text(text = title)
            },
            text = {
                Text(
                    text = onClickDialogText,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            onDismissRequest = {
                openDialog.value = false
            },
            // shiroikuma-nekokan fork: both buttons are pills, OK the main one; the panel is
            // drawn from the dialog roles.
            confirmButton = {
                SkPillButton(
                    text = stringResource(R.string.ok),
                    onClick = {
                        openDialog.value = false
                    },
                    main = true,
                )
            },
            dismissButton = {
                if (onClickUrl != null) {
                    SkPillButton(
                        text = stringResource(R.string.view_online),
                        onClick = {
                            OpenWebLinkHandler().openBrowser(activity, onClickUrl)
                        },
                    )
                }
            },
            modifier = Modifier.skPanelFrame(SkSlot.DIALOG_FRAME, 28),
            containerColor = skRole(SkSlot.DIALOG_GROUND),
            titleContentColor = skRole(SkSlot.DIALOG_TITLE),
            textContentColor = skRole(SkSlot.DIALOG_TEXT),
        )
    }
}