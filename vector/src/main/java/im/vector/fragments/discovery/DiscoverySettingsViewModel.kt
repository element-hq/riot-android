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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.airbnb.mvrx.*
import com.google.i18n.phonenumbers.PhoneNumberUtil
import im.vector.Matrix
import im.vector.ui.arch.LiveEvent
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

data class DiscoverySettingsState(
        val identityServer: Async<String?> = Uninitialized,
        val emailList: Async<List<PidInfo>> = Uninitialized,
        val phoneNumbersList: Async<List<PidInfo>> = Uninitialized,
        val termsNotSigned: Boolean = false
) : MvRxState

class DiscoverySettingsViewModel(initialState: DiscoverySettingsState, private val mxSession: MXSession)
    : BaseMvRxViewModel<DiscoverySettingsState>(initialState, false) {

    private val identityServerManagerListener = object : IdentityServerManager.IdentityServerManagerListener {
        override fun onIdentityServerChange() = withState { state ->
            val identityServerUrl = mxSession.identityServerManager.identityServerUrl
            val currentIS = state.identityServer()
            setState {
                copy(identityServer = Success(identityServerUrl))
            }
            if (currentIS != identityServerUrl) refreshModel()
        }
    }

    private val _errorLiveEvent = MutableLiveData<LiveEvent<Throwable>>()
    val errorLiveEvent: LiveData<LiveEvent<Throwable>>
        get() = _errorLiveEvent

    init {
        startListenToIdentityManager()
        refreshModel()
    }

    override fun onCleared() {
        super.onCleared()
        stopListenToIdentityManager()
    }

    fun changeIdentityServer(server: String?) {
        setState {
            copy(
                    identityServer = Loading()
            )
        }

        mxSession.identityServerManager.setIdentityServerUrl(server, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                setState {
                    copy(
                            identityServer = Success(server)
                    )
                }
                refreshModel()
            }

            override fun onUnexpectedError(e: Exception) {
                setState {
                    copy(
                            identityServer = Fail(e)
                    )
                }
            }

            override fun onNetworkError(e: Exception) {
                setState {
                    copy(
                            identityServer = Fail(e)
                    )
                }
            }

            override fun onMatrixError(e: MatrixError) {
                setState {
                    copy(
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
                        _errorLiveEvent.postValue(LiveEvent(e))

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

    private fun changeMsisdnState(address: String, state: Async<PidInfo.SharedState>, threePid: ThreePid?) {
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
                if (info.first) {
                    // requires mail validation
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
                _errorLiveEvent.postValue(LiveEvent(e))

                changeMailState(email, Fail(e))
            }

        })

    }

    fun revokeMsisdn(msisdn: String) = withState { state ->
        if (state.identityServer() == null) return@withState
        if (state.emailList() == null) return@withState
        changeMsisdnState(msisdn, Loading())

        val phoneNumber = PhoneNumberUtil.getInstance()
                .parse("+$msisdn", null)
        val countryCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(phoneNumber.countryCode)

        mxSession.identityServerManager.startUnBindSession(ThreePid.MEDIUM_MSISDN, msisdn, countryCode, object : ApiCallback<Pair<Boolean, ThreePid?>> {
            override fun onSuccess(info: Pair<Boolean, ThreePid?>) {
                if (info.first /*requires mail validation */) {
                    changeMsisdnState(msisdn, Success(PidInfo.SharedState.NOT_VERIFIED_FOR_UNBIND), info.second)
                } else {
                    changeMsisdnState(msisdn, Success(PidInfo.SharedState.NOT_SHARED))
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
                _errorLiveEvent.postValue(LiveEvent(e))

                changeMsisdnState(msisdn, Fail(e))
            }

        })

    }

    fun shareMsisdn(msisdn: String) = withState { state ->
        if (state.identityServer() == null) return@withState
        changeMsisdnState(msisdn, Loading())

        val phoneNumber = PhoneNumberUtil.getInstance()
                .parse("+$msisdn", null)
        val countryCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(phoneNumber.countryCode)


        mxSession.identityServerManager.startBindSessionForPhoneNumber(msisdn, countryCode, null, object : ApiCallback<ThreePid> {
            override fun onSuccess(id: ThreePid) {
                changeMsisdnState(msisdn, Success(PidInfo.SharedState.NOT_VERIFIED_FOR_BIND), id)
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
                _errorLiveEvent.postValue(LiveEvent(e))

                changeMsisdnState(msisdn, Fail(e))
            }

        })
    }

    private fun changeMsisdnState(msisdn: String, sharedState: Async<PidInfo.SharedState>) {
        setState {
            val currentMsisdns = phoneNumbersList()!!
            copy(phoneNumbersList = Success(
                    currentMsisdns.map {
                        if (it.value == msisdn) {
                            it.copy(isShared = sharedState)
                        } else {
                            it
                        }
                    })
            )
        }
    }

    private fun startListenToIdentityManager() {
        mxSession.identityServerManager.addListener(identityServerManagerListener)
    }

    private fun stopListenToIdentityManager() {
        mxSession.identityServerManager.addListener(identityServerManagerListener)
    }

    fun refreshModel() = withState { state ->
        if (state.identityServer().isNullOrBlank()) return@withState

        setState {
            copy(
                    emailList = Loading(),
                    phoneNumbersList = Loading()
            )
        }

        mxSession.myUser.refreshThirdPartyIdentifiers(object : ApiCallback<Void> {
            override fun onUnexpectedError(e: Exception) {
                _errorLiveEvent.postValue(LiveEvent(e))

                setState {
                    copy(
                            emailList = Fail(e),
                            phoneNumbersList = Fail(e)
                    )
                }
            }

            override fun onNetworkError(e: Exception) {
                _errorLiveEvent.postValue(LiveEvent(e))

                setState {
                    copy(
                            emailList = Fail(e),
                            phoneNumbersList = Fail(e)
                    )
                }
            }

            override fun onMatrixError(e: MatrixError) {
                _errorLiveEvent.postValue(LiveEvent(Exception(e.message)))

                setState {
                    copy(
                            emailList = Fail(Throwable(e.message)),
                            phoneNumbersList = Fail(Throwable(e.message))
                    )
                }
            }

            override fun onSuccess(info: Void?) {
                setState {
                    copy(termsNotSigned = false)
                }

                retrieveBinding()
            }
        })
    }

    fun retrieveBinding() {
        val linkedMailsInfo = mxSession.myUser.getlinkedEmails()
        val knownEmails = linkedMailsInfo.map { it.address }
        // Note: it will be a list of "email"
        val knownEmailMedium = linkedMailsInfo.map { it.medium }

        val linkedMsisdnsInfo = mxSession.myUser.getlinkedPhoneNumbers()
        val knownMsisdns = linkedMsisdnsInfo.map { it.address }
        // Note: it will be a list of "msisdn"
        val knownMsisdnMedium = linkedMsisdnsInfo.map { it.medium }

        setState {
            copy(
                    emailList = Success(knownEmails.map { PidInfo(it, Loading()) }),
                    phoneNumbersList = Success(knownMsisdns.map { PidInfo(it, Loading()) })
            )
        }

        mxSession.identityServerManager.lookup3Pids(knownEmails + knownMsisdns,
                knownEmailMedium + knownMsisdnMedium,
                object : ApiCallback<List<String>> {
                    override fun onSuccess(info: List<String>) {
                        setState {
                            copy(
                                    emailList = Success(toPidInfoList(knownEmails, info.take(knownEmails.size))),
                                    phoneNumbersList = Success(toPidInfoList(knownMsisdns, info.takeLast(knownMsisdns.size)))
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

                    private fun onError(e: Throwable) {
                        _errorLiveEvent.postValue(LiveEvent(e))

                        setState {
                            copy(
                                    emailList = Success(knownEmails.map { PidInfo(it, Fail(e)) }),
                                    phoneNumbersList = Success(knownMsisdns.map { PidInfo(it, Fail(e)) })
                            )
                        }
                    }

                })
    }

    private fun toPidInfoList(addressList: List<String>, matrixIds: List<String>): List<PidInfo> {
        return addressList.map {
            val hasMatrixId = matrixIds[addressList.indexOf(it)].isNotBlank()
            PidInfo(
                    value = it,
                    isShared = Success(PidInfo.SharedState.SHARED.takeIf { hasMatrixId } ?: PidInfo.SharedState.NOT_SHARED)
            )
        }
    }

    fun submitMsisdnToken(msisdn: String, code: String, bind: Boolean) = withState { state ->
        val pid = state.phoneNumbersList()?.find { it.value == msisdn }?._3pid ?: return@withState

        mxSession.identityServerManager.submitValidationToken(pid,
                code,
                object : ApiCallback<SuccessResult> {
                    override fun onSuccess(info: SuccessResult) {
                        finalizeBind3pid(ThreePid.MEDIUM_MSISDN, msisdn, bind)
                    }

                    override fun onNetworkError(e: Exception) {
                        _errorLiveEvent.postValue(LiveEvent(e))
                        changeMsisdnState(msisdn, Fail(e))
                    }

                    override fun onMatrixError(e: MatrixError) {
                        _errorLiveEvent.postValue(LiveEvent(Exception(e.message)))
                        changeMsisdnState(msisdn, Fail(Throwable(e.message)))
                    }

                    override fun onUnexpectedError(e: Exception) {
                        _errorLiveEvent.postValue(LiveEvent(e))
                        changeMsisdnState(msisdn, Fail(e))
                    }

                }
        )
    }

    fun finalizeBind3pid(medium: String, address: String, bind: Boolean) = withState { state ->
        val _3pid: ThreePid
        if (medium == ThreePid.MEDIUM_EMAIL) {
            changeMailState(address, Loading())
            _3pid = state.emailList()?.find { it.value == address }?._3pid ?: return@withState
        } else {
            changeMsisdnState(address, Loading())
            _3pid = state.phoneNumbersList()?.find { it.value == address }?._3pid
                    ?: return@withState
        }

        mxSession.identityServerManager.finalizeBindSessionFor3PID(_3pid, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                val sharedState = Success(if (bind) PidInfo.SharedState.SHARED else PidInfo.SharedState.NOT_SHARED)
                if (medium == ThreePid.MEDIUM_EMAIL) {
                    changeMailState(address, sharedState, null)
                } else {
                    changeMsisdnState(address, sharedState, null)
                }
            }

            override fun onUnexpectedError(e: Exception) {
                reportError(e)
            }

            override fun onNetworkError(e: Exception) {
                reportError(e)
            }

            private fun reportError(e: Exception) {
                _errorLiveEvent.postValue(LiveEvent(e))

                // Restore previous state after an error
                val sharedState = Success(if (bind) PidInfo.SharedState.NOT_VERIFIED_FOR_BIND else PidInfo.SharedState.NOT_VERIFIED_FOR_UNBIND)
                if (medium == ThreePid.MEDIUM_EMAIL) {
                    changeMailState(address, sharedState)
                } else {
                    changeMsisdnState(address, sharedState)
                }
            }


            override fun onMatrixError(e: MatrixError) {
                reportError(Exception(e.message))
            }

        })

    }

    fun refreshPendingEmailBindings() = withState { state ->
        state.emailList()?.forEach { info ->
            when (info.isShared()) {
                PidInfo.SharedState.NOT_VERIFIED_FOR_BIND   -> finalizeBind3pid(ThreePid.MEDIUM_EMAIL, info.value, true)
                PidInfo.SharedState.NOT_VERIFIED_FOR_UNBIND -> finalizeBind3pid(ThreePid.MEDIUM_EMAIL, info.value, false)
                else                                        -> Unit
            }
        }
    }


    companion object : MvRxViewModelFactory<DiscoverySettingsViewModel, DiscoverySettingsState> {

        override fun create(viewModelContext: ViewModelContext, state: DiscoverySettingsState): DiscoverySettingsViewModel? {
            val matrixId = viewModelContext.args<String>()
            val mxSession = Matrix.getInstance(viewModelContext.activity).getSession(matrixId)
            return DiscoverySettingsViewModel(state, mxSession)
        }

        override fun initialState(viewModelContext: ViewModelContext): DiscoverySettingsState? {
            val matrixId = viewModelContext.args<String>()
            val mxSession = Matrix.getInstance(viewModelContext.activity).getSession(matrixId)

            return DiscoverySettingsState(
                    identityServer = Success(mxSession.identityServerManager.identityServerUrl),
                    emailList = Success(emptyList()),
                    phoneNumbersList = Success(emptyList())
            )
        }
    }

}