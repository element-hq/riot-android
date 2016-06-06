/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCall;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.util.EventDisplay;
import im.vector.VectorApp;
import im.vector.Matrix;
import im.vector.R;
import im.vector.ViewedRoomTracker;
import im.vector.activity.CallViewActivity;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorHomeActivity;
import im.vector.util.NotificationUtils;
import im.vector.util.VectorUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * A foreground service in charge of controlling whether the event stream is running or not.
 */
public class EventStreamService extends Service {
    public enum StreamAction {
        UNKNOWN,
        STOP,
        START,
        PAUSE,
        RESUME,
        CATCHUP,
        GCM_STATUS_UPDATE
    }

    public static final String EXTRA_STREAM_ACTION = "EventStreamService.EXTRA_STREAM_ACTION";
    public static final String EXTRA_MATRIX_IDS = "EventStreamService.EXTRA_MATRIX_IDS";

    private static final String LOG_TAG = "EventStreamService";
    private static final int NOTIFICATION_ID = 42;
    private static final int MSG_NOTIFICATION_ID = 43;
    private static final int PENDING_CALL_ID = 44;

    private ArrayList<MXSession> mSessions;
    private ArrayList<String> mMatrixIds;
    private StreamAction mState = StreamAction.UNKNOWN;

    // store the notifications description
    private String mNotificationSessionId = null;
    private String mNotificationRoomId = null;
    private String mNotificationEventId = null;
    private String mNotificationCallId = null;

    // call in progress
    // foreground notification
    private String mBackgroundNotificationCallId = null;

    private boolean mIsForegound = false;

    private Notification mLatestNotification = null;

    private static EventStreamService mActiveEventStreamService = null;

    public static EventStreamService getInstance() {
        return mActiveEventStreamService;
    }

    /**
     * Cancel the push notifications for a dedicated roomId.
     * If the roomId is null, cancel all the push notification.
     * @param roomId
     */
    public static void cancelNotificationsForRoomId(String accountId, String roomId) {
        Log.d(LOG_TAG, "cancelNotificationsForRoomId " + accountId + " - " + roomId);
        if (null != mActiveEventStreamService) {
            mActiveEventStreamService.cancelNotifications(accountId ,roomId);
        }
    }

    /**
     * Clear any displayed notification.
     */
    private void clearNotification() {
        Log.d(LOG_TAG, "clearNotification " + mNotificationSessionId + " - " + mNotificationRoomId + " - " + mNotificationEventId + " - " + mNotificationCallId);

        NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();

        // reset the identifiers
        mNotificationSessionId = null;
        mNotificationRoomId = null;
        mNotificationEventId = null;
        mNotificationCallId = null;
        mLatestNotification = null;
    }

    /**
     * Clear any displayed notification for a dedicated room and session id.
     * @param accountId the account id.
     * @param roomId the room id.
     */
    private void cancelNotifications(String accountId, String roomId) {
        Log.d(LOG_TAG, "cancelNotifications " + accountId + " - " + roomId);

        // sanity checks
        if ((null != accountId) && (null != roomId)) {
            Log.d(LOG_TAG, "cancelNotifications expected " + mNotificationSessionId + " - " + mNotificationRoomId);

            // cancel the notifications
            if (TextUtils.equals(mNotificationRoomId, roomId) && TextUtils.equals(accountId, mNotificationSessionId)) {
                clearNotification();
            }
        }
    }

    /**
     * Remove any pending notification.
     * It should be called when the application is logged out.
     */
    public static void removeNotification() {
        if (null != mActiveEventStreamService) {
            mActiveEventStreamService.clearNotification();
        }
    }

    /**
     * Check if a notification must be cleared because the linked event has been read, deleted ...
     */
    public static void checkDisplayedNotification() {
        if (null != mActiveEventStreamService) {
            mActiveEventStreamService.checkNotification();
        }
    }

