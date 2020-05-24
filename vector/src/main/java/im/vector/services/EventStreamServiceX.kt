/*
 * Copyright 2019 New Vector Ltd
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

package im.vector.services

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import im.vector.BuildConfig
import im.vector.Matrix
import im.vector.R
import im.vector.VectorApp
import im.vector.notifications.NotifiableEventResolver
import im.vector.notifications.NotificationUtils
import im.vector.notifications.OutdatedEventDetector
import im.vector.push.PushManager
import im.vector.receiver.VectorBootReceiver
import im.vector.util.CallsManager
import im.vector.util.PreferencesManager
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.data.RoomState
import org.matrix.androidsdk.data.store.IMXStore
import org.matrix.androidsdk.data.store.MXStoreListener
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.bingrules.BingRule
import java.util.concurrent.TimeUnit

/**
 * A service in charge of controlling whether the event stream is running or not.
 *
 * It manages messages notifications displayed to the end user.
 */
class EventStreamServiceX : VectorService() {

    /**
     * Managed session (no multi session for Riot)
     */
    private var mSession: MXSession? = null

    /**
     * Set to true to simulate a push immediately when service is destroyed
     */
    private var mSimulatePushImmediate = false

    /**
     * The current state.
     */
    private var serviceState = ServiceState.INIT
        set(newServiceState) {
            Log.i(LOG_TAG, "setServiceState from $field to $newServiceState")
            field = newServiceState
        }

    /**
     * Push manager
     */
    private var mPushManager: PushManager? = null

    private var mNotifiableEventResolver: NotifiableEventResolver? = null

    /**
     * Live events listener
     */
    private val mEventsListener = object : MXEventListener() {
        override fun onBingEvent(event: Event, roomState: RoomState, bingRule: BingRule) {
            if (BuildConfig.LOW_PRIVACY_LOG_ENABLE) {
                Log.i(LOG_TAG, "%%%%%%%%  MXEventListener: the event $event")
            }

            Log.i(LOG_TAG, "prepareNotification : " + event.eventId + " in " + roomState.roomId)
            val session = Matrix.getMXSession(applicationContext, event.matrixId)

            // invalid session ?
            // should never happen.
            // But it could be triggered because of multi accounts management.
            // The dedicated account is removing but some pushes are still received.
            if (null == session || !session.isAlive) {
                Log.i(LOG_TAG, "prepareNotification : don't bing - no session")
                return
            }

            if (Event.EVENT_TYPE_CALL_INVITE == event.getType()) {
                handleCallInviteEvent(event)
                return
            }


            val notifiableEvent = mNotifiableEventResolver!!.resolveEvent(event, roomState, bingRule, session)
            if (notifiableEvent != null) {
                VectorApp.getInstance().notificationDrawerManager.onNotifiableEventReceived(notifiableEvent)
            }
        }

        override fun onLiveEventsChunkProcessed(fromToken: String, toToken: String) {
            Log.i(LOG_TAG, "%%%%%%%%  MXEventListener: onLiveEventsChunkProcessed[$fromToken->$toToken]")

            VectorApp.getInstance().notificationDrawerManager.refreshNotificationDrawer(OutdatedEventDetector(this@EventStreamServiceX))

            // do not suspend the application if there is some active calls
            if (ServiceState.CATCHUP == serviceState) {
                val hasActiveCalls = mSession?.mCallsManager?.hasActiveCalls() == true

                // if there are some active calls, the catchup should not be stopped.
                // because an user could answer to a call from another device.
                // there will no push because it is his own message.
                // so, the client has no choice to catchup until the ring is shutdown
                if (hasActiveCalls) {
                    Log.i(LOG_TAG, "onLiveEventsChunkProcessed : Catchup again because there are active calls")
                    catchup(false)
                } else if (ServiceState.CATCHUP == serviceState) {
                    Log.i(LOG_TAG, "onLiveEventsChunkProcessed : no Active call")
                    CallsManager.getSharedInstance().checkDeadCalls()
                    stop()
                }
            }
        }
    }

    /**
     * Service internal state
     */
    private enum class ServiceState {
        // Initial state
        INIT,
        // Service is started for a Catchup. Once the catchup is finished the service will be stopped
        CATCHUP,
        // Service is started, and session is monitored
        STARTED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Cancel any previous worker
        cancelAnySimulatedPushSchedule()

