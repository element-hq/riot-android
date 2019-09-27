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

import com.airbnb.mvrx.*
import com.google.i18n.phonenumbers.PhoneNumberUtil
import im.vector.Matrix
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.features.identityserver.IdentityServerManager
import org.matrix.androidsdk.features.terms.TermsNotSignedException
import org.matrix.androidsdk.rest.model.SuccessResult
import org.matrix.androidsdk.rest.model.pid.ThreePid


data class PidInfo(
        val value: String,
        val isShared: Async<SharedState>,
        val _3pid: ThreePid? = null
) {
    enum class SharedState {
        SHARED,
        NOT_SHARED,
        NOT_VERIFIED_FOR_BIND,
        NOT_VERIFIED_FOR_UNBIND
    }
}

data class PidState(val value: String, val isShared: Async<Boolean>)

data class DiscoverySettingsState(
        val modalLoadingState: Async<Boolean> = Success(false),
        val identityServer: Async<String?> = Success(null),
        val emailList: Async<List<PidInfo>> = Success(emptyList()),
        val phoneNumbersList: Async<List<PidInfo>> = Success(emptyList()),
        val termsNotSigned: Boolean = false
) : MvRxState

class DiscoverySettingsViewModel(initialState: DiscoverySettingsState, private val mxSession: MXSession) : BaseMvRxViewModel<DiscoverySettingsState>(initialState, false) {

