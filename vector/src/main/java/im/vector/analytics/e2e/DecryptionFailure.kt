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

package im.vector.analytics.e2e

import java.util.*

/**
 * Failure reasons as defined in https://docs.google.com/document/d/1es7cTCeJEXXfRCTRgZerAM2Wg5ZerHjvlpfTW-gsOfI.
 */
enum class DecryptionFailureReason(val value: String) {
    UNSPECIFIED("unspecified"),
    OLM_KEYS_NOT_SENT("olmKeysNotSent"),
    OLM_INDEX_ERROR("olmIndexError"),
    UNEXPECTED("unexpected")
}


/**
 * This class represents a decryption failure to be reported
 */
data class DecryptionFailure(val reason: DecryptionFailureReason,
                             val failedEventId: String) {

    val timestamp: Long = Date().time

}


