package im.vector.fragments.keybackupsetup

import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.text.TextUtils
import com.nulabinc.zxcvbn.Strength

class KeybackupSetupStep2ViewModel : ViewModel() {

    var passphrase: MutableLiveData<String> = MutableLiveData()

    var confirmPassphrase: MutableLiveData<String> = MutableLiveData()

    var passwordStrength: MutableLiveData<Strength> = MutableLiveData()


    var confirmPassphraseError: MutableLiveData<Int> = MutableLiveData()


    var showPasswordMode: MutableLiveData<Boolean> = MutableLiveData()

    val formValidLiveData = MediatorLiveData<Boolean>().apply {
        addSource(passphrase) { value = checkValidity() }
        addSource(confirmPassphrase) { value = checkValidity() }
        addSource(passwordStrength) { value = checkValidity() }
    }

    init {
        showPasswordMode.value = false
    }

    fun toggleVisibilityMode() {
        showPasswordMode.value = !(showPasswordMode.value ?: false)
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
