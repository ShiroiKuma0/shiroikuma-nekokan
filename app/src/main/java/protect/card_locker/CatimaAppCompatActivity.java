package protect.card_locker;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import protect.card_locker.shiroikuma.SkSlot;
import protect.card_locker.shiroikuma.SkStyler;
import protect.card_locker.shiroikuma.SkTheme;

public class CatimaAppCompatActivity extends AppCompatActivity {
    protected boolean activityOverridesNavBarColor = false;

    @Override
    protected void attachBaseContext(Context base) {
        // Apply chosen language
        super.attachBaseContext(Utils.updateBaseContextLocale(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        Utils.patchColors(this);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // material 3 designer does not consider status bar colors
        // XXX changing this in onCreate causes issues with the splash screen activity, so doing this here
        Window window = getWindow();
        if (window != null) {
            View decorView = window.getDecorView();
            WindowInsetsControllerCompat wic = new WindowInsetsControllerCompat(window, decorView);
            // shiroikuma-nekokan fork: the background is dark regardless of day/night mode.
            wic.setAppearanceLightStatusBars(false);
            window.setStatusBarColor(Color.TRANSPARENT);
        }
        // XXX android 9 and below has a nasty rendering bug if the theme was patched earlier
        Utils.postPatchColors(this);
        // shiroikuma-nekokan fork: apply the 白い熊 猫管 UI colors/fonts to the view tree.
        SkStyler.INSTANCE.apply(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!activityOverridesNavBarColor) {
            Utils.setNavigationBarColor(this, null, SkTheme.INSTANCE.color(this, SkSlot.BACKGROUND), false);
        }
        // shiroikuma-nekokan fork: re-apply on return (e.g. after edits in the UI page).
        SkStyler.INSTANCE.apply(this);
    }

    protected void enableToolbarBackButton() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }
}
