package protect.card_locker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import protect.card_locker.shiroikuma.SkToast; // shiroikuma-nekokan fork

public class OpenWebLinkHandler {

    private static final String TAG = "Catima";

    public void openBrowser(Activity activity, String url) {
        if (url == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            SkToast.makeText(activity, R.string.failedToOpenUrl, Toast.LENGTH_LONG).show();
            Log.e(TAG, "No activity found to handle intent", e);
        }
    }
}
