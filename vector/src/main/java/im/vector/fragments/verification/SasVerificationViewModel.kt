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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import im.vector.ui.arch.LiveEvent
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.verification.*
import org.matrix.androidsdk.rest.model.User

class SasVerificationViewModel : ViewModel(), VerificationManager.VerificationManagerListener {


    companion object {
        const val NAVIGATE_FINISH = "NAVIGATE_FINISH"
        const val NAVIGATE_FINISH_SUCCESS = "NAVIGATE_FINISH_SUCCESS"
        const val NAVIGATE_SAS_DISPLAY = "NAVIGATE_SAS_DISPLAY"
        const val NAVIGATE_SUCCESS = "NAVIGATE_SUCCESS"
        const val NAVIGATE_CANCELLED = "NAVIGATE_CANCELLED"
    }

    lateinit var session: MXSession

    var otherUserId: String? = null
    var otherDeviceId: String? = null
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
                transaction = session.crypto?.shortCodeVerificationManager?.getExistingTransaction(otherUserId!!, value) as? SASVerificationTransaction
                transactionState.value = transaction?.state
                otherDeviceId = transaction?.otherDeviceId
            }
            field = value
        }


    fun initIncoming(session: MXSession, otherUserId: String, transactionID: String?) {
        this.session = session
        this.otherUserId = otherUserId
        this.transactionID = transactionID
        session.crypto?.shortCodeVerificationManager?.addListener(this)
        this.otherUser = session.dataHandler.store?.getUser(otherUserId)
        if (transactionID == null || transaction == null) {
            //sanity, this transaction is not known anymore
            _navigateEvent.value = LiveEvent(NAVIGATE_FINISH)
        }
    }

    fun initOutgoing(session: MXSession, otherUserId: String, otherDeviceId: String) {
        this.session = session
        this.otherUserId = otherUserId
        this.otherDeviceId = otherDeviceId
        session.crypto?.shortCodeVerificationManager?.addListener(this)
        this.otherUser = session.dataHandler.store?.getUser(otherUserId)
    }

    fun beginSasKeyVerification() {
        val verificationSAS = session.crypto?.shortCodeVerificationManager?.beginKeyVerificationSAS(otherUserId!!, otherDeviceId!!)
        this.transactionID = verificationSAS
    }


    override fun transactionCreated(tx: VerificationTransaction) {

    }

    override fun transactionUpdated(tx: VerificationTransaction) {
        if (transactionID == tx.transactionId && tx is SASVerificationTransaction) {
            transactionState.value = tx.state
        }
    }

    override fun markedAsManuallyVerified(userId: String, deviceId: String) {

    }

    fun cancelTransaction() {
        transaction?.cancel(session, CancelCode.User)
        _navigateEvent.value = LiveEvent(NAVIGATE_FINISH)
    }

    fun finishSuccess() {
        _navigateEvent.value = LiveEvent(NAVIGATE_FINISH_SUCCESS)
    }

    fun manuallyVerified() {
        if (otherUserId != null && otherDeviceId != null) {
            session.crypto?.shortCodeVerificationManager?.markedLocallyAsManuallyVerified(otherUserId!!, otherDeviceId!!)
        }
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
        _navigateEvent.value = LiveEvent(NAVIGATE_SAS_DISPLAY)
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
        if (::session.isInitialized) {
            session.crypto?.shortCodeVerificationManager?.removeListener(this)
        }
    }


}