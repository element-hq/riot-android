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

package im.vector.activity

import android.content.Intent
import androidx.core.view.isVisible
import butterknife.OnClick
import im.vector.Matrix
import im.vector.R
import im.vector.util.openPlayStore
import kotlinx.android.synthetic.main.activity_warning.*
import org.matrix.androidsdk.util.Log

class WarningActivity : VectorAppCompatActivity() {

    override fun getLayoutRes() = R.layout.activity_warning

    override fun getTitleRes() = R.string.warning_screen_title

    override fun initUiAndData() {
        super.initUiAndData()

        configureToolbar()

        val hasCredential = hasCredentials()

        // Reveal some elements
        warning_text_connected.isVisible = hasCredential
        warning_button_launch_riot.isVisible = hasCredential
    }

    @OnClick(R.id.warning_button_install_new_riot)
    fun installNewRiot() {
        // TODO FDroid?
        // TODO second link on description has to be updated
        openPlayStore(this, "im.vector.app")
    }

    @OnClick(R.id.warning_button_launch_riot)
    fun launchRiot() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // Copied from LoginActivity
    private fun hasCredentials(): Boolean {
        try {
            val session = Matrix.getInstance(this)!!.defaultSession
            return null != session && session.isAlive

        } catch (e: Exception) {
            Log.e(LOG_TAG, "## Exception: " + e.message, e)
        }

        Log.e(LOG_TAG, "## hasCredentials() : invalid credentials")

        runOnUiThread {
            try {
                // getDefaultSession could trigger an exception if the login data are corrupted
                CommonActivityUtils.logout(this@WarningActivity)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "## Exception: " + e.message, e)
            }
        }

        return false
    }

    companion object {
        private const val LOG_TAG = "WarningActivity"
    }
}
