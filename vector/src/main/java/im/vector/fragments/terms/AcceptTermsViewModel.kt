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
package im.vector.fragments.terms

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel

class AcceptTermsViewModel : ViewModel() {

    var termsList: MutableLiveData<List<Term>> = MutableLiveData()

    fun acceptTerm(url: String, accepted: Boolean) {

        termsList.value?.map {
            if (it.url == url) {
                it.copy(accepted = accepted)
            } else it
        }?.let {
            termsList.postValue(it)
        }
    }

    fun reviewTerm(url: String) {

    }


}

data class Term (
        val url: String,
        val name: String,
        val description: String? = null,
        val version: String? = null,
        val accepted: Boolean = false
)