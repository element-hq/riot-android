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
package im.vector.activity

import android.content.Intent
import im.vector.Matrix
import im.vector.R
import im.vector.fragments.VectorSettingsPreferencesFragment
import im.vector.util.PERMISSION_REQUEST_CODE_EXPORT_KEYS
import im.vector.util.PERMISSION_REQUEST_CODE_LAUNCH_CAMERA
import im.vector.util.VectorUtils
import im.vector.util.allGranted

/**
 * Displays the client settings.
 */
class VectorSettingsActivity : MXCActionBarActivity() {

    private lateinit var vectorSettingsPreferencesFragment: VectorSettingsPreferencesFragment

    override fun getLayoutRes(): Int {
        return R.layout.activity_vector_settings
    }

    override fun getTitleRes(): Int {
        return R.string.title_activity_settings
    }

    override fun initUiAndData() {
        var session = getSession(intent)

        if (null == session) {
            session = Matrix.getInstance(this).defaultSession
        }

        if (session == null) {
            finish()
            return
        }

        if (isFirstCreation()) {
            vectorSettingsPreferencesFragment = VectorSettingsPreferencesFragment.newInstance(session.myUserId)
            // display the fragment
            fragmentManager.beginTransaction()
                    .replace(R.id.vector_settings_page, vectorSettingsPreferencesFragment, FRAGMENT_TAG)
                    .commit()
        } else {
            vectorSettingsPreferencesFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG) as VectorSettingsPreferencesFragment
        }
    }

    /**
     * Keep this code here, cause PreferenceFragment does not extend v4 Fragment
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (allGranted(grantResults)) {
            if (requestCode == PERMISSION_REQUEST_CODE_LAUNCH_CAMERA) {
                val intent = Intent(this, VectorMediasPickerActivity::class.java)
                intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true)
                startActivityForResult(intent, VectorUtils.TAKE_IMAGE)
            } else if (requestCode == PERMISSION_REQUEST_CODE_EXPORT_KEYS) {
                vectorSettingsPreferencesFragment.exportKeys()
            }
        }
    }

    companion object {
        private const val FRAGMENT_TAG = "VectorSettingsPreferencesFragment"
    }
}
