package im.vector.util;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.RemoteInput;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;

import im.vector.R;
import im.vector.activity.LockScreenActivity;
import im.vector.activity.VectorRoomActivity;

import java.util.Random;

/**
 * Util class for creating notifications.
 */
public class NotificationUtils {

    public static final String QUICK_LAUNCH_ACTION = "EventStreamService.QUICK_LAUNCH_ACTION";
    public static final String TAP_TO_VIEW_ACTION = "EventStreamService.TAP_TO_VIEW_ACTION";
    public static final String CAR_VOICE_REPLY_KEY = "EventStreamService.CAR_VOICE_REPLY_KEY" ;
    public static final String ACTION_MESSAGE_HEARD = "ACTION_MESSAGE_HEARD";
    public static final String ACTION_MESSAGE_REPLY = "ACTION_MESSAGE_REPLY";
    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";

    // the bubble radius is computed for 99
    static int mUnreadBubbleWidth = -1;


    public static Notification buildCallNotification(Context context, String roomName, String roomId, String matrixId, String callId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setWhen(System.currentTimeMillis());

        builder.setContentTitle(roomName);
        builder.setContentText(context.getString(R.string.call_in_progress));
        builder.setSmallIcon(R.drawable.logo_transparent);

        // Build the pending intent for when the notification is clicked
        Intent roomIntent = new Intent(context, VectorRoomActivity.class);
        roomIntent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
        roomIntent.putExtra(VectorRoomActivity.EXTRA_MATRIX_ID, matrixId);
        roomIntent.putExtra(VectorRoomActivity.EXTRA_START_CALL_ID, callId);

        // Recreate the back stack
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context)
                .addParentStack(VectorRoomActivity.class)
                .addNextIntent(roomIntent);


        // android 4.3 issue
        // use a generator for the private requestCode.
        // When using 0, the intent is not created/launched when the user taps on the notification.
        //
        PendingIntent pendingIntent = stackBuilder.getPendingIntent((new Random()).nextInt(1000), PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        Notification n = builder.build();
        n.flags |= Notification.FLAG_SHOW_LIGHTS;
        n.defaults |= Notification.DEFAULT_LIGHTS;

        return n;
    }

    private static Bitmap createSquareBitmap(Bitmap bitmap) {
        Bitmap resizedBitmap = null;

        if (null != bitmap) {
            // convert the bitmap to a square bitmap
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            if (width == height) {
                resizedBitmap = bitmap;
            }
            // larger than high
            else if (width > height) {
                resizedBitmap = Bitmap.createBitmap(
                        bitmap,
                        (width - height) / 2,
                        0,
                        height,
                        height
                );

            }
            // higher than large
            else {
                resizedBitmap = Bitmap.createBitmap(
                        bitmap,
                        0,
                        (height - width) / 2,
                        width,
                        width
                );
            }
        }

        return resizedBitmap;
    }

    public static Notification buildMessageNotification(
            Context context, String from, String matrixId, String callId, Boolean displayMatrixId, Bitmap largeIcon, int globalUnseen, int memberUnseen, String body, String roomId, String roomName,
            boolean shouldPlaySound) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setWhen(System.currentTimeMillis());

        if (!TextUtils.isEmpty(from)) {
            // don't display the room name for 1:1 room notifications.
            if (!TextUtils.isEmpty(roomName) && !roomName.equals(from)) {
                builder.setContentTitle(from + " (" + roomName + ")");
            } else {
                builder.setContentTitle(from);
            }
        } else {
            builder.setContentTitle(roomName);
        }

        builder.setContentText(body);
        builder.setAutoCancel(true);
        builder.setSmallIcon(R.drawable.logo_transparent);

        if (null != largeIcon) {
            largeIcon = createSquareBitmap(largeIcon);

            // add a bubble in the top right
            if (0 != memberUnseen) {
                try {
                    android.graphics.Bitmap.Config bitmapConfig = largeIcon.getConfig();

                    // set default bitmap config if none
                    if(bitmapConfig == null) {
                        bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
                    }

                    // setLargeIcon must used a 64 * 64 pixels bitmap
                    // rescale to have the same text UI.
                    float densityScale = context.getResources().getDisplayMetrics().density;
                    int side = (int)(64 * densityScale);

                    Bitmap bitmapCopy = Bitmap.createBitmap(side, side, bitmapConfig);
                    Canvas canvas = new Canvas(bitmapCopy);

                    // resize the bitmap to fill in size
                    int bitmapWidth = largeIcon.getWidth();
                    int bitmapHeight = largeIcon.getHeight();

                    float scale = Math.min((float) canvas.getWidth() / (float) bitmapWidth, (float) canvas.getHeight() / (float) bitmapHeight);

                    int scaledWidth = (int) (bitmapWidth * scale);
                    int scaledHeight = (int) (bitmapHeight * scale);

                    Bitmap rescaledBitmap = Bitmap.createScaledBitmap(largeIcon, scaledWidth, scaledHeight, true);
                    canvas.drawBitmap(rescaledBitmap, (side - scaledWidth) / 2, (side - scaledHeight) / 2, null);

                    String text = "" + memberUnseen;

                    // prepare the text drawing
                    Paint textPaint = new Paint();
                    textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(10 * densityScale);

                    // get its size
                    Rect textBounds = new Rect();

                    if (-1 == mUnreadBubbleWidth) {
                        textPaint.getTextBounds("99", 0, 2, textBounds);
                        mUnreadBubbleWidth = textBounds.width();
                    }

                    textPaint.getTextBounds(text, 0, text.length(), textBounds);

                    // draw a red circle
                    int radius = mUnreadBubbleWidth;
                    Paint paint = new Paint();
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.RED);
                    canvas.drawCircle(canvas.getWidth() - radius, radius,  radius , paint);

                    // draw the text
                    canvas.drawText(text, canvas.getWidth() - textBounds.width() - (radius - (textBounds.width() / 2)), -textBounds.top + (radius - (-textBounds.top / 2)), textPaint);

                    // get the new bitmap
                    largeIcon = bitmapCopy;
                } catch (Exception e) {
                }
            }

