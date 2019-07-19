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
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import androidx.core.view.isVisible
import im.vector.R
import im.vector.dialogs.ExportKeysDialog
import im.vector.fragments.keysbackup.setup.KeysBackupSetupSharedViewModel
import im.vector.fragments.keysbackup.setup.KeysBackupSetupStep1Fragment
import im.vector.fragments.keysbackup.setup.KeysBackupSetupStep2Fragment
import im.vector.fragments.keysbackup.setup.KeysBackupSetupStep3Fragment
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.core.model.MatrixError

class KeysBackupSetupActivity : SimpleFragmentActivity() {

    override fun getTitleRes() = R.string.title_activity_keys_backup_setup

    private lateinit var viewModel: KeysBackupSetupSharedViewModel

    override fun initUiAndData() {
        super.initUiAndData()
        if (isFirstCreation()) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, KeysBackupSetupStep1Fragment.newInstance())
                    .commitNow()
        }

        viewModel = ViewModelProviders.of(this).get(KeysBackupSetupSharedViewModel::class.java)
        viewModel.showManualExport.value = intent.getBooleanExtra(EXTRA_SHOW_MANUAL_EXPORT, false)
        viewModel.initSession(mSession)


        viewModel.isCreatingBackupVersion.observe(this, Observer {
            val isCreating = it ?: false
            if (isCreating) {
                showWaitingView()
            } else {
                hideWaitingView()
            }
        })

        viewModel.loadingStatus.observe(this, Observer {
            it?.let {
                updateWaitingView(it)
            }
        })

        viewModel.navigateEvent.observe(this, Observer { uxStateEvent ->
            when (uxStateEvent?.getContentIfNotHandled()) {
                KeysBackupSetupSharedViewModel.NAVIGATE_TO_STEP_2 -> {
                    supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.container, KeysBackupSetupStep2Fragment.newInstance())
                            .commit()
                }
                KeysBackupSetupSharedViewModel.NAVIGATE_TO_STEP_3 -> {
                    supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.container, KeysBackupSetupStep3Fragment.newInstance())
                            .commit()
                }
                KeysBackupSetupSharedViewModel.NAVIGATE_FINISH -> {
                    val resultIntent = Intent()
                    viewModel.keysVersion.value?.version?.let {
                        resultIntent.putExtra(KeysBackupSetupActivity.KEYS_VERSION, it)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
                KeysBackupSetupSharedViewModel.NAVIGATE_PROMPT_REPLACE -> {
                    AlertDialog.Builder(this)
                            .setTitle(R.string.keys_backup_setup_override_backup_prompt_tile)
                            .setMessage(R.string.keys_backup_setup_override_backup_prompt_description)
                            .setPositiveButton(R.string.keys_backup_setup_override_replace) { _, _ ->
                                viewModel.forceCreateKeyBackup(this)
                            }.setNegativeButton(R.string.keys_backup_setup_override_stop) { _, _ ->
                               viewModel.stopAndKeepAfterDetectingExistingOnServer()
                            }
                            .show()
                }
                KeysBackupSetupSharedViewModel.NAVIGATE_MANUAL_EXPORT -> {
                    exportKeysManually()
                }
            }
        })


        viewModel.prepareRecoverFailError.observe(this, Observer { error ->
            if (error != null) {
                AlertDialog.Builder(this)
                        .setTitle(R.string.unknown_error)
                        .setMessage(error.localizedMessage)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            //nop
                            viewModel.prepareRecoverFailError.value = null
                        }
                        .show()
            }
        })

        viewModel.creatingBackupError.observe(this, Observer { error ->
            if (error != null) {
                AlertDialog.Builder(this)
                        .setTitle(R.string.unexpected_error)
                        .setMessage(error.localizedMessage)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            //nop
                            viewModel.creatingBackupError.value = null
                        }
                        .show()
            }
        })
    }

    fun exportKeysManually() {
        ExportKeysDialog().show(this, object : ExportKeysDialog.ExportKeyDialogListener {
            override fun onPassphrase(passphrase: String) {
                showWaitingView()

                CommonActivityUtils.exportKeys(mSession, passphrase, object : SimpleApiCallback<String>(this@KeysBackupSetupActivity) {
                    override fun onSuccess(filename: String) {
                        hideWaitingView()

                        AlertDialog.Builder(this@KeysBackupSetupActivity)
                                .setMessage(getString(R.string.encryption_export_saved_as, filename))
                                .setCancelable(false)
                                .setPositiveButton(R.string.ok) { dialog, which ->
                                    val resultIntent = Intent()
                                    resultIntent.putExtra(MANUAL_EXPORT, true)
                                    setResult(RESULT_OK, resultIntent)
                                    finish()
                                }
                                .show()
                    }

                    override fun onNetworkError(e: Exception) {
                        super.onNetworkError(e)
                        hideWaitingView()
                    }

                    override fun onMatrixError(e: MatrixError) {
                        super.onMatrixError(e)
                        hideWaitingView()
                    }

                    override fun onUnexpectedError(e: Exception) {
                        super.onUnexpectedError(e)
                        hideWaitingView()
                    }
                })
            }
        })


    }


    override fun onBackPressed() {
        if (viewModel.shouldPromptOnBack) {
            if (waitingView?.isVisible == true) {
                return
            }
            AlertDialog.Builder(this)
                    .setTitle(R.string.keys_backup_setup_skip_title)
                    .setMessage(R.string.keys_backup_setup_skip_msg)
                    .setNegativeButton(R.string.stay, null)
                    .setPositiveButton(R.string.abort) { _, _ ->
                        finish()
                    }
                    .show()
        } else {
            super.onBackPressed()
        }
    }

//    I think this code is useful, but it violates the code quality rules
//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        if (item.itemId == android .R. id.  home) {
//            onBackPressed()
//            return true
//        }
//
//        return super.onOptionsItemSelected(item)
//    }


    companion object {
        const val KEYS_VERSION = "KEYS_VERSION"
        const val MANUAL_EXPORT = "MANUAL_EXPORT"
        const val EXTRA_SHOW_MANUAL_EXPORT = "SHOW_MANUAL_EXPORT"

        fun intent(context: Context, matrixID: String, showManualExport: Boolean): Intent {
            val intent = Intent(context, KeysBackupSetupActivity::class.java)
            intent.putExtra(EXTRA_MATRIX_ID, matrixID)
            intent.putExtra(EXTRA_SHOW_MANUAL_EXPORT, showManualExport)
            return intent
        }
    }
}
