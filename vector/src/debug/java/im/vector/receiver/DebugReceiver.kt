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

package im.vector.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import im.vector.util.lsFiles

/**
 * Receiver to handle some command from ADB
 */
class DebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DEBUG_ACTION_DUMP_FILESYSTEM -> lsFiles(context)
        }
    }

    companion object {
        private const val DEBUG_ACTION_DUMP_FILESYSTEM = "im.vector.receiver.DEBUG_ACTION_DUMP_FILESYSTEM"

        fun getIntentFilter() = IntentFilter().apply {
            addAction(DEBUG_ACTION_DUMP_FILESYSTEM)
        }
    }
}
