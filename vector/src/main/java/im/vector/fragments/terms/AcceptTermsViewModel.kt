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
import im.vector.R
import im.vector.ui.arch.LiveEvent
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.rest.client.TermsRestClient
import org.matrix.androidsdk.rest.model.sync.AccountDataElement
import org.matrix.androidsdk.rest.model.terms.TermsResponse

class AcceptTermsViewModel : ViewModel() {

    var termsArgs: ServiceTermsArgs? = null

    var termsList: MutableLiveData<List<Term>> = MutableLiveData()
    var isLoading: MutableLiveData<Boolean> = MutableLiveData()
    var hasError: MutableLiveData<Boolean> = MutableLiveData()


    var popError: MutableLiveData<LiveEvent<Int>> = MutableLiveData()

    val client = TermsRestClient()
    var mxSession: MXSession? = null

    fun markTermAsAccepted(url: String, accepted: Boolean) {
        termsList.value?.map {
            if (it.url == url) {
                it.copy(accepted = accepted)
            } else it
        }?.let {
            termsList.postValue(it)
        }
    }

    fun acceptTerms() {
        val acceptedTerms = termsList.value ?: return

        if (termsArgs?.type == TermsRestClient.Companion.ServiceType.IntegrationManager) {
            isLoading.postValue(true)
            val agreedUrls = acceptedTerms.map { it.url }
            client.agreeToTerms("${termsArgs!!.baseURL}_matrix/integrations/v1",
                    agreedUrls,
                    object : ApiCallback<TermsResponse> {
                        override fun onSuccess(info: TermsResponse?) {
                            isLoading.postValue(false)
                            //client SHOULD update this account data section adding any the URLs
                            // of any additional documents that the user agreed to this list.
                            //TODO get current m.accepted_terms append new ones and update account data
                            mxSession?.myUserId?.let { userId ->
                                mxSession?.accountDataRestClient?.setAccountData(
                                        userId, AccountDataElement.ACCOUNT_DATA_ACCEPTED_TERMS,
                                        agreedUrls,
                                        object : SimpleApiCallback<Void>() {
                                            override fun onSuccess(info: Void?) {
                                                Log.d(LOG_TAG, "Account data accepted terms updated")
                                            }
                                        }
                                )
                            }

                        }

                        override fun onUnexpectedError(e: java.lang.Exception?) {
                            isLoading.postValue(false)
                            popError.postValue(LiveEvent(R.string.unknown_error))
                            Log.e(LOG_TAG, "Failed to agree to terms ", e)
                        }

                        override fun onNetworkError(e: java.lang.Exception?) {
                            isLoading.postValue(false)
                            popError.postValue(LiveEvent(R.string.unknown_error))
                            Log.e(LOG_TAG, "Failed to agree to terms ", e)
                        }

                        override fun onMatrixError(e: MatrixError?) {
                            isLoading.postValue(false)
                            popError.postValue(LiveEvent(R.string.unknown_error))
                            Log.e(LOG_TAG, "Failed to agree to terms " + e?.message)
                        }

                    })
        }
    }

    fun reviewTerm(url: String) {

    }

    fun loadTerms() {
        isLoading.postValue(true)
        hasError.postValue(false)
        if (termsArgs?.type == TermsRestClient.Companion.ServiceType.IntegrationManager) {
            client.get("${termsArgs!!.baseURL}_matrix/integrations/v1", object : ApiCallback<TermsResponse> {
                override fun onSuccess(info: TermsResponse?) {
                    val terms = ArrayList<Term>()
                    info?.getLocalizedPrivacyPolicies()?.let {
                        Log.e("FOO", it.localizedUrl)
                        terms.add(
                                Term(it.localizedUrl ?: "",
                                        it.localizedName ?: "",
                                        "Utiliser des robots, des passerelles, des widgets ou des packs de stickers"
                                )
                        )
                    }
                    info?.getLocalizedTermOfServices()?.let {
                        terms.add(
                                Term(it.localizedUrl ?: "",
                                        it.localizedName ?: "",
                                        "Utiliser des robots, des passerelles, des widgets ou des packs de stickers")
                        )
                    }
                    isLoading.postValue(false)
                    termsList.postValue(terms)
                }


                override fun onUnexpectedError(e: Exception?) {
                    hasError.postValue(true)
                    isLoading.postValue(false)
                }

                override fun onNetworkError(e: Exception?) {
                    hasError.postValue(true)
                    isLoading.postValue(false)
                }

                override fun onMatrixError(e: MatrixError?) {
                    hasError.postValue(true)
                    isLoading.postValue(false)
                }

            })
        }
    }

    companion object {
        private val LOG_TAG = AcceptTermsViewModel::javaClass.name
    }

}

data class Term(
        val url: String,
        val name: String,
        val description: String? = null,
        val version: String? = null,
        val accepted: Boolean = false
)