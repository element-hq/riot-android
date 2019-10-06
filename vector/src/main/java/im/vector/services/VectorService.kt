/*
 * Copyright 2019 New Vector Ltd
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

package im.vector.services

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import org.matrix.androidsdk.core.Log

/**
 * Parent class for all services
 */
abstract class VectorService : Service() {

    /**
     * Tells if the service self destroyed.
     */
    private var mIsSelfDestroyed = false

    override fun onCreate() {
        super.onCreate()

        Log.i(LOG_TAG, "## onCreate() : $this")
    }

    override fun onDestroy() {
        Log.i(LOG_TAG, "## onDestroy() : $this")

        if (!mIsSelfDestroyed) {
            Log.w(LOG_TAG, "## Destroy by the system : $this")
        }

        super.onDestroy()
    }

    protected fun myStopSelf() {
        Handler().postDelayed({
            mIsSelfDestroyed = true
            stopSelf()
        }, 100)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val LOG_TAG = "VectorService"
    }
}