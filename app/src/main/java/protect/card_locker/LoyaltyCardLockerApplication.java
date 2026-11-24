package protect.card_locker;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import protect.card_locker.preferences.Settings;
import protect.card_locker.shiroikuma.SkAppContext;
import protect.card_locker.shiroikuma.SkPalette;
import protect.card_locker.shiroikuma.SkToast;
import protect.card_locker.wearos.BluetoothStateReceiver;
import protect.card_locker.wearos.WearSyncServiceManager;

public class LoyaltyCardLockerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // shiroikuma-nekokan fork: upstream initialised the ACRA crash reporter here. The fork
        // ships no crash reporter at all, so nothing is initialised.

        // shiroikuma-nekokan fork: the colour roles, laid over the resources before anything
        // reads a colour from them (see shiroikuma/SkPalette.kt).
        SkAppContext.set(this);
        SkPalette.apply(this);
        SkToast.watch(this);

        // Set theme
        Settings settings = new Settings(this);
        AppCompatDelegate.setDefaultNightMode(settings.getTheme());

        // Start Bluetooth server for Wear OS companion if enabled.
        // The service checks BLUETOOTH_CONNECT itself and stops if the permission is missing.
        // The permission is requested from the launcher Activity when the UI resumes.
        WearSyncServiceManager.INSTANCE.synchronize(this, null);

        // Watch Bluetooth for state changes so we can start the sync service on Bluetooth start
        // This may fail if the Catima background process gets killed,
        // so for the future it's worth considering potentially extending the sync manager to keep
        // regardless of Bluetooth state (at the cost of having the notification visible more often)
        ContextCompat.registerReceiver(
                this,
                new BluetoothStateReceiver(),
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }
}
