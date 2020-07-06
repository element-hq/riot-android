package im.vector.directory.role

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DirectoryRoleViewModel : ViewModel() {
    val advancedSearchVisibility = MutableLiveData<Boolean>()

    fun toggleSearchView() {
        if (advancedSearchVisibility.value == null || advancedSearchVisibility.value == false) {
            advancedSearchVisibility.postValue(true)
        } else {
            advancedSearchVisibility.postValue(false)
        }
    }
}