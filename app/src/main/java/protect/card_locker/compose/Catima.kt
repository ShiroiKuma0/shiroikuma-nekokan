package protect.card_locker.compose

import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import protect.card_locker.R
import protect.card_locker.compose.theme.skRole
import protect.card_locker.shiroikuma.SkSlot
import protect.card_locker.preferences.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatimaTopAppBar(title: String,
                    onBackPressedDispatcher: OnBackPressedDispatcher?,
                    actions : @Composable RowScope.() -> Unit = {}) {
    // shiroikuma-nekokan fork: the bar's ground, title and icons are the top-bar roles.
    // (Upstream chose between its default and a literal black for the OLED setting here.)
    val appBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = skRole(SkSlot.TOOLBAR_BACKGROUND),
        scrolledContainerColor = skRole(SkSlot.TOOLBAR_BACKGROUND),
        titleContentColor = skRole(SkSlot.TOOLBAR_TITLE),
        navigationIconContentColor = skRole(SkSlot.TOOLBAR_ICON),
        actionIconContentColor = skRole(SkSlot.TOOLBAR_ICON),
    )

    TopAppBar(
        modifier = Modifier.testTag("topbar_catima"),
        title = { Text(text = title) },
        colors = appBarColors,
        navigationIcon = {
            if (onBackPressedDispatcher != null) {
                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        },
        actions = actions
    )
}