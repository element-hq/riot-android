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

import android.app.AlarmManager;
import android.app.Notification;
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
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.ViewedRoomTracker;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.notifications.NotificationUtils;
import im.vector.notifications.NotifiedEvent;
import im.vector.notifications.RoomsNotifications;
import im.vector.receiver.DismissNotificationReceiver;
import im.vector.util.CallsManager;
import im.vector.util.PreferencesManager;
import im.vector.util.RiotEventDisplay;

/**
 * A foreground service in charge of controlling whether the event stream is running or not.
 * <p>
 * It manages messages notifications displayed to the end user. It can also display foreground
 * notifications in some situations to let the app run in background.
 */
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
        GCM_STATUS_UPDATE,
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
    public enum ForegroundNotificationState {
        // the foreground notification is not displayed
        NONE,
        // initial sync in progress or the app is resuming
        // once started, we want the application completes its first sync even if it is background meanwhile
        INITIAL_SYNCING,
        // fdroid mode or GCM registration failed
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
    private static ForegroundNotificationState mForegroundNotificationState = ForegroundNotificationState.NONE;

    /**
     * Default bing rule
     */
    private static final BingRule mDefaultBingRule = new BingRule("ruleKind", "aPattern", true, true, false);

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
     * store the notifications description
     */
    private final LinkedHashMap<String, NotifiedEvent> mPendingNotifications = new LinkedHashMap<>();
    private Map<String, List<NotifiedEvent>> mNotifiedEventsByRoomId = null;
    private static HandlerThread mNotificationHandlerThread = null;
    private static android.os.Handler mNotificationsHandler = null;

    // get the text to display when the background sync is disabled
    private static final List<CharSequence> mBackgroundNotificationStrings = new ArrayList<>();
    private static final Set<String> mBackgroundNotificationEventIds = new HashSet<>();
    private static String mLastBackgroundNotificationRoomId = null;
    private static int mLastBackgroundNotificationUnreadCount = 0;

    /**
     * call in progress (foreground notification)
     */
    private String mCallIdInProgress = null;

    /**
     * incoming (foreground notification)
     */
    private String mIncomingCallId = null;

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
     * Tells if the service self destroyed.
     * Use to restart the service is killed by the OS.
     */
    private boolean mIsSelfDestroyed = false;

    /**
     * @return the event stream instance
     */
    @Nullable
    public static EventStreamService getInstance() {
        return mActiveEventStreamService;
    }

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
            prepareNotification(event, bingRule);
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
                    session.getDataHandler().getBingRulesManager().removeBingRulesUpdateListener(mBingRulesUpdatesListener);
                    CallsManager.getSharedInstance().removeSession(session);
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
                    return START_NOT_STICKY;
                }

                if ((null != VectorApp.getInstance()) && VectorApp.getInstance().didAppCrash()) {
                    Log.e(LOG_TAG, "onStartCommand : no auto restart because the application crashed");
                    return START_NOT_STICKY;
                }

                GcmRegistrationManager gcmManager = Matrix.getInstance(getApplicationContext()).getSharedGCMRegistrationManager();
                if (!gcmManager.canStartAppInBackground()) {
                    Log.e(LOG_TAG, "onStartCommand : no auto restart because the user disabled the background sync");
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
                mIsSelfDestroyed = true;
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
     * Restart the service
     */
    private void autoRestart() {
        int delay = 3000 + (new Random()).nextInt(5000);

        Log.d(LOG_TAG, "## autoRestart() : restarts after " + delay + " ms");

        // reset the service identifier
        mForegroundNotificationState = ForegroundNotificationState.NONE;

        // restart the services after 3 seconds
        Intent restartServiceIntent = new Intent(getApplicationContext(), getClass());
        restartServiceIntent.setPackage(getPackageName());
        restartServiceIntent.putExtra(EXTRA_AUTO_RESTART_ACTION, EXTRA_AUTO_RESTART_ACTION);
        PendingIntent restartPendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);

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
            // and if the GCM registration was done
            if (!PreferencesManager.isIgnoringBatteryOptimizations(getApplicationContext())
                    && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    && (mForegroundNotificationState == ForegroundNotificationState.INITIAL_SYNCING)
                    && Matrix.getInstance(getApplicationContext()).getSharedGCMRegistrationManager().hasRegistrationToken()) {
                setForegroundNotificationState(ForegroundNotificationState.NONE, null);
            }

            Log.d(LOG_TAG, "## onDestroy() : restart it");
            autoRestart();
        } else {
            Log.d(LOG_TAG, "## onDestroy() : do nothing");
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
        session.getDataHandler().getBingRulesManager().addBingRulesUpdateListener(mBingRulesUpdatesListener);
        CallsManager.getSharedInstance().addSession(session);

        session.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                session.getDataHandler().getStore().post(new Runnable() {
                    @Override
                    public void run() {
                        (new Handler(getMainLooper())).post(new Runnable() {
                            @Override
                            public void run() {
                                refreshForegroundNotification();
                            }
                        });
                    }
                });

            }
        });

        final IMXStore store = session.getDataHandler().getStore();

        // the store is ready (no data loading in progress...)
        if (store.isReady()) {
            startEventStream(session, store);
            if (mSuspendWhenStarted) {
                if (null != mGcmRegistrationManager) {
                    session.setSyncDelay(mGcmRegistrationManager.getBackgroundSyncDelay());
                    session.setSyncTimeout(mGcmRegistrationManager.getBackgroundSyncTimeOut());
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
                        if (null != mGcmRegistrationManager) {
                            session.setSyncDelay(mGcmRegistrationManager.getBackgroundSyncDelay());
                            session.setSyncTimeout(mGcmRegistrationManager.getBackgroundSyncTimeOut());
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

    /**
     * internal start.
     */
    private void start() {
        mGcmRegistrationManager = Matrix.getInstance(getApplicationContext()).getSharedGCMRegistrationManager();
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

        // release previous instance
        if (null != mActiveEventStreamService && this != mActiveEventStreamService) {
            mActiveEventStreamService.stop();
        }

        mActiveEventStreamService = this;

        for (final MXSession session : mSessions) {
            // session == null has been reported by GA
            if ((null == session) || (null == session.getDataHandler()) || (null == session.getDataHandler().getStore())) {
                Log.e(LOG_TAG, "start : the session is not anymore valid.");
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
                    session.getDataHandler().getBingRulesManager().removeBingRulesUpdateListener(mBingRulesUpdatesListener);
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

    //================================================================================
    // notification management
    //================================================================================

    /**
     * The GCM status has been updated (i.e disabled or enabled).
     */
    private void gcmStatusUpdate() {
        Log.d(LOG_TAG, "## gcmStatusUpdate");

        if (ForegroundNotificationState.NONE != mForegroundNotificationState) {
            Log.d(LOG_TAG, "## gcmStatusUpdate : gcm status succeeds. So, stop foreground service (" + mForegroundNotificationState + ")");

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
        return (!mGcmRegistrationManager.useGCM() ||
                // the GCM registration was not done
                TextUtils.isEmpty(mGcmRegistrationManager.getCurrentRegistrationToken())
                        && !mGcmRegistrationManager.isServerRegistered())
                && mGcmRegistrationManager.isBackgroundSyncAllowed()
                && mGcmRegistrationManager.areDeviceNotificationsAllowed();
    }

    /**
     * Manages the sticky foreground notification.
     * It displays the background state of the app ("Listen for events", "synchronising", ...)
     */
    public void refreshForegroundNotification() {
        Log.d(LOG_TAG, "## refreshForegroundNotification from state " + mForegroundNotificationState);

        MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();

        if (null == session) {
            Log.e(LOG_TAG, "## updateServiceForegroundState(): no session");
            return;
        }

        // call in progress notifications
        if ((mForegroundNotificationState == ForegroundNotificationState.INCOMING_CALL)
                || (mForegroundNotificationState == ForegroundNotificationState.CALL_IN_PROGRESS)) {
            Log.d(LOG_TAG, "## refreshForegroundNotification : does nothing as there is a pending call");
            return;
        }

        // GA issue
        if (null == mGcmRegistrationManager) {
            return;
        }

        boolean isInitialSyncInProgress = !session.getDataHandler().isInitialSyncComplete() || isStopped() || (mServiceState == StreamAction.CATCHUP);

        if (isInitialSyncInProgress) {
            Log.d(LOG_TAG, "## refreshForegroundNotification : put the service in foreground because of an initial sync " + mForegroundNotificationState);
            setForegroundNotificationState(ForegroundNotificationState.INITIAL_SYNCING, null);
        } else if (shouldDisplayListenForEventsNotification()) {
            Log.d(LOG_TAG, "## refreshForegroundNotification : put the service in foreground because of GCM registration");
            setForegroundNotificationState(ForegroundNotificationState.LISTENING_FOR_EVENTS, null);
        } else {
            Log.d(LOG_TAG, "## refreshForegroundNotification : put the service in background from state " + mForegroundNotificationState);
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
        if (foregroundNotificationState == mForegroundNotificationState) {
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
                notification = NotificationUtils.INSTANCE.buildForegroundServiceNotification(this, R.string.notification_sync_in_progress);
                break;
            case LISTENING_FOR_EVENTS:
                notification = NotificationUtils.INSTANCE.buildForegroundServiceNotification(this, R.string.notification_listen_for_events);
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
            Log.d(LOG_TAG, "prepareCallNotification : don't bing - the room does not exist");
            return;
        }

        String callId = null;

        try {
            callId = event.getContentAsJsonObject().get("call_id").getAsString();
        } catch (Exception e) {
            Log.e(LOG_TAG, "prepareNotification : getContentAsJsonObject " + e.getMessage(), e);
        }

        if (!TextUtils.isEmpty(callId)) {
            displayIncomingCallNotification(session, room, event, callId, bingRule);
        }
    }

    /**
     * Prepare a notification for the expected event.
     *
     * @param event    the event
     * @param bingRule the bing rule
     */
    private void prepareNotification(Event event, BingRule bingRule) {
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

        mPendingNotifications.put(event.eventId, new NotifiedEvent(event.roomId, event.eventId, bingRule, event.getOriginServerTs()));
    }

    /**
     * Cancel the push notifications for a dedicated roomId.
     * If the roomId is null, cancel all the push notification.
     *
     * @param accountId the account id
     */
    public static void onMessagesNotificationDismiss(String accountId) {
        Log.d(LOG_TAG, "onMessagesNotificationDismiss " + accountId);
        if (null != mActiveEventStreamService) {
            mActiveEventStreamService.refreshMessagesNotification();
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
            mActiveEventStreamService.cancelNotifications(roomId);
        }
    }

    /**
     * Provide the notifications handler
     *
     * @return the notifications handler.
     */
    private android.os.Handler getNotificationsHandler() {
        if (null == mNotificationHandlerThread) {
            try {
                mNotificationHandlerThread = new HandlerThread("NotificationsService_" + System.currentTimeMillis(), Thread.MIN_PRIORITY);
                mNotificationHandlerThread.start();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getNotificationsHandler failed : " + e.getMessage(), e);
            }
        }

        if (null == mNotificationsHandler) {
            try {
                mNotificationsHandler = new android.os.Handler(mNotificationHandlerThread.getLooper());
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getNotificationsHandler failed : " + e.getMessage(), e);
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
        NotificationUtils.INSTANCE.cancelAllNotifications(this);

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

                RoomsNotifications.deleteCachedRoomNotifications(VectorApp.getInstance());
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
                    if (mActiveEventStreamService != null) {
                        mActiveEventStreamService.refreshMessagesNotification();
                    }
                }
            });
        }
    }

    /**
     * Cancel notifications for a dedicated room.
     *
     * @param roomId the room Id
     */
    private void cancelNotifications(final String roomId) {
        getNotificationsHandler().post(new Runnable() {
            @Override
            public void run() {
                if ((null != mNotifiedEventsByRoomId) && ((null == roomId) || mNotifiedEventsByRoomId.containsKey(roomId))) {
                    mNotifiedEventsByRoomId = null;
                    refreshMessagesNotification();
                }
            }
        });
    }

    /**
     * Try to trigger a notification when the event stream is not created.
     *
     * @param context             the context
     * @param event               the notified event
     * @param roomName            the room name
     * @param senderDisplayName   the sender display name
     * @param unreadMessagesCount the unread messages count
     */
    public static void onStaticNotifiedEvent(Context context,
                                             @Nullable Event event,
                                             String roomName,
                                             String senderDisplayName,
                                             int unreadMessagesCount) {
        if (null != event && !mBackgroundNotificationEventIds.contains(event.eventId)) {
            mBackgroundNotificationEventIds.add(event.eventId);
            String header = (TextUtils.isEmpty(roomName) ? "" : roomName + ": ");
            String text;

            if (null == event.content) {
                // Check whether the room id is available and a room name has been retrieved
                if (null != event.roomId && !TextUtils.isEmpty(header)) {
                    // Check whether the previous notification (if any) was from the same room
                    if (null != mLastBackgroundNotificationRoomId && mLastBackgroundNotificationRoomId.equals(event.roomId)) {
                        // Remove the last notified line to replace it
                        mBackgroundNotificationStrings.remove(0);
                    } else {
                        // Reset the current count
                        mLastBackgroundNotificationUnreadCount = 0;
                        mLastBackgroundNotificationRoomId = event.roomId;
                    }
                    mLastBackgroundNotificationUnreadCount++;
                } else {
                    // Reset the current notification string, only one notification will be displayed.
                    mBackgroundNotificationStrings.clear();
                    // Reset the unread count by considering the size of the event ids array.
                    mLastBackgroundNotificationUnreadCount = mBackgroundNotificationEventIds.size();
                }
                text = context.getResources().getQuantityString(R.plurals.room_new_messages_notification,
                        mLastBackgroundNotificationUnreadCount, mLastBackgroundNotificationUnreadCount);
            } else {
                // Add the potential sender name in the header
                String senderName = (TextUtils.isEmpty(senderDisplayName) ? event.sender : senderDisplayName);
                if (!TextUtils.isEmpty(senderName) && !senderName.equalsIgnoreCase(roomName)) {
                    header += senderName + " ";
                }

                if (event.isEncrypted()) {
                    text = context.getString(R.string.encrypted_message);
                } else {
                    EventDisplay eventDisplay = new RiotEventDisplay(context, event, null);
                    eventDisplay.setPrependMessagesWithAuthor(false);
                    text = eventDisplay.getTextualDisplay().toString();
                }
            }

            if (!TextUtils.isEmpty(text)) {
                SpannableString notifiedLine = new SpannableString(header + text);
                notifiedLine.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, header.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                mBackgroundNotificationStrings.add(0, notifiedLine);
                displayMessagesNotificationStatic(context, mBackgroundNotificationStrings, new BingRule(null, null, true, true, true));
            }
        } else if (0 == unreadMessagesCount) {
            mBackgroundNotificationStrings.clear();
            mLastBackgroundNotificationUnreadCount = 0;
            mLastBackgroundNotificationRoomId = null;
            displayMessagesNotificationStatic(context, null, null);
        }
    }

    /**
     * Display a list of messages in the messages notification, when the EventStreamService is not started.
     *
     * @param messages the messages list, null will hide the messages notification.
     * @param rule     the bing rule to use
     */
    private static void displayMessagesNotificationStatic(Context context, List<CharSequence> messages, BingRule rule) {
        if (!Matrix.getInstance(context).getSharedGCMRegistrationManager().areDeviceNotificationsAllowed()
                || null == messages
                || messages.isEmpty()) {
            NotificationUtils.INSTANCE.cancelNotificationMessage(context);
            RoomsNotifications.deleteCachedRoomNotifications(VectorApp.getInstance());
        } else {
            Notification notification = NotificationUtils.INSTANCE.buildMessagesListNotification(context, messages, rule);

            if (null != notification) {
                NotificationUtils.INSTANCE.showNotificationMessage(context, notification);
            } else {
                NotificationUtils.INSTANCE.cancelNotificationMessage(context);
            }
        }
    }

    /**
     * Display a list of messages in the messages notification.
     *
     * @param messages the messages list, null will hide the messages notification.
     * @param rule     the bing rule to use
     */
    private void displayMessagesNotification(final List<CharSequence> messages, final BingRule rule) {
        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (!mGcmRegistrationManager.areDeviceNotificationsAllowed()
                        || null == messages
                        || messages.isEmpty()) {
                    NotificationUtils.INSTANCE.cancelNotificationMessage(EventStreamService.this);
                    RoomsNotifications.deleteCachedRoomNotifications(VectorApp.getInstance());
                } else {
                    Notification notification = NotificationUtils.INSTANCE.buildMessagesListNotification(getApplicationContext(), messages, rule);

                    if (null != notification) {
                        NotificationUtils.INSTANCE.showNotificationMessage(EventStreamService.this, notification);
                    } else {
                        NotificationUtils.INSTANCE.cancelNotificationMessage(EventStreamService.this);
                    }
                }
            }
        });
    }

    /**
     * Refresh the messages notification.
     * Must always be called in getNotificationsHandler() thread.
     */
    private void refreshMessagesNotification() {
        // disabled background sync management
        mBackgroundNotificationStrings.clear();
        mLastBackgroundNotificationUnreadCount = 0;
        mLastBackgroundNotificationRoomId = null;
        mBackgroundNotificationEventIds.clear();

        NotifiedEvent eventToNotify = getEventToNotify();
        if (!mGcmRegistrationManager.areDeviceNotificationsAllowed()) {
            mNotifiedEventsByRoomId = null;
            new Handler(getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    displayMessagesNotification(null, null);
                }
            });
        } else if (refreshNotifiedMessagesList()) {
            // no more notifications
            if ((null == mNotifiedEventsByRoomId) || mNotifiedEventsByRoomId.size() == 0) {
                new Handler(getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        displayMessagesNotification(null, null);
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

                    // search the latest message to refresh the notification
                    for (String roomId : roomIds) {
                        List<NotifiedEvent> events = mNotifiedEventsByRoomId.get(roomId);
                        NotifiedEvent notifiedEvent = events.get(events.size() - 1);

                        Event event = store.getEvent(notifiedEvent.mEventId, notifiedEvent.mRoomId);

                        // detect if the event still exists
                        if (null == event) {
                            Log.e(LOG_TAG, "## refreshMessagesNotification() : the event " + notifiedEvent.mEventId
                                    + " in room " + notifiedEvent.mRoomId + " does not exist anymore");
                            mNotifiedEventsByRoomId.remove(roomId);
                        } else if ((event.getOriginServerTs()) > ts) {
                            eventToNotify = notifiedEvent;
                            ts = event.getOriginServerTs();
                        }
                    }
                }

                final NotifiedEvent fEventToNotify = eventToNotify;
                final Map<String, List<NotifiedEvent>> fNotifiedEventsByRoomId = new HashMap<>(mNotifiedEventsByRoomId);

                if (null != fEventToNotify) {
                    DismissNotificationReceiver.setLatestNotifiedMessageTs(this, fEventToNotify.mOriginServerTs);
                }

                new Handler(getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        // check if the notification has not been cancelled
                        if (fNotifiedEventsByRoomId.size() > 0) {
                            Notification notification = NotificationUtils.INSTANCE.buildMessageNotification(getApplicationContext(),
                                    new HashMap<>(fNotifiedEventsByRoomId),
                                    fEventToNotify,
                                    isBackgroundNotif);

                            // the notification cannot be built
                            if (null != notification) {
                                NotificationUtils.INSTANCE.showNotificationMessage(EventStreamService.this, notification);
                            } else {
                                displayMessagesNotification(null, null);
                            }
                        } else {
                            Log.e(LOG_TAG, "## refreshMessagesNotification() : mNotifiedEventsByRoomId is empty");
                            displayMessagesNotification(null, null);
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
    private NotifiedEvent getEventToNotify() {
        if (mPendingNotifications.size() > 0) {
            // TODO add multi sessions
            MXSession session = Matrix.getInstance(getBaseContext()).getDefaultSession();
            IMXStore store = session.getDataHandler().getStore();

            // notified only the latest unread message
            List<NotifiedEvent> eventsToNotify = new ArrayList<>(mPendingNotifications.values());

            Collections.reverse(eventsToNotify);

            for (NotifiedEvent eventToNotify : eventsToNotify) {
                Room room = store.getRoom(eventToNotify.mRoomId);

                // test if the message has not been read
                if ((null != room) && !room.isEventRead(eventToNotify.mEventId)) {
                    String body = null;
                    Event event = store.getEvent(eventToNotify.mEventId, eventToNotify.mRoomId);

                    if (null != event) {
                        // test if the message is displayable
                        EventDisplay eventDisplay = new RiotEventDisplay(getApplicationContext(), event, room.getState());
                        eventDisplay.setPrependMessagesWithAuthor(false);

                        CharSequence textualDisplay = eventDisplay.getTextualDisplay();

                        // reported by GA
                        if (null != textualDisplay) {
                            body = textualDisplay.toString();
                        }
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

        long minTs = DismissNotificationReceiver.getNotificationDismissTs(this);

        // initialise the map it was not yet done (after restarting the application for example)
        if (null == mNotifiedEventsByRoomId) {
            Log.d(LOG_TAG, "##refreshNotifiedMessagesList() : min message TS " + minTs);

            mNotifiedEventsByRoomId = new HashMap<>();
            Collection<Room> rooms = store.getRooms();

            for (Room room : rooms) {
                // invitation : add the dedicated event
                if (room.isInvited()) {
                    Collection<Event> events = store.getRoomMessages(room.getRoomId());

                    if (null != events) {
                        for (Event event : events) {
                            if (event.getOriginServerTs() < minTs) {
                                //Log.d(LOG_TAG, "##refreshNotifiedMessagesList() : ignore event "
                                // + event.eventId + " in room " + event.roomId + " because of the TS "+ event.getOriginServerTs());
                            } else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.getType())) {
                                try {
                                    if ("invite".equals(event.getContentAsJsonObject().getAsJsonPrimitive("membership").getAsString())) {
                                        BingRule rule = session.fulfillRule(event);

                                        if ((null != rule) && rule.isEnabled && rule.shouldNotify()) {
                                            List<NotifiedEvent> list = new ArrayList<>();
                                            list.add(new NotifiedEvent(event.roomId, event.eventId, rule, event.getOriginServerTs()));
                                            mNotifiedEventsByRoomId.put(room.getRoomId(), list);
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "##refreshNotifiedMessagesList() : invitation parsing failed", e);
                                }
                            }
                        }
                    }
                } else {
                    try {
                        List<Event> unreadEvents = store.unreadEvents(room.getRoomId(), null);

                        if ((null != unreadEvents) && unreadEvents.size() > 0) {
                            List<NotifiedEvent> list = new ArrayList<>();

                            for (Event event : unreadEvents) {
                                if (event.getOriginServerTs() > minTs) {
                                    BingRule rule = session.fulfillRule(event);

                                    if ((null != rule) && rule.isEnabled && rule.shouldNotify()) {
                                        list.add(new NotifiedEvent(event.roomId, event.eventId, rule, event.getOriginServerTs()));
                                        //Log.d(LOG_TAG, "## refreshNotifiedMessagesList() : the event "
                                        // + event.eventId + " in room " + event.roomId + " fulfills " + rule);
                                    }
                                } else {
                                    Log.d(LOG_TAG, "##refreshNotifiedMessagesList() : ignore event " + event.eventId
                                            + " in room " + event.roomId + " because of the TS " + (event.originServerTs));
                                }
                            }

                            if (list.size() > 0) {
                                mNotifiedEventsByRoomId.put(room.getRoomId(), list);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "##refreshNotifiedMessagesList(): failed checking the unread " + e.getMessage(), e);
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
                        List<NotifiedEvent> events = mNotifiedEventsByRoomId.get(roomId);

                        // if the oldest event has been read
                        // something has been updated
                        NotifiedEvent oldestEvent = events.get(0);

                        if (room.isEventRead(oldestEvent.mEventId) || (oldestEvent.mOriginServerTs < minTs)) {
                            // if the latest message has been read
                            // we have to find out the unread messages
                            NotifiedEvent latestEvent = events.get(events.size() - 1);
                            if (!room.isEventRead(latestEvent.mEventId) && latestEvent.mOriginServerTs > minTs) {
                                // search for the read messages
                                for (int i = 0; i < events.size(); ) {
                                    NotifiedEvent event = events.get(i);

                                    if (room.isEventRead(event.mEventId) || (event.mOriginServerTs <= minTs)) {
                                        // Log.d(LOG_TAG, "## refreshNotifiedMessagesList() : the event "
                                        // + event.mEventId + " in room " + room.getRoomId() + " is read");

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
                Log.e(LOG_TAG, "##refreshNotifiedMessagesList(): failed while building mNotifiedEventsByRoomId " + e.getMessage(), e);
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
    public void displayIncomingCallNotification(MXSession session, Room room, Event event, String callId, BingRule bingRule) {
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
                    RoomsNotifications.getRoomName(getApplicationContext(), session, room, event),
                    session.getMyUserId(),
                    callId);
            setForegroundNotificationState(ForegroundNotificationState.INCOMING_CALL, notification);

            mIncomingCallId = callId;

            // turn the screen on for 3 seconds
            if (Matrix.getInstance(VectorApp.getInstance()).getSharedGCMRegistrationManager().isScreenTurnedOn()) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "MXEventListener");
                wl.acquire(3000);
                wl.release();
            }
        } else {
            Log.d(LOG_TAG, "displayIncomingCallNotification : do not display the incoming call notification because there is a pending call");
        }
    }

    /**
     * Display a call in progress notification.
     *
     * @param session the session
     * @param callId  the callId
     */
    public void displayCallInProgressNotification(MXSession session, Room room, String callId) {
        if (null != callId) {
            Notification notification = NotificationUtils.INSTANCE.buildPendingCallNotification(getApplicationContext(),
                    room.getName(session.getCredentials().userId), room.getRoomId(), session.getCredentials().userId, callId);
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
