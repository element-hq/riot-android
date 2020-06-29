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

import androidx.fragment.app.Fragment
import im.vector.Matrix
import im.vector.R
import im.vector.VectorApp
import im.vector.fragments.troubleshoot.TroubleshootTest
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError

/**
 * Force registration of the token to HomeServer
 */
class TestTokenRegistration(val fragment: Fragment) : TroubleshootTest(R.string.settings_troubleshoot_test_token_registration_title) {

    override fun perform() {
        Matrix.getInstance(VectorApp.getInstance().baseContext).pushManager.forceSessionsRegistration(object : ApiCallback<Void> {
            override fun onSuccess(info: Void?) {
                description = fragment.getString(R.string.settings_troubleshoot_test_token_registration_success)
                status = TestStatus.SUCCESS
            }

            override fun onNetworkError(e: Exception?) {
                description = fragment.getString(R.string.settings_troubleshoot_test_token_registration_failed, e?.localizedMessage)
                status = TestStatus.FAILED
            }

            override fun onMatrixError(e: MatrixError?) {
                description = fragment.getString(R.string.settings_troubleshoot_test_token_registration_failed, e?.localizedMessage)
                status = TestStatus.FAILED
            }

            override fun onUnexpectedError(e: Exception?) {
                description = fragment.getString(R.string.settings_troubleshoot_test_token_registration_failed, e?.localizedMessage)
                status = TestStatus.FAILED
            }
        })
    }

}