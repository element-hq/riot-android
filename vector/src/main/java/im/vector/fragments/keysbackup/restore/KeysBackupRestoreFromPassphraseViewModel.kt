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
package im.vector.fragments.keysbackup.restore

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import im.vector.R
import im.vector.activity.util.WaitingViewData
import im.vector.view.KeysBackupBanner
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.core.listeners.StepProgressListener
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.crypto.data.ImportRoomKeysResult
import org.matrix.androidsdk.crypto.keysbackup.KeysBackup
import org.matrix.androidsdk.crypto.model.keys.KeysVersionResult

class KeysBackupRestoreFromPassphraseViewModel : ViewModel() {

    var passphrase: MutableLiveData<String> = MutableLiveData()
    var passphraseErrorText: MutableLiveData<String> = MutableLiveData()
    var showPasswordMode: MutableLiveData<Boolean> = MutableLiveData()

    init {
        passphrase.value = null
        passphraseErrorText.value = null
        showPasswordMode.value = false
    }

    //========= Actions =========

    fun updatePassphrase(newValue: String) {
        passphrase.value = newValue
        passphraseErrorText.value = null
    }

    fun recoverKeys(context: Context, sharedViewModel: KeysBackupRestoreSharedViewModel) {
        val keysBackup = sharedViewModel.session.crypto?.keysBackup

        if (keysBackup != null) {
            passphraseErrorText.value = null

            val keysVersionResult = sharedViewModel.keyVersionResult.value!!

            keysBackup.restoreKeyBackupWithPassword(keysVersionResult,
                    passphrase.value!!,
                    null,
                    sharedViewModel.session.myUserId,
                    object : StepProgressListener {
                        override fun onStepProgress(step: StepProgressListener.Step) {
                            when (step) {
                                is StepProgressListener.Step.ComputingKey -> {
                                    sharedViewModel.loadingEvent.value = WaitingViewData(context.getString(R.string.keys_backup_restoring_waiting_message)
                                            + "\n" + context.getString(R.string.keys_backup_restoring_computing_key_waiting_message),
                                            step.progress,
                                            step.total)
                                }
                                is StepProgressListener.Step.DownloadingKey -> {
                                    sharedViewModel.loadingEvent.value = WaitingViewData(context.getString(R.string.keys_backup_restoring_waiting_message)
                                            + "\n" + context.getString(R.string.keys_backup_restoring_downloading_backup_waiting_message),
                                            isIndeterminate = true)
                                }
                                is StepProgressListener.Step.ImportingKey -> {
                                    // Progress 0 can take a while, display an indeterminate progress in this case
                                    if (step.progress == 0) {
                                        sharedViewModel.loadingEvent.value = WaitingViewData(context.getString(R.string.keys_backup_restoring_waiting_message)
                                                + "\n" + context.getString(R.string.keys_backup_restoring_importing_keys_waiting_message),
                                                isIndeterminate = true)

                                    } else {
                                        sharedViewModel.loadingEvent.value = WaitingViewData(context.getString(R.string.keys_backup_restoring_waiting_message)
                                                + "\n" + context.getString(R.string.keys_backup_restoring_importing_keys_waiting_message),
                                                step.progress,
                                                step.total)
                                    }
                                }
                            }
                        }
                    },
                    object : ApiCallback<ImportRoomKeysResult> {
                        override fun onSuccess(info: ImportRoomKeysResult) {
                            sharedViewModel.loadingEvent.value = null
                            sharedViewModel.didRecoverSucceed(info)

                            KeysBackupBanner.onRecoverDoneForVersion(context, keysVersionResult.version!!)
                            trustOnDecrypt(keysBackup, keysVersionResult)
                        }

                        override fun onUnexpectedError(e: Exception) {
                            sharedViewModel.loadingEvent.value = null
                            passphraseErrorText.value = context.getString(R.string.keys_backup_passphrase_error_decrypt)
                            Log.e(LOG_TAG, "## onUnexpectedError ${e.localizedMessage}", e)
                        }

                        override fun onNetworkError(e: Exception) {
                            sharedViewModel.loadingEvent.value = null
                            passphraseErrorText.value = context.getString(R.string.network_error_please_check_and_retry)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            sharedViewModel.loadingEvent.value = null
                            passphraseErrorText.value = context.getString(R.string.keys_backup_passphrase_error_decrypt)
                            Log.e(LOG_TAG, "## onMatrixError ${e.localizedMessage}")
                        }
                    })
        } else {
            //Can this happen?
            Log.e(KeysBackupRestoreFromPassphraseViewModel::class.java.name, "Cannot find keys backup")
        }
    }

    private fun trustOnDecrypt(keysBackup: KeysBackup, keysVersionResult: KeysVersionResult) {
        keysBackup.trustKeysBackupVersion(keysVersionResult, true,
                object : SimpleApiCallback<Void>() {

                    override fun onSuccess(info: Void?) {
                        Log.d(LOG_TAG, "##### trustKeysBackupVersion onSuccess")
                    }

                })
    }

    companion object {
        private val LOG_TAG = KeysBackupRestoreFromPassphraseViewModel::class.java.name
    }
}