    /**
     * Check if the current displayed notification must be cleared
     * because it doesn't make sense anymore.
     */
    private void checkNotification() {
        Log.d(LOG_TAG, "checkNotification " + mNotificationSessionId + " - " + mNotificationRoomId + " - " + mNotificationEventId);

        if (null != mNotificationRoomId) {
            boolean clearNotification = true;

            MXSession session = Matrix.getInstance(this).getSession(mNotificationSessionId);

            if (null != session) {
                Room room = session.getDataHandler().getRoom(mNotificationRoomId);

                if (null != room) {
                    Log.d(LOG_TAG, "checkNotification :  the room exists");

                    // invitation notification
                    if (null == mNotificationEventId) {
                        Log.d(LOG_TAG, "checkNotification :  room invitation case");
                        clearNotification = !room.isInvited();
                    } else if (null != mNotificationCallId) {
                        Log.d(LOG_TAG, "checkNotification :  call case");
                        IMXCall call =  CallViewActivity.getActiveCall();
                        clearNotification  = (null != call) && !TextUtils.equals(call.getCallId(), mNotificationCallId);
                    } else {
                        Log.d(LOG_TAG, "checkNotification :  event case");
                        clearNotification = room.isEventRead(mNotificationEventId);
                    }

                    Log.d(LOG_TAG, "checkNotification :  clearNotification " + clearNotification);

                } else {
                    Log.d(LOG_TAG, "checkNotification :  the room does not exist");
                }
            }
            
            if (clearNotification) {
                clearNotification();
            }
        }
    }

    // this imageView is used to preload the avatar thumbnail
    static private ImageView mDummyImageView;

    // live events listener
    private MXEventListener mListener = new MXEventListener() {
        /**
         * Manage hangup event.
         * The ringing sound is disabled and pending incoming call is dismissed.
         * @param event the hangup event.
         */
        private void manageHangUpEvent(Event event) {
            // check if the user answer from another device
            if (Event.EVENT_TYPE_CALL_ANSWER.equals(event.type)) {
                MXSession session = Matrix.getMXSession(getApplicationContext(), event.getMatrixId());

                // ignore the answer event if it was sent by another member
                if (!TextUtils.equals(event.getSender(), session.getCredentials().userId)) {
                    return;
                }
            }

            String callId = null;

            try {
                callId = event.getContentAsJsonObject().get("call_id").getAsString();
            } catch (Exception e) {}

            if (null != callId) {
                // hide the "call in progress notification"
                hidePendingCallNotification(callId);
            }

            Log.d(LOG_TAG, "manageHangUpEvent stopRinging");
            CallViewActivity.stopRinging();
        }

        @Override
        public void onLiveEvent(Event event, RoomState roomState) {
            if (Event.EVENT_TYPE_CALL_HANGUP.equals(event.type) || Event.EVENT_TYPE_CALL_ANSWER.equals(event.type)) {
                manageHangUpEvent(event);
            }
        }

        @Override
        public void onBingEvent(Event event, RoomState roomState, BingRule bingRule) {
            Log.d(LOG_TAG, "onBingEvent : the event " + event);
            Log.d(LOG_TAG, "onBingEvent : the bingRule " + bingRule);

            final String roomId = event.roomId;

            // Just don't bing for the room the user's currently in
            if (!VectorApp.isAppInBackground() && (roomId != null) && event.roomId.equals(ViewedRoomTracker.getInstance().getViewedRoomId())) {
                Log.d(LOG_TAG, "onBingEvent : don't bing because it is the currently opened room");
                return;
            }

            String senderID = event.getSender();
            // FIXME: Support event contents with no body
            if (!event.content.getAsJsonObject().has("body")) {
                // only the membership events are supported
                if (!Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) && !event.isCallEvent()) {
                    Log.d(LOG_TAG, "onBingEvent : don't bing - no body and not a call event");
                    return;
                }

                // display only the invitation messages by now
                // because the other ones are not displayed.
                if (event.isCallEvent() && !event.type.equals(Event.EVENT_TYPE_CALL_INVITE)) {
                    // dismiss the call notifications
                    if (event.type.equals(Event.EVENT_TYPE_CALL_HANGUP)) {
                        manageHangUpEvent(event);
                    }

                    Log.d(LOG_TAG, "onBingEvent : don't bing - Call invite");
                    return;
                }
            }

            MXSession session = Matrix.getMXSession(getApplicationContext(), event.getMatrixId());

            // invalid session ?
            // should never happen.
            // But it could be triggered because of multi accounts management.
            // The dedicated account is removing but some pushes are still received.
            if ((null == session) || !session.isAlive()) {
                Log.d(LOG_TAG, "onBingEvent : don't bing - no session");
                return;
            }

            Room room = session.getDataHandler().getRoom(roomId);

            // invalid room ?
            if (null == room) {
                Log.d(LOG_TAG, "onBingEvent : don't bing - the room does not exist");
                return;
            }

            boolean isInvitationEvent = false;
            String body;

            String notifiedCallId = null;

            // call invitation
            if (event.isCallEvent()) {
                if (event.type.equals(Event.EVENT_TYPE_CALL_INVITE)) {
                    body = getApplicationContext().getString(R.string.incoming_call);

                    try {
                        mNotificationCallId = notifiedCallId = event.getContentAsJsonObject().get("call_id").getAsString();
                     } catch (Exception e) {}


                } else {
                    EventDisplay eventDisplay = new EventDisplay(getApplicationContext(), event, room.getLiveState());
                    body = eventDisplay.getTextualDisplay().toString();
                }
            } else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                body = EventDisplay.getMembershipNotice(getApplicationContext(), event, roomState);

                try {
                    isInvitationEvent = "invite".equals(event.getContentAsJsonObject().getAsJsonPrimitive("membership").getAsString());
                } catch (Exception e) {}

                if (isInvitationEvent) {

                }

            } else {
                body = event.getContentAsJsonObject().getAsJsonPrimitive("body").getAsString();
            }

