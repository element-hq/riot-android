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
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.data.RoomTag
import org.matrix.androidsdk.rest.model.RoomMember
import org.matrix.androidsdk.util.Log

/**
 * This class is responsible for filtering and ranking rooms whenever there is a need to update in the context of the HomeScreens
 */

class HomeRoomsViewModel(private val session: MXSession) {

    /**
     * A data class holding the result of filtering and ranking algorithm
     */
    data class Result(val favourites: List<Room> = emptyList(),
                      val directChats: List<Room> = emptyList(),
                      val lowPriorities: List<Room> = emptyList(),
                      val otherRooms: List<Room> = emptyList())

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
        val lowPriorities = ArrayList<Room>()
        val directChats = ArrayList<Room>()
        val otherRooms = ArrayList<Room>()

        val joinedRooms = getJoinedRooms()
        for (room in joinedRooms) {
            val tags = room.accountData?.keys ?: emptySet()
            when {
                tags.contains(RoomTag.ROOM_TAG_FAVOURITE) -> favourites.add(room)
                tags.contains(RoomTag.ROOM_TAG_LOW_PRIORITY) -> lowPriorities.add(room)
                RoomUtils.isDirectChat(session, room.roomId) -> directChats.add(room)
                else -> otherRooms.add(room)
            }
        }

        result = Result(
                favourites,
                directChats,
                lowPriorities,
                otherRooms
        )
        Log.d("HomeRoomsViewModel", result.toString())
        return result
    }

    //region private methods

    private fun getJoinedRooms(): List<Room> {
        return session.dataHandler.store.rooms
                .filter {
                    val isJoined = it.hasMembership(RoomMember.MEMBERSHIP_JOIN)
                    val tombstoneContent = it.state.roomTombstoneContent
                    val redirectRoom = session.dataHandler.getRoom(tombstoneContent?.replacementRoom)
                    val isVersioned = redirectRoom?.hasMembership(RoomMember.MEMBERSHIP_JOIN)
                            ?: false
                    isJoined && !isVersioned && !it.isConferenceUserRoom
                }
    }

    //endregion


}