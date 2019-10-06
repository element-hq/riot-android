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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import im.vector.R
import im.vector.activity.util.WaitingViewData
import im.vector.ui.arch.LiveEvent
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.crypto.data.ImportRoomKeysResult
import org.matrix.androidsdk.crypto.model.keys.KeysVersionResult

class KeysBackupRestoreSharedViewModel : ViewModel() {

    companion object {
        const val NAVIGATE_TO_RECOVER_WITH_KEY = "NAVIGATE_TO_RECOVER_WITH_KEY"
        const val NAVIGATE_TO_SUCCESS = "NAVIGATE_TO_SUCCESS"
    }

    lateinit var session: MXSession

    var keyVersionResult: MutableLiveData<KeysVersionResult> = MutableLiveData()

    private var _keyVersionResultError: MutableLiveData<LiveEvent<String>> = MutableLiveData()
    val keyVersionResultError: LiveData<LiveEvent<String>>
        get() = _keyVersionResultError


    private var _navigateEvent: MutableLiveData<LiveEvent<String>> = MutableLiveData()
    val navigateEvent: LiveData<LiveEvent<String>>
        get() = _navigateEvent

    var loadingEvent: MutableLiveData<WaitingViewData> = MutableLiveData()


    var importKeyResult: ImportRoomKeysResult? = null
    var importRoomKeysFinishWithResult: MutableLiveData<LiveEvent<ImportRoomKeysResult>> = MutableLiveData()


    init {
        keyVersionResult.value = null
        _keyVersionResultError.value = null
        loadingEvent.value = null
    }

    fun initSession(session: MXSession) {
        this.session = session
    }


    fun getLatestVersion(context: Context) {
        val keysBackup = session.crypto?.keysBackup
        if (keysBackup == null) {
            //can this happen?
            _keyVersionResultError.value = LiveEvent(context.getString(R.string.keys_backup_no_keysbackup_sdk_error))
        } else {
            loadingEvent.value = WaitingViewData(context.getString(R.string.keys_backup_restore_is_getting_backup_version))

            keysBackup.getCurrentVersion(object : ApiCallback<KeysVersionResult?> {
                override fun onSuccess(info: KeysVersionResult?) {
                    loadingEvent.value = null
                    if (info?.version.isNullOrBlank()) {
                        //should not happen
                        _keyVersionResultError.value = LiveEvent(context.getString(R.string.keys_backup_get_version_error, ""))
                    } else {
                        keyVersionResult.value = info
                    }
                }

                override fun onUnexpectedError(e: Exception) {
                    loadingEvent.value = null
                    _keyVersionResultError.value = LiveEvent(context.getString(R.string.keys_backup_get_version_error, e.localizedMessage))
                }

                override fun onNetworkError(e: Exception) {
                    loadingEvent.value = null
                    _keyVersionResultError.value = LiveEvent(context.getString(R.string.network_error_please_check_and_retry))
                }

                override fun onMatrixError(e: MatrixError) {
                    loadingEvent.value = null
                    _keyVersionResultError.value = LiveEvent(context.getString(R.string.keys_backup_get_version_error, e.localizedMessage))
                }
            })
        }
    }

    fun moveToRecoverWithKey() {
        _navigateEvent.value = LiveEvent(NAVIGATE_TO_RECOVER_WITH_KEY)
    }

    fun didRecoverSucceed(result: ImportRoomKeysResult) {
        importKeyResult = result
        _navigateEvent.value = LiveEvent(NAVIGATE_TO_SUCCESS)
    }
}