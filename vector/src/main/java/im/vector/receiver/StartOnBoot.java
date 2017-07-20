package im.vector.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import im.vector.activity.CommonActivityUtils;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.Matrix;
import org.matrix.androidsdk.MXSession;

public class StartOnBoot extends BroadcastReceiver {
    private static final String LOG_TAG = "StartOnBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "received " + intent.getAction());
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            MXSession session = Matrix.getInstance(context).getDefaultSession();
            GcmRegistrationManager gcmMgr = Matrix.getInstance(context).getSharedGCMRegistrationManager();
            if (null != session && gcmMgr.isBackgroundSyncAllowed() && gcmMgr.isStartBackgroundSyncOnBoot() && !gcmMgr.hasRegistrationToken()) {
                Log.d(LOG_TAG, "start EventStreamService");
                CommonActivityUtils.startEventStreamService(context);
            }
        }
    }
}
