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
import android.view.MenuItem
import im.vector.R
import im.vector.fragments.keysbackup.setup.KeysBackupSetupSharedViewModel
import im.vector.fragments.keysbackup.setup.KeysBackupSetupStep1Fragment
import im.vector.fragments.keysbackup.setup.KeysBackupSetupStep2Fragment
import im.vector.fragments.keysbackup.setup.KeysBackupSetupStep3Fragment

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
                updateWaitingStatus(getString(it))
            }
        })

        viewModel.prepareRecoveryProgressProgress.observe(this, Observer {
            val progress = it ?: -1
            if (progress == -1) {
                updateWaitingProgress(false, 0, 100)
            } else {
                updateWaitingProgress(true, progress, viewModel.prepareRecoveryProgressTotal.value
                        ?: 100)
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


    override fun onBackPressed() {
        if (viewModel.shouldPromptOnBack) {
            AlertDialog.Builder(this)
                    .setTitle(R.string.keys_backup_setup_skip_title)
                    .setMessage(R.string.keys_backup_setup_skip_msg)
                    .setNegativeButton(R.string.keys_backup_setup_step1_button_title, null)
                    .setPositiveButton(R.string.abort) { _, _ ->
                        finish()
                    }
                    .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }

        return super.onOptionsItemSelected(item)
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
