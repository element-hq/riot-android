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
package im.vector.fragments.troubleshoot

import android.support.v4.app.Fragment
import android.support.v4.app.NotificationManagerCompat
import im.vector.R
import im.vector.util.startNotificationSettingsIntent

/**
 * Checks if notifications are enable in the system settings for this app.
 */
class TestSystemSettings(val fragment: Fragment) : TroubleshootTest(R.string.settings_troubleshoot_test_system_settings_title) {

    override fun perform() {
        if (NotificationManagerCompat.from(fragment.context!!).areNotificationsEnabled()) {
            description = fragment.getString(R.string.settings_troubleshoot_test_system_settings_success)
            quickFix = null
            status = TestStatus.SUCCESS
        } else {
            description = fragment.getString(R.string.settings_troubleshoot_test_system_settings_failed)
            quickFix = object : TroubleshootQuickFix(R.string.open_settings) {
                override fun doFix() {
                    if (manager?.diagStatus == TestStatus.RUNNING) return //wait before all is finished
                    startNotificationSettingsIntent(fragment, NotificationTroubleshootTestManager.REQ_CODE_FIX)
                }

            }
            status = TestStatus.FAILED
        }
    }
}