        // no intent : restarted by Android
        if (null == intent) {
            // Cannot happen anymore
            Log.e(LOG_TAG, "onStartCommand : null intent")
            myStopSelf()
            return START_NOT_STICKY
        }
        val action = intent.action

        Log.i(LOG_TAG, "onStartCommand with action : $action (current state $serviceState)")

        // Manage foreground notification
        when (action) {
            ACTION_BOOT_COMPLETE,
            ACTION_APPLICATION_UPGRADE,
            ACTION_SIMULATED_PUSH_RECEIVED -> {
                // Display foreground notification
                Log.i(LOG_TAG, "startForeground")
                val notification = NotificationUtils.buildForegroundServiceNotification(this, R.string.notification_sync_in_progress)
                startForeground(NotificationUtils.NOTIFICATION_ID_FOREGROUND_SERVICE, notification)
            }
            ACTION_SIMULATED_PERMANENT_LISTENING -> {
                // Display foreground notification
                Log.i(LOG_TAG, "startForeground")
                val notification = NotificationUtils.buildForegroundServiceNotification(this, R.string.notification_listening_for_events, false)
                startForeground(NotificationUtils.NOTIFICATION_ID_FOREGROUND_SERVICE, notification)
            }
            ACTION_GO_TO_FOREGROUND -> {
                // Stop foreground notification display
                if (!BuildConfig.IS_SABA) {
                    Log.i(LOG_TAG, "stopForeground")
                    stopForeground(true)
                }
            }
        }

        mSession = Matrix.getInstance(applicationContext)!!.defaultSession
        mPushManager = Matrix.getInstance(applicationContext)!!.pushManager

        if (null == mSession || !mSession!!.isAlive) {
            Log.e(LOG_TAG, "onStartCommand : no sessions")
            myStopSelf()
            return START_NOT_STICKY
        }

        if (mSession?.dataHandler == null || mSession?.dataHandler?.store == null) {
            Log.e(LOG_TAG, "onStartCommand : invalid session")
            //this might launch riot?
            Matrix.getInstance(applicationContext)?.reloadSessions(applicationContext, false)
            myStopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START,
            ACTION_GO_TO_FOREGROUND -> {

                //We are back in foreground, we can sync
                mSession?.syncDelay = 0
                mSession?.syncTimeout = 30000

                when (serviceState) {
                    EventStreamServiceX.ServiceState.INIT ->
                        start(false)
                    EventStreamServiceX.ServiceState.CATCHUP ->
                        // A push has been received before, just change state, to avoid stopping the service when catchup is over
                        serviceState = ServiceState.STARTED
                    EventStreamServiceX.ServiceState.STARTED -> {
                        // Nothing to do
                    }
                }
            }
            ACTION_STOP,
            ACTION_GO_TO_BACKGROUND,
            ACTION_LOGOUT ->
                if (!BuildConfig.IS_SABA) stop()
            ACTION_PUSH_RECEIVED,
            ACTION_SIMULATED_PUSH_RECEIVED -> {

                // Catchup it asap
                mSession?.syncTimeout = 0

                when (serviceState) {
                    EventStreamServiceX.ServiceState.INIT ->
                        start(true)
                    EventStreamServiceX.ServiceState.CATCHUP ->
                        catchup(true)
                    EventStreamServiceX.ServiceState.STARTED ->
                        // Nothing to do
                        Unit
                }
            }

            ACTION_SIMULATED_PERMANENT_LISTENING -> {

                //Configure the delay and time out for background
                mSession?.syncDelay = mPushManager?.backgroundSyncDelay ?: 60 * 1000
                mSession?.syncTimeout = mPushManager?.backgroundSyncTimeOut ?: 6000

                when (serviceState) {
                    EventStreamServiceX.ServiceState.INIT ->
                        start(false)
                    EventStreamServiceX.ServiceState.CATCHUP ->
                        // A push has been received before, just change state, to avoid stopping the service when catchup is over
                        serviceState = ServiceState.STARTED
                    EventStreamServiceX.ServiceState.STARTED ->
                        // Nothing to do
                        Unit
                }
            }

            ACTION_PUSH_UPDATE -> pushStatusUpdate()
            ACTION_BOOT_COMPLETE -> {
                // No FCM only
                if (!BuildConfig.IS_SABA) {
                    mSimulatePushImmediate = true
                    stop()
                }
            }
            ACTION_APPLICATION_UPGRADE -> {
                // FDroid only
                catchup(true)
            }
            else -> {
                // Should not happen
            }
        }

        // We don't want the service to be restarted automatically by the System
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // Schedule worker?
        configureBackgroundBehavior()
    }

