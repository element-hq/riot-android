/*
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

package im.vector.notifications;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.widget.ImageView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.LockScreenActivity;
import im.vector.util.RiotEventDisplay;
import im.vector.util.VectorUtils;

/**
 * RoomsNotifications
 */
public class RoomsNotifications implements Parcelable {
    private static final String LOG_TAG = RoomsNotifications.class.getSimpleName();

    // max number of lines to display the notification text styles
    static final int MAX_NUMBER_NOTIFICATION_LINES = 10;

    /****** Parcelable items ********/
    // the session id
    String mSessionId = "";

    // the notified event room Id
    String mRoomId = "";

    // the notification summary
    String mSummaryText = "";

    // latest message with sender header
    String mQuickReplyBody = "";

    // wearable notification message
    String mWearableMessage = "";

    // true when the notified event is an invitation one
    boolean mIsInvitationEvent = false;

    // the room avatar
    String mRoomAvatarPath = "";

    // notified message TS
    long mContentTs = -1;

    // content title
    String mContentTitle = "";

    // the context text
    String mContentText = "";

    String mSenderName = "";

    // the notifications list
    List<RoomNotifications> mRoomNotifications = new ArrayList<>();

    // messages list
    List<CharSequence> mReversedMessagesList = new ArrayList<>();

    /****** others items ********/
    // notified event
    private NotifiedEvent mEventToNotify;

    // notified events by room id
    private Map<String, List<NotifiedEvent>> mNotifiedEventsByRoomId;

    // notification details
    private Context mContext;
    private MXSession mSession;
    private Room mRoom;
    private Event mEvent;

    /**
     * Empty constructor
     */
    public RoomsNotifications() {
    }

    /**
     * Constructor
     *
     * @param anEventToNotify            the event to notify
     * @param someNotifiedEventsByRoomId the notified events
     */
    public RoomsNotifications(NotifiedEvent anEventToNotify,
                              Map<String, List<NotifiedEvent>> someNotifiedEventsByRoomId) {
        mContext = VectorApp.getInstance();
        mSession = Matrix.getInstance(mContext).getDefaultSession();
        IMXStore store = mSession.getDataHandler().getStore();

        mEventToNotify = anEventToNotify;
        mNotifiedEventsByRoomId = someNotifiedEventsByRoomId;

        // the session id
        mSessionId = mSession.getMyUserId();
        mRoomId = anEventToNotify.mRoomId;
        mRoom = store.getRoom(mEventToNotify.mRoomId);
        mEvent = store.getEvent(mEventToNotify.mEventId, mEventToNotify.mRoomId);

        // sanity check
        if ((null == mRoom) || (null == mEvent)) {
            if (null == mRoom) {
                Log.e(LOG_TAG, "## RoomsNotifications() : null room " + mEventToNotify.mRoomId);
            } else {
                Log.e(LOG_TAG, "## RoomsNotifications() : null event " + mEventToNotify.mEventId + " " + mEventToNotify.mRoomId);
            }
            return;
        }

        mIsInvitationEvent = false;

        EventDisplay eventDisplay = new RiotEventDisplay(mContext, mEvent, mRoom.getLiveState());
        eventDisplay.setPrependMessagesWithAuthor(true);
        CharSequence textualDisplay = eventDisplay.getTextualDisplay();
        String body = !TextUtils.isEmpty(textualDisplay) ? textualDisplay.toString() : "";

        if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(mEvent.getType())) {
            try {
                mIsInvitationEvent = "invite".equals(mEvent.getContentAsJsonObject().getAsJsonPrimitive("membership").getAsString());
            } catch (Exception e) {
                Log.e(LOG_TAG, "RoomsNotifications : invitation parsing failed");
            }
        }
        // when the event is an invitation one
        // don't check if the sender ID is known because the members list are not yet downloaded
        if (!mIsInvitationEvent) {
            int size = mContext.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size);

            File f = mSession.getMediasCache().thumbnailCacheFile(mRoom.getAvatarUrl(), size);

