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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
     * @return true if there is a noisy notification
     */
    private static boolean addTextStyleWithSeveralRooms(Context context,
                                                  android.support.v7.app.NotificationCompat.Builder builder,
                                                  Map<String, List<NotifiedEvent>> notifiedEventsByRoomId) {
        // TODO manage multi accounts
        MXSession session = Matrix.getInstance(context).getDefaultSession();
        IMXStore store = session.getDataHandler().getStore();
        android.support.v7.app.NotificationCompat.InboxStyle inboxStyle = new android.support.v7.app.NotificationCompat.InboxStyle();

        int sum = 0;

        List<NotificationDisplay> noisyNotifiedList = new ArrayList<>();
        List<NotificationDisplay> notifiedList = new ArrayList<>();

        for (String roomId : notifiedEventsByRoomId.keySet()) {
            Room room = session.getDataHandler().getRoom(roomId);
            String roomName = getRoomName(context, session, room, null);

            Event latestEvent = null;
            boolean isNoisyNotified = false;
            List<NotifiedEvent> notifiedEvents = notifiedEventsByRoomId.get(roomId);

            for (NotifiedEvent notifiedEvent : notifiedEvents) {
                isNoisyNotified |= notifiedEvent.mBingRule.isDefaultNotificationSound(notifiedEvent.mBingRule.notificationSound());
                latestEvent = store.getEvent(notifiedEvent.mEventId, roomId);
            }

            String text;
            String header;

            EventDisplay eventDisplay = new EventDisplay(context, latestEvent, room.getLiveState());
            eventDisplay.setPrependMessagesWithAuthor(false);

            if (room.isInvited()) {
                header = roomName + ": ";
                text = eventDisplay.getTextualDisplay().toString();
            } else if (1 == notifiedEvents.size()) {
                eventDisplay = new EventDisplay(context, latestEvent, room.getLiveState());
                eventDisplay.setPrependMessagesWithAuthor(false);

                header = roomName + ": " + room.getLiveState().getMemberName(latestEvent.getSender()) + " ";
                text = eventDisplay.getTextualDisplay().toString();
            } else {
                header = roomName + ": ";
                text = context.getString(R.string.notification_unread_messages, session.getDataHandler().getStore().unreadEvents(roomId, null).size());
            }

            SpannableString notifiedLine = new SpannableString(header + text);
            notifiedLine.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, header.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            if (isNoisyNotified) {
                noisyNotifiedList.add(new NotificationDisplay(latestEvent.getOriginServerTs(), notifiedLine));
            } else {
                notifiedList.add(new NotificationDisplay(latestEvent.getOriginServerTs(), notifiedLine));
            }

            sum += session.getDataHandler().getStore().unreadEvents(roomId, null).size();
        }

        Collections.sort(noisyNotifiedList, mNotificationDisplaySort);
        Collections.sort(notifiedList, mNotificationDisplaySort);

        List<NotificationDisplay> mergedNotificationDisplaysList = new ArrayList<>();
        mergedNotificationDisplaysList.addAll(noisyNotifiedList);
        mergedNotificationDisplaysList.addAll(notifiedList);

        if (mergedNotificationDisplaysList.size() > MAX_NUMBER_NOTIFICATION_LINES) {
            mergedNotificationDisplaysList = mergedNotificationDisplaysList.subList(0, MAX_NUMBER_NOTIFICATION_LINES);
        }

        for (NotificationDisplay notificationDisplay : mergedNotificationDisplaysList) {
            SpannableString notifiedLine = notificationDisplay.mMessage;

            if (noisyNotifiedList.contains(notificationDisplay)) {
                notifiedLine.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.vector_fuchsia_color)), 0, notifiedLine.length(), 0);
            }
            inboxStyle.addLine(notifiedLine);
        }

        inboxStyle.setBigContentTitle(context.getString(R.string.riot_app_name));
        inboxStyle.setSummaryText(context.getString(R.string.notification_unread_messages_in_room, sum, notifiedEventsByRoomId.keySet().size()));
        builder.setStyle(inboxStyle);

        // Build the pending intent for when the notification is clicked
        Intent roomIntentTap;
        roomIntentTap = new Intent(context, VectorHomeActivity.class);
        // the action must be unique else the parameters are ignored
        roomIntentTap.setAction(TAP_TO_VIEW_ACTION + ((int) (System.currentTimeMillis())));

        // Recreate the back stack
        TaskStackBuilder stackBuilderTap = TaskStackBuilder.create(context)
                .addParentStack(VectorHomeActivity.class)
                .addNextIntent(roomIntentTap);

        builder.setContentIntent(stackBuilderTap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));

        return noisyNotifiedList.size() > 0;
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
     * @return true if there is a noisy notified events
     */
    private static boolean addTextStyle(Context context,
                                                  android.support.v7.app.NotificationCompat.Builder builder,
                                                  NotifiedEvent eventToNotify,
                                                  boolean isInvitationEvent,
                                                  Map<String, List<NotifiedEvent>> notifiedEventsByRoomId) {

        // when there are several rooms, the text style is not the same
        if (notifiedEventsByRoomId.size() > 1) {
            return addTextStyleWithSeveralRooms(context, builder, notifiedEventsByRoomId);
        }

        // TODO manage multi accounts
        MXSession session = Matrix.getInstance(context).getDefaultSession();
        IMXStore store = session.getDataHandler().getStore();
        android.support.v7.app.NotificationCompat.InboxStyle inboxStyle = new android.support.v7.app.NotificationCompat.InboxStyle();

        String roomId = notifiedEventsByRoomId.keySet().iterator().next();

        Room room = session.getDataHandler().getRoom(roomId);
        String roomName = getRoomName(context, session, room, null);

        boolean isNoisyNotified = false;
        List<NotifiedEvent> notifiedEvents = notifiedEventsByRoomId.get(roomId);
        int unreadCount = notifiedEvents.size();

        for (NotifiedEvent notifiedEvent : notifiedEvents) {
            isNoisyNotified |= notifiedEvent.mBingRule.isDefaultNotificationSound(notifiedEvent.mBingRule.notificationSound());
        }

        // the messages are sorted from the oldest to the latest
        Collections.reverse(notifiedEvents);

        if (notifiedEvents.size() > MAX_NUMBER_NOTIFICATION_LINES) {
            notifiedEvents = notifiedEvents.subList(0, MAX_NUMBER_NOTIFICATION_LINES);
        }

        for (NotifiedEvent notifiedEvent : notifiedEvents) {
            Event event = store.getEvent(notifiedEvent.mEventId, notifiedEvent.mRoomId);
            EventDisplay eventDisplay = new EventDisplay(context, event, room.getLiveState());
            eventDisplay.setPrependMessagesWithAuthor(true);

            SpannableString notifiedLine = new SpannableString(eventDisplay.getTextualDisplay().toString());
            if (notifiedEvent.mBingRule.isDefaultNotificationSound(notifiedEvent.mBingRule.notificationSound())) {
                notifiedLine.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.vector_fuchsia_color)), 0, notifiedLine.length(), 0);
            }
            inboxStyle.addLine(notifiedLine);
        }

        inboxStyle.setBigContentTitle(roomName);

        if (unreadCount > MAX_NUMBER_NOTIFICATION_LINES) {
            inboxStyle.setSummaryText(context.getString(R.string.notification_unread_messages, unreadCount));
        }

        builder.setStyle(inboxStyle);

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
                String body = eventDisplay.getTextualDisplay().toString();

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
                    .addParentStack(VectorRoomActivity.class)
                    .addNextIntent(roomIntentTap);

            builder.setContentIntent(stackBuilderTap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));

            builder.addAction(
                    R.drawable.vector_notification_open,
                    context.getString(R.string.action_open),
                    stackBuilderTap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
        }


        return isNoisyNotified;
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
        BingRule bingRule = eventToNotify.mBingRule;

        boolean isInvitationEvent = false;

        EventDisplay eventDisplay = new EventDisplay(context, event, room.getLiveState());
        eventDisplay.setPrependMessagesWithAuthor(true);
        String body = eventDisplay.getTextualDisplay().toString();

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

        boolean hasNoisyNotifications = addTextStyle(context, builder, eventToNotify, isInvitationEvent, notifiedEventsByRoomId); //addMultiRoomsTextStyle(context, builder, notifiedEventsByRoomId);

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

        int highlightColor = ContextCompat.getColor(context, R.color.vector_fuchsia_color);
        int defaultColor = Color.TRANSPARENT;

        if (isBackground) {
            builder.setPriority(android.support.v7.app.NotificationCompat.PRIORITY_DEFAULT);
            builder.setColor(hasNoisyNotifications ? highlightColor : defaultColor);
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
