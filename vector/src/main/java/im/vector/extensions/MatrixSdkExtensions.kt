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

package im.vector.extensions

import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.rest.model.Event
import kotlin.math.max

/* ==========================================================================================
 * MXDeviceInfo
 * ========================================================================================== */

fun MXDeviceInfo.getFingerprintHumanReadable() = fingerprint()
        ?.chunked(4)
        ?.joinToString(separator = " ")


/* ==========================================================================================
 * Room
 * ========================================================================================== */

/**
 * Helper method to retrieve the max power level contained in the room.
 * This value is used to indicate what is the power level value required
 * to be admin of the room.
 *
 * @return max power level of the current room
 */
fun Room?.getRoomMaxPowerLevel(): Int {
    if (this == null) {
        return 0
    }

    var maxPowerLevel = 0

    state?.powerLevels?.let {
        maxPowerLevel = max(it.users_default, it.users?.values?.max() ?: 0)
    }

    return maxPowerLevel
}

/**
 * Check if the user power level allows to update the room avatar. This is mainly used to
 * determine if camera permission must be checked or not.
 *
 * @param aSession the session
 * @return true if the user power level allows to update the avatar, false otherwise.
 */
fun Room.isPowerLevelEnoughForAvatarUpdate(aSession: MXSession?): Boolean {
    var canUpdateAvatarWithCamera = false

    if (null != aSession) {
        state.powerLevels?.let {
            val powerLevel = it.getUserPowerLevel(aSession.myUserId)

            // check the power level against avatar level
            canUpdateAvatarWithCamera = powerLevel >= it.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_AVATAR)
        }
    }

    return canUpdateAvatarWithCamera
}

/* ==========================================================================================
 * Event
 * ========================================================================================== */

fun Event.getSessionId() = wireContent
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?.get("session_id")
        ?.asString