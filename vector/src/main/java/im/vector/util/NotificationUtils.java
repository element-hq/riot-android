/*
 * Copyright 2016 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;
import android.util.Log;

import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.JoinScreenActivity;
import im.vector.activity.LockScreenActivity;
import im.vector.activity.VectorFakeRoomPreviewActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorRoomActivity;

import java.lang.reflect.Method;
import java.util.Random;

/**
 * Util class for creating notifications.
 */
public class NotificationUtils {
    private static final String LOG_TAG = "NotificationUtils";

    public static final String QUICK_LAUNCH_ACTION = "EventStreamService.QUICK_LAUNCH_ACTION";
    public static final String TAP_TO_VIEW_ACTION = "EventStreamService.TAP_TO_VIEW_ACTION";
    public static final String CAR_VOICE_REPLY_KEY = "EventStreamService.CAR_VOICE_REPLY_KEY" ;
    public static final String ACTION_MESSAGE_HEARD = "ACTION_MESSAGE_HEARD";
    public static final String ACTION_MESSAGE_REPLY = "ACTION_MESSAGE_REPLY";
    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";

    // the bubble radius is computed for 99
    static private int mUnreadBubbleWidth = -1;

    /**
     * Build an incoming call notification.
     * This notification starts the VectorHomeActivity which is in charge of centralizing the incoming call flow.
     * @param context the context.
     * @param roomName the room name in which the call is pending.
     * @param matrixId the matrix id
     * @param callId the call id.
     * @return the call notification.
     */
    public static Notification buildIncomingCallNotification(Context context, String roomName, String matrixId, String callId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setWhen(System.currentTimeMillis());

        builder.setContentTitle(roomName);
        builder.setContentText(context.getString(R.string.incoming_call));
        builder.setSmallIcon(R.drawable.incoming_call_notification_transparent);

        // clear the activity stack to home activity
        Intent intent = new Intent(context, VectorHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(VectorHomeActivity.EXTRA_CALL_SESSION_ID, matrixId);
        intent.putExtra(VectorHomeActivity.EXTRA_CALL_ID, callId);

        // Recreate the back stack
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context)
                .addParentStack(VectorHomeActivity.class)
                .addNextIntent(intent);


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

    /**
     * Build a pending call notification
     * @param context the context.
     * @param roomName the room name in which the call is pending.
     * @param roomId the room Id
     * @param matrixId the matrix id
     * @param callId the call id.
     * @return the call notification.
     */
    public static Notification buildPendingCallNotification(Context context, String roomName, String roomId, String matrixId, String callId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setWhen(System.currentTimeMillis());

        builder.setContentTitle(roomName);
        builder.setContentText(context.getString(R.string.call_in_progress));
        builder.setSmallIcon(R.drawable.incoming_call_notification_transparent);

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

    /**
     * Create a square bitmap from another one.
     * It is centered.
     * @param bitmap the bitmap to "square"
     * @return the squared bitmap
     */
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

    /**
     * Build a message notification.
     * @param context the context
     * @param from the sender
     * @param matrixId the user account id;
     * @param displayMatrixId true to display the matrix id
     * @param largeIcon the notification icon
     * @param unseenNotifiedRoomsCount the number of notified rooms
     * @param body the message body
     * @param roomId the room id
     * @param roomName the room name
     * @param shouldPlaySound true when the notification as sound.
     * @param isInvitationEvent true if it is an invitation notification
     * @return the notification
     */
    public static Notification buildMessageNotification(
            Context context,
            String from,
            String matrixId,
            boolean displayMatrixId,
            Bitmap largeIcon,
            int unseenNotifiedRoomsCount,
            String body,
            String roomId,
            String roomName,
            boolean shouldPlaySound,
            boolean isInvitationEvent) {

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
        builder.setSmallIcon(R.drawable.message_notification_transparent);

        if (null != largeIcon) {
            largeIcon = createSquareBitmap(largeIcon);

            // add a bubble in the top right
            if (0 != unseenNotifiedRoomsCount) {
                try {
                    android.graphics.Bitmap.Config bitmapConfig = largeIcon.getConfig();

                    // set default bitmap config if none
                    if (bitmapConfig == null) {
                        bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
                    }

                    // setLargeIcon must used a 64 * 64 pixels bitmap
                    // rescale to have the same text UI.
                    float densityScale = context.getResources().getDisplayMetrics().density;
                    int side = (int) (64 * densityScale);

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

                    String text = "" + unseenNotifiedRoomsCount;

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
                    canvas.drawCircle(canvas.getWidth() - radius, radius, radius, paint);

                    // draw the text
                    canvas.drawText(text, canvas.getWidth() - textBounds.width() - (radius - (textBounds.width() / 2)), -textBounds.top + (radius - (-textBounds.top / 2)), textPaint);

                    // get the new bitmap
                    largeIcon = bitmapCopy;
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## buildMessageNotification(): Exception Msg=" + e.getMessage());
                }
            }

            builder.setLargeIcon(largeIcon);
        }

        String name = ": ";
        if (!TextUtils.isEmpty(roomName)) {
            name = " (" + roomName + "): ";
        }

        if (displayMatrixId) {
            from = "[" + matrixId + "]\n" + from;
        }

        builder.setTicker(from + name + body);

        TaskStackBuilder stackBuilder;
        Intent intent;

        intent = new Intent(context, VectorRoomActivity.class);
        intent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId);

        if (null != matrixId) {
            intent.putExtra(VectorRoomActivity.EXTRA_MATRIX_ID, matrixId);
        }

        stackBuilder = TaskStackBuilder.create(context)
                .addParentStack(VectorRoomActivity.class)
                .addNextIntent(intent);


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
        if (!LockScreenActivity.isDisplayingALockScreenActivity()) {
            if (!isInvitationEvent) {
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
                        R.drawable.vector_notification_quick_reply,
                        context.getString(R.string.action_quick_reply),
                        pIntent);
            } else {
                {
                    // offer to type a quick reject button
                    Intent leaveIntent = new Intent(context, JoinScreenActivity.class);
                    leaveIntent.putExtra(JoinScreenActivity.EXTRA_ROOM_ID, roomId);
                    leaveIntent.putExtra(JoinScreenActivity.EXTRA_MATRIX_ID, matrixId);
                    leaveIntent.putExtra(JoinScreenActivity.EXTRA_REJECT, true);

                    // the action must be unique else the parameters are ignored
                    leaveIntent.setAction(QUICK_LAUNCH_ACTION + ((int) (System.currentTimeMillis())));
                    PendingIntent pIntent = PendingIntent.getActivity(context, 0, leaveIntent, 0);
                    builder.addAction(
                            R.drawable.vector_notification_reject_invitation,
                            context.getString(R.string.reject),
                            pIntent);
                }

                {
                    // offer to type a quick accept button
                    Intent acceptIntent = new Intent(context, JoinScreenActivity.class);
                    acceptIntent.putExtra(JoinScreenActivity.EXTRA_ROOM_ID, roomId);
                    acceptIntent.putExtra(JoinScreenActivity.EXTRA_MATRIX_ID, matrixId);
                    acceptIntent.putExtra(JoinScreenActivity.EXTRA_JOIN, true);

                    // the action must be unique else the parameters are ignored
                    acceptIntent.setAction(QUICK_LAUNCH_ACTION + ((int) (System.currentTimeMillis())));
                    PendingIntent pIntent = PendingIntent.getActivity(context, 0, acceptIntent, 0);
                    builder.addAction(
                            R.drawable.vector_notification_accept_invitation,
                            context.getString(R.string.join),
                            pIntent);
                }
            }

            // Build the pending intent for when the notification is clicked
            Intent roomIntentTap;
            if(isInvitationEvent) {
                // for invitation the room preview must be displayed
                roomIntentTap = CommonActivityUtils.buildIntentPreviewRoom(matrixId, roomId, context, VectorFakeRoomPreviewActivity.class);
            } else{
                roomIntentTap = new Intent(context, VectorRoomActivity.class);
                roomIntentTap.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
            }
            // the action must be unique else the parameters are ignored
            roomIntentTap.setAction(TAP_TO_VIEW_ACTION + ((int) (System.currentTimeMillis())));

            // Recreate the back stack
            TaskStackBuilder stackBuilderTap = TaskStackBuilder.create(context)
                    .addParentStack(VectorRoomActivity.class)
                    .addNextIntent(roomIntentTap);

            builder.addAction(
                    R.drawable.vector_notification_open,
                    context.getString(R.string.action_open),
                    stackBuilderTap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        //extendForCar(context, builder, roomId, roomName, from, body);

        Notification n = builder.build();
        n.flags |= Notification.FLAG_SHOW_LIGHTS;
        n.defaults |= Notification.DEFAULT_LIGHTS;

        if (shouldPlaySound) {
            n.defaults |= Notification.DEFAULT_SOUND;
        }

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // some devices crash if this field is not set
            // even if it is deprecated

            // setLatestEventInfo() is deprecated on Android M, so we try to use
            // reflection at runtime, to avoid compiler error: "Cannot resolve method.."
            try {
                Method deprecatedMethod = n.getClass().getMethod("setLatestEventInfo", Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                deprecatedMethod.invoke(n, context, from, body, pendingIntent);
            } catch (Exception ex) {
                Log.e(LOG_TAG, "## buildMessageNotification(): Exception - setLatestEventInfo() Msg="+ex.getMessage());
            }
        }

        return n;
    }

    /*
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

    }*/

    private NotificationUtils() {}
}
