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
package im.vector.fragments.verification

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import im.vector.ui.arch.LiveEvent
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.verification.*
import org.matrix.androidsdk.rest.model.User

class SasVerificationViewModel : ViewModel(), VerificationManager.ManagerListener {

    companion object {
        const val NAVIGATE_FINISH = "NAVIGATE_FINISH"
        const val NAVIGATE_FINISH_SUCCESS = "NAVIGATE_FINISH_SUCCESS"
        const val NAVIGATE_EMOJI = "NAVIGATE_EMOJI"
        const val NAVIGATE_SUCCESS = "NAVIGATE_SUCCESS"
        const val NAVIGATE_CANCELLED = "NAVIGATE_CANCELLED"
        //const val NAVIGATE_TO_SUCCESS = "NAVIGATE_TO_SUCCESS"
    }

    lateinit var session: MXSession

    var otherUserId: String? = null
    var otherDevice: String? = null
    var otherUser: User? = null
    var transaction: SASVerificationTransaction? = null


    var transactionState: MutableLiveData<SASVerificationTransaction.SASVerificationTxState> = MutableLiveData()

    init {
        //Force a first observe
        transactionState.value = null
    }

    private var _navigateEvent: MutableLiveData<LiveEvent<String>> = MutableLiveData()
    val navigateEvent: LiveData<LiveEvent<String>>
        get() = _navigateEvent


    var loadingLiveEvent: MutableLiveData<Int> = MutableLiveData()

    var transactionID: String? = null
        set(value) {
            if (value != null) {
                transaction = session.crypto?.shortCodeVerificationManager?.getExistingTransaction(otherUserId!!, value
                        ?: "") as? SASVerificationTransaction
                transactionState.value = transaction?.state
                otherDevice = transaction?.otherDevice
            }
            field = value
        }


    fun initSession(session: MXSession, otherUserId: String, transactionID: String?) {
        this.session = session
        this.otherUserId = otherUserId
        this.transactionID = transactionID
        session.crypto?.shortCodeVerificationManager?.addListener(this)
        this.otherUser = session.dataHandler.store.getUser(otherUserId)
    }

    fun initOutgoing(session: MXSession, otherUserId: String, otherDeviceId: String) {
        this.session = session
        this.otherUserId = otherUserId
        this.otherDevice = otherDeviceId
        session.crypto?.shortCodeVerificationManager?.addListener(this)
        this.otherUser = session.dataHandler.store.getUser(otherUserId)
    }

    fun beginSasKeyVerification() {
        val verificationSAS = session.crypto?.shortCodeVerificationManager?.beginKeyVerificationSAS(otherUserId!!, otherDevice!!)
        this.transactionID = verificationSAS
    }


    override fun transactionCreated(tx: VerificationTransaction) {

    }

    override fun transactionUpdated(tx: VerificationTransaction) {
        if (transactionID == tx.transactionId && tx is SASVerificationTransaction) {
            transactionState.value = tx.state
        }
    }

    fun cancelTransaction() {
        transaction?.cancel(session, CancelCode.User)
        _navigateEvent.value = LiveEvent(NAVIGATE_FINISH)
    }

    //TODO reason code
    fun interrupt() {
        _navigateEvent.value = LiveEvent(NAVIGATE_FINISH)
    }

    fun finishSuccess() {
        _navigateEvent.value = LiveEvent(NAVIGATE_FINISH_SUCCESS)
    }


    fun acceptTransaction() {
        (transaction as? IncomingSASVerificationTransaction)?.performAccept(session)
    }

    fun confirmEmojiSame() {
        transaction?.userHasVerifiedShortCode(session)
    }

    fun shortCodeReady() {
        loadingLiveEvent.value = null
        _navigateEvent.value = LiveEvent(NAVIGATE_EMOJI)
    }

    fun deviceIsVerified() {
        loadingLiveEvent.value = null
        _navigateEvent.value = LiveEvent(NAVIGATE_SUCCESS)
    }

    fun navigateCancel() {
        _navigateEvent.value = LiveEvent(NAVIGATE_CANCELLED)
    }

    override fun onCleared() {
        super.onCleared()
        session.crypto?.shortCodeVerificationManager?.removeListener(this)
    }

}