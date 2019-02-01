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

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.support.v7.app.AlertDialog
import im.vector.R
import im.vector.fragments.keysbackup.setup.KeysBackupSetupSharedViewModel
import im.vector.fragments.keysbackup.setup.KeysBackupSetupStep1Fragment
import im.vector.fragments.keysbackup.setup.KeysBackupSetupStep2Fragment

class KeysBackupSetupActivity : SimpleFragmentActivity() {

    override fun getTitleRes() = R.string.title_activity_keys_backup_setup

    override fun initUiAndData() {
        super.initUiAndData()
        if (isFirstCreation()) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, KeysBackupSetupStep1Fragment.newInstance())
                    .commitNow()
        }
        ViewModelProviders.of(this).get(KeysBackupSetupSharedViewModel::class.java).initSession(mSession)
    }

    override fun onBackPressed() {
        // Warn user if he wants to leave step 1 or step 3
        if (supportFragmentManager.fragments.isEmpty()
                || supportFragmentManager.fragments[0] is KeysBackupSetupStep2Fragment) {
            super.onBackPressed()
        } else {
            AlertDialog.Builder(this)
                    .setTitle(R.string.keys_backup_setup_skip_title)
                    .setMessage(R.string.keys_backup_setup_skip_msg)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.action_exit) { _, _ ->
                        finish()
                    }
                    .show()
        }
    }

    companion object {
        const val KEYS_VERSION = "KEYS_VERSION"

        fun intent(context: Context, matrixID: String): Intent {
            val intent = Intent(context, KeysBackupSetupActivity::class.java)
            intent.putExtra(EXTRA_MATRIX_ID, matrixID)
            return intent
        }
    }
}
