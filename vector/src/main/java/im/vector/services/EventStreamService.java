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
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.util.EventDisplay;
import im.vector.VectorApp;
import im.vector.Matrix;
import im.vector.R;
import im.vector.ViewedRoomTracker;
import im.vector.activity.CallViewActivity;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorHomeActivity;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.util.NotificationUtils;
import im.vector.util.VectorUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;

/**
 * A foreground service in charge of controlling whether the event stream is running or not.
 */
public class EventStreamService extends Service {
    public enum StreamAction {
        IDLE,
        STOP,
        START,
        PAUSE,
        RESUME,
        CATCHUP,
        GCM_STATUS_UPDATE
    }

    // notification sub title,  when sync polling thread is enabled:
    private static final String NOTIFICATION_SUB_TITLE = "Listening for events";

    public static final String EXTRA_STREAM_ACTION = "EventStreamService.EXTRA_STREAM_ACTION";
    public static final String EXTRA_MATRIX_IDS = "EventStreamService.EXTRA_MATRIX_IDS";

    private static final String LOG_TAG = "EventStreamService";
    private static final int NOTIFICATION_ID = 42;
    private static final int MSG_NOTIFICATION_ID = 43;
    private static final int PENDING_CALL_ID = 44;

    private ArrayList<MXSession> mSessions;
    private ArrayList<String> mMatrixIds;
    private StreamAction mState = StreamAction.IDLE;

    // store the notifications description
    private String mNotificationSessionId = null;
    private String mNotificationRoomId = null;
    private String mNotificationEventId = null;
    private String mNotificationCallId = null;

    // call in progress
    // foreground notification
    private String mBackgroundNotificationCallId = null;

    // true when the service is in foreground ie the GCM registration failed.
    private boolean mIsForeground = false;

    // the latest prepared notification
    private Notification mLatestNotification = null;

    // list the notifications found between two onLiveEventsChunkProcessed()
    private ArrayList<String> mPendingNotifications = new ArrayList<>();

    // define a default bing rule
    private static BingRule mDefaultBingRule = new BingRule("ruleKind", "aPattern" , true, true, false);

    // static instance
    private static EventStreamService mActiveEventStreamService = null;

    /**
     * @return the event stream instance
     */
    public static EventStreamService getInstance() {
        return mActiveEventStreamService;
    }

    // this imageView is used to preload the avatar thumbnail
    static private ImageView mDummyImageView;

    private MXCallsManager.MXCallsManagerListener mCallsManagerListener = new MXCallsManager.MXCallsManagerListener() {

        /**
         * Manage hangup event.
         * The ringing sound is disabled and pending incoming call is dismissed.
         * @param callId the callId
         */
        private void manageHangUpEvent(String callId) {
            if (null != callId) {
                // hide the "call in progress notification"
                hidePendingCallNotification(callId);
            }

            Log.d(LOG_TAG, "manageHangUpEvent stopRinging");
            CallViewActivity.stopRinging();
        }

        @Override
        public void onIncomingCall(final IMXCall call) {
            Log.d(LOG_TAG, "onIncomingCall " + call.getCallId());

            IMXCall.MXCallListener callListener = new IMXCall.MXCallListener() {
                @Override
                public void onStateDidChange(String state) {
                    Log.d(LOG_TAG, "onStateDidChange " + call.getCallId() + " : " + state);
                }

                @Override
                public void onCallError(String error) {
                    Log.d(LOG_TAG, "onCallError " + call.getCallId() + " : " + error);
                    manageHangUpEvent(call.getCallId());
                }

                @Override
                public void onViewLoading(View callview) {
                }

                @Override
                public void onViewReady() {
                }

                @Override
                public void onCallAnsweredElsewhere() {
                    Log.d(LOG_TAG, "onCallAnsweredElsewhere " + call.getCallId());
                    manageHangUpEvent(call.getCallId());
                }

                @Override
                public void onCallEnd() {
                    Log.d(LOG_TAG, "onCallEnd " + call.getCallId());
                    manageHangUpEvent(call.getCallId());
                }
            };

            call.addListener(callListener);
        }

        @Override
        public void onCallHangUp(final IMXCall call) {
            Log.d(LOG_TAG, "onCallHangUp " + call.getCallId());

            manageHangUpEvent(call.getCallId());
        }
    };

    // live events listener
    private MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onBingEvent(Event event, RoomState roomState, BingRule bingRule) {
            Log.d(LOG_TAG, "onBingEvent : the event " + event);
            Log.d(LOG_TAG, "onBingEvent : the bingRule " + bingRule);

            prepareNotification(event, roomState, bingRule);
        }

