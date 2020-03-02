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

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import androidx.annotation.CallSuper
import im.vector.R
import im.vector.extensions.appendParamToUrl
import im.vector.types.JsonDict
import im.vector.util.toJsonMap
import im.vector.widgets.WidgetsManager
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.RoomMember
import java.util.*

class IntegrationManagerActivity : AbstractWidgetActivity() {

    /* ==========================================================================================
     * parameters
     * ========================================================================================== */

    private var mWidgetId: String? = null
    private var mScreenId: String? = null

    override fun getLayoutRes() = R.layout.activity_integration_manager

    /* ==========================================================================================
     * LIFECYCLE
     * ========================================================================================== */

    @CallSuper
    override fun initUiAndData() {
        mWidgetId = intent.getStringExtra(EXTRA_WIDGET_ID)
        mScreenId = intent.getStringExtra(EXTRA_SCREEN_ID)

        waitingView = findViewById(R.id.integration_progress_layout)

        super.initUiAndData()

        // Some widgets need popup to be enabled
        mWebView.settings.javaScriptCanOpenWindowsAutomatically = true
    }

    /* ==========================================================================================
     * IMPLEMENTS METHOD
     * ========================================================================================== */

    override fun canScalarTokenBeProvided() = true

    /**
     * Compute the integration URL
     *
     * @return the integration URL
     */
    override fun buildInterfaceUrl(scalarToken: String?): String? {
        try {
            return StringBuilder(widgetManager.uiUrl)
                    .apply {
                        scalarToken?.let {
                            appendParamToUrl("scalar_token", it)
                        }
                        mWidgetId?.let {
                            appendParamToUrl("integ_id", it)
                        }
                        mScreenId?.let {
                            appendParamToUrl("screen", it)
                        }
                    }
                    .appendParamToUrl("room_id", mRoom!!.roomId)
                    .toString()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## buildInterfaceUrl() failed " + e.message, e)
        }

        return null
    }

