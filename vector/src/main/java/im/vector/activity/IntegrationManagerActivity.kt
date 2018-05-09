/*
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

package im.vector.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

import com.google.gson.reflect.TypeToken

import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.RoomMember
import org.matrix.androidsdk.util.JsonUtils
import org.matrix.androidsdk.util.Log

import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.HashSet

import butterknife.BindView
import butterknife.ButterKnife
import im.vector.Matrix
import im.vector.R
import im.vector.widgets.WidgetsManager

class IntegrationManagerActivity : RiotAppCompatActivity() {

    @BindView(R.id.integration_webview)
    lateinit var mWebView: WebView

    // parameters
    private var mSession: MXSession? = null
    private var mRoom: Room? = null
    private var mWidgetId: String? = null
    private var mScreenId: String? = null
    private var mScalarToken: String? = null

    /**
     * Compute the integration URL
     *
     * @return the integration URL
     */
    private fun buildInterfaceUrl(): String? {
        try {
            var url = WidgetsManager.INTEGRATION_UI_URL + "?" +
                    "scalar_token=" + URLEncoder.encode(mScalarToken, "utf-8") + "&" +
                    "room_id=" + URLEncoder.encode(mRoom!!.roomId, "utf-8")

            if (null != mScreenId) {
                url += "&screen=" + URLEncoder.encode(mScreenId, "utf-8")
            }

            if (null != mWidgetId) {
                url += "&integ_id=" + URLEncoder.encode(mWidgetId, "utf-8")
            }
            return url
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## buildInterfaceUrl() failed " + e.message)
        }

        return null
    }

    // private class
    private inner class IntegrationWebAppInterface internal constructor() {

        @JavascriptInterface
        fun onScalarEvent(eventData: String) {
            val gson = JsonUtils.getGson(false)
            val objectAsMap: Map<String, Map<String, Any>>

            try {
                objectAsMap = gson.fromJson(eventData, object : TypeToken<Map<String, Map<String, Any>>>() {

                }.type)
                runOnUiThread {
                    Log.d(LOG_TAG, "onScalarEvent : $objectAsMap")
                    onScalarMessage(objectAsMap)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "## onScalarEvent() failed " + e.message)
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_integration_manager)
        ButterKnife.bind(this)

        waitingView = findViewById(R.id.integration_progress_layout)

        val intent = intent
        mSession = Matrix.getInstance(this)!!.getSession(intent.getStringExtra(EXTRA_MATRIX_ID))

        if (null == mSession || !mSession!!.isAlive) {
            Log.e(LOG_TAG, "## onCreate() : invalid session")
            finish()
            return
        }

        mRoom = mSession!!.dataHandler.getRoom(intent.getStringExtra(EXTRA_ROOM_ID))
        mWidgetId = intent.getStringExtra(EXTRA_WIDGET_ID)
        mScreenId = intent.getStringExtra(EXTRA_SCREEN_ID)

        showWaitingView()

        WidgetsManager.getScalarToken(this, mSession!!, object : ApiCallback<String> {
            override fun onSuccess(scalarToken: String) {
                mScalarToken = scalarToken
                hideWaitingView()
                launchUrl()
            }

            private fun onError(errorMessage: String) {
                CommonActivityUtils.displayToast(this@IntegrationManagerActivity, errorMessage)
                finish()
            }

            override fun onNetworkError(e: Exception) {
                onError(e.localizedMessage)
            }

            override fun onMatrixError(e: MatrixError) {
                onError(e.localizedMessage)
            }

            override fun onUnexpectedError(e: Exception) {
                onError(e.localizedMessage)
            }
        })
    }

    @SuppressLint("NewApi")
    private fun launchUrl() {
        val url = buildInterfaceUrl()

        if (null == url) {
            finish()
            return
        }

        mWebView.let {
            it.addJavascriptInterface(IntegrationWebAppInterface(), "Android")

            // Permission requests
            it.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread { request.grant(request.resources) }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.e(LOG_TAG, "## onConsoleMessage() : " + consoleMessage.message() + " line " + consoleMessage.lineNumber() + " source Id" + consoleMessage.sourceId())
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            it.settings.let {
                // Enable Javascript
                it.javaScriptEnabled = true

                // Use WideViewport and Zoom out if there is no viewport defined
                it.useWideViewPort = true
                it.loadWithOverviewMode = true

                // Enable pinch to zoom without the zoom buttons
                it.builtInZoomControls = true

                // Allow use of Local Storage
                it.domStorageEnabled = true

                it.allowFileAccessFromFileURLs = true
                it.allowUniversalAccessFromFileURLs = true

                it.displayZoomControls = false
            }

            it.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val js = getJSCodeToInject(this@IntegrationManagerActivity)

                    if (null != js) {
                        runOnUiThread { mWebView.loadUrl("javascript:$js") }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptThirdPartyCookies(mWebView, true)
            }

            it.loadUrl(url)
        }
    }

    override fun displayInFullscreen() = true

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Manage the modular requests
     *
     * @param JSData the js data request
     */
    private fun onScalarMessage(JSData: Map<String, Map<String, Any>>?) {
        if (null == JSData) {
            Log.e(LOG_TAG, "## onScalarMessage() : invalid JSData")
            return
        }

        val eventData = JSData["event.data"]

        if (null == eventData) {
            Log.e(LOG_TAG, "## onScalarMessage() : invalid JSData")
            return
        }

        try {
            val roomIdInEvent = eventData["room_id"] as String?
            val userId = eventData["user_id"] as String?
            val action = eventData["action"] as String?

            when {
                TextUtils.equals(action, "close_scalar") -> finish()

            // other APIs requires a roomId
                null == roomIdInEvent -> sendError(getString(R.string.widget_integration_missing_room_id), eventData)

            // Room ids must match
                !TextUtils.equals(roomIdInEvent, mRoom!!.roomId) -> sendError(getString(R.string.widget_integration_room_not_visible), eventData)

            // These APIs don't require userId
                TextUtils.equals(action, "join_rules_state") -> getJoinRules(eventData)
                TextUtils.equals(action, "set_plumbing_state") -> setPlumbingState(eventData)
                TextUtils.equals(action, "get_membership_count") -> getMembershipCount(eventData)
                TextUtils.equals(action, "set_widget") -> setWidget(eventData)
                TextUtils.equals(action, "get_widgets") -> getWidgets(eventData)
                TextUtils.equals(action, "can_send_event") -> canSendEvent(eventData)
            // For the next APIs, a userId is required
                null == userId -> sendError(getString(R.string.widget_integration_missing_user_id), eventData)
                TextUtils.equals(action, "membership_state") -> getMembershipState(userId, eventData)
                TextUtils.equals(action, "invite") -> inviteUser(userId, eventData)
                TextUtils.equals(action, "bot_options") -> getBotOptions(userId, eventData)
                TextUtils.equals(action, "set_bot_options") -> setBotOptions(userId, eventData)
                TextUtils.equals(action, "set_bot_power") -> setBotPower(userId, eventData)
                else -> Log.e(LOG_TAG, "## onScalarMessage() : Unhandled postMessage event with action $action : $JSData")
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "## onScalarMessage() : failed " + e.message)
            sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
        }
    }

    /*
     * *********************************************************************************************
     * Message sending methods
     * *********************************************************************************************
     */

    /**
     * Send the response to the javascript
     *
     * @param jsString  the response data
     * @param eventData the modular data
     */
    private fun sendResponse(jsString: String, eventData: Map<String, Any>) {
        try {
            val functionLine = "sendResponseFromRiotAndroid('" + eventData["_id"] + "' , " + jsString + ");"

            // call the javascript method
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                mWebView.loadUrl("javascript:$functionLine")
            } else {
                mWebView.evaluateJavascript(functionLine, null)
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## sendResponse() failed " + e.message)
        }
    }

    /**
     * Send a boolean response
     *
     * @param response  the response
     * @param eventData the modular data
     */
    private fun sendBoolResponse(response: Boolean, eventData: Map<String, Any>) {
        sendResponse(if (response) "true" else "false", eventData)
    }

    /**
     * Send an integer response
     *
     * @param response  the response
     * @param eventData the modular data
     */
    private fun sendIntegerResponse(response: Int, eventData: Map<String, Any>) {
        sendResponse(response.toString() + "", eventData)
    }

    /**
     * Send an object response
     *
     * @param response  the response
     * @param eventData the modular data
     */
    private fun sendObjectResponse(response: Any?, eventData: Map<String, Any>) {
        var jsString: String? = null

        if (null != response) {
            try {
                jsString = "JSON.parse('" + JsonUtils.getGson(false).toJson(response) + "')"
            } catch (e: Exception) {
                Log.e(LOG_TAG, "## sendObjectResponse() : toJson failed " + e.message)
            }

        }

        if (null == jsString) {
            jsString = "null"
        }

        sendResponse(jsString, eventData)
    }

    /**
     * Send an error
     *
     * @param message   the error message
     * @param eventData the modular data
     */
    private fun sendError(message: String, eventData: Map<String, Any>) {
        Log.e(LOG_TAG, "## sendError() : eventData $eventData failed $message")

        // TODO: JS has an additional optional parameter: nestedError
        val params = HashMap<String, Map<String, String>>()
        val subMap = HashMap<String, String>()
        subMap["message"] = message
        params["error"] = subMap

        sendObjectResponse(params, eventData)
    }

    /**
     * Send an object as a JSON map
     *
     * @param object    the object to send
     * @param eventData the modular data
     */
    private fun sendObjectAsJsonMap(`object`: Any, eventData: Map<String, Any>) {
        sendObjectResponse(getObjectAsJsonMap(`object`), eventData)
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
    </T> */
    inner class IntegrationManagerApiCallback<T>(internal val mEventData: Map<String, Any>, internal val mDescription: String) : ApiCallback<T> {

        override fun onSuccess(info: T) {
            Log.d(LOG_TAG, "$mDescription succeeds")
            sendObjectResponse(HashMap(mSucceedResponse), mEventData)
        }

        private fun onError(error: String) {
            Log.e(LOG_TAG, "$mDescription failed with error $error")
            sendError(getString(R.string.widget_integration_failed_to_send_request), mEventData)
        }

        override fun onNetworkError(e: Exception) {
            onError(e.message!!)
        }

        override fun onMatrixError(e: MatrixError) {
            onError(e.message)
        }

        override fun onUnexpectedError(e: Exception) {
            onError(e.message!!)
        }
    }

    /**
     * Invite an user to this room
     *
     * @param userId    the user id
     * @param eventData the modular data
     */
    private fun inviteUser(userId: String, eventData: Map<String, Any>) {
        val description = "Received request to invite " + userId + " into room " + mRoom!!.roomId

        Log.d(LOG_TAG, description)

        val member = mRoom!!.getMember(userId)

        if (null != member && TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
            sendObjectResponse(HashMap(mSucceedResponse), eventData)
        } else {
            mRoom!!.invite(userId, IntegrationManagerApiCallback(eventData, description))
        }
    }

    /**
     * Set a new widget
     *
     * @param eventData the modular data
     */
    private fun setWidget(eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "Received request to set widget in room " + mRoom!!.roomId)

        val widgetId = eventData["widget_id"] as String?
        val widgetType = eventData["type"] as String?
        val widgetUrl = eventData["url"] as String?

        // optional
        val widgetName = eventData["name"] as String?
        // optional
        val widgetData = eventData["data"] as Map<Any, Any>?

        if (null == widgetId) {
            sendError(getString(R.string.widget_integration_unable_to_create), eventData)
            return
        }

        val widgetEventContent = HashMap<String, Any>()

        if (null != widgetUrl) {
            if (null == widgetType) {
                sendError(getString(R.string.widget_integration_unable_to_create), eventData)
                return
            }

            widgetEventContent["type"] = widgetType
            widgetEventContent["url"] = widgetUrl

            if (null != widgetName) {
                widgetEventContent["name"] = widgetName
            }

            if (null != widgetData) {
                widgetEventContent["data"] = widgetData
            }
        }

        mSession!!.roomsApiClient.sendStateEvent(mRoom!!.roomId,
                WidgetsManager.WIDGET_EVENT_TYPE,
                widgetId,
                widgetEventContent,
                IntegrationManagerApiCallback(eventData, "## setWidget()"))
    }

    /**
     * Provide the widgets list
     *
     * @param eventData the modular data
     */
    private fun getWidgets(eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "Received request to get widget in room " + mRoom!!.roomId)

        val widgets = WidgetsManager.getSharedInstance().getActiveWidgets(mSession, mRoom)
        val responseData = ArrayList<Map<String, Any>>()

        for (widget in widgets) {
            val map = getObjectAsJsonMap(widget.widgetEvent)

            if (null != map) {
                responseData.add(map)
            }
        }

        Log.d(LOG_TAG, "## getWidgets() returns $responseData")

        sendObjectResponse(responseData, eventData)
    }

    /**
     * Check if the user can send an event of predefined type
     *
     * @param eventData the modular data
     */
    private fun canSendEvent(eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "Received request canSendEvent in room " + mRoom!!.roomId)

        val member = mRoom!!.liveState.getMember(mSession!!.myUserId)

        if (null == member || !TextUtils.equals(RoomMember.MEMBERSHIP_JOIN, member.membership)) {
            sendError(getString(R.string.widget_integration_must_be_in_room), eventData)
            return
        }

        val eventType = eventData["event_type"] as String
        val isState = eventData["is_state"] as Boolean

        Log.d(LOG_TAG, "## canSendEvent() : eventType $eventType isState $isState")

        val powerLevels = mRoom!!.liveState.powerLevels

        val userPowerLevel = powerLevels!!.getUserPowerLevel(mSession!!.myUserId)

        val canSend = if (isState) {
            userPowerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(eventType)
        } else {
            userPowerLevel >= powerLevels.minimumPowerLevelForSendingEventAsMessage(eventType)
        }

        if (canSend) {
            Log.d(LOG_TAG, "## canSendEvent() returns true")
            sendBoolResponse(true, eventData)
        } else {
            Log.d(LOG_TAG, "## canSendEvent() returns widget_integration_no_permission_in_room")
            sendError(getString(R.string.widget_integration_no_permission_in_room), eventData)
        }
    }

    /**
     * Provides the membership state
     *
     * @param userId    the user id
     * @param eventData the modular data
     */
    private fun getMembershipState(userId: String, eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " requested")

        mRoom!!.getMemberEvent(userId, object : ApiCallback<Event> {
            override fun onSuccess(event: Event?) {
                Log.d(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " returns " + event)

                if (null != event) {
                    sendObjectAsJsonMap(event.content, eventData)
                } else {
                    sendObjectResponse(null, eventData)
                }
            }

            override fun onNetworkError(e: Exception) {
                Log.e(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " failed " + e.message)
                sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
            }

            override fun onMatrixError(e: MatrixError) {
                Log.e(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " failed " + e.message)
                sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
            }

            override fun onUnexpectedError(e: Exception) {
                Log.e(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " failed " + e.message)
                sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
            }
        })
    }

    /**
     * Request the latest joined room event
     *
     * @param eventData the modular data
     */
    private fun getJoinRules(eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "Received request join rules  in room " + mRoom!!.roomId)
        val joinedEvents = mRoom!!.liveState.getStateEvents(HashSet(Arrays.asList(Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES)))

        if (joinedEvents.size > 0) {
            Log.d(LOG_TAG, "Received request join rules returns " + joinedEvents[joinedEvents.size - 1])
            sendObjectAsJsonMap(joinedEvents[joinedEvents.size - 1], eventData)
        } else {
            Log.e(LOG_TAG, "Received request join rules failed widget_integration_failed_to_send_request")
            sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
        }
    }

    /**
     * Update the 'plumbing state"
     *
     * @param eventData the modular data
     */
    private fun setPlumbingState(eventData: Map<String, Any>) {
        val description = "Received request to set plumbing state to status " + eventData["status"] + " in room " + mRoom!!.roomId + " requested"
        Log.d(LOG_TAG, description)

        val status = eventData["status"] as String

        val params = HashMap<String, Any>()
        params["status"] = status

        mSession!!.roomsApiClient.sendStateEvent(mRoom!!.roomId,
                Event.EVENT_TYPE_ROOM_PLUMBING,
                null,
                params,
                IntegrationManagerApiCallback(eventData, description))
    }

    /**
     * Retrieve the latest botOptions event
     *
     * @param userId    the userID
     * @param eventData the modular data
     */
    private fun getBotOptions(userId: String, eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "Received request to get options for bot " + userId + " in room " + mRoom!!.roomId + " requested")

        val stateEvents = mRoom!!.liveState.getStateEvents(HashSet(Arrays.asList(Event.EVENT_TYPE_ROOM_BOT_OPTIONS)))

        var botOptionsEvent: Event? = null
        val stateKey = "_$userId"

        for (stateEvent in stateEvents) {
            if (TextUtils.equals(stateEvent.stateKey, stateKey)) {
                if (null == botOptionsEvent || stateEvent.getAge() > botOptionsEvent.getAge()) {
                    botOptionsEvent = stateEvent
                }
            }
        }

        if (null != botOptionsEvent) {
            Log.d(LOG_TAG, "Received request to get options for bot $userId returns $botOptionsEvent")
            sendObjectAsJsonMap(botOptionsEvent, eventData)
        } else {
            Log.d(LOG_TAG, "Received request to get options for bot $userId returns null")
            sendObjectResponse(null, eventData)
        }
    }

    /**
     * Update the bot options
     *
     * @param userId    the userID
     * @param eventData the modular data
     */
    private fun setBotOptions(userId: String, eventData: Map<String, Any>) {
        val description = "Received request to set options for bot " + userId + " in room " + mRoom!!.roomId
        Log.d(LOG_TAG, description)

        val content = eventData["content"] as Map<String, Any>
        val stateKey = "_$userId"

        mSession!!.roomsApiClient.sendStateEvent(mRoom!!.roomId,
                Event.EVENT_TYPE_ROOM_BOT_OPTIONS,
                stateKey,
                content,
                IntegrationManagerApiCallback(eventData, description))
    }

    /**
     * Update the bot power levels
     *
     * @param userId    the userID
     * @param eventData the modular data
     */
    private fun setBotPower(userId: String, eventData: Map<String, Any>) {
        val description = "Received request to set power level to " + eventData["level"] + " for bot " + userId + " in room " + mRoom!!.roomId

        Log.d(LOG_TAG, description)

        val level = eventData["level"] as Int

        if (level >= 0) {
            mRoom!!.updateUserPowerLevels(userId, level, IntegrationManagerApiCallback(eventData, description))
        } else {
            Log.e(LOG_TAG, "## setBotPower() : Power level must be positive integer.")
            sendError(getString(R.string.widget_integration_positive_power_level), eventData)
        }
    }

    /**
     * Provides the number of members in the rooms
     *
     * @param eventData the modular data
     */
    private fun getMembershipCount(eventData: Map<String, Any>) {
        sendIntegerResponse(mRoom!!.joinedMembers.size, eventData)
    }

    companion object {
        private val LOG_TAG = IntegrationManagerActivity::class.java.simpleName

        /**
         * the parameters
         */
        private const val EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID"
        private const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"
        private const val EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID"
        private const val EXTRA_SCREEN_ID = "EXTRA_SCREEN_ID"

        fun getIntent(context: Context, matrixId: String, roomId: String): Intent {
            return Intent(context, IntegrationManagerActivity::class.java)
                    .apply {
                        putExtra(EXTRA_MATRIX_ID, matrixId)
                        putExtra(EXTRA_ROOM_ID, roomId)
                    }
        }

        // success result
        // must be copied else the conversion to string does not work
        private val mSucceedResponse = HashMap<String, Boolean>().apply {
            put("success", true)
        }

        /**
         * Read the JS code to inject from the resource directory.
         *
         * @param context the context
         * @return the JS code to inject
         */
        private fun getJSCodeToInject(context: Context): String? {
            var code: String? = null

            try {
                val inputStream = context.assets.open("integrationManager.js")
                val buffer = CharArray(1024)
                val out = StringBuilder()

                val inputStreamReader = InputStreamReader(inputStream, "UTF-8")
                while (true) {
                    val rsz = inputStreamReader.read(buffer, 0, buffer.size)
                    if (rsz < 0)
                        break
                    out.append(buffer, 0, rsz)
                }
                code = out.toString()

                inputStreamReader.close()
                inputStream.close()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "## getJSCodeToInject() failed : " + e.message)
            }

            return code
        }

        /**
         * Convert an object to a map
         *
         * @param `object` the object to convert
         * @return the event as a map
         */
        private fun getObjectAsJsonMap(any: Any): Map<String, Any>? {
            val gson = JsonUtils.getGson(false)
            var objectAsMap: Map<String, Any>? = null

            try {
                val stringifiedEvent = gson.toJson(any)
                objectAsMap = gson.fromJson<Map<String, Any>>(stringifiedEvent, object : TypeToken<HashMap<String, Any>>() {

                }.type)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "## getObjectAsJsonMap() failed " + e.message)
            }

            return objectAsMap
        }
    }
}