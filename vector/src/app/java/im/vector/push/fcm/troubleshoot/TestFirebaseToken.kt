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

import android.support.v4.app.Fragment
import com.google.firebase.iid.FirebaseInstanceId
import im.vector.R
import im.vector.fragments.troubleshoot.TroubleshootTest
import org.matrix.androidsdk.util.Log

/*
* Test that app can successfully retrieve a token via firebase
 */
class TestFirebaseToken(val fragment: Fragment) : TroubleshootTest(R.string.settings_troubleshoot_test_fcm_title) {

    override fun perform() {
        status = TestStatus.RUNNING
        fragment.activity?.let { fragmentActivity ->
            FirebaseInstanceId.getInstance().instanceId
                    .addOnCompleteListener(fragmentActivity) { task ->
                        if (!task.isSuccessful) {
                            val errorMsg = if (task.exception == null) "Unknown" else task.exception!!.localizedMessage
                            description = fragment.getString(R.string.settings_troubleshoot_test_fcm_failed, errorMsg)
                            status = TestStatus.FAILED

                        } else {
                            task.result?.token?.let {
                                val tok = it.substring(0, Math.min(8, it.length)) + "********************"
                                description = fragment.getString(R.string.settings_troubleshoot_test_fcm_success, tok)
                                Log.e(this::class.java.simpleName, "Retrieved FCM token success [$it].")
                            }
                            status = TestStatus.SUCCESS
                        }
                    }
        } ?: run {
            status = TestStatus.FAILED
        }
    }

}