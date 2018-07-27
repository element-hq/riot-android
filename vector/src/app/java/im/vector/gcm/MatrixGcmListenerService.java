/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.gcm;

import android.support.annotation.Nullable;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.JsonParser;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.util.Log;

import java.util.Collection;
import java.util.Map;

import im.vector.Matrix;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.services.EventStreamService;
import im.vector.util.VectorUtils;

/**
 * Class implementing GcmListenerService.
 */
public class MatrixGcmListenerService extends FirebaseMessagingService {
    private static final String LOG_TAG = MatrixGcmListenerService.class.getSimpleName();

    // Tells if the events service running state has been tested
    private Boolean mCheckLaunched = false;

    // UI handler
    private android.os.Handler mUIHandler = null;

    /**
     * Try to create an event from the GCM data
     *
     * @param data the GCM data
     * @return the event
     */
    @Nullable
    private Event parseEvent(Map<String, String> data) {
        // accept only event with room id.
        if ((null == data) || !data.containsKey("room_id") || !data.containsKey("event_id")) {
            return null;
        }

        try {
            Event event = new Event();
            event.eventId = data.get("event_id");
            event.sender = data.get("sender");
            event.roomId = data.get("room_id");
            event.setType(data.get("type"));

            if (data.containsKey("content")) {
                event.updateContent((new JsonParser()).parse(data.get("content")).getAsJsonObject());
            }

            return event;
        } catch (Exception e) {
            Log.e(LOG_TAG, "buildEvent fails " + e.getLocalizedMessage(), e);
        }

        return null;
    }

    /**
     * Internal receive method
     *
     * @param data Data map containing message data as key/value pairs.
     *             For Set of keys use data.keySet().
     */
    private void onMessageReceivedInternal(final Map<String, String> data) {
        try {
            int unreadCount = 0;
            String roomId = null;
            String eventId = null;

            if ((null != data) && data.containsKey("unread")) {
                if (data.containsKey("unread")) {
                    unreadCount = Integer.parseInt(data.get("unread"));
                }

                if (data.containsKey("room_id")) {
                    roomId = data.get("room_id");
                }

                if (data.containsKey("event_id")) {
                    eventId = data.get("event_id");
                }
            }

            Log.d(LOG_TAG, "## onMessageReceivedInternal() : roomId " + roomId + " eventId " + eventId + " unread " + unreadCount);

            // update the badge counter
            CommonActivityUtils.updateBadgeCount(getApplicationContext(), unreadCount);

            GcmRegistrationManager gcmManager = Matrix.getInstance(getApplicationContext()).getSharedGCMRegistrationManager();

            if (!gcmManager.areDeviceNotificationsAllowed()) {
                Log.d(LOG_TAG, "## onMessageReceivedInternal() : the notifications are disabled");
                return;
            }
            if (!gcmManager.isBackgroundSyncAllowed() && VectorApp.isAppInBackground()) {
                EventStreamService eventStreamService = EventStreamService.getInstance();
                Event event = parseEvent(data);

                String roomName = data.get("room_name");
                if (null == roomName && null != roomId) {
                    // Try to get the room name from our store
                    MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();

                    if ((null != session) && session.getDataHandler().getStore().isReady()) {
                        Room room = session.getDataHandler().getStore().getRoom(roomId);
                        if (null != room) {
                            roomName = VectorUtils.getRoomDisplayName(this, session, room);
                        }
                    }
                }

                Log.d(LOG_TAG, "## onMessageReceivedInternal() : the background sync is disabled with eventStreamService " + eventStreamService);

                EventStreamService.onStaticNotifiedEvent(getApplicationContext(), event, roomName, data.get("sender_display_name"), unreadCount);
                return;
            }

            // check if the application has been launched once
            // the first GCM event could have been triggered whereas the application is not yet launched.
            // so it is required to create the sessions and to start/resume event stream
            if (!mCheckLaunched && null != Matrix.getInstance(getApplicationContext()).getDefaultSession()) {
                CommonActivityUtils.startEventStreamService(this);
                mCheckLaunched = true;
            }

            // check if the event was not yet received
            // a previous catchup might have already retrieved the notified event
            if (null != eventId && null != roomId) {
                try {
                    Collection<MXSession> sessions = Matrix.getInstance(getApplicationContext()).getSessions();

                    if (null != sessions && !sessions.isEmpty()) {
                        for (MXSession session : sessions) {
                            if (session.getDataHandler().getStore().isReady()) {
                                if (null != session.getDataHandler().getStore().getEvent(eventId, roomId)) {
                                    Log.e(LOG_TAG, "## onMessageReceivedInternal() : ignore the event " + eventId
                                            + " in room " + roomId + " because it is already known");
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## onMessageReceivedInternal() : failed to check if the event was already defined " + e.getMessage(), e);
                }
            }

            CommonActivityUtils.catchupEventStream(this);
        } catch (Exception e) {
            Log.d(LOG_TAG, "## onMessageReceivedInternal() failed : " + e.getMessage(), e);
        }
    }

    /**
     * Called when message is received.
     *
     * @param message the message
     */
    @Override
    public void onMessageReceived(RemoteMessage message) {
        Log.d(LOG_TAG, "## onMessageReceived() from FCM");

        // Ensure event stream service is started
        if (EventStreamService.getInstance() == null) {
            CommonActivityUtils.startEventStreamService(this);
        }

        final Map<String, String> data = message.getData();

        if (null == mUIHandler) {
            mUIHandler = new android.os.Handler(VectorApp.getInstance().getMainLooper());
        }

        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                onMessageReceivedInternal(data);
            }
        });
    }
}
