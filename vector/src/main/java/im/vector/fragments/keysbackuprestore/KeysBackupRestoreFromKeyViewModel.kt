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
package im.vector.fragments.keysbackuprestore

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.Context
import im.vector.R
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.data.ImportRoomKeysResult
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.util.Log
import java.lang.Exception

class KeysBackupRestoreFromKeyViewModel : ViewModel() {

    var recoveryCode: MutableLiveData<String> = MutableLiveData()
    var recoveryCodeErrorText: MutableLiveData<String> = MutableLiveData()

    var isRestoring: MutableLiveData<Boolean> = MutableLiveData()
    var importRoomKeysResult: MutableLiveData<ImportRoomKeysResult> = MutableLiveData()

    init {
        recoveryCode.value = null
        recoveryCodeErrorText.value = null
        isRestoring.value = false
        importRoomKeysResult.value = null
    }

    //========= Actions =========
    fun updateCode(newValue: String) {
        recoveryCode.value = newValue
        recoveryCodeErrorText.value = null
    }

    fun recoverKeys(context: Context, session: MXSession, version: String) {
        val keysBackup = session.crypto?.keysBackup
        if (keysBackup != null) {
            isRestoring.value = true
            recoveryCodeErrorText.value = null
            keysBackup.restoreKeyBackupWithRecoveryKey(version,
                    recoveryCode.value!!,
                    null,
                    session.myUserId,
                    object : ApiCallback<ImportRoomKeysResult> {
                        override fun onSuccess(info: ImportRoomKeysResult) {
                            isRestoring.value = false
                            importRoomKeysResult.value = info
                        }

                        override fun onUnexpectedError(e: Exception) {
                            isRestoring.value = false
                            recoveryCodeErrorText.value = context.getString(R.string.keys_backup_recovery_code_error_decrypt, e.localizedMessage)
                        }

                        override fun onNetworkError(e: Exception) {
                            isRestoring.value = false
                            recoveryCodeErrorText.value = context.getString(R.string.keys_backup_passphrase_error_network, e.localizedMessage)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            isRestoring.value = false
                            recoveryCodeErrorText.value = context.getString(R.string.keys_backup_recovery_code_error_decrypt, e.localizedMessage)
                        }
                    })
        } else {
            //Can this happen?
            Log.e(KeysBackupRestoreFromPassphraseViewModel::class.java.name, "Cannot find keys backup")
        }
    }
}