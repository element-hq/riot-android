/*
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

package im.vector.features.hhs

import org.matrix.androidsdk.MXDataHandler
import org.matrix.androidsdk.core.JsonUtils
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.data.RoomState
import org.matrix.androidsdk.data.RoomTag
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.ServerNoticeUsageLimitContent

// We need to fetch each pinned message individually (if we don't already have it)
// so each pinned message may trigger a request. Limit the number per room for sanity.
// NB. this is just for server notices rather than pinned messages in general.
private const val MAX_PINNED_NOTICES_PER_ROOM = 2

/**
 * This class represents the possible states the listener can have (Normal or Exceeded)
 */
sealed class LimitResourceState {
    object Normal : LimitResourceState()
    data class Exceeded(val roomId: String, val eventId: String, val matrixError: MatrixError) : LimitResourceState()

    fun softErrorOrNull(): MatrixError? = when (this) {
        is Normal -> null
        is Exceeded -> matrixError
    }
}

/**
 * This class is responsible for listening to system alert rooms and looking for pinning events.
 * When an EVENT_TYPE_SERVER_NOTICE_USAGE_LIMIT is pinned, the state goes from Normal to Exceeded.
 * When the event is unpinned, the state goes back to Normal.
 */
class ResourceLimitEventListener(private val dataHandler: MXDataHandler, private val callback: Callback?) : MXEventListener() {

    private var serverNoticesRooms: List<Room> = emptyList()
    var limitResourceState: LimitResourceState = LimitResourceState.Normal
        private set

    /**
     * At init, we check the state of each server notice rooms
     */
    init {
        loadAndProcessServerNoticeRooms()
    }

    override fun onRoomTagEvent(roomId: String) {
        loadAndProcessServerNoticeRooms()
    }

    override fun onLiveEvent(event: Event, roomState: RoomState) {
        if (event.type == Event.EVENT_TYPE_STATE_PINNED_EVENT && serverNoticesRooms.map { it.roomId }.contains(event.roomId)) {
            val room = dataHandler.getRoom(event.roomId)
            processPinnedEvents(room)
        }
    }

    // Private methods *****************************************************************************

    private fun loadAndProcessServerNoticeRooms() {
        serverNoticesRooms = loadServerNoticeRooms()
        serverNoticesRooms.forEach {
            processPinnedEvents(it)
        }
    }

    /**
     * Reload the server notice rooms.
     * This method is called when a room tag event is emitted
     */
    private fun loadServerNoticeRooms(): List<Room> {
        Log.v("ResourceLimitEventListener", "Load server notice rooms")
        return dataHandler.store?.rooms?.filter {
            val tags = it.accountData?.keys ?: emptySet()
            tags.contains(RoomTag.ROOM_TAG_SERVER_NOTICE)
        } ?: emptyList()
    }

    private fun processPinnedEvents(room: Room) {
        Log.v("ResourceLimitEventListener", "Process pinned events")
        val roomState = room.state
        val pinned = roomState.roomPinnedEventsContent?.pinned ?: return
        if (shouldStateBeBackToNormal(limitResourceState, room.roomId, pinned)) {
            limitResourceState = LimitResourceState.Normal
            callback?.onResourceLimitStateChanged()
        } else {
            pinned.take(MAX_PINNED_NOTICES_PER_ROOM).forEach {
                retrieveResourceLimitExceededEvent(room.roomId, it)
            }
        }
    }

    private fun shouldStateBeBackToNormal(state: LimitResourceState, roomId: String, pinnedEvents: List<String>): Boolean {
        return when (state) {
            is LimitResourceState.Exceeded -> roomId == state.roomId && !pinnedEvents.contains(state.eventId)
            is LimitResourceState.Normal -> false
        }

    }

    private fun retrieveResourceLimitExceededEvent(roomId: String, eventId: String) {
        val dataRetriever = dataHandler.dataRetriever
        dataRetriever.getEvent(dataHandler.store, roomId, eventId, object : SimpleApiCallback<Event>() {
            override fun onSuccess(event: Event) {
                val content = JsonUtils.toClass(event.contentAsJsonObject, ServerNoticeUsageLimitContent::class.java)
                if (content.isServerNoticeUsageLimit) {
                    // map the content as a matrix error
                    val matrixError = MatrixError(MatrixError.RESOURCE_LIMIT_EXCEEDED, "").apply {
                        limitType = content.limit
                        adminUri = content.adminUri
                    }
                    limitResourceState = LimitResourceState.Exceeded(roomId, eventId, matrixError)
                    callback?.onResourceLimitStateChanged()
                }
            }
        })
    }

    /**
     * This im.vector.callback allows to alert when the state change
     */
    interface Callback {
        fun onResourceLimitStateChanged()
    }
}