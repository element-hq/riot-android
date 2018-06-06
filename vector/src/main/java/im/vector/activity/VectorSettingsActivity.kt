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
import android.content.pm.PackageManager
import android.view.MenuItem

import org.matrix.androidsdk.MXSession

import im.vector.Matrix
import im.vector.R
import im.vector.fragments.VectorSettingsPreferencesFragment
import im.vector.util.VectorUtils

/**
 * Displays the client settings.
 */
class VectorSettingsActivity : MXCActionBarActivity() {
    // the UI items
    private var mFragment: VectorSettingsPreferencesFragment? = null

    override fun getLayoutRes(): Int {
        return R.layout.activity_vector_settings
    }

    override fun getTitleRes(): Int {
        return R.string.title_activity_settings
    }

    override fun initUiAndData() {
        val intent = intent
        var session = getSession(intent)

        if (null == session) {
            session = Matrix.getInstance(this@VectorSettingsActivity)!!.defaultSession
        }

        if (session == null) {
            finish()
            return
        }

        // display the fragment
        mFragment = VectorSettingsPreferencesFragment.newInstance(session.myUserId)
        fragmentManager.beginTransaction().replace(R.id.vector_settings_page, mFragment).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        // pass the result to the fragment
        // FIXME This is not necessary, if Fragment.startActivityForResult is used (and it should be used)
        mFragment!!.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(aRequestCode: Int, aPermissions: Array<String>, aGrantResults: IntArray) {
        if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO) {
            var granted = false

            for (i in aGrantResults.indices) {
                granted = granted or (PackageManager.PERMISSION_GRANTED == aGrantResults[i])
            }

            if (granted) {
                val intent = Intent(this, VectorMediasPickerActivity::class.java)
                intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true)
                startActivityForResult(intent, VectorUtils.TAKE_IMAGE)
            }
        }
    }
}
