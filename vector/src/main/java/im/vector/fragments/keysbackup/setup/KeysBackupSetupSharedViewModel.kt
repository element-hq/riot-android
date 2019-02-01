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

package im.vector.fragments.keysbackup.setup

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.nulabinc.zxcvbn.Strength
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.keysbackup.KeysBackup
import org.matrix.androidsdk.crypto.keysbackup.MegolmBackupCreationInfo
import org.matrix.androidsdk.listeners.ProgressListener
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.callback.SuccessErrorCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.keys.KeysVersion
import org.matrix.androidsdk.util.Log

/**
 * The shared view model between all fragments.
 */
class KeysBackupSetupSharedViewModel : ViewModel() {


    lateinit var session: MXSession


    // Step 2
    var passphrase: MutableLiveData<String> = MutableLiveData()
    var passphraseError: MutableLiveData<String> = MutableLiveData()

    var confirmPassphrase: MutableLiveData<String> = MutableLiveData()
    var confirmPassphraseError: MutableLiveData<String> = MutableLiveData()

    var passwordStrength: MutableLiveData<Strength> = MutableLiveData()
    var showPasswordMode: MutableLiveData<Boolean> = MutableLiveData()

    // Step 3
    // Var to ignore events from previous request(s) to generate a recovery key
    private var currentRequestId: MutableLiveData<Long> = MutableLiveData()
    var recoveryKey: MutableLiveData<String> = MutableLiveData()
    var prepareRecoverFailError: MutableLiveData<Exception> = MutableLiveData()
    var prepareRecoveryProgressProgress: MutableLiveData<Int> = MutableLiveData()
    var prepareRecoveryProgressTotal: MutableLiveData<Int> = MutableLiveData()
    var megolmBackupCreationInfo: MegolmBackupCreationInfo? = null
    var copyHasBeenMade = false
    var isCreatingBackupVersion: MutableLiveData<Boolean> = MutableLiveData()
    var creatingBackupError: MutableLiveData<Exception> = MutableLiveData()
    var keysVersion: MutableLiveData<KeysVersion> = MutableLiveData()

    init {
        showPasswordMode.value = false
        recoveryKey.value = null
        isCreatingBackupVersion.value = false
        prepareRecoverFailError.value = null
        creatingBackupError.value = null
    }

    fun initSession(session: MXSession) {
        this.session = session
    }

    fun prepareRecoveryKey(session: MXSession?, withPassphrase: String?) {
        // Update requestId
        currentRequestId.value = System.currentTimeMillis()

        recoveryKey.value = null
        prepareRecoverFailError.value = null
        session?.let { mxSession ->
            val requestedId = currentRequestId.value!!

            prepareRecoveryProgressProgress.value = -1

            mxSession.crypto?.keysBackup?.prepareKeysBackupVersion(withPassphrase,
                    object : ProgressListener {
                        override fun onProgress(progress: Int, total: Int) {
                            if (requestedId != currentRequestId.value) {
                                //this is an old request, we can't cancel but we can ignore
                                return
                            }
                            prepareRecoveryProgressProgress.value = progress
                            prepareRecoveryProgressTotal.value = total
                        }
                    },
                    object : SuccessErrorCallback<MegolmBackupCreationInfo> {
                        override fun onSuccess(info: MegolmBackupCreationInfo) {
                            if (requestedId != currentRequestId.value) {
                                //this is an old request, we can't cancel but we can ignore
                                return
                            }
                            recoveryKey.value = info.recoveryKey
                            megolmBackupCreationInfo = info
                            copyHasBeenMade = false
                        }

                        override fun onUnexpectedError(e: java.lang.Exception?) {
                            if (requestedId != currentRequestId.value) {
                                //this is an old request, we can't cancel but we can ignore
                                return
                            }
                            prepareRecoverFailError.value = e ?: Exception()
                        }
                    })
        }
    }

    fun createKeysBackup(keysBackup: KeysBackup) {
        isCreatingBackupVersion.value = true
        creatingBackupError.value = null
        keysBackup.createKeysBackupVersion(megolmBackupCreationInfo!!, object : ApiCallback<KeysVersion> {

            override fun onSuccess(info: KeysVersion) {
                isCreatingBackupVersion.value = false
                keysVersion.value = info
            }

            override fun onUnexpectedError(e: java.lang.Exception) {
                Log.e(LOG_TAG, "## createKeyBackupVersion ${e.localizedMessage}")
                isCreatingBackupVersion.value = false
                creatingBackupError.value = e
            }

            override fun onNetworkError(e: java.lang.Exception) {
                Log.e(LOG_TAG, "## createKeyBackupVersion ${e.localizedMessage}")
                isCreatingBackupVersion.value = false
                creatingBackupError.value = e
            }

            override fun onMatrixError(e: MatrixError) {
                Log.e(LOG_TAG, "## createKeyBackupVersion ${e.mReason}")
                isCreatingBackupVersion.value = false
                creatingBackupError.value = Exception(e.message)
            }
        })
    }

    companion object {
        private val LOG_TAG = KeysBackupSetupSharedViewModel::class.java.name
    }
}