    /**
     * Tell the WorkManager to cancel any schedule of push simulation
     */
    private fun cancelAnySimulatedPushSchedule() {
        WorkManager.getInstance().cancelAllWorkByTag(PUSH_SIMULATOR_REQUEST_TAG)
    }

    /**
     * Configure the WorkManager to schedule a simulated push, if necessary
     */
    private fun configureBackgroundBehavior() {

        when (getBackgroundBehavior()) {

            NotifMode.FCM_FALLBACK,
            NotifMode.FDROID_OPTIMIZED_FOR_BATTERY -> {
                val delay = if (mSimulatePushImmediate) 0 else PreferencesManager.getWorkManagerSyncIntervalMillis(this)
                Log.i(LOG_TAG, "## service is schedule to restart in $delay millis, if network is connected")

                val pushSimulatorRequest = OneTimeWorkRequestBuilder<PushSimulatorWorker>()
                        .setInitialDelay(delay.toLong(), TimeUnit.MILLISECONDS)
                        .setConstraints(Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build())
                        .addTag(PUSH_SIMULATOR_REQUEST_TAG)
                        .build()

                WorkManager.getInstance().let {
                    // Cancel any previous worker
                    it.cancelAllWorkByTag(PUSH_SIMULATOR_REQUEST_TAG)
                    it.enqueue(pushSimulatorRequest)
                }
            }
            NotifMode.FDROID_OPTIMIZED_FOR_REALTIME -> {
                //we restart the service asap with permanent notification
                //TODO bad
                val intent = Intent(this, VectorBootReceiver::class.java)
                intent.action = VectorBootReceiver.PERMANENT_LISTENT
                sendBroadcast(intent)
            }
            NotifMode.VIA_FCM,
            NotifMode.NOTHING -> {
                //do nothing
            }
        }
    }

    /**
     * Start the even stream.
     *
     * @param session the session
     * @param store   the store
     */
    private fun startEventStream(session: MXSession, store: IMXStore?) {
        // resume if it was only suspended
        if (null != session.currentSyncToken) {
            session.resumeEventStream()
        } else {
            session.startEventStream(store?.eventStreamToken)
        }
    }

    /**
     * Monitor the provided session.
     *
     * @param session the session
     */
    private fun monitorSession(session: MXSession) {
        session.dataHandler.addListener(mEventsListener)
        CallsManager.getSharedInstance().addSession(session)

        val store = session.dataHandler.store

        if (store == null) {
            //reported by rage shake
            //TODO what should we do?
            Log.e(LOG_TAG, "## monitorSession: Store is null")
            return
        }

        // the store is ready (no data loading in progress...)
        if (store.isReady) {
            startEventStream(session, store)
        } else {
            // wait that the store is ready  before starting the events stream
            store.addMXStoreListener(object : MXStoreListener() {
                override fun onStoreReady(accountId: String) {
                    startEventStream(session, store)

                    store.removeMXStoreListener(this)
                }

                override fun onStoreCorrupted(accountId: String?, description: String?) {
                    // start a new initial sync
                    if (null == store.eventStreamToken) {
                        startEventStream(session, store)
                    } else {
                        // the data are out of sync
                        Matrix.getInstance(applicationContext)?.reloadSessions(applicationContext, false)
                    }

                    store.removeMXStoreListener(this)
                }

                override fun onStoreOOM(accountId: String?, description: String?) {
                    val uiHandler = Handler(mainLooper)

                    uiHandler.post {
                        Toast.makeText(applicationContext, "$accountId : $description", Toast.LENGTH_LONG).show()
                        Matrix.getInstance(applicationContext)!!.reloadSessions(applicationContext, true)
                    }
                }
            })

            store.open()
        }
    }

    /**
     * internal start.
     */
    private fun start(forPush: Boolean) {
        val applicationContext = applicationContext
        mNotifiableEventResolver = NotifiableEventResolver(applicationContext)

        monitorSession(mSession!!)

        serviceState = if (forPush) {
            ServiceState.CATCHUP
        } else {
            ServiceState.STARTED
        }
    }

    /**
     * internal stop.
     */
    private fun stop() {
        Log.i(LOG_TAG, "## stop(): the service is stopped")

        if (null != mSession && mSession!!.isAlive) {
            mSession!!.stopEventStream()
            mSession!!.dataHandler.removeListener(mEventsListener)
            CallsManager.getSharedInstance().removeSession(mSession)
        }
        mSession = null

        // Stop the service
        myStopSelf()
    }

