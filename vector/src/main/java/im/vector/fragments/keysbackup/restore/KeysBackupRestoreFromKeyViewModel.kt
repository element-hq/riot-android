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

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.Context
import im.vector.R
import im.vector.ui.arch.LiveEvent
import im.vector.view.KeysBackupBanner
import org.matrix.androidsdk.crypto.data.ImportRoomKeysResult
import org.matrix.androidsdk.crypto.keysbackup.KeysBackup
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.callback.SimpleApiCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.keys.KeysVersionResult
import org.matrix.androidsdk.util.Log

class KeysBackupRestoreFromKeyViewModel : ViewModel() {

    var recoveryCode: MutableLiveData<String> = MutableLiveData()
    var recoveryCodeErrorText: MutableLiveData<String> = MutableLiveData()

    init {
        recoveryCode.value = null
        recoveryCodeErrorText.value = null
    }

    //========= Actions =========
    fun updateCode(newValue: String) {
        recoveryCode.value = newValue
        recoveryCodeErrorText.value = null
    }

    fun recoverKeys(context: Context, sharedViewModel: KeysBackupRestoreSharedViewModel) {
        val session = sharedViewModel.session
        val keysBackup = session.crypto?.keysBackup
        if (keysBackup != null) {
            sharedViewModel.loadingEvent.value = LiveEvent(R.string.keys_backup_restoring_waiting_message)
            recoveryCodeErrorText.value = null
            val recoveryKey = recoveryCode.value!!

            val keysVersionResult = sharedViewModel.keyVersionResult.value
            val version = keysVersionResult!!.version!!

            keysBackup.restoreKeysWithRecoveryKey(version,
                    recoveryKey,
                    null,
                    session.myUserId,
                    object : ApiCallback<ImportRoomKeysResult> {
                        override fun onSuccess(info: ImportRoomKeysResult) {
                            sharedViewModel.loadingEvent.value = null
                            sharedViewModel.didRecoverSucceed(info)

                            KeysBackupBanner.onRecoverDoneForVersion(context, version)
                            trustOnDecrypt(keysBackup, keysVersionResult)
                        }

                        override fun onUnexpectedError(e: Exception) {
                            sharedViewModel.loadingEvent.value = null
                            recoveryCodeErrorText.value = context.getString(R.string.keys_backup_recovery_code_error_decrypt)
                            Log.e(LOG_TAG, "## onUnexpectedError ${e.localizedMessage}", e)
                        }

                        override fun onNetworkError(e: Exception) {
                            sharedViewModel.loadingEvent.value = null
                            recoveryCodeErrorText.value = context.getString(R.string.network_error_please_check_and_retry)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            sharedViewModel.loadingEvent.value = null
                            recoveryCodeErrorText.value = context.getString(R.string.keys_backup_recovery_code_error_decrypt)
                            Log.e(LOG_TAG, "## onMatrixError ${e.localizedMessage}")
                        }
                    })
        } else {
            //Can this happen?
            Log.e(LOG_TAG, "Cannot find keysBackup")
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
        private val LOG_TAG = KeysBackupRestoreFromKeyViewModel::class.java.name
    }
}