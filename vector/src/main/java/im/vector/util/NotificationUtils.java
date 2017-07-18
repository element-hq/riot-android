/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.ImageView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.Log;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.JoinScreenActivity;
import im.vector.activity.LockScreenActivity;
import im.vector.activity.VectorFakeRoomPreviewActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.services.EventStreamService;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Util class for creating notifications.
 */
public class NotificationUtils {
    private static final String LOG_TAG = "NotificationUtils";

    public static final String QUICK_LAUNCH_ACTION = "EventStreamService.QUICK_LAUNCH_ACTION";
    public static final String TAP_TO_VIEW_ACTION = "EventStreamService.TAP_TO_VIEW_ACTION";
    public static final String CAR_VOICE_REPLY_KEY = "EventStreamService.CAR_VOICE_REPLY_KEY";
    public static final String ACTION_MESSAGE_HEARD = "ACTION_MESSAGE_HEARD";
    public static final String ACTION_MESSAGE_REPLY = "ACTION_MESSAGE_REPLY";
    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";

    // the bubble radius is computed for 99
    static private int mUnreadBubbleWidth = -1;

    /**
     * Retrieve the room name.
     *
     * @param session the session
     * @param room    the room
     * @param event   the event
     * @return the room name
     */
    public static String getRoomName(Context context, MXSession session, Room room, Event event) {
        String roomName = VectorUtils.getRoomDisplayName(context, session, room);

        // avoid displaying the room Id
        // try to find the sender display name
        if (TextUtils.equals(roomName, room.getRoomId())) {
            roomName = room.getName(session.getMyUserId());

            // avoid room Id as name
            if (TextUtils.equals(roomName, room.getRoomId()) && (null != event)) {
                User user = session.getDataHandler().getStore().getUser(event.sender);

                if (null != user) {
                    roomName = user.displayname;
                } else {
                    roomName = event.sender;
                }
            }
        }

        return roomName;
    }