            String from = "";
            Bitmap largeBitmap = null;

            // when the event is an invitation one
            // don't check if the sender ID is known because the members list are not yet downloaded
            if (!isInvitationEvent) {
                RoomMember member = room.getMember(senderID);

                // invalid member
                if (null == member) {
                    return;
                }

                from = member.getName();

                // is there any avatar url
                if (!TextUtils.isEmpty(member.avatarUrl)) {
                    int size = getApplicationContext().getResources().getDimensionPixelSize(R.dimen.profile_avatar_size);

                    // check if the thumbnail is already downloaded
                    File f = session.getMediasCache().thumbnailCacheFile(member.avatarUrl, size);

                    if (null != f) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        largeBitmap = BitmapFactory.decodeFile(f.getPath(), options);
                    } else {
                        // else load it
                        mDummyImageView = new ImageView(getApplicationContext());
                        session.getMediasCache().loadAvatarThumbnail(session.getHomeserverConfig(), mDummyImageView, member.avatarUrl, size);
                    }
                }
            }

            if (null == largeBitmap) {
                largeBitmap = VectorUtils.getAvatar(getApplicationContext(), VectorUtils.getAvatarcolor(senderID), TextUtils.isEmpty(from) ? senderID : from, true);
            }

            String roomName = null;
            if(session.getMyUser() != null) {
                roomName = room.getName(session.getMyUserId());
            }

            mNotificationSessionId = session.getCredentials().userId;
            mNotificationRoomId = roomId;
            mNotificationEventId = event.eventId;

            if (bingRule.isCallRingNotificationSound(bingRule.notificationSound())) {
                if (null == CallViewActivity.getActiveCall()) {
                    Log.d(LOG_TAG, "onBingEvent starting");
                    CallViewActivity.startRinging(EventStreamService.this);
                }
            }

            Log.d(LOG_TAG, "onBingEvent : with sound " + bingRule.isDefaultNotificationSound(bingRule.notificationSound()));

            mLatestNotification = NotificationUtils.buildMessageNotification(
                    EventStreamService.this,
                    from, session.getCredentials().userId,
                    notifiedCallId,
                    Matrix.getMXSessions(getApplicationContext()).size() > 1,
                    largeBitmap,
                    CommonActivityUtils.getBadgeCount(),
                    CommonActivityUtils.getBadgeCount(),
                    body,
                    event.roomId,
                    roomName,
                    bingRule.isDefaultNotificationSound(bingRule.notificationSound()));
        }

        @Override
        public void onLiveEventsChunkProcessed() {
            if (null != mLatestNotification) {
                // check first if the message has not been read
                checkNotification();

                // if it is still defined.
                if (null != mLatestNotification) {
                    try {
                        NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
                        nm.cancelAll();
                        nm.notify(MSG_NOTIFICATION_ID, mLatestNotification);

                        // turn the screen on for 3 seconds
                        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "MXEventListener");
                        wl.acquire(3000);
                        wl.release();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onLiveEventsChunkProcessed crashed " + e.getLocalizedMessage());
                    }

                    mLatestNotification = null;
                }
            }

            // special catchup cases
            if (mState == StreamAction.CATCHUP) {
                boolean hasActiveCalls = false;

                for (MXSession session : mSessions) {
                    hasActiveCalls |= session.mCallsManager.hasActiveCalls();
                }

                // if there are some active calls, the catchup should not be stopped.
                // because an user could answer to a call from another device.
                // there will no push because it is his own message.
                // so, the client has no choice to catchup until the ring is shutdown
                if (hasActiveCalls) {
                    Log.d(LOG_TAG, "Catchup again because there are active calls");
                    catchup();
                }
            }
        }
    };

    /**
     * Add some accounts to the current service.
     * @param matrixIds the account identifiers to add.
     */
    public void startAccounts(List<String> matrixIds) {
        for(String matrixId : matrixIds) {
            // not yet started
            if (mMatrixIds.indexOf(matrixId) < 0) {
                MXSession session = Matrix.getInstance(getApplicationContext()).getSession(matrixId);

                mSessions.add(session);
                mMatrixIds.add(matrixId);
                session.getDataHandler().addListener(mListener);
                // perform a full sync
                session.startEventStream(null);
            }
        }
    }

    /**
     * Stop some accounts of the current service.
     * @param matrixIds the account identifiers to add.
     */
    public void stopAccounts(List<String> matrixIds) {
        for(String matrixId : matrixIds) {
            // not yet started
            if (mMatrixIds.indexOf(matrixId) >= 0) {
                MXSession session = Matrix.getInstance(getApplicationContext()).getSession(matrixId);

                if (null != session) {

                    session.stopEventStream();
                    session.getDataHandler().removeListener(mListener);

                    mSessions.remove(session);
                    mMatrixIds.remove(matrixId);
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (null == intent) {
            Log.e(LOG_TAG, "onStartCommand : null intent");

            if (null != mActiveEventStreamService) {
                Log.e(LOG_TAG, "onStartCommand : null intent with an active events stream service");
            } else {
                Log.e(LOG_TAG, "onStartCommand : null intent with no events stream service");
            }

            return START_NOT_STICKY;
        }

        StreamAction action = StreamAction.values()[intent.getIntExtra(EXTRA_STREAM_ACTION, StreamAction.UNKNOWN.ordinal())];

        if (intent.hasExtra(EXTRA_MATRIX_IDS)) {
            if (null == mMatrixIds) {
                mMatrixIds = new ArrayList<String>(Arrays.asList(intent.getStringArrayExtra(EXTRA_MATRIX_IDS)));

                mSessions = new ArrayList<MXSession>();

                for(String matrixId : mMatrixIds) {
                    mSessions.add(Matrix.getInstance(getApplicationContext()).getSession(matrixId));
                }
            }
        }

        Log.d(LOG_TAG, "onStartCommand >> "+action);
        switch (action) {
            case START:
            case RESUME:
                start();
                break;
            case STOP:
                Log.d(LOG_TAG, "the service is stopped");
                stop();
                break;
            case PAUSE:
                pause();
                break;
            case CATCHUP:
                catchup();
                break;
            case GCM_STATUS_UPDATE:
                gcmStatusUpdate();
            default:
                break;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "the service is destroyed");
        stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Start the even stream.
     * @param session the session
     * @param store the store
     */
    private void startEventStream(final MXSession session, final IMXStore store) {
        session.getDataHandler().checkPermanentStorageData();
        session.startEventStream(store.getEventStreamToken());

        session.getDataHandler().onStoreReady();
    }

    /**
     * internal start.
     */
    private void start() {
        if (mState == StreamAction.START) {
            Log.e(LOG_TAG, "Already started.");
            return;
        }
        else if ((mState == StreamAction.PAUSE) || (mState == StreamAction.CATCHUP)) {
            Log.e(LOG_TAG, "Resuming active stream.");
            resume();
            return;
        }

        if (mSessions == null) {
            Log.e(LOG_TAG, "No valid MXSession.");
            return;
        }

        mActiveEventStreamService = this;

        for(MXSession session : mSessions) {
            session.getDataHandler().addListener(mListener);
            final IMXStore store = session.getDataHandler().getStore();

            // the store is ready (no data loading in progress...)
            if (store.isReady()) {
                startEventStream(session, store);
            } else {
                final MXSession fSession = session;
                // wait that the store is ready  before starting the events listener
                store.setMXStoreListener(new IMXStore.MXStoreListener() {
                    @Override
                    public void onStoreReady(String accountId) {
                        startEventStream(fSession, store);
                    }

                    @Override
                    public void onStoreCorrupted(String accountId, String description) {
                        Toast.makeText(getApplicationContext(), accountId + " : " + description, Toast.LENGTH_LONG).show();
                        startEventStream(fSession, store);
                    }
                });
            }
        }

        updateListenerNotification();

        mState = StreamAction.START;
    }

    /**
     * internal stop.
     */
    private void stop() {
        Log.d(LOG_TAG, "the service is stopped.");

        if (mIsForegound) {
            stopForeground(true);
        }

        if (mSessions != null) {
            for(MXSession session : mSessions) {
                if (session.isAlive()) {
                    session.stopEventStream();
                    session.getDataHandler().removeListener(mListener);
                }
            }
        }
        mMatrixIds = null;
        mSessions = null;
        mState = StreamAction.STOP;

        mActiveEventStreamService = null;
    }

    /**
     * internal pause method.
     */
    private void pause() {
        if ((mState == StreamAction.START) || (mState == StreamAction.RESUME)) {
            Log.d(LOG_TAG, "onStartCommand pause");

            if (mSessions != null) {
                for(MXSession session : mSessions) {
                    session.pauseEventStream();
                }
                mState = StreamAction.PAUSE;
            }
        } else {
            Log.e(LOG_TAG, "onStartCommand invalid state pause " + mState);
        }
    }

    /**
     * internal catchup method.
     */
    private void catchup() {
        Log.d(LOG_TAG, "catchup with state " + mState + " CurrentActivity " + VectorApp.getCurrentActivity());

        // the catchup should only be done when the thread is suspended
        Boolean canCatchup = (mState == StreamAction.PAUSE) || (mState == StreamAction.CATCHUP);
        //

        // other use case
        // the application has been launched by a push
        // so there is no displayed activity
        if (!canCatchup && (mState == StreamAction.START)) {
            canCatchup = (null == VectorApp.getCurrentActivity());
        }

        if (canCatchup) {
            if (mSessions != null) {
                for (MXSession session : mSessions) {
                    session.catchupEventStream();
                }
            } else {
                Log.e(LOG_TAG, "catchup no session");
            }

            mState = StreamAction.CATCHUP;
        } else {
            Log.d(LOG_TAG, "No catchup is triggered because there is already a running event thread");
        }
    }

    /**
     * internal resume method.
     */
    private void resume() {
        if (mSessions != null) {
            for(MXSession session : mSessions) {
                session.resumeEventStream();
            }
        }

        mState = StreamAction.START;
    }

    /**
     * The GCM status has been updated (i.e disabled or enabled).
     */
    private void gcmStatusUpdate() {
        if (mIsForegound) {
            stopForeground(true);
            mIsForegound = false;
        }

        updateListenerNotification();
    }

    /**
     * polling listener when the GCM is disabled
     */
    private void updateListenerNotification() {
        if (!Matrix.getInstance(this).getSharedGcmRegistrationManager().useGCM()) {
            Notification notification = buildNotification();
            startForeground(NOTIFICATION_ID, notification);
            mIsForegound = true;
        } else {
            stopForeground(true);
            mIsForegound = false;
        }
    }

    /**
     * @return the polling thread listener notification
     */
    private Notification buildNotification() {
        Notification notification = new Notification(
                (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) ? R.drawable.ic_menu_small_matrix : R.drawable.ic_menu_small_matrix_transparent,
                "Matrix",
                System.currentTimeMillis()
        );

        // go to the home screen if this is clicked.
        Intent i = new Intent(this, VectorHomeActivity.class);

        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

        notification.setLatestEventInfo(this, getString(R.string.app_name),
                "Listening for events",
                pi);
        notification.flags |= Notification.FLAG_NO_CLEAR;
        return notification;
    }


    /**
     * A call is in progress.
     * @param session the session
     * @param callId the callId
     */
    public void displayPendingCallNotification(MXSession session, Room room, String callId) {
        if (null != callId) {
            Notification notification = NotificationUtils.buildCallNotification(getApplicationContext(), room.getName(session.getCredentials().userId), room.getRoomId(), session.getCredentials().userId, callId);
            startForeground(PENDING_CALL_ID, notification);
            mBackgroundNotificationCallId = callId;
        }
    }

    /**
     * @param callId the ended call call id
     */
    public void hidePendingCallNotification(String callId) {
        if (TextUtils.equals(mBackgroundNotificationCallId, callId)) {
            stopForeground(true);
            updateListenerNotification();
            mBackgroundNotificationCallId = null;
        }

        // hide the "incoming call" notification
        if (TextUtils.equals(mNotificationCallId, callId)) {
            NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancelAll();
            mNotificationCallId = null;
        }
    }
}
