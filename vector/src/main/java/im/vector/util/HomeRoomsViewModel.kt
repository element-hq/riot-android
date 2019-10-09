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
package im.vector.util

import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.data.RoomTag

/**
 * This class is responsible for filtering and ranking rooms whenever there is a need to update in the context of the HomeScreens
 */
class HomeRoomsViewModel(private val session: MXSession) {

    /**
     * A data class holding the result of filtering and ranking algorithm
     * A room can't be in multiple lists at the same time.
     * Order is favourites -> directChats -> otherRooms -> lowPriorities -> serverNotices
     */
    data class Result(val favourites: List<Room> = emptyList(),
                      val directChats: List<Room> = emptyList(),
                      val otherRooms: List<Room> = emptyList(),
                      val lowPriorities: List<Room> = emptyList(),
                      val serverNotices: List<Room> = emptyList()) {

        /**
         * Use this method when you need to get all the directChats, favorites included
         * Low Priorities are always excluded
         */
        fun getDirectChatsWithFavorites(): List<Room> {
            return directChats + favourites.filter { it.isDirect }
        }

        /**
         * Use this method when you need to get all the other rooms, favorites included
         * Low Priorities are always excluded
         */
        fun getOtherRoomsWithFavorites(): List<Room> {
            return otherRooms + favourites.filter { !it.isDirect }
        }
    }

    /**
     * The last result
     */
    var result = Result()

    /**
     * The update method
     * This method should be called whenever the room data have changed
     */
    //TODO Take it off the main thread using coroutine
    fun update(): Result {
        val favourites = ArrayList<Room>()
        val directChats = ArrayList<Room>()
        val otherRooms = ArrayList<Room>()
        val lowPriorities = ArrayList<Room>()
        val serverNotices = ArrayList<Room>()

        val joinedRooms = getJoinedRooms()
        for (room in joinedRooms) {
            val tags = room.accountData?.keys ?: emptySet()
            when {
                tags.contains(RoomTag.ROOM_TAG_SERVER_NOTICE) -> serverNotices.add(room)
                tags.contains(RoomTag.ROOM_TAG_FAVOURITE) -> favourites.add(room)
                tags.contains(RoomTag.ROOM_TAG_LOW_PRIORITY) -> lowPriorities.add(room)
                RoomUtils.isDirectChat(session, room.roomId) -> directChats.add(room)
                else -> otherRooms.add(room)
            }
        }

        result = Result(
                favourites = favourites,
                directChats = directChats,
                otherRooms = otherRooms,
                lowPriorities = lowPriorities,
                serverNotices = serverNotices)
        Log.d("HomeRoomsViewModel", result.toString())
        return result
    }

    //region private methods

    private fun getJoinedRooms(): List<Room> {
        return session.dataHandler.store?.rooms?.filter {
                    val isJoined = it.isJoined
                    val tombstoneContent = it.state.roomTombstoneContent
                    val redirectRoom = if (tombstoneContent?.replacementRoom != null) {
                        session.dataHandler.getRoom(tombstoneContent.replacementRoom)
                    } else {
                        null
                    }
                    val isVersioned = redirectRoom?.isJoined
                            ?: false
                    isJoined && !isVersioned && !it.isConferenceUserRoom
                } .orEmpty()
    }

    //endregion
}
