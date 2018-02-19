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

package im.vector.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
public class WidgetsManager {
    private static final String LOG_TAG = WidgetsManager.class.getSimpleName();

    /**
     * The type of matrix event used for scalar widgets.
     */
    public static final String WIDGET_EVENT_TYPE = "im.vector.modular.widgets";

    /**
     * Known types widgets.
     */
    private static final String WIDGET_TYPE_JITSI = "jitsi";

    /**
     * Integration rest url
     */
    private static final String INTEGRATION_REST_URL = "https://scalar.vector.im";

    /**
     * Integration ui url
     */
    public static final String INTEGRATION_UI_URL = "https://scalar-staging.riot.im/scalar-web/";

    /**
     * Widget preferences
     */
    private static final String SCALAR_TOKEN_PREFERENCE_KEY = "SCALAR_TOKEN_PREFERENCE_KEY";

    /**
     * Widget error code
     */
    public class WidgetError extends MatrixError {
        public static final String WIDGET_NOT_ENOUGH_POWER_ERROR_CODE = "WIDGET_NOT_ENOUGH_POWER_ERROR_CODE";
        public static final String WIDGET_CREATION_FAILED_ERROR_CODE = "WIDGET_CREATION_FAILED_ERROR_CODE";

        /**
         * Create a widget error
         *
         * @param code                     the error code (see XX_ERROR_CODE)
         * @param detailedErrorDescription the detailed error description
         */
        public WidgetError(String code, String detailedErrorDescription) {
            errcode = code;
            error = detailedErrorDescription;
        }
    }

    /**
     * unique instance
     */
    private static final WidgetsManager mSharedInstance = new WidgetsManager();

    /**
     * @return the shared instance
     */
    public static WidgetsManager getSharedInstance() {
        return mSharedInstance;
    }

    /**
     * Pending widget creation callback
     */
    private final Map<String, ApiCallback<Widget>> mPendingWidgetCreationCallbacks = new HashMap<>();

    /**
     * List all active widgets in a room.
     *
     * @param session the session.
     * @param room    the room to check.
     * @return the active widgets list
     */
    public List<Widget> getActiveWidgets(MXSession session, Room room) {
        return getActiveWidgets(session, room, null, null);
    }

