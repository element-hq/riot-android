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
package im.vector.fragments.discovery

import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import com.airbnb.mvrx.BaseMvRxViewModel
import com.airbnb.mvrx.MvRxState
import com.airbnb.mvrx.MvRxViewModelFactory
import com.airbnb.mvrx.ViewModelContext
import im.vector.Matrix
import im.vector.R
import im.vector.ui.arch.LiveEvent
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.HttpException
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.features.terms.GetTermsResponse
import org.matrix.androidsdk.features.terms.TermsManager

data class SetIdentityServerState(
        val existingIdentityServer: String? = null,
        val newIdentityServer: String? = null,
        @StringRes val errorMessageId: Int? = null,
        val isVerifyingServer: Boolean = false
) : MvRxState


sealed class NavigateEvent {
    data class ShowTerms(val newIdentityServer: String) : NavigateEvent()
    object NoTerms : NavigateEvent()
    object TermsAccepted : NavigateEvent()
}

class SetIdentityServerViewModel(private val mxSession: MXSession,
                                 private val userLanguage: String,
                                 initialState: SetIdentityServerState)
    : BaseMvRxViewModel<SetIdentityServerState>(initialState, false) {

    var navigateEvent = MutableLiveData<LiveEvent<NavigateEvent>>()

    fun updateServerName(server: String) {
        setState {
            copy(
                    newIdentityServer = server,
                    errorMessageId = null
            )
        }
    }

    fun doChangeServerName() = withState {
        var baseUrl: String? = it.newIdentityServer
        if (baseUrl.isNullOrBlank()) {
            setState {
                copy(errorMessageId = R.string.settings_discovery_please_enter_server)
            }
            return@withState
        }
        baseUrl = sanitatizeBaseURL(baseUrl)
        setState {
            copy(isVerifyingServer = true)
        }

        mxSession.termsManager.get(TermsManager.ServiceType.IdentityService,
                baseUrl,
                object : ApiCallback<GetTermsResponse> {
                    override fun onSuccess(info: GetTermsResponse) {
                        //has all been accepted?
                        setState {
                            copy(isVerifyingServer = false)
                        }
                        val resp = info.serverResponse
                        val tos = resp.getLocalizedTerms(userLanguage)
                        if (tos.isEmpty()) {
                            //prompt do not define policy
                            navigateEvent.value = LiveEvent(NavigateEvent.NoTerms)
                        } else {
                            val shouldPrompt = tos.any { !info.alreadyAcceptedTermUrls.contains(it.localizedUrl) }
                            if (shouldPrompt) {
                                navigateEvent.value = LiveEvent(NavigateEvent.ShowTerms(baseUrl))
                            } else {
                                navigateEvent.value = LiveEvent(NavigateEvent.TermsAccepted)
                            }
                        }
                    }

                    override fun onUnexpectedError(e: Exception) {
                        if (e is HttpException && e.httpError.httpCode == 404) {
                            setState {
                                copy(isVerifyingServer = false)
                            }
                            navigateEvent.value = LiveEvent(NavigateEvent.NoTerms)
                        } else {
                            setState {
                                copy(
                                        isVerifyingServer = false,
                                        errorMessageId = R.string.settings_discovery_bad_identity_server
                                )
                            }
                        }
                    }

                    override fun onNetworkError(e: Exception) {
                        setState {
                            copy(
                                    isVerifyingServer = false,
                                    errorMessageId = R.string.settings_discovery_bad_identity_server
                            )
                        }
                    }

                    override fun onMatrixError(e: MatrixError) {
                        setState {
                            copy(
                                    isVerifyingServer = false,
                                    errorMessageId = R.string.settings_discovery_bad_identity_server
                            )
                        }
                    }

                })
    }


    companion object : MvRxViewModelFactory<SetIdentityServerViewModel, SetIdentityServerState> {

        fun sanitatizeBaseURL(baseUrl: String): String {
            var baseUrl1 = baseUrl
            if (!baseUrl1.startsWith("http://") && !baseUrl1.startsWith("https://")) {
                baseUrl1 = "https://$baseUrl1"
            }
            return baseUrl1
        }

        override fun create(viewModelContext: ViewModelContext, state: SetIdentityServerState): SetIdentityServerViewModel? {
            val fArgs = viewModelContext.args<SetIdentityServerFragmentArgs>()
            val session = Matrix.getInstance(viewModelContext.activity).getSession(fArgs.matrixId)
            return SetIdentityServerViewModel(
                    session,
                    viewModelContext.activity.getString(R.string.resources_language),
                    SetIdentityServerState(fArgs.serverName)
            )
        }
    }
}