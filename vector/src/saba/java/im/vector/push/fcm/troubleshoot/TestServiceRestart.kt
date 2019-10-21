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
package im.vector.push.fcm.troubleshoot

import android.app.ActivityManager
import android.content.Context
//import android.support.v4.app.Fragment
import androidx.fragment.app.Fragment
import im.vector.R
import im.vector.VectorApp
import im.vector.fragments.troubleshoot.TroubleshootTest
import im.vector.services.EventStreamService
import java.util.*
import kotlin.concurrent.timerTask


/**
 * Stop the event stream service and check that it is restarted
 */
class TestServiceRestart(val fragment: Fragment) : TroubleshootTest(R.string.settings_troubleshoot_test_service_restart_title) {

    var timer: Timer? = null

    override fun perform() {
        status = TestStatus.RUNNING
        EventStreamService.getInstance()?.stopSelf()
        timer = Timer()
        timer?.schedule(timerTask {
            if (isMyServiceRunning(EventStreamService::class.java)) {
                fragment.activity?.runOnUiThread {
                    description = fragment.getString(R.string.settings_troubleshoot_test_service_restart_success)
                    quickFix = null
                    status = TestStatus.SUCCESS
                }
                timer?.cancel()
            }
        }, 0, 1000)

        timer?.schedule(timerTask {
            fragment.activity?.runOnUiThread {
                status = TestStatus.FAILED
                description = fragment.getString(R.string.settings_troubleshoot_test_service_restart_failed)
            }
            timer?.cancel()
        }, 15000)
    }


    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = VectorApp.getInstance().baseContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun cancel() {
        super.cancel()
        timer?.cancel()
    }
}