    /**
     * List all active widgets in a room.
     *
     * @param session     the session.
     * @param room        the room to check.
     * @param widgetTypes the the widget types
     * @param excludedTypes the the excluded widget types
     * @return the active widgets list
     */
    private List<Widget> getActiveWidgets(final MXSession session, final Room room, final Set<String> widgetTypes, final Set<String> excludedTypes) {
        // Get all im.vector.modular.widgets state events in the room
        List<Event> widgetEvents = room.getLiveState().getStateEvents(new HashSet<>(Arrays.asList(WIDGET_EVENT_TYPE)));

        // Widget id -> widget
        Map<String, Widget> widgets = new HashMap<>();

        // Order widgetEvents with the last event first
        // There can be several im.vector.modular.widgets state events for a same widget but
        // only the last one must be considered.

        Collections.sort(widgetEvents, new Comparator<Event>() {
            @Override
            public int compare(Event e1, Event e2) {
                long diff = e1.getOriginServerTs() - e2.getOriginServerTs();
                return (diff < 0) ? +1 : ((diff > 0) ? -1 : 0);
            }
        });

        // Create each widget from its latest im.vector.modular.widgets state event
        for (Event widgetEvent : widgetEvents) {
            // Filter widget types if required
            if ((null != widgetTypes) || (null != excludedTypes)) {
                String widgetType = null;

                try {
                    JsonObject jsonObject = widgetEvent.getContentAsJsonObject();

                    if (jsonObject.has("type")) {
                        widgetType = jsonObject.get("type").getAsString();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## getWidgets() failed : " + e.getMessage());
                }

                if (null != widgetType) {
                    if ((null != widgetTypes) && !widgetTypes.contains(widgetType)) {
                        continue;
                    }

                    if ((null != excludedTypes) && excludedTypes.contains(widgetType)) {
                        continue;
                    }
                }
            }

            // widgetEvent.stateKey = widget id
            if ((null != widgetEvent.stateKey) && !widgets.containsKey(widgetEvent.stateKey)) {
                Widget widget = null;

                try {
                    if (null == widgetEvent.roomId) {
                        Log.e(LOG_TAG, "## getWidgets() : set the room id to the event " + widgetEvent.eventId);
                        widgetEvent.roomId = room.getRoomId();
                    }

                    widget = new Widget(session, widgetEvent);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## getWidgets() : widget creation failed " + e.getMessage());
                }

                if (null != widget) {
                    widgets.put(widget.getWidgetId(), widget);
                }
            }
        }

        // Return active widgets only
        List<Widget> activeWidgets = new ArrayList<>();

        for (Widget widget : widgets.values()) {
            if (widget.isActive()) {
                activeWidgets.add(widget);
            }
        }

        return activeWidgets;
    }

    /**
     * Provides the list of active widgets for a room
     *
     * @param session the session
     * @param room    the room
     * @return the list of active widgets
     */
    public List<Widget> getActiveJitsiWidgets(final MXSession session, final Room room) {
        return getActiveWidgets(session, room, new HashSet<>(Arrays.asList(WidgetsManager.WIDGET_TYPE_JITSI)), null);
    }

    /**
     * Provides the widgets which can be displayed in a webview.
     * @param session the session
     * @param room the room
     * @return the list of active widgets
     */
    public List<Widget> getActiveWebviewWidgets(final MXSession session, final Room room) {
        return getActiveWidgets(session, room, null, new HashSet<>(Arrays.asList(WidgetsManager.WIDGET_TYPE_JITSI)));
    }
    /**
     * Check user's power for widgets management in a room.
     *
     * @param session the session
     * @param room    the room
     * @return an error if the user cannot act on widgets in this room. Else, null.
     */

    public WidgetError checkWidgetPermission(MXSession session, Room room) {
        WidgetError error = null;

        if ((null != room) && (null != room.getLiveState()) && (null != room.getLiveState().getPowerLevels())) {
            int oneSelfPowerLevel = room.getLiveState().getPowerLevels().getUserPowerLevel(session.getMyUserId());

            if (oneSelfPowerLevel < room.getLiveState().getPowerLevels().state_default) {
                error = new WidgetError(WidgetError.WIDGET_NOT_ENOUGH_POWER_ERROR_CODE, VectorApp.getInstance().getString(R.string.widget_no_power_to_manage));
            }
        }

        return error;
    }

    /**
     * Add a scalar widget to a room.
     *
     * @param session  the session to create the widget to.
     * @param room     the room to create the widget to.
     * @param widgetId the id of the widget.
     * @param content  the widget content.
     * @param callback the asynchronous callback
     */
    private void createWidget(MXSession session, Room room, String widgetId, Map<String, Object> content, final ApiCallback<Widget> callback) {
        WidgetError permissionError = checkWidgetPermission(session, room);

        if (null != permissionError) {
            if (null != callback) {
                callback.onMatrixError(permissionError);
            }
            return;
        }

        final String key = session.getMyUserId() + "_" + widgetId;

        if (null != callback) {
            mPendingWidgetCreationCallbacks.put(key, callback);
        }

        // Send a state event with the widget data
        // TODO: This API will be shortly replaced by a pure scalar API
        session.getRoomsApiClient().sendStateEvent(room.getRoomId(), WIDGET_EVENT_TYPE, widgetId, content, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // wait echo from the live stream
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
                mPendingWidgetCreationCallbacks.remove(key);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
                mPendingWidgetCreationCallbacks.remove(key);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
                mPendingWidgetCreationCallbacks.remove(key);
            }
        });
    }

    /**
     * Add a jitsi conference widget to a room.
     *
     * @param session   the session
     * @param room      the room
     * @param withVideo true to make a video call
     * @param callback  the asynchronous callback
     */
    public void createJitsiWidget(MXSession session, Room room, boolean withVideo, final ApiCallback<Widget> callback) {
        // Build data for a jitsi widget
        String widgetId = WIDGET_TYPE_JITSI + "_" + session.getMyUserId() + "_" + System.currentTimeMillis();

        // Create a random enough jitsi conference id
        // Note: the jitsi server automatically creates conference when the conference
        // id does not exist yet
        String widgetSessionId = UUID.randomUUID().toString();

        if (widgetSessionId.length() > 8) {
            widgetSessionId = widgetSessionId.substring(0, 7);
        }
        String roomId = room.getRoomId();
        String confId = roomId.substring(1, roomId.indexOf(":") - 1) + widgetSessionId.toLowerCase(VectorApp.getApplicationLocale());

        // TODO: This url may come from scalar API
        // Note: this url can be used as is inside a web container (like iframe for Riot-web)
        // Riot-iOS does not directly use it but extracts params from it (see `[JitsiViewController openWidget:withVideo:]`)
        String url = "https://scalar.vector.im/api/widgets/jitsi.html?confId=" + confId + "&isAudioConf=" + (withVideo ? "false" : "true") + "&displayName=$matrix_display_name&avatarUrl=$matrix_avatar_url&email=$matrix_user_id";

        Map<String, Object> params = new HashMap<>();
        params.put("url", url);
        params.put("type", WIDGET_TYPE_JITSI);

        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("widgetSessionId", widgetSessionId);
        params.put("data", dataMap);

        createWidget(session, room, widgetId, params, callback);
    }

    /**
     * Close a widget
     *
     * @param session  the session
     * @param room     the room
     * @param widgetId the widget id
     * @param callback the asynchronous callback
     */
    public void closeWidget(MXSession session, Room room, String widgetId, final ApiCallback<Void> callback) {
        // sanity checks
        if ((null != session) && (null != room) && (null != widgetId)) {
            WidgetError permissionError = checkWidgetPermission(session, room);

            if (null != permissionError) {
                if (null != callback) {
                    callback.onMatrixError(permissionError);
                }
                return;
            }

            // Send a state event with the widget data
            // TODO: This API will be shortly replaced by a pure scalar API
            session.getRoomsApiClient().sendStateEvent(room.getRoomId(), WIDGET_EVENT_TYPE, widgetId, new HashMap<String, Object>(), callback);
        }
    }

    public interface onWidgetUpdateListener {
        /**
         * Warn that there is an update on a widget.
         *
         * @param widget the widget
         */
        void onWidgetUpdate(Widget widget);
    }

    private static final Set<onWidgetUpdateListener> mListeners = new HashSet<>();

    /**
     * Add a listener.
     *
     * @param listener the listener to add
     */
    public static void addListener(onWidgetUpdateListener listener) {
        if (null != listener) {
            synchronized (mListeners) {
                mListeners.add(listener);
            }
        }
    }

    /**
     * Remove a listener.
     *
     * @param listener the listener to remove
     */
    public static void removeListener(onWidgetUpdateListener listener) {
        if (null != listener) {
            synchronized (mListeners) {
                mListeners.remove(listener);
            }
        }
    }

    /**
     * Dispatches the widget update event.
     *
     * @param widget the widget
     */
    private void onWidgetUpdate(Widget widget) {
        synchronized (mListeners) {
            for (onWidgetUpdateListener listener : mListeners) {
                try {
                    listener.onWidgetUpdate(widget);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## onWidgetUpdate failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Manage the live event
     *
     * @param session the session
     * @param event   the event
     */
    public void onLiveEvent(MXSession session, Event event) {
        if (TextUtils.equals(WIDGET_EVENT_TYPE, event.getType())) {
            // stateKey = widgetId
            String widgetId = event.stateKey;

            final String callbackKey = session.getMyUserId() + "_" + widgetId;

            Log.d(LOG_TAG, "## onLiveEvent() : New widget detected: " + widgetId + " in room " + event.roomId);

            Widget widget = null;

            try {
                widget = new Widget(session, event);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## onLiveEvent () : widget creation failed " + e.getMessage());
            }

            if (null != widget) {
                // If it is a widget we have just created, indicate its creation is complete
                if (mPendingWidgetCreationCallbacks.containsKey(callbackKey)) {
                    try {
                        mPendingWidgetCreationCallbacks.get(callbackKey).onSuccess(widget);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## onLiveEvent() : get(callbackKey).onSuccess failed " + e.getMessage());
                    }
                }

                onWidgetUpdate(widget);
            } else {
                Log.e(LOG_TAG, "## onLiveEvent() : Cannot decode new widget - event: " + event);

                if (mPendingWidgetCreationCallbacks.containsKey(callbackKey)) {
                    try {
                        mPendingWidgetCreationCallbacks.get(callbackKey).onMatrixError(new WidgetError(WidgetError.WIDGET_CREATION_FAILED_ERROR_CODE, VectorApp.getInstance().getString(R.string.widget_creation_failure)));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## onLiveEvent() : get(callbackKey).onMatrixError failed " + e.getMessage());
                    }
                }
            }

            mPendingWidgetCreationCallbacks.remove(callbackKey);
        }
    }

    /**
     * Format the widget URL to be displayed.
     *
     * @param context  context
     * @param widget   the widget
     * @param callback the callback
     */
    public static void getFormattedWidgetUrl(final Context context, final Widget widget, final ApiCallback<String> callback) {
        getScalarToken(context, Matrix.getInstance(context).getSession(widget.getSessionId()), new ApiCallback<String>() {
            @Override
            public void onSuccess(String token) {
                if (null == token) {
                    callback.onSuccess(widget.getUrl());
                } else {
                    callback.onSuccess(widget.getUrl() + "&scalar_token=" + token);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }


    /**
     * Retrieve the scalar token
     *
     * @param context  the context
     * @param session  the session
     * @param callback the asynchronous callback
     */
    public static void getScalarToken(final Context context, final MXSession session, final ApiCallback<String> callback) {
        final String preferenceKey = SCALAR_TOKEN_PREFERENCE_KEY + session.getMyUserId();

        final String scalarToken = PreferenceManager.getDefaultSharedPreferences(context).getString(preferenceKey, null);

        if (null != scalarToken) {
            (new Handler(Looper.getMainLooper())).post(new Runnable() {
                @Override
                public void run() {
                    if (null != scalarToken) {
                        callback.onSuccess(scalarToken);
                    }
                }
            });
        } else {
            session.openIdToken(new ApiCallback<Map<Object, Object>>() {
                @Override
                public void onSuccess(Map<Object, Object> tokensMap) {
                    WidgetsRestClient widgetsRestClient = new WidgetsRestClient(new HomeServerConnectionConfig(Uri.parse(INTEGRATION_REST_URL)));

                    widgetsRestClient.register(tokensMap, new ApiCallback<Map<String, String>>() {
                        @Override
                        public void onSuccess(Map<String, String> response) {
                            String token = response.get("scalar_token");

                            if (null != token) {
                                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putString(preferenceKey, token);
                                editor.commit();
                            }

                            if (null != callback) {
                                callback.onSuccess(token);
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            if (null != callback) {
                                callback.onNetworkError(e);
                            }
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            if (null != callback) {
                                callback.onMatrixError(e);
                            }
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            if (null != callback) {
                                callback.onUnexpectedError(e);
                            }
                        }
                    });
                }

                @Override
                public void onNetworkError(Exception e) {
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });
        }
    }
}
