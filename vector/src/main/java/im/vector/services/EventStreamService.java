/*
 * Copyright 2015 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXStoreListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import im.vector.BuildConfig;
import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.notifications.NotifiableEvent;
import im.vector.notifications.NotifiableEventResolver;
import im.vector.notifications.NotificationUtils;
import im.vector.notifications.OutdatedEventDetector;
import im.vector.push.PushManager;
import im.vector.tools.VectorUncaughtExceptionHandler;
import im.vector.util.CallsManager;
import im.vector.util.SystemUtilsKt;

/**
 * A foreground service in charge of controlling whether the event stream is running or not.
 * <p>
 * It manages messages notifications displayed to the end user. It can also display foreground
 * notifications in some situations to let the app run in background.
 *
 * @Deprecated Use {@link EventStreamServiceX}
 */
@SuppressLint("Registered")
public class EventStreamService extends Service {
    private static final String LOG_TAG = EventStreamService.class.getSimpleName();

    /**
     * static instance
     */
    @Nullable
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
        PUSH_STATUS_UPDATE,
        AUTO_RESTART
    }

    /**
     * Parameters to the service
     */
    public static final String EXTRA_STREAM_ACTION = "EventStreamService.EXTRA_STREAM_ACTION";
    public static final String EXTRA_MATRIX_IDS = "EventStreamService.EXTRA_MATRIX_IDS";
    public static final String EXTRA_AUTO_RESTART_ACTION = "EventStreamService.EXTRA_AUTO_RESTART_ACTION";

    /**
     * States of the foreground service notification.
     */
    private enum ForegroundNotificationState {
        PRESTART,
        // the foreground notification is not displayed
        NONE,
        // initial sync in progress or the app is resuming
        // once started, we want the application completes its first sync even if it is background meanwhile
        INITIAL_SYNCING,
        // fdroid mode or FCM registration failed
        // put this service in foreground to keep the app in life
        LISTENING_FOR_EVENTS,
        // there is a pending incoming call
        // continue to sync in background to keep the call signaling up
        INCOMING_CALL,
        // a call is in progress
        // same requirement as INCOMING_CALL
        CALL_IN_PROGRESS,
    }

    /**
     * The current state of the foreground service notification (`NOTIFICATION_ID_FOREGROUND_SERVICE`).
     */
    private static ForegroundNotificationState mForegroundNotificationState = ForegroundNotificationState.PRESTART;

    /**
     * Managed sessions
     */
    private List<MXSession> mSessions;

    /**
     * Session identifiers
     */
    private List<String> mMatrixIds;

    /**
     * The current state.
     */
    private StreamAction mServiceState = StreamAction.IDLE;

    /**
     * call in progress (foreground notification)
     */
    private String mCallIdInProgress = null;

    /**
     * incoming (foreground notification)
     */
    private String mIncomingCallId = null;

    /**
     * Push manager
     */
    private PushManager mPushManager;

    /**
     * Tell if the service must be suspended after started.
     * It is used when the service is automatically restarted by Android.
     */
    private boolean mSuspendWhenStarted = false;

    /**
     * Tells if the service self destroyed.
     * Use to restart the service is killed by the OS.
     */
    private boolean mIsSelfDestroyed = false;

    private NotifiableEventResolver mNotifiableEventResolver;

    /**
     * @return the event stream instance
     */
    @Nullable
    public static EventStreamService getInstance() {
        return mActiveEventStreamService;
    }

    /**
     * Live events listener
     */
    private final MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onBingEvent(Event event, RoomState roomState, BingRule bingRule) {
            if (BuildConfig.LOW_PRIVACY_LOG_ENABLE) {
                Log.d(LOG_TAG, "%%%%%%%%  MXEventListener: the event " + event);
            }
            // privacy
            //Log.d(LOG_TAG, "onBingEvent : the event " + event);
            //Log.d(LOG_TAG, "onBingEvent : the bingRule " + bingRule);

            Log.d(LOG_TAG, "prepareNotification : " + event.eventId + " in " + roomState.roomId);
            MXSession session = Matrix.getMXSession(getApplicationContext(), event.getMatrixId());

            // invalid session ?
            // should never happen.
            // But it could be triggered because of multi accounts management.
            // The dedicated account is removing but some pushes are still received.
            if ((null == session) || !session.isAlive()) {
                Log.i(LOG_TAG, "prepareNotification : don't bing - no session");
                return;
            }

            if (event.isCallEvent()) {
                prepareCallNotification(event, bingRule);
                return;
            }


            NotifiableEvent notifiableEvent = mNotifiableEventResolver.resolveEvent(event, roomState, bingRule, session);
            if (notifiableEvent != null) {
                VectorApp.getInstance().getNotificationDrawerManager().onNotifiableEventReceived(notifiableEvent);
            }
        }

        @Override
        public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
            Log.d(LOG_TAG, "%%%%%%%%  MXEventListener: onLiveEventsChunkProcessed[" + fromToken + "->" + toToken + "]");

            VectorApp.getInstance().getNotificationDrawerManager().refreshNotificationDrawer(new OutdatedEventDetector(EventStreamService.this));

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
                    Log.i(LOG_TAG, "onLiveEventsChunkProcessed : Catchup again because there are active calls");
                    catchup(false);
                } else if (StreamAction.CATCHUP == mServiceState) {
                    Log.i(LOG_TAG, "onLiveEventsChunkProcessed : no Active call");
                    CallsManager.getSharedInstance().checkDeadCalls();
                    setServiceState(StreamAction.PAUSE);
                }
            }

            // dismiss the initial sync notification
            // it seems there are some race conditions with onInitialSyncComplete
            if (mForegroundNotificationState == ForegroundNotificationState.INITIAL_SYNCING) {
                Log.d(LOG_TAG, "onLiveEventsChunkProcessed : end of init sync");
                refreshForegroundNotification();
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
                final MXSession session = Matrix.getInstance(getApplicationContext()).getSession(matrixId);

                mSessions.add(session);
                mMatrixIds.add(matrixId);
                monitorSession(session);
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
                    CallsManager.getSharedInstance().removeSession(session);
                    mSessions.remove(session);
                    mMatrixIds.remove(matrixId);
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (mForegroundNotificationState == ForegroundNotificationState.PRESTART) {
            //The service has been started in foreground, we must display a notif ASAP
            Notification notification = NotificationUtils.INSTANCE.buildForegroundServiceNotification(this, R.string.notification_sync_init, false);
            startForeground(NotificationUtils.NOTIFICATION_ID_FOREGROUND_SERVICE, notification);
            //And switch now to NONE
            mForegroundNotificationState = ForegroundNotificationState.NONE;
        }
        // no intent : restarted by Android
        // EXTRA_AUTO_RESTART_ACTION : restarted by the service itself (
        if (null == intent || intent.hasExtra(EXTRA_AUTO_RESTART_ACTION)) {
            boolean restart = false;

            if (StreamAction.AUTO_RESTART == mServiceState) {
                Log.e(LOG_TAG, "onStartCommand : auto restart in progress ignore current command");
                return START_STICKY;
            } else if (null == intent) {
                Log.e(LOG_TAG, "onStartCommand : null intent -> restart the service");
                restart = true;
            } else if (StreamAction.IDLE == mServiceState) {
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
                    //Fix: Rageshake Context.startForegroundService() did not then call Service.startForeground()
                    stopForeground(true);
                    return START_NOT_STICKY;
                }

                if (VectorUncaughtExceptionHandler.INSTANCE.didAppCrash(this)) {
                    Log.e(LOG_TAG, "onStartCommand : no auto restart because the application crashed");
                    //Fix: Rageshake Context.startForegroundService() did not then call Service.startForeground()
                    stopForeground(true);
                    return START_NOT_STICKY;
                }

                PushManager pushManager = Matrix.getInstance(getApplicationContext()).getPushManager();
                if (!pushManager.canStartAppInBackground()) {
                    Log.e(LOG_TAG, "onStartCommand : no auto restart because the user disabled the background sync");
                    //Fix: Rageshake Context.startForegroundService() did not then call Service.startForeground()
                    stopForeground(true);
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
                    //Fix rage shake 4081
                    stopForeground(true);
                    return START_NOT_STICKY;
                }

                start();
                break;
            }
            case STOP:
                Log.i(LOG_TAG, "## onStartCommand(): service stopped");
                mIsSelfDestroyed = true;
                stopSelf();
                break;
            case PAUSE:
                pause();
                break;
            case CATCHUP:
                catchup(true);
                break;
            case PUSH_STATUS_UPDATE:
                pushStatusUpdate();
            default:
                break;
        }

        return START_STICKY;
    }

    /**
     * Restart the service
     */
    private void autoRestart() {
        int delay = 3000 + (new Random()).nextInt(5000);

        Log.d(LOG_TAG, "## autoRestart() : restarts after " + delay + " ms");

        // reset the service identifier
        mForegroundNotificationState = ForegroundNotificationState.PRESTART;

        // restart the services after 3 seconds
        Intent restartServiceIntent = new Intent(getApplicationContext(), getClass());
        restartServiceIntent.setPackage(getPackageName());
        restartServiceIntent.putExtra(EXTRA_AUTO_RESTART_ACTION, EXTRA_AUTO_RESTART_ACTION);
        PendingIntent restartPendingIntent = PendingIntent.getService(
                getApplicationContext(),
                1,
                restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT);

        AlarmManager myAlarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        myAlarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                // use a random part to avoid matching to system auto restart value
                SystemClock.elapsedRealtime() + delay,
                restartPendingIntent);
    }

    /**
     * onTaskRemoved is called when the user swipes the application from the active applications.
     * On some devices, the service is not automatically restarted.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(LOG_TAG, "## onTaskRemoved");

        autoRestart();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (!mIsSelfDestroyed) {
            setServiceState(StreamAction.STOP);

            // stop the foreground service on devices which respects battery optimizations
            // during the initial syncing
            // and if the FCM registration was done
            if (!SystemUtilsKt.isIgnoringBatteryOptimizations(getApplicationContext())
                    && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    && (mForegroundNotificationState == ForegroundNotificationState.INITIAL_SYNCING)
                    && Matrix.getInstance(getApplicationContext()).getPushManager().hasRegistrationToken()) {
                setForegroundNotificationState(ForegroundNotificationState.NONE, null);
            }

            Log.i(LOG_TAG, "## onDestroy() : restart it");
            autoRestart();
        } else {
            Log.i(LOG_TAG, "## onDestroy() : do nothing");
            stop();
            super.onDestroy();
        }

        mIsSelfDestroyed = false;
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
        // resume if it was only suspended
        if (null != session.getCurrentSyncToken()) {
            session.resumeEventStream();
        } else {
            session.startEventStream(store.getEventStreamToken());
        }
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
     * Tells if the service stopped.
     *
     * @return true if the service is stopped.
     */
    public static boolean isStopped() {
        return getInstance() == null
                || getInstance().mServiceState == StreamAction.STOP;
    }

    /**
     * Monitor the provided session.
     *
     * @param session the session
     */
    private void monitorSession(final MXSession session) {
        session.getDataHandler().addListener(mEventsListener);
        CallsManager.getSharedInstance().addSession(session);

        session.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                session.getDataHandler().getStore().post(() -> (new Handler(getMainLooper())).post(() -> refreshForegroundNotification()));
            }
        });

        final IMXStore store = session.getDataHandler().getStore();

        // the store is ready (no data loading in progress...)
        if (store.isReady()) {
            startEventStream(session, store);
            if (mSuspendWhenStarted) {
                if (null != mPushManager) {
                    session.setSyncDelay(mPushManager.getBackgroundSyncDelay());
                    session.setSyncTimeout(mPushManager.getBackgroundSyncTimeOut());
                }

                catchup(false);
            }
        } else {

            // wait that the store is ready  before starting the events listener
            store.addMXStoreListener(new MXStoreListener() {
                @Override
                public void onStoreReady(String accountId) {
                    startEventStream(session, store);

                    if (mSuspendWhenStarted) {
                        if (null != mPushManager) {
                            session.setSyncDelay(mPushManager.getBackgroundSyncDelay());
                            session.setSyncTimeout(mPushManager.getBackgroundSyncTimeOut());
                        }

                        catchup(false);
                    }
                }

                @Override
                public void onStoreCorrupted(String accountId, String description) {
                    // start a new initial sync
                    if (null == store.getEventStreamToken()) {
                        startEventStream(session, store);
                    } else {
                        // the data are out of sync
                        Matrix.getInstance(getApplicationContext()).reloadSessions(getApplicationContext(), true);
                    }
                }

                @Override
                public void onStoreOOM(final String accountId, final String description) {
                    Handler uiHandler = new Handler(getMainLooper());

                    uiHandler.post(() -> {
                        Toast.makeText(getApplicationContext(), accountId + " : " + description, Toast.LENGTH_LONG).show();
                        Matrix.getInstance(getApplicationContext()).reloadSessions(getApplicationContext(), true);
                    });
                }
            });
        }
    }

    /**
     * internal start.
     */
    private void start() {
        Context applicationContext = getApplicationContext();
        mPushManager = Matrix.getInstance(applicationContext).getPushManager();
        mNotifiableEventResolver = new NotifiableEventResolver(applicationContext);
        StreamAction state = getServiceState();

        if (state == StreamAction.START) {
            Log.i(LOG_TAG, "start : Already started.");

            for (MXSession session : mSessions) {
                session.refreshNetworkConnection();
            }
            return;
        } else if ((state == StreamAction.PAUSE) || (state == StreamAction.CATCHUP)) {
            Log.i(LOG_TAG, "start : Resuming active stream.");
            resume();
            return;
        }

        if (mSessions == null) {
            Log.i(LOG_TAG, "start : No valid MXSession.");
            return;
        }

        Log.d(LOG_TAG, "## start : start the service");

        // release previous instance
        if (null != mActiveEventStreamService && this != mActiveEventStreamService) {
            mActiveEventStreamService.stop();
        }

        mActiveEventStreamService = this;

        for (final MXSession session : mSessions) {
            // session == null has been reported by GA
            if ((null == session)) {
                Log.i(LOG_TAG, "start : the session is not anymore valid.");
                return;
            }
            monitorSession(session);
        }

        refreshForegroundNotification();

        setServiceState(StreamAction.START);
    }

    /**
     * Stop the service without delay
     */
    public void stopNow() {
        stop();
        mIsSelfDestroyed = true;
        stopSelf();
    }

    /**
     * internal stop.
     */
    private void stop() {
        Log.d(LOG_TAG, "## stop(): the service is stopped");

        // stop the foreground service, if any
        setForegroundNotificationState(ForegroundNotificationState.NONE, null);

        if (mSessions != null) {
            for (MXSession session : mSessions) {
                if (null != session && session.isAlive()) {
                    session.stopEventStream();
                    session.getDataHandler().removeListener(mEventsListener);
                    CallsManager.getSharedInstance().removeSession(session);
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
            Log.i(LOG_TAG, "catchup  without checking state ");
        } else {
            Log.i(LOG_TAG, "catchup with state " + state + " CurrentActivity " + VectorApp.getCurrentActivity());

            // the catchup should only be done
            // 1- the state is in catchup : the event stream might have gone to sleep between two catchups
            // 2- the thread is suspended
            // 3- the application has been launched by a push so there is no displayed activity
            canCatchup = (state == StreamAction.CATCHUP)
                    || (state == StreamAction.PAUSE)
                    || ((StreamAction.START == state) && (null == VectorApp.getCurrentActivity()));
        }

        if (canCatchup) {
            if (mSessions != null) {
                for (MXSession session : mSessions) {
                    session.catchupEventStream();
                }
            } else {
                Log.i(LOG_TAG, "catchup no session");
            }

            setServiceState(StreamAction.CATCHUP);
        } else {
            Log.i(LOG_TAG, "No catchup is triggered because there is already a running event thread");
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

    //================================================================================
    // notification management
    //================================================================================

    /**
     * The push status has been updated (i.e disabled or enabled).
     */
    private void pushStatusUpdate() {
        Log.d(LOG_TAG, "## pushStatusUpdate");

        if (ForegroundNotificationState.NONE != mForegroundNotificationState) {
            Log.d(LOG_TAG, "## pushStatusUpdate : push status succeeds. So, stop foreground service (" + mForegroundNotificationState + ")");

            if (ForegroundNotificationState.LISTENING_FOR_EVENTS == mForegroundNotificationState) {
                setForegroundNotificationState(ForegroundNotificationState.NONE, null);
            }
        }

        refreshForegroundNotification();
    }

    /**
     * @return true if the "listen for events" notification should be displayed
     */
    private boolean shouldDisplayListenForEventsNotification() {
        // fdroid
        return (!mPushManager.useFcm()
                // the FCM registration was not done
                || TextUtils.isEmpty(mPushManager.getCurrentRegistrationToken())
                && !mPushManager.isServerRegistered())
                && mPushManager.isBackgroundSyncAllowed()
                && mPushManager.areDeviceNotificationsAllowed();
    }

    /**
     * Manages the sticky foreground notification.
     * It displays the background state of the app ("Listen for events", "synchronising", ...)
     */
    public void refreshForegroundNotification() {
        Log.i(LOG_TAG, "## refreshForegroundNotification from state " + mForegroundNotificationState);

        MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();

        if (null == session) {
            Log.i(LOG_TAG, "## updateServiceForegroundState(): no session");
            return;
        }

        // call in progress notifications
        if ((mForegroundNotificationState == ForegroundNotificationState.INCOMING_CALL)
                || (mForegroundNotificationState == ForegroundNotificationState.CALL_IN_PROGRESS)) {
            Log.i(LOG_TAG, "## refreshForegroundNotification : does nothing as there is a pending call");
            return;
        }

        // GA issue
        if (null == mPushManager) {
            Log.i(LOG_TAG, "## refreshForegroundNotification : pushManager is null");
            return;
        }

        boolean isInitialSyncInProgress = !session.getDataHandler().isInitialSyncComplete() || isStopped() || (mServiceState == StreamAction.CATCHUP);

        if (isInitialSyncInProgress) {
            Log.i(LOG_TAG,
                    "## refreshForegroundNotification : put the service in foreground because of an initial sync " + mForegroundNotificationState);
            setForegroundNotificationState(ForegroundNotificationState.INITIAL_SYNCING, null);
        } else if (shouldDisplayListenForEventsNotification()) {
            Log.i(LOG_TAG, "## refreshForegroundNotification : put the service in foreground because of FCM registration");
            setForegroundNotificationState(ForegroundNotificationState.LISTENING_FOR_EVENTS, null);
        } else {
            Log.i(LOG_TAG, "## refreshForegroundNotification : put the service in background from state " + mForegroundNotificationState);
            setForegroundNotificationState(ForegroundNotificationState.NONE, null);
        }
    }

    /**
     * Set the new foreground notification state.
     * And display the foreground service notification (`NOTIFICATION_ID_FOREGROUND_SERVICE`) if required.
     *
     * @param foregroundNotificationState the new state
     * @param notification                an already built notification. Required for `INCOMING_CALL` and `CALL_IN_PROGRESS`
     */
    private void setForegroundNotificationState(ForegroundNotificationState foregroundNotificationState, Notification notification) {
        if (foregroundNotificationState == mForegroundNotificationState
                && foregroundNotificationState != ForegroundNotificationState.NONE) {
            return;
        }

        mForegroundNotificationState = foregroundNotificationState;
        switch (mForegroundNotificationState) {
            case NONE:
                // The foreground/ sticky notification can be removed
                NotificationUtils.INSTANCE.cancelNotificationForegroundService(this);

                if (getInstance() != null) {
                    getInstance().stopForeground(true);
                }
                break;
            case INITIAL_SYNCING:
                notification = NotificationUtils.INSTANCE.buildForegroundServiceNotification(this, R.string.notification_sync_in_progress, false);
                break;
            case LISTENING_FOR_EVENTS:
                notification = NotificationUtils.INSTANCE.buildForegroundServiceNotification(this, R.string.notification_listening_for_events, false);
                break;
            case INCOMING_CALL:
            case CALL_IN_PROGRESS:
                // A prebuilt notification must be passed for changing to these states
                if (notification == null) {
                    throw new IllegalArgumentException("A notification object must be passed for state " + foregroundNotificationState);
                }
                break;
        }

        if (notification != null) {
            // display the stick foreground notification
            if (getInstance() != null) {
                getInstance().startForeground(NotificationUtils.NOTIFICATION_ID_FOREGROUND_SERVICE, notification);
            }
        }
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
            Log.i(LOG_TAG, "prepareCallNotification : don't bing - the room does not exist");
            return;
        }

        String callId = null;
        boolean isVideo = false;

        try {
            callId = event.getContentAsJsonObject().get("call_id").getAsString();

            // Check if it is a video call
            JsonObject offer = event.getContentAsJsonObject().get("offer").getAsJsonObject();
            JsonElement sdp = offer.get("sdp");
            String sdpValue = sdp.getAsString();

            isVideo = sdpValue.contains("m=video");
        } catch (Exception e) {
            Log.e(LOG_TAG, "prepareNotification : getContentAsJsonObject " + e.getMessage(), e);
        }

        if (!TextUtils.isEmpty(callId)) {
            displayIncomingCallNotification(session, isVideo, room, event, callId, bingRule);
        }
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
            VectorApp.getInstance().getNotificationDrawerManager().clearMessageEventOfRoom(roomId);
        }
    }

    /**
     * Remove any pending notification.
     * It should be called when the application is logged out.
     */
    public static void removeNotification() {
        if (null != mActiveEventStreamService) {
            VectorApp.getInstance().getNotificationDrawerManager().clearAllEvents();
        }
    }

    //================================================================================
    // Call notification management
    //================================================================================

    /**
     * Display a permanent notification when there is an incoming call.
     *
     * @param session  the session
     * @param isVideo  true if this is a video call, false for voice call
     * @param room     the room
     * @param event    the event
     * @param callId   the callId
     * @param bingRule the bing rule.
     */
    public void displayIncomingCallNotification(MXSession session, boolean isVideo, Room room, Event event, String callId, BingRule bingRule) {
        Log.d(LOG_TAG, "displayIncomingCallNotification : " + callId + " in " + room.getRoomId());

        // the incoming call in progress is already displayed
        if (!TextUtils.isEmpty(mIncomingCallId)) {
            Log.d(LOG_TAG, "displayIncomingCallNotification : the incoming call in progress is already displayed");
        } else if (!TextUtils.isEmpty(mCallIdInProgress)) {
            Log.d(LOG_TAG, "displayIncomingCallNotification : a 'call in progress' notification is displayed");
        }
        // test if there is no active call
        else if (null == CallsManager.getSharedInstance().getActiveCall()) {
            Log.d(LOG_TAG, "displayIncomingCallNotification : display the dedicated notification");
            Notification notification = NotificationUtils.INSTANCE.buildIncomingCallNotification(
                    EventStreamService.this,
                    isVideo,
                    room.getRoomDisplayName(this),
                    session.getMyUserId(),
                    callId);
            setForegroundNotificationState(ForegroundNotificationState.INCOMING_CALL, notification);

            mIncomingCallId = callId;

            // turn the screen on for 3 seconds
            if (Matrix.getInstance(VectorApp.getInstance()).getPushManager().isScreenTurnedOn()) {
                try {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    PowerManager.WakeLock wl = pm.newWakeLock(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            EventStreamService.class.getSimpleName());
                    wl.acquire(3000);
                    wl.release();
                } catch (RuntimeException re) {
                    Log.i(LOG_TAG, "displayIncomingCallNotification : failed to turn screen on ", re);
                }
            }
        } else {
            Log.i(LOG_TAG, "displayIncomingCallNotification : do not display the incoming call notification because there is a pending call");
        }
    }

    /**
     * Display a call in progress notification.
     *
     * @param session the session
     * @param isVideo true if this is a video call, false for voice call
     * @param room    the room
     * @param callId  the callId
     */
    public void displayCallInProgressNotification(MXSession session, boolean isVideo, Room room, String callId) {
        if (null != callId) {
            Notification notification = NotificationUtils.INSTANCE.buildPendingCallNotification(getApplicationContext(),
                    isVideo,
                    room.getRoomDisplayName(this),
                    room.getRoomId(),
                    session.getCredentials().userId,
                    callId);
            setForegroundNotificationState(ForegroundNotificationState.CALL_IN_PROGRESS, notification);
            mCallIdInProgress = callId;
        }
    }

    /**
     * Hide the permanent call notifications
     */
    public void hideCallNotifications() {
        // hide the foreground notification for calls only if we are in these states
        if ((ForegroundNotificationState.CALL_IN_PROGRESS == mForegroundNotificationState)
                || (ForegroundNotificationState.INCOMING_CALL == mForegroundNotificationState)) {
            if (ForegroundNotificationState.CALL_IN_PROGRESS == mForegroundNotificationState) {
                mCallIdInProgress = null;
            } else {
                mIncomingCallId = null;
            }

            setForegroundNotificationState(ForegroundNotificationState.NONE, null);
            refreshForegroundNotification();
        }
    }
}
