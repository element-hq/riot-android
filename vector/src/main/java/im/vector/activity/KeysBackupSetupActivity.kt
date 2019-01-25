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

import android.support.v7.app.AlertDialog
import android.view.MenuItem
import im.vector.R
import im.vector.fragments.keysbackupsetup.KeysBackupSetupStep1Fragment

class KeysBackupSetupActivity : MXCActionBarActivity() {

    override fun getLayoutRes() = R.layout.keys_backup_setup_activity

    override fun getTitleRes() = R.string.title_activity_keys_backup_setup

    override fun getMenuRes() = R.menu.keys_backup_setup

    override fun initUiAndData() {
        if (isFirstCreation()) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, KeysBackupSetupStep1Fragment.newInstance())
                    .commitNow()
        }
        configureToolbar()
        waitingView = findViewById(R.id.keys_backup_waiting_view)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.ic_action_keybackup_setup_skip) {
            onSkip()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onSkip() {
        AlertDialog.Builder(this)
                .setTitle(R.string.keys_backup_setup_skip_title)
                .setMessage(R.string.keys_backup_setup_skip_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.skip) { _, _ ->
                    finish()
                }
                .show()
    }

    companion object {
        const val KEYS_VERSION = "KEYS_VERSION"
    }
}