    /**
     * Build an incoming call notification.
     * This notification starts the VectorHomeActivity which is in charge of centralizing the incoming call flow.
     *
     * @param context  the context.
     * @param roomName the room name in which the call is pending.
     * @param matrixId the matrix id
     * @param callId   the call id.
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
     *
     * @param context  the context.
     * @param roomName the room name in which the call is pending.
     * @param roomId   the room Id
     * @param matrixId the matrix id
     * @param callId   the call id.
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
     *
     * @param bitmap the bitmap to "square"
     * @return the squared bitmap
     */
    public static Bitmap createSquareBitmap(Bitmap bitmap) {
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
     * This class manages the notification display.
     * It contains the message to display and its timestamp
     */
    static class NotificationDisplay {
        final long mEventTs;
        final SpannableString mMessage;

        NotificationDisplay(long ts, SpannableString message) {
            mEventTs = ts;
            mMessage = message;
        }
    }

    /**
     * NotificationDisplay comparator
     */
    private static final Comparator<NotificationDisplay> mNotificationDisplaySort = new Comparator<NotificationDisplay>() {
        @Override
        public int compare(NotificationDisplay lhs, NotificationDisplay rhs) {
            long t0 = lhs.mEventTs;
            long t1 = rhs.mEventTs;

            if (t0 > t1) {
                return -1;
            } else if (t0 < t1) {
                return +1;
            }
            return 0;
        }
    };

    /**
     * Define a notified event
     * i.e the matched bing rules
     */
    public static class NotifiedEvent {
        public final BingRule mBingRule;
        public final String mRoomId;
        public final String mEventId;

        public NotifiedEvent(String roomId, String eventId, BingRule bingRule) {
            mRoomId = roomId;
            mEventId = eventId;
            mBingRule = bingRule;
        }
    }

    // max number of lines to display the notification text styles
    static final int MAX_NUMBER_NOTIFICATION_LINES = 10;

    /**
     * Add a text style to a notification when there are several notified rooms.
     * @param context the context
     * @param builder the notification builder
     * @param notifiedEventsByRoomId the notified events by room ids
     */
    private static void addTextStyleWithSeveralRooms(Context context,
                                                     android.support.v7.app.NotificationCompat.Builder builder,
                                                     NotifiedEvent eventToNotify,
                                                     boolean isInvitationEvent,
                                                     Map<String, List<NotifiedEvent>> notifiedEventsByRoomId) {
        // TODO manage multi accounts
        MXSession session = Matrix.getInstance(context).getDefaultSession();
        IMXStore store = session.getDataHandler().getStore();
        android.support.v7.app.NotificationCompat.InboxStyle inboxStyle = new android.support.v7.app.NotificationCompat.InboxStyle();

        int sum = 0;
        int roomsCount = 0;


        List<NotificationDisplay> notificationsList = new ArrayList<>();

        for (String roomId : notifiedEventsByRoomId.keySet()) {
            Room room = session.getDataHandler().getRoom(roomId);
            String roomName = getRoomName(context, session, room, null);

            List<NotifiedEvent> notifiedEvents = notifiedEventsByRoomId.get(roomId);
            Event latestEvent = store.getEvent(notifiedEvents.get(notifiedEvents.size()-1).mEventId, roomId);

            String text;
            String header;

            EventDisplay eventDisplay = new EventDisplay(context, latestEvent, room.getLiveState());
            eventDisplay.setPrependMessagesWithAuthor(false);

            if (room.isInvited()) {
                header = roomName + ": ";
                CharSequence textualDisplay = eventDisplay.getTextualDisplay();
                text = !TextUtils.isEmpty(textualDisplay) ? textualDisplay.toString() : "";
            } else if (1 == notifiedEvents.size()) {
                eventDisplay = new EventDisplay(context, latestEvent, room.getLiveState());
                eventDisplay.setPrependMessagesWithAuthor(false);

                header = roomName + ": " + room.getLiveState().getMemberName(latestEvent.getSender()) + " ";

                CharSequence textualDisplay = eventDisplay.getTextualDisplay();

                // the event might have been redacted
                if (!TextUtils.isEmpty(textualDisplay)) {
                    text = textualDisplay.toString();
                } else {
                    text = "";
                }
            } else {
                header = roomName + ": ";
                text = context.getString(R.string.notification_unread_notified_messages, notifiedEvents.size());
            }

            // ad the line if it makes sense
            if (!TextUtils.isEmpty(text)) {
                SpannableString notifiedLine = new SpannableString(header + text);
                notifiedLine.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, header.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                notificationsList.add(new NotificationDisplay(latestEvent.getOriginServerTs(), notifiedLine));
                sum += notifiedEvents.size();
                roomsCount++;
            }
        }

        Collections.sort(notificationsList, mNotificationDisplaySort);

        if (notificationsList.size() > MAX_NUMBER_NOTIFICATION_LINES) {
            notificationsList = notificationsList.subList(0, MAX_NUMBER_NOTIFICATION_LINES);
        }

        for (NotificationDisplay notificationDisplay : notificationsList) {
            inboxStyle.addLine(notificationDisplay.mMessage);
        }

        inboxStyle.setBigContentTitle(context.getString(R.string.riot_app_name));
        inboxStyle.setSummaryText(context.getString(R.string.notification_unread_notified_messages_in_room, sum, roomsCount));
        builder.setStyle(inboxStyle);

        TaskStackBuilder stackBuilderTap = TaskStackBuilder.create(context);
        Intent roomIntentTap;
        // sanity check
        if ((null == eventToNotify) || TextUtils.isEmpty(eventToNotify.mRoomId)) {
            // Build the pending intent for when the notification is clicked
            roomIntentTap = new Intent(context, VectorHomeActivity.class);
        } else {
            // add the home page the activity stack
            stackBuilderTap.addNextIntentWithParentStack(new Intent(context, VectorHomeActivity.class));

            if (isInvitationEvent) {
                // for invitation the room preview must be displayed
                roomIntentTap = CommonActivityUtils.buildIntentPreviewRoom(session.getMyUserId(), eventToNotify.mRoomId, context, VectorFakeRoomPreviewActivity.class);
            } else {
                roomIntentTap = new Intent(context, VectorRoomActivity.class);
                roomIntentTap.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, eventToNotify.mRoomId);
            }
        }

        // the action must be unique else the parameters are ignored
        roomIntentTap.setAction(TAP_TO_VIEW_ACTION + ((int) (System.currentTimeMillis())));
        stackBuilderTap.addNextIntent(roomIntentTap);
        builder.setContentIntent(stackBuilderTap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));

