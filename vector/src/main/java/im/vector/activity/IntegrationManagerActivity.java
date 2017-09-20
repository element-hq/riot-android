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

package im.vector.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ClientCertRequest;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXChromeCall;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
import im.vector.widgets.WidgetsManager;

public class IntegrationManagerActivity extends RiotAppCompatActivity {
    private static final String LOG_TAG = IntegrationManagerActivity.class.getSimpleName();

    /**
     * the parameters
     */
    public static final String EXTRA_SESSION_ID = "EXTRA_SESSION_ID";
    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";
    public static final String EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID";
    public static final String EXTRA_SCREEN_ID = "EXTRA_SCREEN_ID";

    @BindView(R.id.integration_progress_layout)
    View mProgressLayout;

    @BindView(R.id.integration_webview)
    WebView mWebView;

    private MXSession mSession;
    private Room mRoom;
    private String mWidgetId;
    private String mScreenId;
    private String mScalarToken;

    private String mUrl;

    private IntegrationWebAppInterface mIntegrationWebAppInterface;

    private static final Map<String, Boolean> mSucceedResponse = new HashMap<String, Boolean>() {{
        put("success", true);
    }};

    // private class
    private class IntegrationWebAppInterface {
        IntegrationWebAppInterface() {
        }

        @JavascriptInterface
        public void onScalarEvent(String eventData) {
            org.matrix.androidsdk.util.Log.d(LOG_TAG, "WebView Message : " + eventData);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_integration_manager);
        ButterKnife.bind(this);

        Intent intent = getIntent();
        mSession = Matrix.getInstance(this).getSession(intent.getStringExtra(EXTRA_SESSION_ID));

        if (null == mSession) {
            Log.e(LOG_TAG, "## onCreate() : invalid session");
            finish();
            return;
        }

        mRoom = mSession.getDataHandler().getRoom(intent.getStringExtra(EXTRA_ROOM_ID));
        mWidgetId = intent.getStringExtra(EXTRA_WIDGET_ID);
        mScreenId = intent.getStringExtra(EXTRA_SCREEN_ID);

        mProgressLayout.setVisibility(View.VISIBLE);

