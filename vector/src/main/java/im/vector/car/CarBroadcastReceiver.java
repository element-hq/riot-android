package im.vector.car;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.RemoteInput;

import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.message.Message;

import im.vector.Matrix;
import im.vector.notifications.NotificationUtils;

public class CarBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = CarBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String roomId = intent.getStringExtra(NotificationUtils.EXTRA_ROOM_ID);
        if (NotificationUtils.ACTION_MESSAGE_HEARD.equals(intent.getAction())) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
        } else if (NotificationUtils.ACTION_MESSAGE_REPLY.equals(intent.getAction())) {
            final CharSequence reply = getMessageText(intent);

            final MXSession session = Matrix.getInstance(context).getDefaultSession();
            if (session == null) {
                return;
            }

            final Room room = session.getDataHandler().getRoom(roomId);
            final Message message = new Message();
            try {
                message.body = reply.toString();
            } catch (Exception e) {
                Log.e(TAG, "reply.toString fails " + e.getLocalizedMessage());
            }
            message.msgtype = Message.MSGTYPE_TEXT;

            Event event = new Event(message, session.getCredentials().userId, room.getRoomId());
            room.storeOutgoingEvent(event);
            room.sendEvent(event, new ApiCallback<Void>() {
                @Override
                public void onSuccess(final Void info) {
                    Log.d(TAG, "reply sent!");
                }

                @Override
                public void onNetworkError(final Exception e) {
                    Log.e(TAG, "sending reply failed!", e);
                }

                @Override
                public void onMatrixError(final MatrixError e) {
                    Log.e(TAG, "sending reply failed with matrix error " + e.errcode + " " + e.error);
                }

                @Override
                public void onUnexpectedError(final Exception e) {
                    Log.e(TAG, "sending reply failed!", e);
                }
            });
        }
    }

    /**
     * Get the message text from the intent.
     * Note that you should call
     * RemoteInput.getResultsFromIntent() to process
     * the RemoteInput.
     */
    private CharSequence getMessageText(Intent intent) {
        Bundle remoteInput =
                RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(NotificationUtils.CAR_VOICE_REPLY_KEY);
        }
        return null;
    }
}
