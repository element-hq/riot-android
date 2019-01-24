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
import im.vector.fragments.keybackupsetup.KeybackupSetupStep1Fragment

class KeybackupSetupActivity : MXCActionBarActivity() {

    override fun getLayoutRes() = R.layout.keybackup_setup_activity

    override fun getTitleRes() = R.string.title_activity_key_backup_setup

    override fun getMenuRes() = R.menu.keybackup_setup

    override fun initUiAndData() {
        if (isFirstCreation()) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, KeybackupSetupStep1Fragment.newInstance())
                    .commitNow()
        }
        setSupportActionBar(findViewById(R.id.keybackup_toolbar))
        waitingView = findViewById(R.id.keybackup_waiting_view)
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
                .setTitle(R.string.keybackup_setup_skip_title)
                .setMessage(R.string.keybackup_setup_skip_msg)
                .setPositiveButton(R.string._continue) { dialog, id ->
                    //nop
                }
                .setNegativeButton(R.string.skip) { dialog, id ->
                    finish()
                }
                .show();
    }

}
