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

package org.matrix.console.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.console.ConsoleApplication;
import org.matrix.console.Matrix;
import org.matrix.console.R;
import org.matrix.console.ViewedRoomTracker;
import org.matrix.console.activity.CommonActivityUtils;
import org.matrix.console.activity.HomeActivity;
import org.matrix.console.util.NotificationUtils;

import java.util.ArrayList;
import java.util.Arrays;
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

    public static final String EXTRA_STREAM_ACTION = "org.matrix.console.services.EventStreamService.EXTRA_STREAM_ACTION";
    public static final String EXTRA_MATRIX_IDS = "org.matrix.console.services.EventStreamService.EXTRA_MATRIX_IDS";

    private static final String LOG_TAG = "EventStreamService";
    private static final int NOTIFICATION_ID = 42;
    private static final int MSG_NOTIFICATION_ID = 43;

    private ArrayList<MXSession> mSessions;
    private ArrayList<String> mMatrixIds;
    private StreamAction mState = StreamAction.UNKNOWN;

    private String mNotificationRoomId = null;

    private Boolean mIsForegound = false;
    private int mUnreadMessagesCounter = 0;

    private static EventStreamService mActiveEventStreamService = null;

    public static EventStreamService getInstance() {
        return mActiveEventStreamService;
    }

    /**
     * Cancel the push notifications for a dedicated roomId.
     * If the roomId is null, cancel all the push notification.
     * @param roomId
     */
    public static void cancelNotificationsForRoomId(String roomId) {
        if (null != mActiveEventStreamService) {
            mActiveEventStreamService.cancelNotifications(roomId);
        }
    }

    private void cancelNotifications(String roomId) {
        boolean cancelNotifications = true;

        // clear only if the notification has been pushed for a dedicated RoomId
        if (null != roomId) {
            cancelNotifications = (null != mNotificationRoomId) && (mNotificationRoomId.equals(roomId));
        }

        // cancel the notifications
        if (cancelNotifications) {
            NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancelAll();
        }
    }

    private MXEventListener mListener = new MXEventListener() {

        // White list of displayable events
        private boolean isDisplayableEvent(Event event) {
            return Event.EVENT_TYPE_MESSAGE.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type);
        }


        @Override
        public void onLiveEvent(Event event, RoomState roomState) {
            if ((event.roomId != null) && isDisplayableEvent(event)) {
                ViewedRoomTracker rTracker = ViewedRoomTracker.getInstance();
                String viewedRoomId = rTracker.getViewedRoomId();
                String fromMatrixId = rTracker.getMatrixId();

                Matrix matrix = Matrix.getInstance(EventStreamService.this);
                RoomSummary summary = null;

                // sanity check
                if (null != matrix) {
                    MXSession session = matrix.getSession(event.getMatrixId());

                    // sanity check
                    if (null != session) {
                        summary = session.getDataHandler().getStore().getSummary(event.roomId);
                    }
                }

                // existing summary ?
                if (null != summary) {
                    // If we're not currently viewing this room or not sent by myself, increment the unread count
                    if (ConsoleApplication.isAppInBackground() || (!event.roomId.equals(viewedRoomId) || !event.getMatrixId().equals(fromMatrixId)) && !event.userId.equals(event.getMatrixId())) {
                        summary.incrementUnreadMessagesCount();
                    } else {
                        summary.resetUnreadMessagesCount();
                    }
                }
            }
        }

        @Override
        public void onBingEvent(Event event, RoomState roomState, BingRule bingRule) {
            Log.i(LOG_TAG, "onMessageEvent >>>> " + event);

            final String roomId = event.roomId;

            // Just don't bing for the room the user's currently in
            if (!ConsoleApplication.isAppInBackground() && (roomId != null) && event.roomId.equals(ViewedRoomTracker.getInstance().getViewedRoomId())) {
                return;
            }

            String senderID = event.userId;
            // FIXME: Support event contents with no body
            if (!event.content.has("body")) {
                // only the membership events are supported
                if (!Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                    return;
                }
            }

            String body;

            if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                body = EventDisplay.getMembershipNotice(getApplicationContext(), event, roomState);
            } else {
                body = event.content.getAsJsonPrimitive("body").getAsString();
            }

            MXSession session = Matrix.getMXSession(getApplicationContext(), event.getMatrixId());

            // invalid session ?
            // should never happen.
            // But it could be triggered because of multi accounts management.
            // The dedicated account is removing but some pushes are still received.
            if (null == session) {
                return;
            }

            Room room = session.getDataHandler().getRoom(roomId);

            // invalid room ?
            if (null == room) {
                return;
            }

            RoomMember member = room.getMember(senderID);

            // invalid member
            if (null == member) {
                return;
            }

            String roomName = null;
            if(session.getMyUser() != null) {
                roomName = room.getName(session.getMyUser().userId);
            }

            mNotificationRoomId = roomId;

            Notification n = NotificationUtils.buildMessageNotification(
                    EventStreamService.this,
                    member.getName(), session.getCredentials().userId, Matrix.getMXSessions(getApplicationContext()).size() > 1, body, event.roomId, roomName, bingRule.shouldPlaySound());
            NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancelAll();

            nm.notify(MSG_NOTIFICATION_ID, n);

            // turn the screen on for 3 seconds
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "MXEventListener");
            wl.acquire(3000);
            wl.release();

            if (ConsoleApplication.isAppInBackground()) {
                CommonActivityUtils.updateUnreadMessagesBadge(getApplicationContext(), ++mUnreadMessagesCounter);
            }
        }

        @Override
        public void onResendingEvent(Event event) {
        }

        @Override
        public void onResentEvent(Event event) {
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

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startEventStream(final MXSession session, final IMXStore store) {
        session.getDataHandler().checkPermanentStorageData();
        session.startEventStream(store.getEventStreamToken());
    }

    private void start() {
        // reset the badbge counter when resuming the application
        if (0 != mUnreadMessagesCounter) {
            mUnreadMessagesCounter = 0;
            CommonActivityUtils.updateUnreadMessagesBadge(this, mUnreadMessagesCounter);
        }

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
                });
            };
        }

        updateListenerNotification();
    }

    private void stop() {
        if (mIsForegound) {
            stopForeground(true);
        }

        if (mSessions != null) {
            for(MXSession session : mSessions) {
                session.stopEventStream();
                session.getDataHandler().removeListener(mListener);
            }
        }
        mMatrixIds = null;
        mSessions = null;
        mState = StreamAction.STOP;

        mActiveEventStreamService = null;
    }

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

    private void catchup() {
        if (mSessions != null) {
            for(MXSession session : mSessions) {
                session.catchupEventStream();
            }
        } else {
            Log.e(LOG_TAG, "catchup no session");
        }

        mState = StreamAction.CATCHUP;
    }

    private void resume() {
        if (mSessions != null) {
            for(MXSession session : mSessions) {
                session.resumeEventStream();
            }
        }

        mState = StreamAction.START;
    }

    private void gcmStatusUpdate() {
        if (mIsForegound) {
            stopForeground(true);
            mIsForegound = false;
        }

        updateListenerNotification();
    }

    private void updateListenerNotification() {
        if (!Matrix.getInstance(this).getSharedGcmRegistrationManager().useGCM()) {
            Notification notification = buildNotification();
            startForeground(NOTIFICATION_ID, notification);
            mIsForegound = true;
        } else {
            NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFICATION_ID);
            mIsForegound = false;
        }
    }

    private Notification buildNotification() {
        Notification notification = new Notification(
                (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) ? R.drawable.ic_menu_small_matrix : R.drawable.ic_menu_small_matrix_transparent,
                "Matrix",
                System.currentTimeMillis()
        );

        // go to the home screen if this is clicked.
        Intent i = new Intent(this, HomeActivity.class);

        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

        notification.setLatestEventInfo(this, getString(R.string.app_name),
                "Listening for events",
                pi);
        notification.flags |= Notification.FLAG_NO_CLEAR;
        return notification;
    }
}