        // offer to open the rooms list
        {
            Intent openIntentTap = new Intent(context, VectorHomeActivity.class);

            // Recreate the back stack
            TaskStackBuilder viewAllTask = TaskStackBuilder.create(context)
                    .addNextIntent(openIntentTap);

            builder.addAction(
                    R.drawable.ic_home_black_24dp,
                    context.getString(R.string.bottom_action_home),
                    viewAllTask.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        // wearable
        try {
            long ts = 0;
            Event latestEvent = null;

            // search the oldest message
            for (String roomId : notifiedEventsByRoomId.keySet()) {
                List<NotifiedEvent> notifiedEvents = notifiedEventsByRoomId.get(roomId);
                Event event = store.getEvent(notifiedEvents.get(notifiedEvents.size() - 1).mEventId, roomId);

                if ((null != event) && (event.getOriginServerTs() > ts)) {
                    ts = event.getOriginServerTs();
                    latestEvent = event;
                }
            }

            // if there is a valid latest message
            if (null != latestEvent) {
                Room room = store.getRoom(latestEvent.roomId);

                if (null != room) {
                    EventDisplay eventDisplay = new EventDisplay(context, latestEvent, room.getLiveState());
                    eventDisplay.setPrependMessagesWithAuthor(false);
                    String roomName = getRoomName(context, session, room, null);

                    String message = roomName + ": " + room.getLiveState().getMemberName(latestEvent.getSender()) + " ";

                    CharSequence textualDisplay = eventDisplay.getTextualDisplay();

                    // the event might have been redacted
                    if (!TextUtils.isEmpty(textualDisplay)) {
                        message += textualDisplay.toString();
                    }

                    NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender();
                    NotificationCompat.Action action =
                            new NotificationCompat.Action.Builder(R.drawable.message_notification_transparent,
                                    message,
                                    stackBuilderTap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT))
                                    .build();
                    wearableExtender.addAction(action);
                    builder.extend(wearableExtender);
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## addTextStyleWithSeveralRooms() : WearableExtender failed " + e.getMessage());
        }
    }

    /**
     * Add a text style for a bunch of notified events.
     *
     * The notification contains the notified messages from any rooms.
     * It does not contain anymore the latest notified message.
     *
     * When there is only one room, it displays the MAX_NUMBER_NOTIFICATION_LINES latest messages.
     * The busy ones are displayed in RED.
     * The QUICK REPLY and other buttons are displayed.
     *
     * When there are several rooms, it displays the busy notified rooms first (sorted by latest message timestamp).
     * Each line is
     * - "Room Name : XX unread messages" if there are many unread messages
     * - 'Room Name : Sender   - Message body" if there is only one unread message.
     *
     * @param context the context
     * @param builder the notification builder
     * @param eventToNotify the latest notified event
     * @param isInvitationEvent true if the notified event is an invitation
     * @param notifiedEventsByRoomId the notified events by room ids
     */
    private static void addTextStyle(Context context,
                                                  android.support.v7.app.NotificationCompat.Builder builder,
                                                  NotifiedEvent eventToNotify,
                                                  boolean isInvitationEvent,
                                                  Map<String, List<NotifiedEvent>> notifiedEventsByRoomId) {

        // nothing to do
        if (0 == notifiedEventsByRoomId.size()) {
            return;
        }

        // when there are several rooms, the text style is not the same
        if (notifiedEventsByRoomId.size() > 1) {
            addTextStyleWithSeveralRooms(context, builder, eventToNotify, isInvitationEvent, notifiedEventsByRoomId);
            return;
        }

        // TODO manage multi accounts
        MXSession session = Matrix.getInstance(context).getDefaultSession();
        IMXStore store = session.getDataHandler().getStore();
        android.support.v7.app.NotificationCompat.InboxStyle inboxStyle = new android.support.v7.app.NotificationCompat.InboxStyle();

        String roomId = notifiedEventsByRoomId.keySet().iterator().next();

        Room room = session.getDataHandler().getRoom(roomId);
        String roomName = getRoomName(context, session, room, null);

        List<NotifiedEvent> notifiedEvents = notifiedEventsByRoomId.get(roomId);
        int unreadCount = notifiedEvents.size();

        // the messages are sorted from the oldest to the latest
        Collections.reverse(notifiedEvents);

        if (notifiedEvents.size() > MAX_NUMBER_NOTIFICATION_LINES) {
            notifiedEvents = notifiedEvents.subList(0, MAX_NUMBER_NOTIFICATION_LINES);
        }

        SpannableString latestText = null;

        for (NotifiedEvent notifiedEvent : notifiedEvents) {
            Event event = store.getEvent(notifiedEvent.mEventId, notifiedEvent.mRoomId);
            EventDisplay eventDisplay = new EventDisplay(context, event, room.getLiveState());
            eventDisplay.setPrependMessagesWithAuthor(true);
            CharSequence textualDisplay = eventDisplay.getTextualDisplay();

            if (!TextUtils.isEmpty(textualDisplay)) {
                inboxStyle.addLine(latestText = new SpannableString(textualDisplay));
            }
        }
        inboxStyle.setBigContentTitle(roomName);

        // adapt the notification display to the number of notified messages
        if ((1 == notifiedEvents.size()) && (null != latestText)) {
            builder.setStyle(new android.support.v7.app.NotificationCompat.BigTextStyle().bigText(latestText));
        } else {
            if (unreadCount > MAX_NUMBER_NOTIFICATION_LINES) {
                inboxStyle.setSummaryText(context.getString(R.string.notification_unread_notified_messages, unreadCount));
            }

            builder.setStyle(inboxStyle);
        }

        // do not offer to quick respond if the user did not dismiss the previous one
        if (!LockScreenActivity.isDisplayingALockScreenActivity()) {
            if (!isInvitationEvent) {
                Event event = store.getEvent(eventToNotify.mEventId, eventToNotify.mRoomId);
                RoomMember member = room.getMember(event.getSender());

                // offer to type a quick answer (i.e. without launching the application)
                Intent quickReplyIntent = new Intent(context, LockScreenActivity.class);
                quickReplyIntent.putExtra(LockScreenActivity.EXTRA_ROOM_ID, roomId);
                quickReplyIntent.putExtra(LockScreenActivity.EXTRA_SENDER_NAME, (null == member) ? event.getSender() : member.getName());

                EventDisplay eventDisplay = new EventDisplay(context, event, room.getLiveState());
                eventDisplay.setPrependMessagesWithAuthor(false);
                CharSequence textualDisplay = eventDisplay.getTextualDisplay();
                String body = !TextUtils.isEmpty(textualDisplay) ? textualDisplay.toString() : "";

                quickReplyIntent.putExtra(LockScreenActivity.EXTRA_MESSAGE_BODY, body);

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
                    leaveIntent.putExtra(JoinScreenActivity.EXTRA_MATRIX_ID, session.getMyUserId());
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
                    acceptIntent.putExtra(JoinScreenActivity.EXTRA_MATRIX_ID, session.getMyUserId());
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

            if (isInvitationEvent) {
                // for invitation the room preview must be displayed
                roomIntentTap = CommonActivityUtils.buildIntentPreviewRoom(session.getMyUserId(), roomId, context, VectorFakeRoomPreviewActivity.class);
            } else {
                roomIntentTap = new Intent(context, VectorRoomActivity.class);
                roomIntentTap.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
            }
            // the action must be unique else the parameters are ignored
            roomIntentTap.setAction(TAP_TO_VIEW_ACTION + ((int) (System.currentTimeMillis())));

            // Recreate the back stack
            TaskStackBuilder stackBuilderTap = TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(new Intent(context, VectorHomeActivity.class))
                    .addNextIntent(roomIntentTap);

            builder.setContentIntent(stackBuilderTap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));

            builder.addAction(
                    R.drawable.vector_notification_open,
                    context.getString(R.string.action_open),
                    stackBuilderTap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));

            // wearable
            if (!isInvitationEvent) {
                try {
                    Event latestEvent = store.getEvent(notifiedEvents.get(notifiedEvents.size() - 1).mEventId, roomId);

                    // if there is a valid latest message
                    if (null != latestEvent) {
                        EventDisplay eventDisplay = new EventDisplay(context, latestEvent, room.getLiveState());
                        eventDisplay.setPrependMessagesWithAuthor(false);

                        String message = roomName + ": " + room.getLiveState().getMemberName(latestEvent.getSender()) + " ";

                        CharSequence textualDisplay = eventDisplay.getTextualDisplay();

                        // the event might have been redacted
                        if (!TextUtils.isEmpty(textualDisplay)) {
                            message += textualDisplay.toString();
                        }

                        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender();
                        NotificationCompat.Action action =
                                new NotificationCompat.Action.Builder(R.drawable.message_notification_transparent,
                                        message,
                                        stackBuilderTap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT))
                                        .build();
                        wearableExtender.addAction(action);
                        builder.extend(wearableExtender);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## addTextStyleWithSeveralRooms() : WearableExtender failed " + e.getMessage());
                }
            }
        }
    }

    /**
     * Build a notification
     * @param context the context
     * @param notifiedEventsByRoomId the notified events
     * @param eventToNotify the latest event to notify
     * @param isBackground true if it is background notification
     * @return the notification
     */
    public static Notification buildMessageNotification(Context context,
                                                         Map<String, List<NotifiedEvent>> notifiedEventsByRoomId,
                                                         NotifiedEvent eventToNotify,
                                                         boolean isBackground) {
        // TODO manage multi accounts
        MXSession session = Matrix.getInstance(context).getDefaultSession();
        IMXStore store = session.getDataHandler().getStore();

        Room room = store.getRoom(eventToNotify.mRoomId);
        Event event = store.getEvent(eventToNotify.mEventId, eventToNotify.mRoomId);

        // sanity check
        if ((null == room) || (null == event)) {
            if (null == room) {
                Log.e(LOG_TAG, "## buildMessageNotification() : null room " + eventToNotify.mRoomId);
            } else {
                Log.e(LOG_TAG, "## buildMessageNotification() : null event " + eventToNotify.mEventId + " " + eventToNotify.mRoomId);
            }
            return null;
        }

        BingRule bingRule = eventToNotify.mBingRule;

        boolean isInvitationEvent = false;

        EventDisplay eventDisplay = new EventDisplay(context, event, room.getLiveState());
        eventDisplay.setPrependMessagesWithAuthor(true);
        CharSequence textualDisplay = eventDisplay.getTextualDisplay();
        String body = !TextUtils.isEmpty(textualDisplay) ? textualDisplay.toString() : "";

        if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.getType())) {
            try {
                isInvitationEvent = "invite".equals(event.getContentAsJsonObject().getAsJsonPrimitive("membership").getAsString());
            } catch (Exception e) {
                Log.e(LOG_TAG, "prepareNotification : invitation parsing failed");
            }
        }

