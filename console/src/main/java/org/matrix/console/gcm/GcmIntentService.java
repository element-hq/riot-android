package org.matrix.console.gcm;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.matrix.console.ViewedRoomTracker;
import org.matrix.console.util.NotificationUtils;

/**
 * Service that receives messages from GCM.
 */
public class GcmIntentService extends IntentService {

    private static final String LOG_TAG = "GcmIntentService";
    private static final int MSG_NOTIFICATION_ID = 43;

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        String messageType = gcm.getMessageType(intent);

        if (messageType.equals(GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE)) {
            handlePushNotification(extras);
        }

        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    private void handlePushNotification(Bundle bundle) {
        final String roomId = bundle.getString("room_id");
        final String roomName = bundle.getString("room_name");

        // Just don't bing for the room the user's currently in
        if ((roomId != null) && roomId.equals(ViewedRoomTracker.getInstance().getViewedRoomId())) {
            return;
        }

        String from = bundle.getString("sender");

        final String body = bundle.getString("body");
        // FIXME: Support event contents with no body
        if (body == null) {
            return;
        }

        Notification n = NotificationUtils.buildMessageNotification(this, from, null, false, body, roomId, roomName, true);
        NotificationManager nm =(NotificationManager) GcmIntentService.this
                .getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(MSG_NOTIFICATION_ID, n);
    }
}
