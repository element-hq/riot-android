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
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXStoreListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.ViewedRoomTracker;
import im.vector.activity.VectorCallViewActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.util.NotificationUtils;
import im.vector.util.VectorCallSoundManager;

/**
 * A foreground service in charge of controlling whether the event stream is running or not.
 */
public class EventStreamService extends Service {

    private static final String LOG_TAG = "EventStreamService";

    /**
     * static instance
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
        GCM_STATUS_UPDATE,
        AUTO_RESTART
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
    public static final String EXTRA_AUTO_RESTART_ACTION = "EventStreamService.EXTRA_AUTO_RESTART_ACTION";

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
    private static final BingRule mDefaultBingRule = new BingRule("ruleKind", "aPattern", true, true, false);

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
    private final LinkedHashMap<String, NotificationUtils.NotifiedEvent> mPendingNotifications = new LinkedHashMap<>();
    private Map<String, List<NotificationUtils.NotifiedEvent>> mNotifiedEventsByRoomId = null;
    private static HandlerThread mNotificationHandlerThread = null;
    private static android.os.Handler mNotificationsHandler = null;

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
     * GCM manager
     */
    private GcmRegistrationManager mGcmRegistrationManager;

    /**
     * Tell if the service must be suspended after started.
     * It is used when the service is automatically restarted by Android.
     */
    private boolean mSuspendWhenStarted = false;

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
     * Track bing rules updates
     */
    private final BingRulesManager.onBingRulesUpdateListener mBingRulesUpdatesListener = new BingRulesManager.onBingRulesUpdateListener() {
        @Override
        public void onBingRulesUpdate() {
            getNotificationsHandler().post(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "## on bing rules update");
                    mNotifiedEventsByRoomId = null;
                    refreshMessagesNotification();
                }
            });
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
            getNotificationsHandler().post(new Runnable() {
                @Override
                public void run() {
                    refreshMessagesNotification();
                    mPendingNotifications.clear();
                }
            });

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
     *
     * @param matrixIds the account identifiers to add.
     */
    public void startAccounts(List<String> matrixIds) {
        for (String matrixId : matrixIds) {
            // not yet started
            if (mMatrixIds.indexOf(matrixId) < 0) {
                MXSession session = Matrix.getInstance(getApplicationContext()).getSession(matrixId);

                mSessions.add(session);
                mMatrixIds.add(matrixId);
                session.getDataHandler().addListener(mEventsListener);
                session.getDataHandler().getCallsManager().addListener(mCallsManagerListener);
                session.getDataHandler().getBingRulesManager().addBingRulesUpdateListener(mBingRulesUpdatesListener);
                // perform a full sync
                session.startEventStream(null);
            }
        }
    }

    /**
     * Stop some accounts of the current service.
     *
     * @param matrixIds the account identifiers to add.
     */
    public void stopAccounts(List<String> matrixIds) {
        for (String matrixId : matrixIds) {
            // not yet started
            if (mMatrixIds.indexOf(matrixId) >= 0) {
                MXSession session = Matrix.getInstance(getApplicationContext()).getSession(matrixId);

                if (null != session) {

                    session.stopEventStream();
                    session.getDataHandler().removeListener(mEventsListener);
                    session.getDataHandler().getCallsManager().removeListener(mCallsManagerListener);
                    session.getDataHandler().getBingRulesManager().removeBingRulesUpdateListener(mBingRulesUpdatesListener);
                    mSessions.remove(session);
                    mMatrixIds.remove(matrixId);
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // no intent : restarted by Android
        // EXTRA_AUTO_RESTART_ACTION : restarted by the service itself (
        if ((null == intent) || intent.hasExtra(EXTRA_AUTO_RESTART_ACTION)) {
            boolean restart = false;

            if (StreamAction.AUTO_RESTART == mServiceState) {
                Log.e(LOG_TAG, "onStartCommand : auto restart in progress ignore current command");
                return START_STICKY;
            } else if (null == intent) {
                Log.e(LOG_TAG, "onStartCommand : null intent -> restart the service");
                restart = true;
            } else if  (StreamAction.IDLE == mServiceState) {
                Log.e(LOG_TAG, "onStartCommand : automatically restart the service");
                restart = true;
            } else if (StreamAction.STOP == mServiceState) {
                // it might have some race conditions when the service is restarted by android
                // the state should first switch from STOP to IDLE : android restarts the service with no parameters
                // the AUTO_RESTART timer should be triggered
                Log.e(LOG_TAG, "onStartCommand : automatically restart the service even if the service is stopped");
                restart = true;
            } else {
                Log.e(LOG_TAG, "onStartCommand : EXTRA_AUTO_RESTART_ACTION has been set but mServiceState = " + mServiceState);
            }

            if (restart) {
                List<MXSession> sessions = Matrix.getInstance(getApplicationContext()).getSessions();

                if ((null == sessions) || sessions.isEmpty()) {
                    Log.e(LOG_TAG, "onStartCommand : no session");
                    return START_NOT_STICKY;
                }

                if ((null != VectorApp.getInstance()) && VectorApp.getInstance().didAppCrash()) {
                    Log.e(LOG_TAG, "onStartCommand : no auto restart because the application crashed");
                    return START_NOT_STICKY;
                }

                mSessions = new ArrayList<>();
                mSessions.addAll(Matrix.getInstance(getApplicationContext()).getSessions());

                mMatrixIds = new ArrayList<>();

                for (MXSession session : mSessions) {
                    session.getDataHandler().getStore().open();
                    mMatrixIds.add(session.getMyUserId());
                }

                mSuspendWhenStarted = true;

                start();

                // if the service successfully restarts
                if (StreamAction.START == mServiceState) {
                    // update the state to a dedicated one
                    setServiceState(StreamAction.AUTO_RESTART);
                }

                return START_STICKY;
            }
        }

        mSuspendWhenStarted = false;

        StreamAction action = StreamAction.values()[intent.getIntExtra(EXTRA_STREAM_ACTION, StreamAction.IDLE.ordinal())];

        Log.d(LOG_TAG, "onStartCommand with action : " + action);

        if (intent.hasExtra(EXTRA_MATRIX_IDS)) {
            if (null == mMatrixIds) {
                mMatrixIds = new ArrayList<>(Arrays.asList(intent.getStringArrayExtra(EXTRA_MATRIX_IDS)));
                mSessions = new ArrayList<>();

                for (String matrixId : mMatrixIds) {
                    mSessions.add(Matrix.getInstance(getApplicationContext()).getSession(matrixId));
                }

                Log.d(LOG_TAG, "onStartCommand : update the matrix ids list to " + mMatrixIds);
            }
        }

        switch (action) {
            case START:
            case RESUME: {
                if ((null == mSessions) || mSessions.isEmpty()) {
                    Log.e(LOG_TAG, "onStartCommand : empty sessions list with action " + action);
                    return START_NOT_STICKY;
                }

                start();
                break;
            }
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

    /**
     * onTaskRemoved is called when the user swipes the application from the active applications.
     * On some devices, the service is not automatically restarted.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(LOG_TAG, "## onTaskRemoved()");

        stop();

        // reset the service identifier
        mForegroundServiceIdentifier = -1;

        // restart the services after 3 seconds
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        restartServiceIntent.putExtra(EXTRA_AUTO_RESTART_ACTION, EXTRA_AUTO_RESTART_ACTION);
        PendingIntent restartPendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager myAlarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        myAlarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 3000,
                restartPendingIntent);

        super.onTaskRemoved(rootIntent);
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
     *
     * @param session the session
     * @param store   the store
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
     *
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
        final GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(getApplicationContext()).getSharedGCMRegistrationManager();
        StreamAction state = getServiceState();

        if (state == StreamAction.START) {
            Log.e(LOG_TAG, "start : Already started.");

            for (MXSession session : mSessions) {
                session.refreshNetworkConnection();
            }
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

        for (final MXSession session : mSessions) {
            if (null == session.getDataHandler()) {
                Log.e(LOG_TAG, "start : the session is not anymore valid.");
                return;
            }

            session.getDataHandler().addListener(mEventsListener);
            session.getDataHandler().getCallsManager().addListener(mCallsManagerListener);
            session.getDataHandler().getBingRulesManager().addBingRulesUpdateListener(mBingRulesUpdatesListener);

            final IMXStore store = session.getDataHandler().getStore();

            // the store is ready (no data loading in progress...)
            if (store.isReady()) {
                startEventStream(session, store);
                if (mSuspendWhenStarted) {
                    if (null != gcmRegistrationManager) {
                        session.setSyncDelay(gcmRegistrationManager.getBackgroundSyncDelay());
                        session.setSyncTimeout(gcmRegistrationManager.getBackgroundSyncTimeOut());
                    }

                    catchup(false);
                }
            } else {
                final MXSession fSession = session;
                // wait that the store is ready  before starting the events listener
                store.addMXStoreListener(new MXStoreListener() {
                    @Override
                    public void onStoreReady(String accountId) {
                        startEventStream(fSession, store);

                        if (mSuspendWhenStarted) {
                            if (null != gcmRegistrationManager) {
                                session.setSyncDelay(gcmRegistrationManager.getBackgroundSyncDelay());
                                session.setSyncTimeout(gcmRegistrationManager.getBackgroundSyncTimeOut());
                            }

                            catchup(false);
                        }
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
            for (MXSession session : mSessions) {
                if (session.isAlive()) {
                    session.stopEventStream();
                    session.getDataHandler().removeListener(mEventsListener);
                    session.getDataHandler().getCallsManager().removeListener(mCallsManagerListener);
                    session.getDataHandler().getBingRulesManager().removeBingRulesUpdateListener(mBingRulesUpdatesListener);
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
                for (MXSession session : mSessions) {
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
     *
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
            for (MXSession session : mSessions) {
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
        notifBuilder.setContentTitle(getString(R.string.riot_app_name));
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
                deprecatedMethod.invoke(notification, this, getString(R.string.riot_app_name), NOTIFICATION_SUB_TITLE, pi);
            } catch (Exception ex) {
                Log.e(LOG_TAG, "## buildNotification(): Exception - setLatestEventInfo() Msg=" + ex.getMessage());
            }
        }

        return notification;
    }

    /**
     * Prepare a call notification.
     * Only the incoming calls are managed by now and have a dedicated notification.
     *
     * @param event    the event
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
     *
     * @param event     the event
     * @param roomState the room state
     * @param bingRule  the bing rule
     */
    public void prepareNotification(Event event, RoomState roomState, BingRule bingRule) {
        if (mPendingNotifications.containsKey(event.eventId)) {
            Log.d(LOG_TAG, "prepareNotification : don't bing - the event was already binged");
            return;
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

        // define a bing rule if it is not provided
        if (null == bingRule) {
            bingRule = mDefaultBingRule;
        }

        mPendingNotifications.put(event.eventId, new NotificationUtils.NotifiedEvent(event.roomId, event.eventId, bingRule));
    }

    /**
     * Cancel the push notifications for a dedicated roomId.
     * If the roomId is null, cancel all the push notification.
     *
     * @param accountId the account id
     * @param roomId    the room id.
     */
    public static void cancelNotificationsForRoomId(String accountId, String roomId) {
        Log.d(LOG_TAG, "cancelNotificationsForRoomId " + accountId + " - " + roomId);
        if (null != mActiveEventStreamService) {
            mActiveEventStreamService.cancelNotifications(accountId, roomId);
        }
    }

    /**
     * Provide the notifications handler
     * @return the notifications handler.
     */
    private android.os.Handler getNotificationsHandler() {
        if (null == mNotificationHandlerThread) {
            try {
                mNotificationHandlerThread = new HandlerThread("NotificationsService_" + System.currentTimeMillis(), Thread.MIN_PRIORITY);
                mNotificationHandlerThread.start();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getNotificationsHandler failed : " + e.getMessage());
            }
        }

        if (null == mNotificationsHandler) {
            try {
                mNotificationsHandler = new android.os.Handler(mNotificationHandlerThread.getLooper());
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getNotificationsHandler failed : " + e.getMessage());
            }
        }

        // never returns a null handler
        if (null == mNotificationsHandler) {
            return new android.os.Handler(getMainLooper());
        } else {
            return mNotificationsHandler;
        }
    }

    /**
     * Clear any displayed notification.
     */
    private void clearNotification() {
        NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            nm.cancelAll();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## clearNotification() failed " + e.getMessage());
        }

        getNotificationsHandler().post(new Runnable() {
            @Override
            public void run() {
                // reset the identifiers
                if (null != mPendingNotifications) {
                    mPendingNotifications.clear();
                }

                if (null != mNotifiedEventsByRoomId) {
                    mNotifiedEventsByRoomId.clear();
                }
            }
        });
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
    public static void checkDisplayedNotifications() {
        if (null != mActiveEventStreamService) {
            mActiveEventStreamService.getNotificationsHandler().post(new Runnable() {
                @Override
                public void run() {
                    mActiveEventStreamService.refreshMessagesNotification();
                }
            });
        }
    }

    /**
     * Cancel notifications for a dedicated room.
     *
     * @param accountId the account
     * @param roomId    the room Id
     */
    private void cancelNotifications(final String accountId, final String roomId) {
        getNotificationsHandler().post(new Runnable() {
            @Override
            public void run() {
                if ((null != mNotifiedEventsByRoomId) && mNotifiedEventsByRoomId.containsKey(roomId)) {
                    mNotifiedEventsByRoomId = null;
                    refreshMessagesNotification();
                }
            }
        });
    }

    /**
     * Refresh the messages notification.
     * Must always be called in getNotificationsHandler() thread.
     */
    public void refreshMessagesNotification() {
        final NotificationManagerCompat nm = NotificationManagerCompat.from(EventStreamService.this);

        NotificationUtils.NotifiedEvent eventToNotify = getEventToNotify();
        if (!mGcmRegistrationManager.areDeviceNotificationsAllowed()) {
            mNotifiedEventsByRoomId = null;
            new Handler(getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    nm.cancel(NOTIF_ID_MESSAGE);
                }
            });
        } else if (refreshNotifiedMessagesList()) {
            // no more notifications
            if ((null == mNotifiedEventsByRoomId) || mNotifiedEventsByRoomId.size() == 0) {
                new Handler(getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        nm.cancel(NOTIF_ID_MESSAGE);
                    }
                });
            } else {
                // a background notification is triggered when some read receipts have been received
                final boolean isBackgroundNotif = (null == eventToNotify);

                if (isBackgroundNotif) {
                    // TODO add multi sessions
                    IMXStore store = Matrix.getInstance(getBaseContext()).getDefaultSession().getDataHandler().getStore();

                    if (null == store) {
                        Log.e(LOG_TAG, "## refreshMessagesNotification() : null store");
                        return;
                    }

                    long ts = 0;

                    List<String> roomIds = new ArrayList<>(mNotifiedEventsByRoomId.keySet());

                    // search the oldest message to refresh the notification
                    for (String roomId : roomIds) {
                        List<NotificationUtils.NotifiedEvent> events = mNotifiedEventsByRoomId.get(roomId);
                        NotificationUtils.NotifiedEvent notifiedEvent = events.get(events.size() - 1);

                        Event event = store.getEvent(notifiedEvent.mEventId, notifiedEvent.mRoomId);

                        // detect if the event still exists
                        if (null == event) {
                            Log.e(LOG_TAG, "## refreshMessagesNotification() : the event " + notifiedEvent.mEventId + " in room " + notifiedEvent.mRoomId + " does not exist anymore");
                            mNotifiedEventsByRoomId.remove(roomId);
                        } else if (event.getOriginServerTs() > ts) {
                            eventToNotify = notifiedEvent;
                            ts = event.getOriginServerTs();
                        }
                    }
                }

                final NotificationUtils.NotifiedEvent fEventToNotify = eventToNotify;
                final Map<String, List<NotificationUtils.NotifiedEvent>> fNotifiedEventsByRoomId = new HashMap<>(mNotifiedEventsByRoomId);

                new Handler(getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        // check if the notification has not been cancelled
                        if (fNotifiedEventsByRoomId.size() > 0) {
                            Notification notif = NotificationUtils.buildMessageNotification(getApplicationContext(),
                                    new HashMap<>(fNotifiedEventsByRoomId),
                                    fEventToNotify,
                                    isBackgroundNotif);

                            // the notification cannot be built
                            if (null != notif) {
                                nm.notify(NOTIF_ID_MESSAGE, notif);
                            } else {
                                nm.cancel(NOTIF_ID_MESSAGE);
                            }
                        } else {
                            Log.e(LOG_TAG, "## refreshMessagesNotification() : mNotifiedEventsByRoomId is empty");
                            nm.cancel(NOTIF_ID_MESSAGE);
                        }
                    }
                });
            }
        }
    }

    /**
     * Check if the current displayed notification must be cleared
     * because it doesn't make sense anymore.
     */
    private NotificationUtils.NotifiedEvent getEventToNotify() {
        if (mPendingNotifications.size() > 0) {
            // TODO add multi sessions
            MXSession session = Matrix.getInstance(getBaseContext()).getDefaultSession();
            IMXStore store = session.getDataHandler().getStore();

            // notified only the latest unread message
            List<NotificationUtils.NotifiedEvent> eventsToNotify = new ArrayList<>(mPendingNotifications.values());

            Collections.reverse(eventsToNotify);

            for (NotificationUtils.NotifiedEvent eventToNotify : eventsToNotify) {
                Room room = store.getRoom(eventToNotify.mRoomId);

                // test if the message has not been read
                if ((null != room) && !room.isEventRead(eventToNotify.mEventId)) {
                    String body = null;
                    Event event = store.getEvent(eventToNotify.mEventId, eventToNotify.mRoomId);

                    if (null != event) {
                        // test if the message is displayable
                        EventDisplay eventDisplay = new EventDisplay(getApplicationContext(), event, room.getLiveState());
                        eventDisplay.setPrependMessagesWithAuthor(false);
                        body = eventDisplay.getTextualDisplay().toString();
                    }

                    if (!TextUtils.isEmpty(body)) {
                        mPendingNotifications.clear();
                        mNotifiedEventsByRoomId = null;
                        return eventToNotify;
                    }
                }
            }

            // clear the list
            mPendingNotifications.clear();
        }
        return null;
    }

    /**
     * Refresh the notified messages list.
     *
     * @return true if there is an update
     */
    private boolean refreshNotifiedMessagesList() {
        // TODO add multi sessions
        MXSession session = Matrix.getInstance(getBaseContext()).getDefaultSession();

        if (session == null) {
            return false;
        }

        // not yet loaded
        if (!session.getDataHandler().getBingRulesManager().isReady()) {
            return false;
        }

        IMXStore store = session.getDataHandler().getStore();

        if (null == store) {
            return false;
        }

        if (!store.areReceiptsReady()) {
            return false;
        }

        // initialise the map it was not yet done (after restarting the application for example)
        if (null == mNotifiedEventsByRoomId) {
            mNotifiedEventsByRoomId = new HashMap<>();
            Collection<Room> rooms = store.getRooms();

            for (Room room : rooms) {
                // invitation : add the dedicated event
                if (room.isInvited()) {
                    Collection<Event> events = store.getRoomMessages(room.getRoomId());

                    if (null != events) {
                        for (Event event : events) {
                            if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.getType())) {
                                try {
                                    if ("invite".equals(event.getContentAsJsonObject().getAsJsonPrimitive("membership").getAsString())) {
                                        BingRule rule = session.fulfillRule(event);

                                        if ((null != rule) && rule.isEnabled && rule.shouldNotify()) {
                                            List<NotificationUtils.NotifiedEvent> list = new ArrayList<>();
                                            list.add(new NotificationUtils.NotifiedEvent(event.roomId, event.eventId, rule));
                                            mNotifiedEventsByRoomId.put(room.getRoomId(), list);
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "##refreshNotifiedMessagesList() : invitation parsing failed");
                                }
                            }
                        }
                    }
                } else {
                    try {
                        List<Event> unreadEvents = store.unreadEvents(room.getRoomId(), null);

                        if ((null != unreadEvents) && unreadEvents.size() > 0) {
                            List<NotificationUtils.NotifiedEvent> list = new ArrayList<>();

                            for (Event event : unreadEvents) {
                                BingRule rule = session.fulfillRule(event);

                                if ((null != rule) && rule.isEnabled && rule.shouldNotify()) {
                                    list.add(new NotificationUtils.NotifiedEvent(event.roomId, event.eventId, rule));
                                    //Log.d(LOG_TAG, "## refreshNotifiedMessagesList() : the event " + event.eventId + " in room " + event.roomId + " fulfills " + rule);
                                }
                            }

                            if (list.size() > 0) {
                                mNotifiedEventsByRoomId.put(room.getRoomId(), list);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "##refreshNotifiedMessagesList(): failed checking the unread " + e.getMessage());
                    }
                }
            }

            return true;
        } else { // test if there is an update (if some messages have been read for example)
            boolean isUpdated = false;

            try {
                List<String> roomIds = new ArrayList<>(mNotifiedEventsByRoomId.keySet());

                for (String roomId : roomIds) {
                    Room room = store.getRoom(roomId);

                    // the room does not exist anymore
                    if (null == room) {
                        Log.d(LOG_TAG, "## refreshNotifiedMessagesList() : the room " + roomId + " does not exist anymore");
                        mNotifiedEventsByRoomId.remove(roomId);
                        isUpdated = true;
                    } else {
                        // the messages are sorted from the oldest to the latest
                        List<NotificationUtils.NotifiedEvent> events = mNotifiedEventsByRoomId.get(roomId);

                        // if the oldest event has been read
                        // something has been updated
                        if (room.isEventRead(events.get(0).mEventId)) {
                            // if the latest message has been read
                            // we have to find out the unread messages
                            if (!room.isEventRead(events.get(events.size() - 1).mEventId)) {
                                // search for the read messages
                                for (int i = 0; i < events.size(); ) {
                                    NotificationUtils.NotifiedEvent event = events.get(i);

                                    if (room.isEventRead(event.mEventId)) {
                                       // Log.d(LOG_TAG, "## refreshNotifiedMessagesList() : the event " + event.mEventId + " in room " + room.getRoomId() + " is read");

                                        events.remove(i);
                                        isUpdated = true;
                                    } else {
                                        i++;
                                    }
                                }
                            } else {
                                events.clear();
                            }

                            // all the messages have been read
                            if (0 == events.size()) {
                                //Log.d(LOG_TAG, "## refreshNotifiedMessagesList() : no more unread messages in " + roomId);
                                mNotifiedEventsByRoomId.remove(roomId);
                                isUpdated = true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "##refreshNotifiedMessagesList(): failed while building mNotifiedEventsByRoomId " + e.getMessage());
            }

            return isUpdated;
        }
    }

    //================================================================================
    // Call notification management
    //================================================================================

    /**
     * Display a permanent notification when there is an incoming call.
     *
     * @param session  the session
     * @param room     the room
     * @param event    the event
     * @param callId   the callId
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
                    NotificationUtils.getRoomName(getApplicationContext(), session, room, event),
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
     *
     * @param session the session
     * @param callId  the callId
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