    fun changeIdentityServer(server: String?) {
        setState {
            copy(modalLoadingState = Loading())
        }

        mxSession.identityServerManager.setIdentityServerUrl(server, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                setState {
                    copy(
                            modalLoadingState = Success(false),
                            identityServer = Success(server)
                    )
                }
                refreshModel()
            }

            override fun onUnexpectedError(e: Exception) {
                setState {
                    copy(
                            modalLoadingState = Success(false),
                            identityServer = Fail(e)
                    )
                }
            }

            override fun onNetworkError(e: Exception) {
                setState {
                    copy(
                            modalLoadingState = Success(false),
                            identityServer = Fail(e)
                    )
                }
            }

            override fun onMatrixError(e: MatrixError) {
                setState {
                    copy(
                            modalLoadingState = Success(false),
                            identityServer = Fail(Throwable(e.message))
                    )
                }
            }

        })
    }

    fun shareEmail(email: String) = withState { state ->
        if (state.identityServer() == null) return@withState
        changeMailState(email, Loading(), null)

        mxSession.identityServerManager.startBindSessionForEmail(email, null,
                object : ApiCallback<ThreePid> {
                    override fun onSuccess(threePid: ThreePid) {
                        changeMailState(email, Success(PidInfo.SharedState.NOT_VERIFIED_FOR_BIND), threePid)
                    }

                    override fun onUnexpectedError(e: Exception) {
                        handleDeleteError(e)
                    }

                    override fun onNetworkError(e: Exception) {
                        handleDeleteError(e)
                    }

                    override fun onMatrixError(e: MatrixError) {
                        handleDeleteError(Exception(e.message))
                    }

                    private fun handleDeleteError(e: Exception) {
                        changeMailState(email, Fail(e))
                    }

                })

    }

    private fun changeMailState(address: String, state: Async<PidInfo.SharedState>, threePid: ThreePid?) {
        setState {
            val currentMails = emailList() ?: emptyList()
            copy(emailList = Success(
                    currentMails.map {
                        if (it.value == address) {
                            it.copy(
                                    _3pid = threePid,
                                    isShared = state
                            )
                        } else {
                            it
                        }
                    }
            ))
        }
    }

    private fun changeMailState(address: String, state: Async<PidInfo.SharedState>) {
        setState {
            val currentMails = emailList() ?: emptyList()
            copy(emailList = Success(
                    currentMails.map {
                        if (it.value == address) {
                            it.copy(isShared = state)
                        } else {
                            it
                        }
                    }
            ))
        }
    }

    private fun changePNState(address: String, state: Async<PidInfo.SharedState>, threePid: ThreePid?) {
        setState {
            val phones = phoneNumbersList() ?: emptyList()
            copy(phoneNumbersList = Success(
                    phones.map {
                        if (it.value == address) {
                            it.copy(
                                    _3pid = threePid,
                                    isShared = state
                            )
                        } else {
                            it
                        }
                    }
            ))
        }
    }

    fun revokeEmail(email: String) = withState { state ->
        if (state.identityServer() == null) return@withState
        if (state.emailList() == null) return@withState
        changeMailState(email, Loading())

        mxSession.identityServerManager.startUnBindSession(ThreePid.MEDIUM_EMAIL, email, null, object : ApiCallback<Pair<Boolean, ThreePid?>> {
            override fun onSuccess(info: Pair<Boolean, ThreePid?>) {
                if (info.first /*requires mail validation */) {
                    changeMailState(email, Success(PidInfo.SharedState.NOT_VERIFIED_FOR_UNBIND), info.second)
                } else {
                    changeMailState(email, Success(PidInfo.SharedState.NOT_SHARED))
                }
            }

            override fun onUnexpectedError(e: Exception) {
                handleDeleteError(e)
            }

            override fun onNetworkError(e: Exception) {
                handleDeleteError(e)
            }

            override fun onMatrixError(e: MatrixError) {
                handleDeleteError(Exception(e.message))
            }

            private fun handleDeleteError(e: Exception) {
                changeMailState(email, Fail(e))
            }

        })

    }

    fun revokePN(pn: String) = withState { state ->
        if (state.identityServer() == null) return@withState
        if (state.emailList() == null) return@withState
        changePNState(pn, Loading())

        val phoneNumber = PhoneNumberUtil.getInstance()
                .parse("+${pn}", null)
        val countryCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(phoneNumber.countryCode)

        mxSession.identityServerManager.startUnBindSession(ThreePid.MEDIUM_MSISDN, pn, countryCode, object : ApiCallback<Pair<Boolean, ThreePid?>> {
            override fun onSuccess(info: Pair<Boolean, ThreePid?>) {
                if (info.first /*requires mail validation */) {
                    changePNState(pn, Success(PidInfo.SharedState.NOT_VERIFIED_FOR_UNBIND), info.second)
                } else {
                    changePNState(pn, Success(PidInfo.SharedState.NOT_SHARED))
                }
            }

            override fun onUnexpectedError(e: Exception) {
                handleDeleteError(e)
            }

            override fun onNetworkError(e: Exception) {
                handleDeleteError(e)
            }

            override fun onMatrixError(e: MatrixError) {
                handleDeleteError(Exception(e.message))
            }

            private fun handleDeleteError(e: Exception) {
                changePNState(pn, Fail(e))
            }

        })

    }

    fun sharePN(pn: String) = withState { state ->
        if (state.identityServer() == null) return@withState
        changePNState(pn, Loading())

        val phoneNumber = PhoneNumberUtil.getInstance()
                .parse("+${pn}", null)
        val countryCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(phoneNumber.countryCode)


        mxSession.identityServerManager.startBindSessionForPhoneNumber(pn, countryCode, null, object : ApiCallback<ThreePid> {
            override fun onSuccess(id: ThreePid) {
                changePNState(pn, Success(PidInfo.SharedState.NOT_VERIFIED_FOR_BIND), id)
            }

            override fun onUnexpectedError(e: Exception) {
                handleDeleteError(e)
            }

            override fun onNetworkError(e: Exception) {
                handleDeleteError(e)
            }

            override fun onMatrixError(e: MatrixError) {
                handleDeleteError(Exception(e.message))
            }


            private fun handleDeleteError(e: Exception) {
                changePNState(pn, Fail(e))
            }

        })
    }

    private fun changePNState(pn: String, sharedState: Async<PidInfo.SharedState>) {
        setState {
            val currentPNS = phoneNumbersList()!!
            copy(phoneNumbersList = Success(
                    currentPNS.map {
                        if (it.value == pn) {
                            it.copy(isShared = sharedState)
                        } else {
                            it
                        }
                    })
            )
        }
    }

    fun startListenToIdentityManager() {
        setState {
            copy(identityServer = Success(mxSession.identityServerManager.getIdentityServerUrl()))
        }
        mxSession.identityServerManager.addListener(identityServerManagerListener)
    }

    fun stopListenToIdentityManager() {
        mxSession.identityServerManager.addListener(identityServerManagerListener)
    }

    private val identityServerManagerListener = object : IdentityServerManager.IdentityServerManagerListener {
        override fun onIdentityServerChange() = withState { state ->
            val identityServerUrl = mxSession.identityServerManager.getIdentityServerUrl()
            val currentIS = state.identityServer()
            setState {
                copy(identityServer = Success(identityServerUrl))
            }
            if (currentIS != identityServerUrl) refreshModel()
        }
    }

    fun refreshModel() = withState { state ->

        if (state.identityServer().isNullOrBlank()) return@withState

        setState {
            copy(emailList = Loading())
        }

        mxSession.myUser?.refreshThirdPartyIdentifiers(object : ApiCallback<Void> {
            override fun onUnexpectedError(e: Exception) {
                setState {
                    copy(emailList = Fail(e))
                }
            }

            override fun onNetworkError(e: Exception) {
                setState {
                    copy(emailList = Fail(e))
                }
            }

            override fun onMatrixError(e: MatrixError) {
                setState {
                    copy(emailList = Fail(Throwable(e.message)))
                }
            }

            override fun onSuccess(info: Void?) {
                val linkedMailsInfo = mxSession.myUser.getlinkedEmails()
                setState {
                    copy(
                            emailList = Success(
                                    linkedMailsInfo?.map { PidInfo(it.address, Loading()) }
                                            ?: emptyList()
                            )
                    )
                }

                val knownEmailList = linkedMailsInfo.map { it.address }
                setState {
                    copy(termsNotSigned = false)
                }
                mxSession.identityServerManager.lookup3Pids(knownEmailList, linkedMailsInfo.map { it.medium },
                        object : ApiCallback<List<String>> {
                            override fun onSuccess(info: List<String>?) {
                                setState {
                                    copy(
                                            emailList = Success(linkedMailsInfo.map {
                                                val hasMatrxId = info?.get(knownEmailList.indexOf(it.address))?.isBlank()?.not()
                                                        ?: false
                                                PidInfo(
                                                        value = it.address,
                                                        isShared = Success(PidInfo.SharedState.SHARED.takeIf { hasMatrxId }
                                                                ?: PidInfo.SharedState.NOT_SHARED)
                                                )
                                            })
                                    )
                                }
                            }


                            override fun onUnexpectedError(e: Exception) {
                                if (e is TermsNotSignedException) {
                                    setState {
                                        copy(termsNotSigned = true)
                                    }
                                }
                                onError(e)
                            }

                            override fun onNetworkError(e: Exception) {
                                onError(e)
                            }

                            override fun onMatrixError(e: MatrixError) {
                                onError(Throwable(e.message))
                            }

                            fun onError(e: Throwable) {
                                setState {
                                    copy(
                                            emailList = Success(linkedMailsInfo.map {
                                                PidInfo(
                                                        value = it.address,
                                                        isShared = Fail(e)
                                                )
                                            })
                                    )
                                }
                            }

                        })

                val linkedPNInfo = mxSession.myUser.getlinkedPhoneNumbers()
                setState {
                    copy(
                            phoneNumbersList = Success(
                                    linkedPNInfo?.map { PidInfo(it.address, Loading()) }
                                            ?: emptyList()
                            )
                    )
                }

                val knownPns = linkedPNInfo.map { it.address }
                mxSession.identityServerManager.lookup3Pids(knownPns, linkedPNInfo.map { it.medium },
                        object : ApiCallback<List<String>> {
                            override fun onSuccess(info: List<String>?) {
                                setState {
                                    copy(
                                            phoneNumbersList = Success(linkedPNInfo.map {
                                                val hasMatrixId = (info?.get(knownPns.indexOf(it.address))?.isBlank()?.not()
                                                        ?: false)
                                                PidInfo(
                                                        value = it.address,
                                                        isShared = Success(PidInfo.SharedState.SHARED.takeIf { hasMatrixId }
                                                                ?: PidInfo.SharedState.NOT_SHARED)
                                                )
                                            })
                                    )
                                }
                            }


                            override fun onUnexpectedError(e: Exception) {
                                if (e is TermsNotSignedException) {
                                    setState {
                                        copy(termsNotSigned = true)
                                    }
                                }
                                onError(e)
                            }

                            override fun onNetworkError(e: Exception) {
                                onError(e)
                            }

                            override fun onMatrixError(e: MatrixError) {
                                onError(Throwable(e.message))
                            }

                            fun onError(e: Throwable) {
                                setState {
                                    copy(
                                            phoneNumbersList = Success(linkedPNInfo.map {
                                                PidInfo(
                                                        value = it.address,
                                                        isShared = Fail(e)
                                                )
                                            })
                                    )
                                }
                            }

                        })
            }

        })
    }

    fun submitPNToken(msisdn: String, code: String, bind: Boolean) = withState { state ->
        val pid = state.phoneNumbersList()?.find { it.value == msisdn }?._3pid ?: return@withState

        mxSession.identityServerManager.submitValidationToken(pid,
                code,
                object : ApiCallback<SuccessResult> {
                    override fun onSuccess(info: SuccessResult) {
                        add3pid(ThreePid.MEDIUM_MSISDN, msisdn, bind)
                    }

                    override fun onNetworkError(e: Exception) {
                        changePNState(msisdn, Fail(e))
                    }

                    override fun onMatrixError(e: MatrixError) {
                        changePNState(msisdn, Fail(Throwable(e.message)))
                    }

                    override fun onUnexpectedError(e: Exception) {
                        changePNState(msisdn, Fail(e))
                    }

                }
        )
    }

    fun add3pid(medium: String, address: String, bind: Boolean) = withState { state ->
        val _3pid: ThreePid
        if (medium == ThreePid.MEDIUM_EMAIL) {
            changeMailState(address, Loading())
            _3pid = state.emailList()?.find { it.value == address }?._3pid ?: return@withState
        } else {
            changePNState(address, Loading())
            _3pid = state.phoneNumbersList()?.find { it.value == address }?._3pid ?: return@withState
        }

        mxSession.identityServerManager.finalizeBindSessionFor3PID(_3pid, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                val sharedState = Success(if (bind) PidInfo.SharedState.SHARED else PidInfo.SharedState.NOT_SHARED)
                if (medium == ThreePid.MEDIUM_EMAIL) {
                    changeMailState(address, sharedState, null)
                } else {
                    changePNState(address, sharedState, null)
                }
            }

            override fun onUnexpectedError(e: Exception) {
                reportError(e)
            }

            override fun onNetworkError(e: Exception) {
                reportError(e)
            }

            private fun reportError(e: Exception) {
                val sharedState = Success(if (bind) PidInfo.SharedState.NOT_VERIFIED_FOR_BIND else PidInfo.SharedState.NOT_VERIFIED_FOR_UNBIND)
                if (medium == ThreePid.MEDIUM_EMAIL) {
                    changeMailState(address, sharedState)
                } else {
                    changePNState(address, sharedState)
                }
            }


            override fun onMatrixError(e: MatrixError) {
                reportError(Exception(e.message))
            }

        })

    }

    companion object : MvRxViewModelFactory<DiscoverySettingsViewModel, DiscoverySettingsState> {

        override fun create(viewModelContext: ViewModelContext, state: DiscoverySettingsState): DiscoverySettingsViewModel? {
            val matrixId = viewModelContext.args<String>()
            val mxSession = Matrix.getInstance(viewModelContext.activity).getSession(matrixId)
            val viewModel = DiscoverySettingsViewModel(state, mxSession)

            mxSession.identityServerManager.getIdentityServerUrl().let {
                viewModel.setState {
                    copy(identityServer = Success(it))
                }
            }

            viewModel.refreshModel()

            return viewModel
        }

        override fun initialState(viewModelContext: ViewModelContext): DiscoverySettingsState? {

            return DiscoverySettingsState(
                    identityServer = Success(null),
                    emailList = Success(emptyList()),
                    phoneNumbersList = Success(emptyList())
            )
        }
    }

}