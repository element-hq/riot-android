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

import android.content.Context
import android.content.Intent
import im.vector.R
import im.vector.fragments.keysbackuprestore.KeysBackupRestoreFromKeyFragment
import im.vector.fragments.keysbackuprestore.KeysBackupRestoreFromPassphraseFragment

class KeysBackupRestoreActivity : SimpleFragmentActivity(),
        KeysBackupRestoreFromPassphraseFragment.InteractionListener,
        KeysBackupRestoreFromKeyFragment.InteractionListener {

    companion object {

        private const val KEY_KEYS_VERSION = "KEY_KEYS_VERSION"

        fun intent(context: Context, keysVersion: String, matrixID: String): Intent {
            val intent = Intent(context, KeysBackupRestoreActivity::class.java)
            intent.putExtra(KEY_KEYS_VERSION, keysVersion)
            intent.putExtra(EXTRA_MATRIX_ID, matrixID)
            return intent
        }

    }

    override fun getTitleRes() = R.string.title_activity_keys_backup_restore

    override fun initUiAndData() {
        super.initUiAndData()
        //TODO if no salt, can skip to key recovery
        if (isFirstCreation()) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, KeysBackupRestoreFromPassphraseFragment.newInstance())
                    .commitNow()
        }
    }

    override fun didSelectRecoveryKeyMode() {
        supportFragmentManager.beginTransaction()
                .replace(R.id.container, KeysBackupRestoreFromKeyFragment.newInstance())
                .setCustomAnimations(R.anim.abc_fade_in, R.anim.abc_fade_out)
                .addToBackStack(null)
                .commit()
    }

    override fun getKeysVersion(): String {
        if (intent.hasExtra(KEY_KEYS_VERSION)) {
            return intent.getStringExtra(KEY_KEYS_VERSION)
        } else {
            //for easy testing
            return mSession.crypto?.keysBackup?.currentBackupVersion ?: ""
        }
    }

    override fun setShowWaitingView(status: String?) {
        dismissKeyboard(this)
        if (status == null) {
            showWaitingView()
        } else {
            showWaitingView(status)
        }
    }

    override fun setHideWaitingView() {
        hideWaitingView()
    }

    override fun didSelectCreateNewRecoveryCode() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


}