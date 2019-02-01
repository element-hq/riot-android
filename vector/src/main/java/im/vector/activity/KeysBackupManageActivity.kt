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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.support.v7.app.AlertDialog
import im.vector.R
import im.vector.fragments.keysbackup.settings.KeysBackupSettingsFragment
import im.vector.fragments.keysbackup.settings.KeysBackupSettingsViewModel
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.MatrixError

class KeysBackupManageActivity : SimpleFragmentActivity() {

    companion object {

        fun intent(context: Context, matrixID: String): Intent {
            val intent = Intent(context, KeysBackupManageActivity::class.java)
            intent.putExtra(EXTRA_MATRIX_ID, matrixID)
            return intent
        }
    }

    override fun getTitleRes() = R.string.title_activity_keys_backup_manage


    private lateinit var viewModel: KeysBackupSettingsViewModel

    override fun initUiAndData() {
        super.initUiAndData()
        viewModel = ViewModelProviders.of(this).get(KeysBackupSettingsViewModel::class.java)
        viewModel.initSession(mSession)


        if (supportFragmentManager.fragments.isEmpty()) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, KeysBackupSettingsFragment.newInstance())
                    .commitNow()

            mSession.crypto
                    ?.keysBackup
                    ?.forceUsingLastVersion(object : ApiCallback<Boolean> {
                        override fun onSuccess(info: Boolean?) {}

                        override fun onUnexpectedError(e: java.lang.Exception?) {}

                        override fun onNetworkError(e: java.lang.Exception?) {}

                        override fun onMatrixError(e: MatrixError?) {}
                    })
        }

        viewModel.loadingEvent.observe(this, Observer {
            if (it == null) {
                hideWaitingView()
            } else {
                try {
                    showWaitingView(getString(it.peekContent()))
                } catch (e: Exception) {
                    showWaitingView()
                }
            }
        })


        viewModel.apiResultError.observe(this, Observer { uxStateEvent ->
            uxStateEvent?.getContentIfNotHandled()?.let {
                AlertDialog.Builder(this)
                        .setTitle(R.string.unknown_error)
                        .setMessage(it)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok, null)
                        .show()
            }
        })

    }
}