    /**
     * A Widget message has been received, deals with it and send the response
     */
    override fun dealsWithWidgetRequest(eventData: JsonDict<Any>): Boolean {
        val action = eventData["action"] as String?

        // Do something depending on action (please add new actions following alphabetical order)
        // FTR: some documentation can be found here:
        // https://github.com/matrix-org/matrix-react-sdk/blob/master/src/ScalarMessaging.js#L1-L233
        when (action) {
            "bot_options" -> getBotOptions(eventData)
                    .also { return true }
            "can_send_event" -> canSendEvent(eventData)
                    .also { return true }
            "close_scalar" -> finish()
                    .also { return true }
            "get_membership_count" -> getMembershipCount(eventData)
                    .also { return true }
            "get_widgets" -> getWidgets(eventData)
                    .also { return true }
            "invite" -> inviteUser(eventData)
                    .also { return true }
            "join_rules_state" -> getJoinRules(eventData)
                    .also { return true }
            "membership_state" -> getMembershipState(eventData)
                    .also { return true }
            "set_bot_options" -> setBotOptions(eventData)
                    .also { return true }
            "set_bot_power" -> setBotPower(eventData)
                    .also { return true }
            "set_plumbing_state" -> setPlumbingState(eventData)
                    .also { return true }
            "set_widget" -> setWidget(eventData)
                    .also { return true }
        }

        return super.dealsWithWidgetRequest(eventData)
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /*
     * *********************************************************************************************
     * Modular postMessage methods
     * *********************************************************************************************
     */

    /**
     * Invite an user to this room
     *
     * @param eventData the modular data
     */
    private fun inviteUser(eventData: JsonDict<Any>) {
        if (checkRoomId(eventData) || checkUserId(eventData)) {
            return
        }

        val userId = eventData["user_id"] as String

        val description = "Received request to invite " + userId + " into room " + mRoom!!.roomId

        Log.d(LOG_TAG, description)

        // FIXME LazyLoading. We cannot rely on getMember nullity anymore
        val member = mRoom!!.getMember(userId)

        if (null != member && TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
            sendSuccess(eventData)
        } else {
            mRoom!!.invite(mSession, userId, WidgetApiCallback(eventData, description))
        }
    }

    /**
     * Set a new widget
     *
     * @param eventData the modular data
     */
    private fun setWidget(eventData: JsonDict<Any>) {
        val userWidget = eventData["userWidget"] as Boolean?

        if (userWidget == true) {
            Log.d(LOG_TAG, "Received request to set widget for user")
        } else {
            if (checkRoomId(eventData)) {
                return
            }

            Log.d(LOG_TAG, "Received request to set widget in room " + mRoom!!.roomId)
        }

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

        if (userWidget == true) {
            val addUserWidgetBody = HashMap<String, Any>().apply {
                put(widgetId, HashMap<String, Any>().apply {
                    put("content", widgetEventContent)

                    put("state_key", widgetId)
                    put("id", widgetId)
                    put("sender", mSession!!.myUserId)
                    put("type", "m.widget")
                })
            }

            mSession!!.addUserWidget(addUserWidgetBody,
                    WidgetApiCallback(eventData, "## setWidget()"))
        } else {
            mSession!!.roomsApiClient.sendStateEvent(mRoom!!.roomId,
                    WidgetsManager.WIDGET_EVENT_TYPE,
                    widgetId,
                    widgetEventContent,
                    WidgetApiCallback(eventData, "## setWidget()"))
        }
    }

    /**
     * Provide the widgets list
     *
     * @param eventData the modular data
     */
    private fun getWidgets(eventData: JsonDict<Any>) {
        if (checkRoomId(eventData)) {
            return
        }

        Log.d(LOG_TAG, "Received request to get widget in room " + mRoom!!.roomId)

        val widgets = WidgetsManager.getActiveWidgets(mSession, mRoom)
        val responseData = ArrayList<JsonDict<Any>>()

        for (widget in widgets) {
            val map = widget.widgetEvent.toJsonMap()

            if (null != map) {
                responseData.add(map)
            }
        }

        // Add user Widgets
        mSession!!.userWidgets
                .forEach {
                    responseData.add(it.value as JsonDict<Any>)
                }

        Log.d(LOG_TAG, "## getWidgets() returns $responseData")

        sendObjectResponse(responseData, eventData)
    }

    /**
     * Check if the user can send an event of predefined type
     *
     * @param eventData the modular data
     */
    private fun canSendEvent(eventData: JsonDict<Any>) {
        if (checkRoomId(eventData)) {
            return
        }

        Log.d(LOG_TAG, "Received request canSendEvent in room " + mRoom!!.roomId)

        if (!mRoom!!.isJoined) {
            sendError(getString(R.string.widget_integration_must_be_in_room), eventData)
            return
        }

        val eventType = eventData["event_type"] as String
        val isState = eventData["is_state"] as Boolean

        Log.d(LOG_TAG, "## canSendEvent() : eventType $eventType isState $isState")

        val powerLevels = mRoom!!.state.powerLevels

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
     * @param eventData the modular data
     */
    private fun getMembershipState(eventData: JsonDict<Any>) {
        if (checkRoomId(eventData) || checkUserId(eventData)) {
            return
        }

        val userId = eventData["user_id"] as String

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
                Log.e(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " failed " + e.message, e)
                sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
            }

            override fun onMatrixError(e: MatrixError) {
                Log.e(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " failed " + e.message)
                sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
            }

            override fun onUnexpectedError(e: Exception) {
                Log.e(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " failed " + e.message, e)
                sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
            }
        })
    }

    /**
     * Request the latest joined room event
     *
     * @param eventData the modular data
     */
    private fun getJoinRules(eventData: JsonDict<Any>) {
        if (checkRoomId(eventData)) {
            return
        }

        Log.d(LOG_TAG, "Received request join rules  in room " + mRoom!!.roomId)
        val joinedEvents = mRoom!!.state.getStateEvents(HashSet(Arrays.asList(Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES)))

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
    private fun setPlumbingState(eventData: JsonDict<Any>) {
        if (checkRoomId(eventData)) {
            return
        }

        val description = "Received request to set plumbing state to status " + eventData["status"] + " in room " + mRoom!!.roomId + " requested"
        Log.d(LOG_TAG, description)

        val status = eventData["status"] as String

        val params = HashMap<String, Any>()
        params["status"] = status

        mSession!!.roomsApiClient.sendStateEvent(mRoom!!.roomId,
                Event.EVENT_TYPE_ROOM_PLUMBING,
                null,
                params,
                WidgetApiCallback(eventData, description))
    }

    /**
     * Retrieve the latest botOptions event
     *
     * @param eventData the modular data
     */
    private fun getBotOptions(eventData: JsonDict<Any>) {
        if (checkRoomId(eventData) || checkUserId(eventData)) {
            return
        }

        val userId = eventData["user_id"] as String

        Log.d(LOG_TAG, "Received request to get options for bot " + userId + " in room " + mRoom!!.roomId + " requested")

        val stateEvents = mRoom!!.state.getStateEvents(HashSet(Arrays.asList(Event.EVENT_TYPE_ROOM_BOT_OPTIONS)))

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
     * @param eventData the modular data
     */
    private fun setBotOptions(eventData: JsonDict<Any>) {
        if (checkRoomId(eventData) || checkUserId(eventData)) {
            return
        }

        val userId = eventData["user_id"] as String

        val description = "Received request to set options for bot " + userId + " in room " + mRoom!!.roomId
        Log.d(LOG_TAG, description)

        val content = eventData["content"] as JsonDict<Any>
        val stateKey = "_$userId"

        mSession!!.roomsApiClient.sendStateEvent(mRoom!!.roomId,
                Event.EVENT_TYPE_ROOM_BOT_OPTIONS,
                stateKey,
                content,
                WidgetApiCallback(eventData, description))
    }

    /**
     * Update the bot power levels
     *
     * @param eventData the modular data
     */
    private fun setBotPower(eventData: JsonDict<Any>) {
        if (checkRoomId(eventData) || checkUserId(eventData)) {
            return
        }

        val userId = eventData["user_id"] as String

        val description = "Received request to set power level to " + eventData["level"] + " for bot " + userId + " in room " + mRoom!!.roomId

        Log.d(LOG_TAG, description)

        val level = eventData["level"] as Int

        if (level >= 0) {
            mRoom!!.updateUserPowerLevels(userId, level, WidgetApiCallback(eventData, description))
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
    private fun getMembershipCount(eventData: JsonDict<Any>) {
        if (checkRoomId(eventData)) {
            return
        }

        sendIntegerResponse(mRoom!!.numberOfJoinedMembers, eventData)
    }

    /**
     * Check if roomId is present in the event and match
     * Send response and return true in case of error
     *
     * @return true in case of error
     */
    private fun checkRoomId(eventData: JsonDict<Any>): Boolean {
        val roomIdInEvent = eventData["room_id"] as String?

        // Check if param is present
        if (null == roomIdInEvent) {
            sendError(getString(R.string.widget_integration_missing_room_id), eventData)
            return true
        }

        // Room ids must match
        if (!TextUtils.equals(roomIdInEvent, mRoom!!.roomId)) {
            sendError(getString(R.string.widget_integration_room_not_visible), eventData)
            return true
        }

        // OK
        return false
    }

    /**
     * Check if userId is present in the event
     * Send response and return true in case of error
     *
     * @return true in case of error
     */
    private fun checkUserId(eventData: JsonDict<Any>): Boolean {
        val userIdInEvent = eventData["user_id"] as String?

        // Check if param is present
        if (null == userIdInEvent) {
            sendError(getString(R.string.widget_integration_missing_user_id), eventData)
            return true
        }

        // OK
        return false
    }

    /* ==========================================================================================
     * companion
     * ========================================================================================== */

    companion object {
        private val LOG_TAG = IntegrationManagerActivity::class.java.simpleName

        /**
         * the parameters
         */
        internal const val EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID"
        internal const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"
        internal const val EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID"
        private const val EXTRA_SCREEN_ID = "EXTRA_SCREEN_ID"

        fun getIntent(context: Context,
                      matrixId: String,
                      roomId: String,
                      widgetId: String? = null,
                      screenId: String? = null): Intent {
            return Intent(context, IntegrationManagerActivity::class.java)
                    .apply {
                        putExtra(EXTRA_MATRIX_ID, matrixId)
                        putExtra(EXTRA_ROOM_ID, roomId)
                        putExtra(EXTRA_WIDGET_ID, widgetId)
                        putExtra(EXTRA_SCREEN_ID, screenId)
                    }
        }
    }
}