        Bitmap largeBitmap = null;

        // when the event is an invitation one
        // don't check if the sender ID is known because the members list are not yet downloaded
        if (!isInvitationEvent) {
            // is there any avatar url
            if (!TextUtils.isEmpty(room.getAvatarUrl())) {
                int size = context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size);

                // check if the thumbnail is already downloaded
                File f = session.getMediasCache().thumbnailCacheFile(room.getAvatarUrl(), size);

                if (null != f) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    try {
                        largeBitmap = BitmapFactory.decodeFile(f.getPath(), options);
                    } catch (OutOfMemoryError oom) {
                        Log.e(LOG_TAG, "decodeFile failed with an oom");
                    }
                } else {
                    session.getMediasCache().loadAvatarThumbnail(session.getHomeserverConfig(), new ImageView(context), room.getAvatarUrl(), size);
                }
            }
        }

        Log.d(LOG_TAG, "prepareNotification : with sound " + bingRule.isDefaultNotificationSound(bingRule.notificationSound()));

        String roomName = getRoomName(context, session, room, event);

        android.support.v7.app.NotificationCompat.Builder builder = new android.support.v7.app.NotificationCompat.Builder(context);
        builder.setWhen(event.getOriginServerTs());
        builder.setContentTitle(roomName);
        builder.setContentText(body);

        builder.setGroup(context.getString(R.string.riot_app_name));
        builder.setGroupSummary(true);

        try {
            addTextStyle(context, builder, eventToNotify, isInvitationEvent, notifiedEventsByRoomId);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## buildMessageNotification() : addTextStyle failed " + e.getMessage());
        }

        // only one room : display the large bitmap (it should be the room avatar
        // several rooms : display the Riot avatar
        if (notifiedEventsByRoomId.keySet().size() == 1) {
            if (null != largeBitmap) {
                largeBitmap = NotificationUtils.createSquareBitmap(largeBitmap);
                builder.setLargeIcon(largeBitmap);
            }
        }

        builder.setSmallIcon(R.drawable.message_notification_transparent);

        boolean is_bing = bingRule.isDefaultNotificationSound(bingRule.notificationSound());

        @ColorInt int highlightColor = ThemeUtils.getColor(context, R.attr.vector_fuchsia_color);
        int defaultColor = Color.TRANSPARENT;

        if (isBackground) {
            builder.setPriority(android.support.v7.app.NotificationCompat.PRIORITY_DEFAULT);
            builder.setColor(defaultColor);
        } else if (is_bing) {
            builder.setPriority(android.support.v7.app.NotificationCompat.PRIORITY_HIGH);
            builder.setColor(highlightColor);
        } else {
            builder.setPriority(android.support.v7.app.NotificationCompat.PRIORITY_DEFAULT);
            builder.setColor(Color.TRANSPARENT);
        }


        Notification n = builder.build();

        if (!isBackground) {
            n.flags |= Notification.FLAG_SHOW_LIGHTS;
            n.defaults |= Notification.DEFAULT_LIGHTS;

            if (is_bing) {
                n.defaults |= Notification.DEFAULT_SOUND;
            }
        }

        return n;
    }
}