    /**
     * internal catchup method.
     *
     * @param checkState true to check if the current state allow to perform a catchup
     */
    private fun catchup(checkState: Boolean) {
        var canCatchup = true

        if (!checkState) {
            Log.i(LOG_TAG, "catchup  without checking serviceState ")
        } else {
            Log.i(LOG_TAG, "catchup with serviceState " + serviceState + " CurrentActivity " + VectorApp.getCurrentActivity())

            // the catchup should only be done
            // 1- the serviceState is in catchup : the event stream might have gone to sleep between two catchups
            // 2- the thread is suspended
            // 3- the application has been launched by a push so there is no displayed activity
            canCatchup = (serviceState == ServiceState.CATCHUP
                    //|| (serviceState == ServiceState.PAUSE)
                    || ServiceState.STARTED == serviceState && null == VectorApp.getCurrentActivity())
        }

        if (canCatchup) {
            if (mSession != null) {
                mSession!!.catchupEventStream()
            } else {
                Log.i(LOG_TAG, "catchup no session")
            }

            serviceState = ServiceState.CATCHUP
        } else {
            Log.i(LOG_TAG, "No catchup is triggered because there is already a running event thread")
        }
    }

    /**
     * The push status has been updated (i.e disabled or enabled).
     * TODO Useless now?
     */
    private fun pushStatusUpdate() {
        Log.i(LOG_TAG, "## pushStatusUpdate")
    }

    /* ==========================================================================================
     * Push simulator
     * ========================================================================================== */


    enum class NotifMode {
        NOTHING,
        VIA_FCM,
        FCM_FALLBACK,
        FDROID_OPTIMIZED_FOR_BATTERY,
        FDROID_OPTIMIZED_FOR_REALTIME
    }

    /**
     * @return true if the FCM is disable or not setup, user allowed background sync, user wants notification
     */
    private fun getBackgroundBehavior(): NotifMode {
        if (Matrix.getInstance(applicationContext)?.defaultSession == null) {
            Log.i(LOG_TAG, "## getBackgroundBehavior: NO: no session")
            return NotifMode.NOTHING
        }

        mPushManager?.let { pushManager ->
            if (pushManager.useFcm()
                    && !TextUtils.isEmpty(pushManager.currentRegistrationToken)
                    && pushManager.isServerRegistered) {
                // FCM is ok
                Log.i(LOG_TAG, "## getBackgroundBehavior: NO: FCM is up")
                return NotifMode.VIA_FCM
            }

            if (!pushManager.isBackgroundSyncAllowed) {
                // User has disabled background sync
                Log.i(LOG_TAG, "## getBackgroundBehavior: NO: background sync not allowed")
                return NotifMode.NOTHING
            }

            if (!pushManager.areDeviceNotificationsAllowed()) {
                // User does not want notifications
                Log.i(LOG_TAG, "## getBackgroundBehavior: NO: user does not want notification")
                return NotifMode.NOTHING
            }

            if (pushManager.idFdroidSyncModeOptimizedForRealTime()) {
                Log.i(LOG_TAG, "## getBackgroundBehavior: Using permanent listening")
                return NotifMode.FDROID_OPTIMIZED_FOR_REALTIME
            } else if (pushManager.idFdroidSyncModeOptimizedForBattery()) {
                Log.i(LOG_TAG, "## getBackgroundBehavior: Using Work Manager")
                return NotifMode.FDROID_OPTIMIZED_FOR_BATTERY
            } else {
                Log.i(LOG_TAG, "## getBackgroundBehavior: NO: user does not want notification")
                return NotifMode.NOTHING
            }
        }
        Log.i(LOG_TAG, "## getBackgroundBehavior: NO: Unknown Bacgkround mode")
        return NotifMode.NOTHING
    }


    //================================================================================
    // Call management
    //================================================================================

    private fun handleCallInviteEvent(event: Event) {
        val session = Matrix.getMXSession(applicationContext, event.matrixId)

        // invalid session ?
        // should never happen.
        // But it could be triggered because of multi accounts management.
        // The dedicated account is removing but some pushes are still received.
        if (null == session || !session.isAlive) {
            Log.d(LOG_TAG, "prepareCallNotification : don't bing - no session")
            return
        }

        val room: Room? = session.dataHandler.getRoom(event.roomId)

        // invalid room ?
        if (null == room) {
            Log.i(LOG_TAG, "prepareCallNotification : don't bing - the room does not exist")
            return
        }

        var callId: String? = null
        var isVideo = false

        try {
            callId = event.contentAsJsonObject?.get("call_id")?.asString

            // Check if it is a video call
            val offer = event.contentAsJsonObject?.get("offer")?.asJsonObject
            val sdp = offer?.get("sdp")
            val sdpValue = sdp?.asString

            isVideo = sdpValue?.contains("m=video") == true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "prepareNotification : getContentAsJsonObject " + e.message, e)
        }