            builder.setLargeIcon(largeIcon);
        }

        String name = ": ";
        if(!TextUtils.isEmpty(roomName)) {
            name = " (" + roomName + "): ";
        }

        if (displayMatrixId) {
            from = "[" + matrixId + "]\n" + from;
        }

        builder.setTicker(from + name + body);

        // Build the pending intent for when the notification is clicked
        Intent roomIntent = new Intent(context, VectorRoomActivity.class);
        roomIntent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId);

        if (null != matrixId) {
            roomIntent.putExtra(VectorRoomActivity.EXTRA_MATRIX_ID, matrixId);
        }

        if (null != callId) {
            roomIntent.putExtra(VectorRoomActivity.EXTRA_START_CALL_ID, callId);
        }

        // Recreate the back stack
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context)
                .addParentStack(VectorRoomActivity.class)
                .addNextIntent(roomIntent);


        // android 4.3 issue
        // use a generator for the private requestCode.
        // When using 0, the intent is not created/launched when the user taps on the notification.
        //
        PendingIntent pendingIntent = stackBuilder.getPendingIntent((new Random()).nextInt(1000), PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        // display the message with more than 1 lines when the device supports it
        NotificationCompat.BigTextStyle textStyle = new NotificationCompat.BigTextStyle();
        textStyle.bigText(from + ":" + body);
        builder.setStyle(textStyle);

        // do not offer to quick respond if the user did not dismiss the previous one
        if (!LockScreenActivity.isDisplayingALockScreenActivity() && (null == callId)) {
            // offer to type a quick answer (i.e. without launching the application)
            Intent quickReplyIntent = new Intent(context, LockScreenActivity.class);
            quickReplyIntent.putExtra(LockScreenActivity.EXTRA_ROOM_ID, roomId);
            quickReplyIntent.putExtra(LockScreenActivity.EXTRA_SENDER_NAME, from);
            quickReplyIntent.putExtra(LockScreenActivity.EXTRA_MESSAGE_BODY, body);

            if (null != matrixId) {
                quickReplyIntent.putExtra(LockScreenActivity.EXTRA_MATRIX_ID, matrixId);
            }

            // the action must be unique else the parameters are ignored
            quickReplyIntent.setAction(QUICK_LAUNCH_ACTION + ((int) (System.currentTimeMillis())));
            PendingIntent pIntent = PendingIntent.getActivity(context, 0, quickReplyIntent, 0);
            builder.addAction(
                    R.drawable.ic_material_mode_edit_green_vector,
                    context.getString(R.string.action_quick_reply),
                    pIntent);

            // Build the pending intent for when the notification is clicked
            Intent roomIntentTap = new Intent(context, VectorRoomActivity.class);
            roomIntentTap.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
            // the action must be unique else the parameters are ignored
            roomIntentTap.setAction(TAP_TO_VIEW_ACTION + ((int) (System.currentTimeMillis())));
            // Recreate the back stack
            TaskStackBuilder stackBuildertap = TaskStackBuilder.create(context)
                    .addParentStack(VectorRoomActivity.class)
                    .addNextIntent(roomIntentTap);
            builder.addAction(
                    R.drawable.ic_material_message_green_vector,
                    context.getString(R.string.action_open),
                    stackBuildertap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        extendForCar(context, builder, roomId, roomName, from, body);

        Notification n = builder.build();
        n.flags |= Notification.FLAG_SHOW_LIGHTS;
        n.defaults |= Notification.DEFAULT_LIGHTS;

        if (shouldPlaySound) {
            n.defaults |= Notification.DEFAULT_SOUND;
        }

        // some devices crash if this field is not set
        // even if it is deprecated
        n.setLatestEventInfo(context, from, body, pendingIntent);

        return n;
    }

    private static void extendForCar(Context context, NotificationCompat.Builder builder, String roomId, String roomName, String from, String body) {
        int carConversationId = roomId.hashCode();
        Intent msgHeardIntent = new Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(ACTION_MESSAGE_HEARD)
                .putExtra(EXTRA_ROOM_ID, roomId);

        PendingIntent msgHeardPendingIntent =
                PendingIntent.getBroadcast(context,
                        carConversationId,
                        msgHeardIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        Intent msgReplyIntent = new Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(ACTION_MESSAGE_REPLY)
                .putExtra(EXTRA_ROOM_ID, roomId);

        PendingIntent msgReplyPendingIntent = PendingIntent.getBroadcast(
                context,
                carConversationId,
                msgReplyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Build a RemoteInput for receiving voice input in a Car Notification
        RemoteInput remoteInput = new RemoteInput.Builder(CAR_VOICE_REPLY_KEY)
                .setLabel(context.getString(R.string.action_quick_reply))
                .build();

        // Create an unread conversation object to organize a group of messages
        // from a room.
        NotificationCompat.CarExtender.UnreadConversation.Builder unreadConvBuilder =
                new NotificationCompat.CarExtender.UnreadConversation.Builder(roomName)
                        .setReadPendingIntent(msgHeardPendingIntent)
                        .setReplyAction(msgReplyPendingIntent, remoteInput);

        unreadConvBuilder.addMessage(context.getString(R.string.user_says_body, from, body))
                .setLatestTimestamp(System.currentTimeMillis());
        builder.extend(new NotificationCompat.CarExtender()
                .setUnreadConversation(unreadConvBuilder.build()));

    }

    private NotificationUtils() {}
}
