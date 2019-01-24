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

package im.vector.fragments.keybackupsetup

import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.text.TextUtils
import com.nulabinc.zxcvbn.Strength
import org.matrix.androidsdk.crypto.keysbackup.MegolmBackupCreationInfo

/**
 * The shared view model between all fragments.
 */
class KeybackupSetupSharedViewModel  : ViewModel() {


    // Step 2
    var passphrase: MutableLiveData<String> = MutableLiveData()
    var confirmPassphrase: MutableLiveData<String> = MutableLiveData()
    var passwordStrength: MutableLiveData<Strength> = MutableLiveData()
    var confirmPassphraseError: MutableLiveData<Int> = MutableLiveData()
    var showPasswordMode: MutableLiveData<Boolean> = MutableLiveData()


    // Step 3
    var recoveryKey: MutableLiveData<String> = MutableLiveData()
    var megolmBackupCreationInfo : MegolmBackupCreationInfo? = null
    var copyHasBeenMade = false
    var isCreatingBackupVersion: MutableLiveData<Boolean> = MutableLiveData()

    val formValidLiveData = MediatorLiveData<Boolean>().apply {
        addSource(passphrase) { value = checkValidity() }
        addSource(confirmPassphrase) { value = checkValidity() }
        addSource(passwordStrength) { value = checkValidity() }
    }

    init {
        showPasswordMode.value = false
        recoveryKey.value = null
        isCreatingBackupVersion.value = false
    }

    fun checkValidity(): Boolean {
        if (TextUtils.isEmpty(passphrase.value)) {
            return false
        }
        if (TextUtils.isEmpty(confirmPassphrase.value)) {
            return false
        } else if (confirmPassphrase.value != passphrase.value) {
            return false
        }
        if (passwordStrength.value?.score ?: 0 <= 3) {
            return false
        }
        return true
    }


}