        // Since This Service is always running and server does not distinguish between incoming and missed calls,
        // We don't need to run CallService. FYI, SDK handles call management itself.
        if (BuildConfig.IS_SABA) return

        if (!TextUtils.isEmpty(callId)) {
            CallService.onIncomingCall(this,
                    isVideo,
                    room.getRoomDisplayName(this),
                    room.roomId,
                    session.myUserId!!,
                    callId!!)
        }
    }

    companion object {
        private val LOG_TAG = EventStreamServiceX::class.java.simpleName

        private const val PUSH_SIMULATOR_REQUEST_TAG = "PUSH_SIMULATOR_REQUEST_TAG"

        private const val ACTION_START = "im.vector.services.EventStreamServiceX.START"
        private const val ACTION_LOGOUT = "im.vector.services.EventStreamServiceX.LOGOUT"
        private const val ACTION_GO_TO_FOREGROUND = "im.vector.services.EventStreamServiceX.GO_TO_FOREGROUND"
        private const val ACTION_GO_TO_BACKGROUND = "im.vector.services.EventStreamServiceX.GO_TO_BACKGROUND"
        private const val ACTION_PUSH_UPDATE = "im.vector.services.EventStreamServiceX.PUSH_UPDATE"
        private const val ACTION_PUSH_RECEIVED = "im.vector.services.EventStreamServiceX.PUSH_RECEIVED"
        private const val ACTION_SIMULATED_PUSH_RECEIVED = "im.vector.services.EventStreamServiceX.SIMULATED_PUSH_RECEIVED"
        public const val ACTION_SIMULATED_PERMANENT_LISTENING = "im.vector.services.EventStreamServiceX.ACTION_SIMULATED_PERMANENT_LISTENING"
        private const val ACTION_STOP = "im.vector.services.EventStreamServiceX.STOP"
        private const val ACTION_BOOT_COMPLETE = "im.vector.services.EventStreamServiceX.BOOT_COMPLETE"
        private const val ACTION_APPLICATION_UPGRADE = "im.vector.services.EventStreamServiceX.APPLICATION_UPGRADE"

        /* ==========================================================================================
         * Events sent to the service
         * ========================================================================================== */

        fun onApplicationStarted(context: Context) {
            sendAction(context, ACTION_START)
        }

        fun onLogout(context: Context) {
            sendAction(context, ACTION_LOGOUT)
        }

        fun onAppGoingToForeground(context: Context) {
            sendAction(context, ACTION_GO_TO_FOREGROUND)
        }

        fun onAppGoingToBackground(context: Context) {
            sendAction(context, ACTION_GO_TO_BACKGROUND)
        }

        fun onPushUpdate(context: Context) {
            sendAction(context, ACTION_PUSH_UPDATE)
        }

        fun onPushReceived(context: Context) {
            sendAction(context, ACTION_PUSH_RECEIVED)
        }

        fun onSimulatedPushReceived(context: Context) {
            sendAction(context, ACTION_SIMULATED_PUSH_RECEIVED, true)
        }

        fun onApplicationStopped(context: Context) {
            sendAction(context, ACTION_STOP)
        }

        fun onBootComplete(context: Context) {
            sendAction(context, ACTION_BOOT_COMPLETE, true)
        }

        fun onApplicationUpgrade(context: Context) {
            sendAction(context, ACTION_APPLICATION_UPGRADE, true)
        }

        fun onForcePermanentEventListening(context: Context) {
            sendAction(context, ACTION_SIMULATED_PERMANENT_LISTENING, true)
        }

        private fun sendAction(context: Context, action: String, foreground: Boolean = false) {
            Log.i(LOG_TAG, "sendAction $action")

            val intent = Intent(context, EventStreamServiceX::class.java)
            intent.action = action

            try {
                if (foreground) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: java.lang.Exception) {
                //Can we recover here? in case of illegal state
                Log.i(LOG_TAG, "## Failed to start event stream", e)
            }
        }
    }
}