        WidgetsManager.getSharedInstance().getScalarToken(this, mSession, new ApiCallback<String>() {
            @Override
            public void onSuccess(String scalarToken) {
                mScalarToken = scalarToken;
                mProgressLayout.setVisibility(View.GONE);
                launchUrl();

            }

            private void onError(String errorMessage) {
                CommonActivityUtils.displayToast(IntegrationManagerActivity.this, errorMessage);
                IntegrationManagerActivity.this.finish();
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }

    @SuppressLint("NewApi")
    private void launchUrl() {
        mUrl = getInterfaceUrl();

        if (null == mUrl) {
            this.finish();
            return;
        }

        mIntegrationWebAppInterface = new IntegrationWebAppInterface();
        mWebView.addJavascriptInterface(mIntegrationWebAppInterface, "Android");

        WebSettings settings = mWebView.getSettings();

        WebView.setWebContentsDebuggingEnabled(true);

        // Enable Javascript
        settings.setJavaScriptEnabled(true);

        // Use WideViewport and Zoom out if there is no viewport defined
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Enable pinch to zoom without the zoom buttons
        settings.setBuiltInZoomControls(true);

        // Allow use of Local Storage
        settings.setDomStorageEnabled(true);

        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        settings.setDisplayZoomControls(false);

        mWebView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                final String js = getJSCodeToInject(IntegrationManagerActivity.this);

                if (null != js) {
                    IntegrationManagerActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String allo = "javascript:" + js;
                            mWebView.loadUrl("javascript:" + js);
                        }
                    });
                    //
                }
            }

            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return shouldOverrideUrlLoading(view, request.getUrl().toString());
            }

            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                int a = 0;
                a++;
            }


            /**
             * Notify the host application that the WebView will load the resource
             * specified by the given url.
             *
             * @param view The WebView that is initiating the callback.
             * @param url The url of the resource the WebView will load.
             */
            public void onLoadResource(WebView view, String url) {
                int a = 0;
                a++;
            }

            public void onPageCommitVisible(WebView view, String url) {
                int a = 0;
                a++;
            }

            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              String url) {
                return null;
            }

            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              WebResourceRequest request) {
                return shouldInterceptRequest(view, request.getUrl().toString());
            }

            @Deprecated
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                int a = 0;
                a++;
            }

            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    onReceivedError(view,
                            error.getErrorCode(), error.getDescription().toString(),
                            request.getUrl().toString());
                }
            }

            public void onReceivedHttpError(
                    WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                int a = 0;
                a++;
            }

            /**
             * As the host application if the browser should resend data as the
             * requested page was a result of a POST. The default is to not resend the
             * data.
             *
             * @param view The WebView that is initiating the callback.
             * @param dontResend The message to send if the browser should not resend
             * @param resend The message to send if the browser should resend data
             */
            public void onFormResubmission(WebView view, Message dontResend,
                                           Message resend) {
                dontResend.sendToTarget();
            }

            /**
             * Notify the host application to update its visited links database.
             *
             * @param view The WebView that is initiating the callback.
             * @param url The url being visited.
             * @param isReload True if this url is being reloaded.
             */
            public void doUpdateVisitedHistory(WebView view, String url,
                                               boolean isReload) {
                int a = 0;
                a++;
            }

            /**
             * Notify the host application that an SSL error occurred while loading a
             * resource. The host application must call either handler.cancel() or
             * handler.proceed(). Note that the decision may be retained for use in
             * response to future SSL errors. The default behavior is to cancel the
             * load.
             *
             * @param view The WebView that is initiating the callback.
             * @param handler An SslErrorHandler object that will handle the user's
             *            response.
             * @param error The SSL error object.
             */
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           SslError error) {
                handler.cancel();
            }

            public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
                request.cancel();
            }

            public void onReceivedHttpAuthRequest(WebView view,
                                                  HttpAuthHandler handler, String host, String realm) {
                handler.cancel();
            }

            public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
                return false;
            }

        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
            cookieManager.setAcceptThirdPartyCookies(mWebView, true);
        }

        //mWebView.setLis

        mWebView.loadUrl(mUrl);
    }

    /**
     * Compute the integration URL
     *
     * @return the integration URL
     */
    private String getInterfaceUrl() {
        try {
            String url =
                    WidgetsManager.INTEGRATION_UI_URL + "?" +
                            "scalar_token=" + URLEncoder.encode(mScalarToken, "utf-8") + "&" +
                            "room_id=" + URLEncoder.encode(mRoom.getRoomId(), "utf-8");

            if (null != mScreenId) {
                url += "&screen=" + URLEncoder.encode(mScreenId, "utf-8");
            }

            if (null != mWidgetId) {
                url += "&integ_id=" + URLEncoder.encode(mWidgetId, "utf-8");
            }
            return url;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getInterfaceUrl() failed " + e.getMessage());
        }

        return null;
    }

    /**
     * Read the JS code to inject from the resource directory.
     *
     * @param context the context
     * @return the JS code to inject
     */
    private static String getJSCodeToInject(Context context) {
        String code = null;

        try {
            InputStream is = context.getAssets().open("integrationManager.js");
            final char[] buffer = new char[1024];
            final StringBuilder out = new StringBuilder();

            Reader in = new InputStreamReader(is, "UTF-8");
            for (; ; ) {
                int rsz = in.read(buffer, 0, buffer.length);
                if (rsz < 0)
                    break;
                out.append(buffer, 0, rsz);
            }
            code = out.toString();

            in.close();
            is.close();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getJSCodeToInject() failed : " + e.getMessage());
        }

        return code;
    }

    /**
     * Force to render the activity in fullscreen
     */
    private void displayInFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayInFullScreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            displayInFullScreen();
        }
    }
    
    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Manage the modular requests
     * @param JSData the js data request
     */
    private void onScalarMessage(Map<String, Map<String, Object>> JSData) {
        try {
            if (null == JSData) {
                Log.e(LOG_TAG, "## onScalarMessage() : invalid JSData");
                return;
            }

            Map<String, Object> eventData = JSData.get("event.data");

            if (null == eventData) {
                Log.e(LOG_TAG, "## onScalarMessage() : invalid JSData");
                return;
            }

            String roomIdInEvent = (String) eventData.get("room_id");
            String userId = (String) eventData.get("user_id");
            String action = (String) eventData.get("action");

            if (TextUtils.equals(action, "close_scalar")) {
                finish();
                return;
            }

            if (null == roomIdInEvent) {
                sendError(getString(R.string.widget_integration_missing_room_id), eventData);
                return;
            }

            if (!TextUtils.equals(roomIdInEvent, mRoom.getRoomId())) {
                sendError(getString(R.string.widget_integration_room_not_visible), eventData);
                return;
            }

            // These APIs don't require userId
            if (TextUtils.equals(action, "join_rules_state")) {
                getJoinRules(eventData);
                return;
            } else if (TextUtils.equals(action, "set_plumbing_state")) {
                setPlumbingState(eventData);
                return;
            } else if (TextUtils.equals(action, "get_membership_count")) {
                getMembershipCount(eventData);
                return;
            } else if (TextUtils.equals(action, "set_widget")) {
                setWidget(eventData);
                return;
            } else if (TextUtils.equals(action, "get_widgets")) {
                getWidgets(eventData);
                return;
            } else if (TextUtils.equals(action, "can_send_event")) {
                canSendEvent(eventData);
                return;
            }

            if (null == userId) {
                sendError(getString(R.string.widget_integration_missing_user_id), eventData);
                return;
            }

            if (TextUtils.equals(action, "membership_state")) {
                getMembershipState(userId, eventData);
            } else if (TextUtils.equals(action, "invite")) {
                inviteUser(userId, eventData);
            } else if (TextUtils.equals(action, "bot_options")) {
                getBotOptions(userId, eventData);
            } else if (TextUtils.equals(action, "set_bot_options")) {
                setBotOptions(userId, eventData);
            } else if (TextUtils.equals(action, "set_bot_power")) {
                setBotPower(userId, eventData);
            } else {
                Log.e(LOG_TAG, "## onScalarMessage() : Unhandled postMessage event with action " + action + " : " + JSData);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onScalarMessage() : failed " + e.getMessage());
            sendError(getString(R.string.widget_integration_failed_to_send_request), JSData);
        }
    }

    /*
     * *********************************************************************************************
     * Message sending methods
     * *********************************************************************************************
     */

    private void sendResponse(String jsString, Map<String, Object> eventData) {
        // TODO
    }

    /**
     * Send a boolean response
     * @param response the response
     * @param eventData the modular data
     */
    private void sendBoolResponse(boolean response, Map<String, Object> eventData) {
        sendResponse(response ? "true" : "false", eventData);
    }

    /**
     * Send an integer response
     * @param response the response
     * @param eventData the modular data
     */
    private void sendIntegerResponse(int response, Map<String, Object> eventData) {
        sendResponse(response + "", eventData);
    }

    /**
     * Send an object response
     * @param response the response
     * @param eventData the modular data
     */
    private void sendObjectResponse(Object response, Map<String, Object> eventData) {
        String jsString = null;

        if (null != response) {
            try {
                jsString = "JSON.parse('" + JsonUtils.getGson(true).toJson(response) + "')";
            } catch (Exception e) {
                Log.e(LOG_TAG, "## sendObjectResponse() : toJson failed " + e.getMessage());
            }
        }

        if (null == jsString) {
            jsString = "null";
        }

        sendResponse(jsString, eventData);
    }

    /**
     * Send an error
     * @param message the error message
     * @param eventData the modular data
     */
    private void sendError(String message, Map<String, Object> eventData) {
        Log.e(LOG_TAG, "## sendError() : eventData " + eventData + " failed " + message);

        // TODO: JS has an additional optional parameter: nestedError
        Map<String, Map<String, String>> params = new HashMap<>();
        Map<String, String> subMap = new HashMap<>();
        subMap.put("message", message);
        params.put("error", subMap);

        sendObjectResponse(params, eventData);
    }

    /**
     * Convert an object to a map
     *
     * @param object the object to convert
     * @return the event as a map
     */
    private static Map<String, Object> getObjectAsJsonMap(Object object) {
        Gson gson = JsonUtils.getGson(false);
        Map<String, Object> objectAsMap = null;

        try {
            String stringifiedEvent = gson.toJson(object);
            objectAsMap = gson.fromJson(stringifiedEvent, new TypeToken<HashMap<String, Object>>() {
            }.getType());
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getObjectAsJsonMap() failed " + e.getMessage());
        }

        return objectAsMap;
    }

    /**
     * Send an object as a JSON map
     * @param object the object to send
     * @param eventData the modular data
     */
    private void sendObjectAsJsonMap(Object object, Map<String, Object> eventData) {
        Map<String, Object> ObjectAsMap = getObjectAsJsonMap(object);

        if (null != ObjectAsMap) {
            sendObjectResponse(ObjectAsMap, eventData);
        } else {
            sendError(getString(R.string.widget_integration_failed_to_send_request), eventData);
        }
    }

    /*
     * *********************************************************************************************
     * Modular postMessage methods
     * *********************************************************************************************
     */

    /**
     * Api callbacks
     *
     * @param <T> the callback type
     */
    public class IntegrationManagerApiCallback<T> implements ApiCallback<T> {
        String mDescription;
        Map<String, Object> mEventData;

        public IntegrationManagerApiCallback(final Map<String, Object> eventData, String description) {
            mDescription = description;
            mEventData = eventData;
        }

        @Override
        public void onSuccess(T info) {
            sendObjectResponse(mSucceedResponse, mEventData);
        }

        private void onError(String error) {
            Log.e(LOG_TAG, mDescription + " failed " + error);
            sendError(getString(R.string.widget_integration_failed_to_send_request), mEventData);
        }

        @Override
        public void onNetworkError(Exception e) {
            onError(e.getMessage());
        }

        @Override
        public void onMatrixError(MatrixError e) {
            onError(e.getMessage());
        }

        @Override
        public void onUnexpectedError(Exception e) {
            onError(e.getMessage());
        }
    }

    /**
     * Invite an user to this room
     *
     * @param userId    the user id
     * @param eventData the modular data
     */
    private void inviteUser(final String userId, final Map<String, Object> eventData) {
        Log.d(LOG_TAG, "Received request to invite " + userId + " into room " + mRoom.getRoomId());

        RoomMember member = mRoom.getMember(userId);

        if ((null != member) && TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
            sendObjectResponse(mSucceedResponse, eventData);
        } else {
            mRoom.invite(userId, new IntegrationManagerApiCallback<Void>(eventData, "## inviteUser()"));
        }
    }

    /**
     * Set a new widget
     *
     * @param eventData the modular data
     */
    private void setWidget(final Map<String, Object> eventData) {
        Log.d(LOG_TAG, "Received request to set widget in room " + mRoom.getRoomId());

        String widget_id, widgetType, widgetUrl;
        String widgetName; // optional
        Map<Object, Object> widgetData; // optional

        widget_id = (String) eventData.get("widget_id");
        widgetType = (String) eventData.get("type");
        widgetUrl = (String) eventData.get("url");
        widgetName = (String) eventData.get("name");
        widgetData = (Map<Object, Object>) eventData.get("data");

        if (null == widget_id) {
            sendError(getString(R.string.widget_integration_unable_to_create), eventData);
            return;
        }

        Map<String, Object> widgetEventContent = new HashMap<>();

        if (null != widgetUrl) {
            if (null == widgetType) {
                sendError(getString(R.string.widget_integration_unable_to_create), eventData);
                return;
            }

            if (null != widgetType) {
                widgetEventContent.put("type", widgetType);
            }

            widgetEventContent.put("url", widgetUrl);

            if (null != widgetName) {
                widgetEventContent.put("name", widgetName);
            }

            if (null != widgetData) {
                widgetEventContent.put("data", widgetData);
            }
        }

        mSession.getRoomsApiClient().sendStateEvent(mRoom.getRoomId(), WidgetsManager.WIDGET_EVENT_TYPE, widget_id, widgetEventContent, new IntegrationManagerApiCallback<Void>(eventData, "## setWidget()"));
    }

    /**
     * Provide the widgets list
     *
     * @param eventData the modular data
     */
    private void getWidgets(Map<String, Object> eventData) {
        List<Event> widgetEvents = WidgetsManager.getSharedInstance().getWidgetEvents(mRoom);
        List<Map<String, Object>> responseData = new ArrayList<>();
        Gson gson = JsonUtils.getGson(false);

        for (Event widgetEvent : widgetEvents) {
            Map<String, Object> map = getObjectAsJsonMap(widgetEvent);

            if (null != map) {
                responseData.add(map);
            }
        }

        sendObjectResponse(responseData, eventData);
    }

    /**
     * Check if the user can send an event of predefined type
     *
     * @param eventData the modular data
     */
    private void canSendEvent(Map<String, Object> eventData) {
        RoomMember member = mRoom.getLiveState().getMember(mSession.getMyUserId());

        if ((null == member) || !TextUtils.equals(RoomMember.MEMBERSHIP_JOIN, member.membership)) {
            sendError(getString(R.string.widget_integration_must_be_in_room), eventData);
            return;
        }

        String eventType = (String) eventData.get("event_type");
        boolean isState = (boolean) eventData.get("is_state");

        PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

        int userPowerLevel = powerLevels.getUserPowerLevel(mSession.getMyUserId());

        boolean canSend;

        if (isState) {
            canSend = (userPowerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(eventType));
        } else {
            canSend = (userPowerLevel >= powerLevels.minimumPowerLevelForSendingEventAsMessage(eventType));
        }

        if (canSend) {
            sendBoolResponse(true, eventData);
        } else {
            sendError(getString(R.string.widget_integration_no_permission_in_room), eventData);
        }
    }

    /**
     * Provides the membership state
     *
     * @param userId    the user id
     * @param eventData the modular data
     */
    private void getMembershipState(String userId, Map<String, Object> eventData) {
        Log.d(LOG_TAG, "membership_state of " + userId + " in room " + mRoom.getRoomId() + " requested");
        sendObjectAsJsonMap(mRoom.getMember(mSession.getMyUserId()), eventData);
    }

    private void getJoinRules(Map<String, Object> eventData) {
        List<Event> joinedEvents = mRoom.getLiveState().getStateEvents(new HashSet<>(Arrays.asList(Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES)));

        if (joinedEvents.size() > 0) {
            sendObjectAsJsonMap(joinedEvents.get(joinedEvents.size() - 1), eventData);
        } else {
            sendError(getString(R.string.widget_integration_failed_to_send_request), eventData);
        }
    }

    /**
     * Update the 'plumbing state"
     *
     * @param eventData the modular data
     */
    private void setPlumbingState(final Map<String, Object> eventData) {
        Log.d(LOG_TAG, "Received request to set plumbing state to status " + eventData.get("status") + " in room " + mRoom.getRoomId() + " requested");

        String status = (String) eventData.get("status");

        Map<String, Object> params = new HashMap<>();
        params.put("status", status);

        mSession.getRoomsApiClient().sendStateEvent(mRoom.getRoomId(), Event.EVENT_TYPE_ROOM_PLUMBING, null, params, new IntegrationManagerApiCallback<Void>(eventData, "## setPlumbingState()"));
    }

    /**
     * Retrieve the latest botOptions event
     *
     * @param userId    the userID
     * @param eventData the modular data
     */
    private void getBotOptions(String userId, final Map<String, Object> eventData) {
        Log.d(LOG_TAG, "Received request to get options for bot " + userId + " in room " + mRoom.getRoomId() + " requested");

        List<Event> stateEvents = mRoom.getLiveState().getStateEvents(new HashSet<>(Arrays.asList(Event.EVENT_TYPE_ROOM_BOT_OPTIONS)));

        Event botOptionsEvent = null;
        String stateKey = "_" + userId;

        for (Event stateEvent : stateEvents) {
            if (TextUtils.equals(stateEvent.stateKey, stateKey)) {
                if ((null == botOptionsEvent) || (stateEvent.getAge() > botOptionsEvent.getAge())) {
                    botOptionsEvent = stateEvent;
                }
            }
        }

        if (null != botOptionsEvent) {
            sendObjectAsJsonMap(botOptionsEvent, eventData);
        } else {
            sendObjectResponse(null, eventData);
        }
    }

    /**
     * Update the bot options
     *
     * @param userId    the userID
     * @param eventData the modular data
     */
    private void setBotOptions(String userId, final Map<String, Object> eventData) {
        Log.d(LOG_TAG, "Received request to set options for bot " + userId + " in room " + mRoom.getRoomId());

        Map<String, Object> content = (Map<String, Object>) eventData.get("content");
        String stateKey = "_" + userId;

        mSession.getRoomsApiClient().sendStateEvent(mRoom.getRoomId(), Event.EVENT_TYPE_ROOM_BOT_OPTIONS, stateKey, content, new IntegrationManagerApiCallback<Void>(eventData, "## setBotOptions()"));
    }

    /**
     * Update the bot power levels
     *
     * @param userId    the userID
     * @param eventData the modular data
     */
    private void setBotPower(final String userId, final Map<String, Object> eventData) {
        Log.d(LOG_TAG, "Received request to set power level to " + eventData.get("level") + " for bot " + userId + " in room " + mRoom.getRoomId());

        int level = (int) eventData.get("level");

        if (level >= 0) {
            mRoom.updateUserPowerLevels(userId, level, new IntegrationManagerApiCallback<Void>(eventData, "## setBotPower()"));
        } else {
            Log.e(LOG_TAG, "## setBotPower() : Power level must be positive integer.");
            sendError(getString(R.string.widget_integration_positive_power_level), eventData);
        }
    }

    /**
     * Provides the number of members in the rooms
     *
     * @param eventData the modular data
     */
    private void getMembershipCount(final Map<String, Object> eventData) {
        sendIntegerResponse(mRoom.getJoinedMembers().size(), eventData);
    }
}