        @Override
        public void onLiveEventsChunkProcessed() {
            triggerPreparedNotification(true);
            mPendingNotifications.clear();

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
                } else {
                    Log.d(LOG_TAG, "no Active call");
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
                session.getDataHandler().addListener(mEventsListener);
                session.getDataHandler().getCallsManager().addListener(mCallsManagerListener);
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
                    session.getDataHandler().removeListener(mEventsListener);
                    session.getDataHandler().getCallsManager().removeListener(mCallsManagerListener);

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

        StreamAction action = StreamAction.values()[intent.getIntExtra(EXTRA_STREAM_ACTION, StreamAction.IDLE.ordinal())];

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
                Log.d(LOG_TAG, "## onStartCommand(): service stopped");
                stopSelf();
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
            session.getDataHandler().addListener(mEventsListener);
            session.getDataHandler().getCallsManager().addListener(mCallsManagerListener);
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
                        //Toast.makeText(getApplicationContext(), accountId + " : " + description, Toast.LENGTH_LONG).show();
                        startEventStream(fSession, store);
                    }
                });
            }
        }

        if (!Matrix.getInstance(getApplicationContext()).getSharedGcmRegistrationManager().useGCM()) {
            updateServiceForegroundState();
        }

        mState = StreamAction.START;
    }

    /**
     * internal stop.
     */
    private void stop() {
        Log.d(LOG_TAG, "## stop(): the service is stopped");

        if (mIsForeground) {
            stopForeground(true);
        }

        if (mSessions != null) {
            for(MXSession session : mSessions) {
                if (session.isAlive()) {
                    session.stopEventStream();
                    session.getDataHandler().removeListener(mEventsListener);
                    session.getDataHandler().getCallsManager().removeListener(mCallsManagerListener);
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
        boolean canCatchup = (mState == StreamAction.PAUSE) || (mState == StreamAction.CATCHUP);


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
        if (mIsForeground) {
            stopForeground(true);
            mIsForeground = false;
        }

        updateServiceForegroundState();
    }

    /**
     * Enable/disable the service to be in foreground or not.
     * The service is put in foreground when a sync polling is used,
     * to strongly reduce the likelihood of the App being killed.
     */
    private void updateServiceForegroundState() {
        MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();

        if (null == session) {
            Log.e(LOG_TAG, "updateServiceForegroundState : no session");
            return;
        }

        GcmRegistrationManager gcmnMgr = Matrix.getInstance(this).getSharedGcmRegistrationManager();

        // detect if the polling thread must be started
        // i.e a session must be defined
        // and GCM disabled or GCM registration failed
        if ((!gcmnMgr.useGCM() || gcmnMgr.usePollingThread()) && gcmnMgr.isBackgroundSyncAllowed()) {
            Notification notification = buildNotification();
            startForeground(NOTIFICATION_ID, notification);
            mIsForeground = true;
        } else {
            stopForeground(true);
            mIsForeground = false;
        }
    }

    //================================================================================
    // notification management
    //================================================================================

    /**
     * Compute an event unique identifier.
     * @param event the event
     * @return the uid idenfier
     */
    private static String computeEventUID(Event event) {
        if (null != event) {
            return (event.roomId + "-" + event.eventId);
        } else {
            return "invalid";
        }
    }

    /**
     * @return the polling thread listener notification
     */
    private Notification buildNotification() {
        // build the pending intent go to the home screen if this is clicked.
        Intent i = new Intent(this, VectorHomeActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

        // build the notification builder
        NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(this);
        notifBuilder.setSmallIcon(R.drawable.logo_transparent);
        notifBuilder.setWhen(System.currentTimeMillis());
        notifBuilder.setContentTitle(getString(R.string.app_name));
        notifBuilder.setContentText(NOTIFICATION_SUB_TITLE);
        notifBuilder.setContentIntent(pi);

        Notification notification = notifBuilder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // some devices crash if this field is not set
            // even if it is deprecated

            // setLatestEventInfo() is deprecated on Android M, so we try to use
            // reflection at runtime, to avoid compiler error: "Cannot resolve method.."
            try {
                Method deprecatedMethod = notification.getClass().getMethod("setLatestEventInfo", Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                deprecatedMethod.invoke(notification, this, getString(R.string.app_name), NOTIFICATION_SUB_TITLE, pi);
            } catch (Exception ex) {
                Log.e(LOG_TAG, "## buildNotification(): Exception - setLatestEventInfo() Msg="+ex.getMessage());
            }
        }

        return notification;
    }

    /**
     * Prepare a notification for the expected event.
     * @param event the event
     * @param roomState the room state
     * @param bingRule the bing rule
     */
    public void prepareNotification(Event event, RoomState roomState, BingRule bingRule) {
        String uid = computeEventUID(event);

        if (mPendingNotifications.indexOf(uid) >= 0) {
            Log.d(LOG_TAG, "onBingEvent : don't bing - the event was already binged");
            checkNotification();
            return;
        }

        mPendingNotifications.add(uid);

        // define a bing rule if it is not provided
        if (null == bingRule) {
            bingRule = mDefaultBingRule;
        }

        GcmRegistrationManager gcmGcmRegistrationManager = Matrix.getInstance(getApplicationContext()).getSharedGcmRegistrationManager();

        if (!gcmGcmRegistrationManager.isNotificationsAllowed()) {
            Log.d(LOG_TAG, "onBingEvent : the push has been disable on this device");
            return;
        }

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
            } catch (Exception e) {
                Log.e(LOG_TAG, "onBingEvent : invitation parsing failed");
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

            // avoid room Id as name
            if (TextUtils.equals(roomName, room.getRoomId())) {
                User user = session.getDataHandler().getStore().getUser(senderID);

                if (null != user) {
                    roomName = user.displayname;
                } else {
                    roomName = senderID;
                }
             }
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

    /**
     * Trigger the latest prepared notification
     * @param checkNotification true to check if the preparaed notification still makes sense.
     */
    public void triggerPreparedNotification(boolean checkNotification) {
        if (null != mLatestNotification) {
            if (checkNotification) {
                // check first if the message has not been read
                checkNotification();
            }

            // if it is still defined.
            if (null != mLatestNotification) {
                try {
                    NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.cancelAll();
                    nm.notify(MSG_NOTIFICATION_ID, mLatestNotification);

                    // turn the screen on for 3 seconds
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "MXEventListener");
                    wl.acquire(3000);
                    wl.release();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "onLiveEventsChunkProcessed crashed " + e.getLocalizedMessage());
                }

                mLatestNotification = null;
            }
        }
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
            updateServiceForegroundState();
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
