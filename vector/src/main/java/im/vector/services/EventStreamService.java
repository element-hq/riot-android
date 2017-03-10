/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.services;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.MXStoreListener;
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
import im.vector.activity.VectorCallViewActivity;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorHomeActivity;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.util.NotificationUtils;
import im.vector.util.VectorCallSoundManager;
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

    private static final String LOG_TAG = "EventStreamService";

    /**
     *  static instance
     */
    private static EventStreamService mActiveEventStreamService = null;

    /**
     * Service action
     */
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
    /**
     * foreground notification description.
     * This permanent notification is displayed when
     * 1- the client uses GCM but the third party server registration fails
     * 2- the client does not use GCM.
     */
    private static final String NOTIFICATION_SUB_TITLE = "Listening for events";

    /**
     * Parameters to the service
     */
    public static final String EXTRA_STREAM_ACTION = "EventStreamService.EXTRA_STREAM_ACTION";
    public static final String EXTRA_MATRIX_IDS = "EventStreamService.EXTRA_MATRIX_IDS";

    /**
     * Notification identifiers
     */
    private static final int NOTIF_ID_MESSAGE = 60;
    private static final int NOTIF_ID_FOREGROUND_SERVICE = 61;

    private static final int FOREGROUND_LISTENING_FOR_EVENTS = 42;
    private static final int FOREGROUND_NOTIF_ID_PENDING_CALL = 44;
    private static final int FOREGROUND_ID_INCOMING_CALL = 45;
    private int mForegroundServiceIdentifier = -1;

    /**
     * Default bing rule
     */
    private static final BingRule mDefaultBingRule = new BingRule("ruleKind", "aPattern" , true, true, false);

    /**
     * Managed sessions
     */
    private ArrayList<MXSession> mSessions;

    /**
     * Session identifiers
     */
    private ArrayList<String> mMatrixIds;

    /**
     * The current state.
     */
    private StreamAction mServiceState = StreamAction.IDLE;

    /**
     * store the notifications description
     */
    private String mNotificationSessionId = null;
    private String mNotificationRoomId = null;
    private String mNotificationEventId = null;

    /**
     * call in progress (foreground notification)
     */
    private String mCallIdInProgress = null;

    /**
     * incoming (foreground notification)
     */
    private String mIncomingCallId = null;

    /**
     * true when the service is in foreground ie the GCM registration failed or is disabled.
     */
    private boolean mIsForeground = false;

    /**
     * the latest built notification
     */
    private Notification mLatestNotification = null;

    /**
     * list the notifications found between two onLiveEventsChunkProcessed()
     */
    private final ArrayList<String> mPendingNotifications = new ArrayList<>();

    /**
     * GCM manager
     */
    private GcmRegistrationManager mGcmRegistrationManager;

    /**
     * @return the event stream instance
     */
    public static EventStreamService getInstance() {
        return mActiveEventStreamService;
    }

    /**
     * Calls events listener.
     */
    private final MXCallsManager.MXCallsManagerListener mCallsManagerListener = new MXCallsManager.MXCallsManagerListener() {

        /**
         * Manage hangup event.
         * The ringing sound is disabled and pending incoming call is dismissed.
         * @param callId the callId
         */
        private void manageHangUpEvent(String callId) {
            if (null != callId) {
                Log.d(LOG_TAG, "manageHangUpEvent : hide call notification and stopRinging for call " + callId);
                hideCallNotifications();
            } else {
                Log.d(LOG_TAG, "manageHangUpEvent : stopRinging");
            }
            VectorCallSoundManager.stopRinging();
        }

        @Override
        public void onIncomingCall(final IMXCall call, MXUsersDevicesMap<MXDeviceInfo> unknownDevices) {
            Log.d(LOG_TAG, "onIncomingCall " + call.getCallId());

            IMXCall.MXCallListener callListener = new IMXCall.MXCallListener() {
                @Override
                public void onStateDidChange(String state) {
                    Log.d(LOG_TAG, "dispatchOnStateDidChange " + call.getCallId() + " : " + state);

                    // if there is no call push rule
                    // display the incoming call notification but with no sound
                    if (TextUtils.equals(state, IMXCall.CALL_STATE_CREATED) || TextUtils.equals(state, IMXCall.CALL_STATE_CREATING_CALL_VIEW)) {
                        displayIncomingCallNotification(call.getSession(), call.getRoom(), null, call.getCallId(), null);
                    }
                }

                @Override
                public void onCallError(String error) {
                    Log.d(LOG_TAG, "dispatchOnCallError " + call.getCallId() + " : " + error);
                    manageHangUpEvent(call.getCallId());
                }

                @Override
                public void onViewLoading(View callView) {
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
                public void onCallEnd(final int aReasonId) {
                    Log.d(LOG_TAG, "dispatchOnCallEnd " + call.getCallId());
                    manageHangUpEvent(call.getCallId());
                }

                @Override
                public void onPreviewSizeChanged(int width, int height) {
                }
            };

            call.addListener(callListener);
        }

        @Override
        public void onCallHangUp(final IMXCall call) {
            Log.d(LOG_TAG, "onCallHangUp " + call.getCallId());
            manageHangUpEvent(call.getCallId());
        }

        @Override
        public void onVoipConferenceStarted(String roomId) {
        }

        @Override
        public void onVoipConferenceFinished(String roomId) {
        }
    };

    /**
     * Live events listener
     */
    private final MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onBingEvent(Event event, RoomState roomState, BingRule bingRule) {
            // privacy
            //Log.d(LOG_TAG, "onBingEvent : the event " + event);
            //Log.d(LOG_TAG, "onBingEvent : the bingRule " + bingRule);

            Log.d(LOG_TAG, "prepareNotification : " + event.eventId + " in " + roomState.roomId);
            prepareNotification(event, roomState, bingRule);
        }

        @Override
        public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
            triggerPreparedNotification(true);
            mPendingNotifications.clear();

            // do not suspend the application if there is some active calls
            if ((StreamAction.CATCHUP == mServiceState) || (StreamAction.PAUSE == mServiceState)) {
                boolean hasActiveCalls = false;

                for (MXSession session : mSessions) {
                    hasActiveCalls |= session.mCallsManager.hasActiveCalls();
                }

                // if there are some active calls, the catchup should not be stopped.
                // because an user could answer to a call from another device.
                // there will no push because it is his own message.
                // so, the client has no choice to catchup until the ring is shutdown
                if (hasActiveCalls) {
                    Log.d(LOG_TAG, "onLiveEventsChunkProcessed : Catchup again because there are active calls");
                    catchup(false);
                } else if (StreamAction.CATCHUP == mServiceState) {
                    Log.d(LOG_TAG, "onLiveEventsChunkProcessed : no Active call");

                    // in some race conditions
                    // the call listener does not dispatch the call end
                    // for example when the call is stopped while the incoming call activity is creating
                    // the call is not initialized so the answerelsewhere / stop don't make sense.
                    if (VectorCallSoundManager.isRinging()) {
                        Log.d(LOG_TAG, "onLiveEventsChunkProcessed : there is no more call but the device is still ringing");
                        hideCallNotifications();
                        VectorCallSoundManager.stopRinging();
                    }
                    setServiceState(StreamAction.PAUSE);
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

        Log.d(LOG_TAG, "onStartCommand with action : " + action);

        if (intent.hasExtra(EXTRA_MATRIX_IDS)) {
            if (null == mMatrixIds) {
                mMatrixIds = new ArrayList<>(Arrays.asList(intent.getStringArrayExtra(EXTRA_MATRIX_IDS)));

                mSessions = new ArrayList<>();

                for(String matrixId : mMatrixIds) {
                    mSessions.add(Matrix.getInstance(getApplicationContext()).getSession(matrixId));
                }

                Log.d(LOG_TAG, "onStartCommand : update the matrix ids list to " + mMatrixIds);
            }
        }

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
                catchup(true);
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
     * @return the current state
     */
    private StreamAction getServiceState() {
        Log.d(LOG_TAG, "getState " + mServiceState);

        return mServiceState;
    }

    /**
     * Update the current thread state.
     * @param newState the new state.
     */
    private void setServiceState(StreamAction newState) {
        Log.d(LOG_TAG, "setState from " + mServiceState + " to " + newState);
        mServiceState = newState;
    }

    /**
     * internal start.
     */
    private void start() {
        StreamAction state = getServiceState();

        if (state == StreamAction.START) {
            Log.e(LOG_TAG, "start : Already started.");
            return;
        } else if ((state == StreamAction.PAUSE) || (state == StreamAction.CATCHUP)) {
            Log.e(LOG_TAG, "start : Resuming active stream.");
            resume();
            return;
        }

        if (mSessions == null) {
            Log.e(LOG_TAG, "start : No valid MXSession.");
            return;
        }

        Log.d(LOG_TAG, "## start : start the service");

        mActiveEventStreamService = this;

        for(MXSession session : mSessions) {
            if (null == session.getDataHandler()) {
                Log.e(LOG_TAG, "start : the session is not anymore valid.");
                return;
            }

            session.getDataHandler().addListener(mEventsListener);
            session.getDataHandler().getCallsManager().addListener(mCallsManagerListener);
            final IMXStore store = session.getDataHandler().getStore();

            // the store is ready (no data loading in progress...)
            if (store.isReady()) {
                startEventStream(session, store);
            } else {
                final MXSession fSession = session;
                // wait that the store is ready  before starting the events listener
                store.addMXStoreListener(new MXStoreListener() {
                    @Override
                    public void onStoreReady(String accountId) {
                        startEventStream(fSession, store);
                    }

                    @Override
                    public void onStoreCorrupted(String accountId, String description) {
                        // start a new initial sync
                        if (null == store.getEventStreamToken()) {
                            startEventStream(fSession, store);
                        } else {
                            // the data are out of sync
                            Matrix.getInstance(getApplicationContext()).reloadSessions(getApplicationContext());
                        }
                    }

                    @Override
                    public void onStoreOOM(final String accountId, final String description) {
                        Handler uiHandler = new Handler(getMainLooper());

                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), accountId + " : " + description, Toast.LENGTH_LONG).show();

                                Matrix.getInstance(getApplicationContext()).reloadSessions(getApplicationContext());
                            }
                        });
                    }
                });
            }
        }

        mGcmRegistrationManager = Matrix.getInstance(getApplicationContext()).getSharedGCMRegistrationManager();

        if (!mGcmRegistrationManager.useGCM()) {
            updateServiceForegroundState();
        }

        setServiceState(StreamAction.START);
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
        setServiceState(StreamAction.STOP);
        mActiveEventStreamService = null;
    }

    /**
     * internal pause method.
     */
    private void pause() {
        StreamAction state = getServiceState();

        if ((StreamAction.START == state) || (StreamAction.RESUME == state)) {
            Log.d(LOG_TAG, "onStartCommand pause from state " + state);

            if (mSessions != null) {
                for(MXSession session : mSessions) {
                    session.pauseEventStream();
                }

                setServiceState(StreamAction.PAUSE);
            }
        } else {
            Log.e(LOG_TAG, "onStartCommand invalid state pause " + state);
        }
    }

    /**
     * internal catchup method.
     * @param checkState true to check if the current state allow to perform a catchup
     */
    private void catchup(boolean checkState) {
        StreamAction state = getServiceState();

        boolean canCatchup = true;

        if (!checkState) {
            Log.d(LOG_TAG, "catchup  without checking state ");
        } else {
            Log.d(LOG_TAG, "catchup with state " + state + " CurrentActivity " + VectorApp.getCurrentActivity());

            // the catchup should only be done
            // 1- the state is in catchup : the event stream might have gone to sleep between two catchups
            // 2- the thread is suspended
            // 3- the application has been launched by a push so there is no displayed activity
            canCatchup = (state == StreamAction.CATCHUP) || (state == StreamAction.PAUSE) ||
                    ((StreamAction.START == state) && (null == VectorApp.getCurrentActivity()));
        }

        if (canCatchup) {
            if (mSessions != null) {
                for (MXSession session : mSessions) {
                    session.catchupEventStream();
                }
            } else {
                Log.e(LOG_TAG, "catchup no session");
            }

            setServiceState(StreamAction.CATCHUP);
        } else {
            Log.d(LOG_TAG, "No catchup is triggered because there is already a running event thread");
        }
    }

    /**
     * internal resume method.
     */
    private void resume() {
        Log.d(LOG_TAG, "## resume : resume the service");

        if (mSessions != null) {
            for(MXSession session : mSessions) {
                session.resumeEventStream();
            }
        }

        setServiceState(StreamAction.START);
    }

    /**
     * The GCM status has been updated (i.e disabled or enabled).
     */
    private void gcmStatusUpdate() {
        Log.d(LOG_TAG, "## gcmStatusUpdate");

        if (mIsForeground) {
            Log.d(LOG_TAG, "## gcmStatusUpdate : gcm status succeeds so stopForeground");
            if (FOREGROUND_LISTENING_FOR_EVENTS == mForegroundServiceIdentifier) {
                stopForeground(true);
                mForegroundServiceIdentifier = -1;
            }
            mIsForeground = false;
        }

        updateServiceForegroundState();
    }

    /**
     * Enable/disable the service foreground status.
     * The service is put in foreground ("Foreground process priority") when a sync polling is used,
     * to strongly reduce the likelihood of the App being killed.
     */
    private void updateServiceForegroundState() {
        Log.d(LOG_TAG, "## updateServiceForegroundState");

        MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();

        if (null == session) {
            Log.e(LOG_TAG, "## updateServiceForegroundState(): no session");
            return;
        }

        // GA issue
        if (null == mGcmRegistrationManager) {
            return;
        }

        if ((!mGcmRegistrationManager.useGCM() || !mGcmRegistrationManager.isServerRegistred()) && mGcmRegistrationManager.isBackgroundSyncAllowed() && mGcmRegistrationManager.areDeviceNotificationsAllowed()) {
            Log.d(LOG_TAG, "## updateServiceForegroundState : put the service in foreground");

            if (-1 == mForegroundServiceIdentifier) {
                Notification notification = buildForegroundServiceNotification();
                startForeground(NOTIF_ID_FOREGROUND_SERVICE, notification);
                mForegroundServiceIdentifier = FOREGROUND_LISTENING_FOR_EVENTS;
            }

            mIsForeground = true;
        } else {
            Log.d(LOG_TAG, "## updateServiceForegroundState : put the service in background");

            if (FOREGROUND_LISTENING_FOR_EVENTS == mForegroundServiceIdentifier) {
                stopForeground(true);
                mForegroundServiceIdentifier = -1;
            }
            mIsForeground = false;
        }
    }

    //================================================================================
    // notification management
    //================================================================================

    /**
     * Compute an event unique identifier.
     * @param event the event
     * @return the uid identifier
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
    @SuppressLint("NewApi")
    private Notification buildForegroundServiceNotification() {
        // build the pending intent go to the home screen if this is clicked.
        Intent i = new Intent(this, VectorHomeActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

        // build the notification builder
        NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(this);
        notifBuilder.setSmallIcon(R.drawable.permanent_notification_transparent);
        notifBuilder.setWhen(System.currentTimeMillis());
        notifBuilder.setContentTitle(getString(R.string.app_name));
        notifBuilder.setContentText(NOTIFICATION_SUB_TITLE);
        notifBuilder.setContentIntent(pi);

        // hide the notification from the status bar
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            notifBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
        }

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
     * Retrieve the room name.
     * @param session the session
     * @param room the room
     * @param event the event
     * @return the room name
     */
    private String getRoomName(MXSession session, Room room, Event event) {
        String roomName = VectorUtils.getRoomDisplayName(EventStreamService.this, session, room);

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
     * Prepare a call notification.
     * Only the incoming calls are managed by now and have a dedicated notification.
     * @param event the event
     * @param bingRule the bing rule
     */
    private void prepareCallNotification(Event event, BingRule bingRule) {

        // display only the invitation messages by now
        // because the other ones are not displayed.
        if (!event.getType().equals(Event.EVENT_TYPE_CALL_INVITE)) {
            Log.d(LOG_TAG, "prepareCallNotification : don't bing - Call invite");
            return;
        }

        MXSession session = Matrix.getMXSession(getApplicationContext(), event.getMatrixId());

        // invalid session ?
        // should never happen.
        // But it could be triggered because of multi accounts management.
        // The dedicated account is removing but some pushes are still received.
        if ((null == session) || !session.isAlive()) {
            Log.d(LOG_TAG, "prepareCallNotification : don't bing - no session");
            return;
        }

        Room room = session.getDataHandler().getRoom(event.roomId);

        // invalid room ?
        if (null == room) {
            Log.d(LOG_TAG, "prepareCallNotification : don't bing - the room does not exist");
            return;
        }

        String callId = null;

        try {
            callId = event.getContentAsJsonObject().get("call_id").getAsString();
        } catch (Exception e) {
            Log.e(LOG_TAG, "prepareNotification : getContentAsJsonObject " + e.getMessage());
        }

        if (!TextUtils.isEmpty(callId)) {
            displayIncomingCallNotification(session, room, event, callId, bingRule);
        }
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
            Log.d(LOG_TAG, "prepareNotification : don't bing - the event was already binged");
            checkNotification();
            return;
        }

        mPendingNotifications.add(uid);

        // define a bing rule if it is not provided
        if (null == bingRule) {
            bingRule = mDefaultBingRule;
        }

        if (!mGcmRegistrationManager.areDeviceNotificationsAllowed()) {
            Log.d(LOG_TAG, "prepareNotification : the push has been disable on this device");
            return;
        }

        if (event.isCallEvent()) {
            prepareCallNotification(event, bingRule);
            return;
        }

        final String roomId = event.roomId;

        // Just don't bing for the room the user's currently in
        if (!VectorApp.isAppInBackground() && (roomId != null) && event.roomId.equals(ViewedRoomTracker.getInstance().getViewedRoomId())) {
            Log.d(LOG_TAG, "prepareNotification : don't bing because it is the currently opened room");
            return;
        }

        String senderID = event.getSender();
        // FIXME: Support event contents with no body
        if (!event.getContent().getAsJsonObject().has("body")) {
            // only the membership events are supported
            if (!Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.getType()) && !event.isCallEvent()) {
                Log.d(LOG_TAG, "onBingEvent : don't bing - no body and not a call event");
                return;
            }
        }

        MXSession session = Matrix.getMXSession(getApplicationContext(), event.getMatrixId());

        // invalid session ?
        // should never happen.
        // But it could be triggered because of multi accounts management.
        // The dedicated account is removing but some pushes are still received.
        if ((null == session) || !session.isAlive()) {
            Log.d(LOG_TAG, "prepareNotification : don't bing - no session");
            return;
        }

        Room room = session.getDataHandler().getRoom(roomId);

        // invalid room ?
        if (null == room) {
            Log.d(LOG_TAG, "prepareNotification : don't bing - the room does not exist");
            return;
        }

        boolean isInvitationEvent = false;
       
        EventDisplay eventDisplay = new EventDisplay(getApplicationContext(), event, roomState);
        eventDisplay.setPrependMessagesWithAuthor(false);
        String body = eventDisplay.getTextualDisplay().toString();

        if (TextUtils.isEmpty(body)) {
            Log.e(LOG_TAG, "prepareNotification : the event " + event.eventId + " cannot be displayed");
            return;
        }

        if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.getType())) {
            try {
                isInvitationEvent = "invite".equals(event.getContentAsJsonObject().getAsJsonPrimitive("membership").getAsString());
            } catch (Exception e) {
                Log.e(LOG_TAG, "prepareNotification : invitation parsing failed");
            }
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
                    try {
                        largeBitmap = BitmapFactory.decodeFile(f.getPath(), options);
                    } catch (OutOfMemoryError oom) {
                        Log.e(LOG_TAG, "decodeFile failed with an oom");
                    }
                } else {
                    session.getMediasCache().loadAvatarThumbnail(session.getHomeserverConfig(), new ImageView(getApplicationContext()), member.avatarUrl, size);
                }
            }
        }

        if (null == largeBitmap) {
            largeBitmap = VectorUtils.getAvatar(getApplicationContext(), VectorUtils.getAvatarColor(senderID), TextUtils.isEmpty(from) ? senderID : from, true);
        }

        mNotificationSessionId = session.getCredentials().userId;
        mNotificationRoomId = roomId;
        mNotificationEventId = event.eventId;

        Log.d(LOG_TAG, "prepareNotification : with sound " + bingRule.isDefaultNotificationSound(bingRule.notificationSound()));

        mLatestNotification = NotificationUtils.buildMessageNotification(
                EventStreamService.this,
                from, session.getCredentials().userId,
                Matrix.getMXSessions(getApplicationContext()).size() > 1,
                largeBitmap,
                CommonActivityUtils.getBadgeCount(),
                body,
                event.roomId,
                getRoomName(session, room, event),
                bingRule.isDefaultNotificationSound(bingRule.notificationSound()),
                isInvitationEvent);
    }

    /**
     * Trigger the latest prepared notification
     * @param checkNotification true to check if the prepared notification still makes sense.
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
                    nm.notify(NOTIF_ID_MESSAGE, mLatestNotification);

                    // turn the screen on
                    if (mGcmRegistrationManager.isScreenTurnedOn()) {
                        // turn the screen on for 3 seconds
                        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "MXEventListener");
                        wl.acquire(3000);
                        wl.release();
                    }


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
     * @param accountId the account id
     * @param roomId the room id.
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
        Log.d(LOG_TAG, "clearNotification " + mNotificationSessionId + " - " + mNotificationRoomId + " - " + mNotificationEventId);

        NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            nm.cancelAll();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## clearNotification() failed " + e.getMessage());
        }

        // reset the identifiers
        mNotificationSessionId = null;
        mNotificationRoomId = null;
        mNotificationEventId = null;
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
        //Log.d(LOG_TAG, "checkNotification : session ID" + mNotificationSessionId + " - Notif ID " + mNotificationRoomId + " - Event id " + mNotificationEventId);

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
     * Display a permanent notification when there is an incoming call.
     * @param session the session
     * @param room the room
     * @param event the event
     * @param callId the callId
     * @param bingRule the bing rule.
     */
    private void displayIncomingCallNotification(MXSession session, Room room, Event event, String callId, BingRule bingRule) {
        Log.d(LOG_TAG, "displayIncomingCallNotification : " + callId + " in " + room.getRoomId());

        // the incoming call in progress is already displayed
        if (!TextUtils.isEmpty(mIncomingCallId)) {
            Log.d(LOG_TAG, "displayIncomingCallNotification : the incoming call in progress is already displayed");
        } else if (!TextUtils.isEmpty(mCallIdInProgress)) {
            Log.d(LOG_TAG, "displayIncomingCallNotification : a 'call in progress' notification is displayed");
        }
        // test if there is no active call
        else if (null == VectorCallViewActivity.getActiveCall()) {
            Log.d(LOG_TAG, "displayIncomingCallNotification : display the dedicated notification");

            if ((null != bingRule) && bingRule.isCallRingNotificationSound(bingRule.notificationSound())) {
                VectorCallSoundManager.startRinging();
            }

            Notification notification = NotificationUtils.buildIncomingCallNotification(
                    EventStreamService.this,
                    getRoomName(session, room, event),
                    session.getMyUserId(),
                    callId);

            if ((null != bingRule) && bingRule.isDefaultNotificationSound(bingRule.notificationSound())) {
                notification.defaults |= Notification.DEFAULT_SOUND;
            }

            startForeground(NOTIF_ID_FOREGROUND_SERVICE, notification);
            mForegroundServiceIdentifier = FOREGROUND_ID_INCOMING_CALL;

            mIncomingCallId = callId;

            // turn the screen on for 3 seconds
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "MXEventListener");
            wl.acquire(3000);
            wl.release();

        } else {
            Log.d(LOG_TAG, "displayIncomingCallNotification : do not display the incoming call notification because there is a pending call");
        }
    }

    /**
     * Display a call in progress notificatin.
     * @param session the session
     * @param callId the callId
     */
    public void displayCallInProgressNotification(MXSession session, Room room, String callId) {
        if (null != callId) {
            Notification notification = NotificationUtils.buildPendingCallNotification(getApplicationContext(), room.getName(session.getCredentials().userId), room.getRoomId(), session.getCredentials().userId, callId);
            startForeground(NOTIF_ID_FOREGROUND_SERVICE, notification);
            mForegroundServiceIdentifier = FOREGROUND_NOTIF_ID_PENDING_CALL;
            mCallIdInProgress = callId;
        }
    }

    /**
     * Hide the permanent call notifications
     */
    public void hideCallNotifications() {
        NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);

        // hide the call
        if ((FOREGROUND_NOTIF_ID_PENDING_CALL == mForegroundServiceIdentifier) || (FOREGROUND_ID_INCOMING_CALL == mForegroundServiceIdentifier)) {
            if (FOREGROUND_NOTIF_ID_PENDING_CALL == mForegroundServiceIdentifier) {
                mCallIdInProgress = null;
            } else {
                mIncomingCallId = null;
            }
            nm.cancel(NOTIF_ID_FOREGROUND_SERVICE);
            mForegroundServiceIdentifier = -1;
            stopForeground(true);
            updateServiceForegroundState();
        }
    }
}