            if (null != f) {
                mRoomAvatarPath = f.getPath();
            } else {
                // prepare for the next time
                mSession.getMediasCache().loadAvatarThumbnail(mSession.getHomeServerConfig(), new ImageView(mContext), mRoom.getAvatarUrl(), size);
            }
        }

        String roomName = getRoomName(mContext, mSession, mRoom, mEvent);

        mContentTs = mEvent.getOriginServerTs();
        mContentTitle = roomName;
        mContentText = body;

        RoomMember member = mRoom.getMember(mEvent.getSender());
        mSenderName = (null == member) ? mEvent.getSender() : member.getName();

        boolean singleRoom = (mNotifiedEventsByRoomId.size() == 1);

        if (singleRoom) {
            initSingleRoom();
        } else {
            initMultiRooms();
        }
    }

    /**
     * Init for a single room notifications
     */
    private void initSingleRoom() {
        RoomNotifications roomNotifications = new RoomNotifications();
        mRoomNotifications.add(roomNotifications);
        roomNotifications.mRoomId = mEvent.roomId;
        roomNotifications.mRoomName = mContentTitle;

        List<NotifiedEvent> notifiedEvents = mNotifiedEventsByRoomId.get(roomNotifications.mRoomId);
        int unreadCount = notifiedEvents.size();

        // the messages are sorted from the oldest to the latest
        Collections.reverse(notifiedEvents);

        if (notifiedEvents.size() > MAX_NUMBER_NOTIFICATION_LINES) {
            notifiedEvents = notifiedEvents.subList(0, MAX_NUMBER_NOTIFICATION_LINES);
        }

        SpannableString latestText = null;
        IMXStore store = mSession.getDataHandler().getStore();

        for (NotifiedEvent notifiedEvent : notifiedEvents) {
            Event event = store.getEvent(notifiedEvent.mEventId, notifiedEvent.mRoomId);
            EventDisplay eventDisplay = new RiotEventDisplay(mContext, event, mRoom.getLiveState());
            eventDisplay.setPrependMessagesWithAuthor(true);
            CharSequence textualDisplay = eventDisplay.getTextualDisplay();

            if (!TextUtils.isEmpty(textualDisplay)) {
                mReversedMessagesList.add(textualDisplay);
            }
        }

        // adapt the notification display to the number of notified messages
        if ((1 == notifiedEvents.size()) && (null != latestText)) {
            roomNotifications.mMessagesSummary = latestText;
        } else {
            if (unreadCount > MAX_NUMBER_NOTIFICATION_LINES) {
                mSummaryText = mContext.getResources().getQuantityString(R.plurals.notification_unread_notified_messages, unreadCount, unreadCount);
            }
        }

        // do not offer to quick respond if the user did not dismiss the previous one
        if (!LockScreenActivity.isDisplayingALockScreenActivity()) {
            if (!mIsInvitationEvent) {
                Event event = store.getEvent(mEventToNotify.mEventId, mEventToNotify.mRoomId);
                RoomMember member = mRoom.getMember(event.getSender());
                roomNotifications.mSenderName = (null == member) ? event.getSender() : member.getName();

                EventDisplay eventDisplay = new RiotEventDisplay(mContext, event, mRoom.getLiveState());
                eventDisplay.setPrependMessagesWithAuthor(false);
                CharSequence textualDisplay = eventDisplay.getTextualDisplay();
                mQuickReplyBody = !TextUtils.isEmpty(textualDisplay) ? textualDisplay.toString() : "";
            }
        }

        initWearableMessage(mContext, mRoom, store.getEvent(notifiedEvents.get(notifiedEvents.size() - 1).mEventId, roomNotifications.mRoomId), mIsInvitationEvent);
    }

    /**
     * Init for multi rooms notifications
     */
    private void initMultiRooms() {
        IMXStore store = mSession.getDataHandler().getStore();

        int sum = 0;
        int roomsCount = 0;

        for (String roomId : mNotifiedEventsByRoomId.keySet()) {
            Room room = mSession.getDataHandler().getRoom(roomId);
            String roomName = getRoomName(mContext, mSession, room, null);

            List<NotifiedEvent> notifiedEvents = mNotifiedEventsByRoomId.get(roomId);
            Event latestEvent = store.getEvent(notifiedEvents.get(notifiedEvents.size() - 1).mEventId, roomId);

            String text;
            String header;

            EventDisplay eventDisplay = new RiotEventDisplay(mContext, latestEvent, room.getLiveState());
            eventDisplay.setPrependMessagesWithAuthor(false);

            if (room.isInvited()) {
                header = roomName + ": ";
                CharSequence textualDisplay = eventDisplay.getTextualDisplay();
                text = !TextUtils.isEmpty(textualDisplay) ? textualDisplay.toString() : "";
            } else if (1 == notifiedEvents.size()) {
                eventDisplay = new RiotEventDisplay(mContext, latestEvent, room.getLiveState());
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
                text = mContext.getResources().getQuantityString(R.plurals.notification_unread_notified_messages, notifiedEvents.size(), notifiedEvents.size());
            }

            // ad the line if it makes sense
            if (!TextUtils.isEmpty(text)) {
                RoomNotifications roomNotifications = new RoomNotifications();
                mRoomNotifications.add(roomNotifications);

                roomNotifications.mRoomId = roomId;
                roomNotifications.mLatestEventTs = latestEvent.getOriginServerTs();
                roomNotifications.mMessageHeader = header;
                roomNotifications.mMessagesSummary = header + text;
                sum += notifiedEvents.size();
                roomsCount++;
            }
        }

        Collections.sort(mRoomNotifications, RoomNotifications.mRoomNotificationsComparator);

        if (mRoomNotifications.size() > MAX_NUMBER_NOTIFICATION_LINES) {
            mRoomNotifications = mRoomNotifications.subList(0, MAX_NUMBER_NOTIFICATION_LINES);
        }

        mSummaryText = mContext.getString(R.string.notification_unread_notified_messages_in_room,
                mContext.getResources().getQuantityString(R.plurals.notification_unread_notified_messages_in_room_msgs, sum, sum),
                mContext.getResources().getQuantityString(R.plurals.notification_unread_notified_messages_in_room_rooms, roomsCount, roomsCount));
    }

    /**
     * Compute the wearable message
     *
     * @param context           the context
     * @param room              the room
     * @param latestEvent       the latest event
     * @param isInvitationEvent true if it is an invitaion
     */
    private void initWearableMessage(Context context, Room room, Event latestEvent, boolean isInvitationEvent) {
        if (!isInvitationEvent) {
            // if there is a valid latest message
            if ((null != latestEvent) && (null != room)) {
                MXSession session = Matrix.getInstance(context).getDefaultSession();
                String roomName = getRoomName(context, session, room, null);

                EventDisplay eventDisplay = new RiotEventDisplay(context, latestEvent, room.getLiveState());
                eventDisplay.setPrependMessagesWithAuthor(false);

                mWearableMessage = roomName + ": " + room.getLiveState().getMemberName(latestEvent.getSender()) + " ";
                CharSequence textualDisplay = eventDisplay.getTextualDisplay();

                // the event might have been redacted
                if (!TextUtils.isEmpty(textualDisplay)) {
                    mWearableMessage += textualDisplay.toString();
                }
            }
        }
    }

    /*
     * *********************************************************************************************
     * Parcelable
     * *********************************************************************************************
     */

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mSessionId);
        out.writeString(mRoomId);
        out.writeString(mSummaryText);
        out.writeString(mQuickReplyBody);
        out.writeString(mWearableMessage);
        out.writeInt(mIsInvitationEvent ? 1 : 0);
        out.writeString(mRoomAvatarPath);
        out.writeLong(mContentTs);

        out.writeString(mContentTitle);
        out.writeString(mContentText);
        out.writeString(mSenderName);

        RoomNotifications[] roomNotifications = new RoomNotifications[mRoomNotifications.size()];
        mRoomNotifications.toArray(roomNotifications);
        out.writeArray(roomNotifications);

        out.writeInt(mReversedMessagesList.size());
        for (CharSequence sequence : mReversedMessagesList) {
            TextUtils.writeToParcel(sequence, out, 0);
        }
    }

    /**
     * Constructor from the parcel.
     *
     * @param in the parcel
     */
    private void init(Parcel in) {
        mSessionId = in.readString();
        mRoomId = in.readString();
        mSummaryText = in.readString();
        mQuickReplyBody = in.readString();
        mWearableMessage = in.readString();
        mIsInvitationEvent = (1 == in.readInt()) ? true : false;
        mRoomAvatarPath = in.readString();
        mContentTs = in.readLong();

        mContentTitle = in.readString();
        mContentText = in.readString();
        mSenderName = in.readString();

        Object[] roomNotificationsAasVoid = in.readArray(RoomNotifications.class.getClassLoader());
        for (Object object : roomNotificationsAasVoid) {
            mRoomNotifications.add((RoomNotifications) object);
        }

        int count = in.readInt();
        mReversedMessagesList = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            mReversedMessagesList.add(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
        }
    }

    /**
     * Parcelable creator
     */
    public final static Parcelable.Creator<RoomsNotifications> CREATOR = new Parcelable.Creator<RoomsNotifications>() {
        public RoomsNotifications createFromParcel(Parcel p) {
            RoomsNotifications res = new RoomsNotifications();
            res.init(p);
            return res;
        }

        public RoomsNotifications[] newArray(int size) {
            return new RoomsNotifications[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    /*
     * *********************************************************************************************
     * Serialisation
     * *********************************************************************************************
     */

    private static final String ROOMS_NOTIFICATIONS_FILE_NAME = "ROOMS_NOTIFICATIONS_FILE_NAME";


    /**
     * @return byte[] from the class
     */
    private byte[] marshall() {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        return bytes;
    }

    /**
     * Create a RoomsNotifications instance from a bytes[].
     *
     * @param bytes the bytes array
     */
    private RoomsNotifications(byte[] bytes) {
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);

        init(parcel);
        parcel.recycle();
    }

    /**
     * Delete the cached RoomNotifications
     *
     * @param context the context
     */
    public static void deleteCachedRoomNotifications(Context context) {
        File file = new File(context.getApplicationContext().getCacheDir(), ROOMS_NOTIFICATIONS_FILE_NAME);

        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Save the roomsNotifications instance into the file system.
     *
     * @param context            the context
     * @param roomsNotifications the roomsNotifications instance
     */
    public static void saveRoomNotifications(Context context, RoomsNotifications roomsNotifications) {
        deleteCachedRoomNotifications(context);

        // no notified messages
        if (roomsNotifications.mRoomNotifications.isEmpty()) {
            return;
        }

        ByteArrayInputStream fis = null;
        FileOutputStream fos = null;

        try {
            fis = new ByteArrayInputStream(roomsNotifications.marshall());
            fos = new FileOutputStream(new File(context.getApplicationContext().getCacheDir(), ROOMS_NOTIFICATIONS_FILE_NAME));

            byte[] readData = new byte[1024];
            int len;

            while ((len = fis.read(readData, 0, 1024)) > 0) {
                fos.write(readData, 0, len);
            }
        } catch (Throwable t) {
            Log.e(LOG_TAG, "## saveRoomNotifications() failed " + t.getMessage());
        }

        try {
            if (null != fis) {
                fis.close();
            }

            if (null != fos) {
                fos.close();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## saveRoomNotifications() failed " + e.getMessage());
        }
    }

    /**
     * Load a saved RoomsNotifications from the file system
     *
     * @param context the context
     * @return a RoomsNotifications instance if found
     */
    public static RoomsNotifications loadRoomsNotifications(Context context) {
        File file = new File(context.getApplicationContext().getCacheDir(), ROOMS_NOTIFICATIONS_FILE_NAME);

        // test if the file exits
        if (!file.exists()) {
            return null;
        }

        RoomsNotifications roomsNotifications = null;
        FileInputStream fis = null;
        ByteArrayOutputStream fos = null;

        try {
            fis = new FileInputStream(file);
            fos = new ByteArrayOutputStream();


            byte[] readData = new byte[1024];
            int len;

            while ((len = fis.read(readData, 0, 1024)) > 0) {
                fos.write(readData, 0, len);
            }

            roomsNotifications = new RoomsNotifications(fos.toByteArray());
        } catch (Throwable t) {
            Log.e(LOG_TAG, "## loadRoomsNotifications() failed " + t.getMessage());
        }

        try {
            if (null != fis) {
                fis.close();
            }

            if (null != fos) {
                fos.close();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## loadRoomsNotifications() failed " + e.getMessage());
        }

        return roomsNotifications;
    }

    /*
     * *********************************************************************************************
     * Utils
     * *********************************************************************************************
     */

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
}
