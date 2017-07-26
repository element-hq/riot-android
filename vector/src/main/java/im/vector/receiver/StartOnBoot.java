package im.vector.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import im.vector.activity.CommonActivityUtils;

public class StartOnBoot extends BroadcastReceiver {
    private static final String LOG_TAG = "StartOnBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.i(LOG_TAG, "Boot completed. Starting Riot in the background...");
            CommonActivityUtils.startEventStreamService(context);
        }
    }
}
