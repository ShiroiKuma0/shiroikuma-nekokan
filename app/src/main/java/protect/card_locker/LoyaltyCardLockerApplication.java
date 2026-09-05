package protect.card_locker;

import android.app.Application;
import android.content.Intent;

import androidx.appcompat.app.AppCompatDelegate;

import protect.card_locker.preferences.Settings;
import protect.card_locker.wearos.WearSyncServiceManager;

public class LoyaltyCardLockerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // shiroikuma-nekokan fork: upstream initialised the ACRA crash reporter here. The fork
        // ships no crash reporter at all, so nothing is initialised.

        // Set theme
        Settings settings = new Settings(this);
        AppCompatDelegate.setDefaultNightMode(settings.getTheme());

        // Start Bluetooth server for Wear OS companion if enabled.
        // The service checks BLUETOOTH_CONNECT itself and stops if the permission is missing.
        // The permission is requested from the launcher Activity when the UI resumes.
        WearSyncServiceManager.INSTANCE.synchronize(this, null);
    }
}
