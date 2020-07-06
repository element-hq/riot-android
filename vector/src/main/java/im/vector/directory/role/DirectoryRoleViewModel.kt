package im.vector.directory.role

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DirectoryRoleViewModel : ViewModel() {
    val advancedSearchVisibility = MutableLiveData<Boolean>()

    init {
        advancedSearchVisibility.value = false
    }

    fun toggleSearchView() {
        if (advancedSearchVisibility.value == false) {
            advancedSearchVisibility.postValue(true)
        } else {
            advancedSearchVisibility.postValue(false)
        }
    }
}