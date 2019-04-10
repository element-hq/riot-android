/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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

package im.vector

import android.content.Context
import android.text.TextUtils
import im.vector.activity.ShortCodeDeviceVerificationActivity
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequestCancellation
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap
import org.matrix.androidsdk.crypto.verification.SASVerificationTransaction
import org.matrix.androidsdk.crypto.verification.VerificationManager
import org.matrix.androidsdk.crypto.verification.VerificationTransaction
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.callback.SimpleApiCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.util.Log
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Manage the key share events.
 * Listens for incoming key request and display an alert to the user asking him to ignore / verify
 * calling device / or accept without verifying.
 * If several requests come from same user/device, a single alert is displayed (this alert will accept/reject all request
 * depending on user action)
 */
class KeyRequestHandler private constructor() : VerificationManager.ManagerListener {

    private val alertsToRequests = HashMap<String, ArrayList<IncomingRoomKeyRequest>>()

    /**
     * Handle incoming key request.
     *
     * @param keyRequest the key request.
     */
    fun handleKeyRequest(keyRequest: IncomingRoomKeyRequest) {
        val userId = keyRequest.mUserId
        val deviceId = keyRequest.mDeviceId
        val requestId = keyRequest.mRequestId

        if (userId.isNullOrBlank() || deviceId.isNullOrBlank() || requestId.isNullOrBlank()) {
            Log.e(LOG_TAG, "## handleKeyRequest() : invalid parameters")
            return
        }

        //Do we already have alerts for this user/device
        val mappingKey = keyForMap(deviceId, userId)
        if (alertsToRequests.containsKey(mappingKey)) {
            //just add the request, there is already an alert for this
            alertsToRequests[mappingKey]?.add(keyRequest)
            return
        }

        alertsToRequests[mappingKey] = ArrayList<IncomingRoomKeyRequest>().apply { this.add(keyRequest) }

        //Add a notification for every incoming request
        val context = VectorApp.getInstance()
        val session = Matrix.getInstance(context)?.defaultSession

        if (context == null || session == null) {
            return
        }

        session.crypto?.deviceList?.downloadKeys(Arrays.asList(userId), false, object : ApiCallback<MXUsersDevicesMap<MXDeviceInfo>> {
            override fun onSuccess(devicesMap: MXUsersDevicesMap<MXDeviceInfo>) {
                val deviceInfo = devicesMap.getObject(deviceId, userId)

                if (null == deviceInfo) {
                    Log.e(LOG_TAG, "## displayKeyShareDialog() : No details found for device $userId:$deviceId")
                    //ignore
                    return
                }

                if (deviceInfo.isUnknown) {
                    session.crypto?.setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, deviceId, userId, object : SimpleApiCallback<Void>() {
                        override fun onSuccess(res: Void?) {
                            deviceInfo.mVerified = MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED
                            postAlert(context, session, userId, deviceId, true, deviceInfo)
                        }
                    })
                } else {
                    postAlert(context, session, userId, deviceId, false, deviceInfo)
                }
            }

            private fun onError(errorMessage: String) {
                Log.e(LOG_TAG, "## displayKeyShareDialog : downloadKeys failed $errorMessage")
                //ignore
            }

            override fun onNetworkError(e: Exception) {
                onError(e.localizedMessage)
            }

            override fun onMatrixError(e: MatrixError) {
                onError(e.message)
            }

            override fun onUnexpectedError(e: Exception) {
                onError(e.localizedMessage)
            }
        })

    }

    internal fun postAlert(context: Context, session: MXSession?, userId: String, deviceId: String, wasNewDevice: Boolean, deviceInfo: MXDeviceInfo?) {


        val deviceName = if (TextUtils.isEmpty(deviceInfo!!.displayName())) deviceInfo.deviceId else deviceInfo.displayName()
        val dialogText = if (wasNewDevice)
            context.getString(R.string.you_added_a_new_device, deviceName)
        else
            context.getString(R.string.your_unverified_device_requesting, deviceName)

        val alert = PopupAlertManager.VectorAlert(
                alertManagerId(deviceId, userId),
                context.getString(R.string.key_share_request),
                dialogText,
                R.drawable.key_small
        )

        alert.colorRes = R.color.key_share_req_accent_color

        val mappingKey = keyForMap(deviceId, userId)
        alert.dismissedAction = Runnable {
            denyAllRequests(mappingKey)
        }

        alert.addButton(
                context.getString(R.string.start_verification),
                Runnable {
                    alert.weakCurrentActivity?.get()?.let {
                        val intent = ShortCodeDeviceVerificationActivity.outgoingIntent(it,
                                session?.myUserId ?: "",
                                userId, deviceId)
                        it.startActivity(intent)
                    }
                },
                false
        )

        alert.addButton(context.getString(R.string.share_without_verifying), Runnable {
            shareAllSessions(mappingKey)
        })

        alert.addButton(context.getString(R.string.ignore), Runnable {
            denyAllRequests(mappingKey)
        })

        PopupAlertManager.postVectorAlert(alert)
    }

    private fun denyAllRequests(mappingKey: String) {
        alertsToRequests[mappingKey]?.forEach {
            it.mIgnore?.run()
        }
        alertsToRequests.remove(mappingKey)
    }

    private fun shareAllSessions(mappingKey: String) {
        alertsToRequests[mappingKey]?.forEach {
            it.mShare?.run()
        }
        alertsToRequests.remove(mappingKey)
    }

    /**
     * Manage a cancellation request.
     *
     * @param cancellation the cancellation request.
     */
    fun handleKeyRequestCancellation(cancellation: IncomingRoomKeyRequestCancellation) {
        // see if we can find the request in the queue
        val userId = cancellation.mUserId
        val deviceId = cancellation.mDeviceId
        val requestId = cancellation.mRequestId

        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(deviceId) || TextUtils.isEmpty(requestId)) {
            Log.e(LOG_TAG, "## handleKeyRequestCancellation() : invalid parameters")
            return
        }

        val alertMgrUniqueKey = alertManagerId(deviceId, userId)
        alertsToRequests[alertMgrUniqueKey]?.removeAll {
            it.mDeviceId == cancellation.mDeviceId
                    && it.mUserId == cancellation.mUserId
                    && it.mRequestId == cancellation.mRequestId
        }
        if (alertsToRequests[alertMgrUniqueKey]?.isEmpty() == true) {
            PopupAlertManager.cancelAlert(alertMgrUniqueKey)
            alertsToRequests.remove(keyForMap(deviceId, userId))
        }
    }

    override fun transactionCreated(tx: VerificationTransaction) {
    }

    override fun transactionUpdated(tx: VerificationTransaction) {
        if (tx is SASVerificationTransaction) {
            val state = tx.state
            if (state == SASVerificationTransaction.SASVerificationTxState.Verified) {
                //ok it's verified, see if we have key request for that
                shareAllSessions("${tx.otherDevice}${tx.otherUserID}")
                PopupAlertManager.cancelAlert("ikr_${tx.otherDevice}${tx.otherUserID}")
            }
        }
    }

    override fun markedAsManuallyVerified(userId: String, deviceId: String) {
        //accept related requests
        shareAllSessions(keyForMap(deviceId, userId))
        PopupAlertManager.cancelAlert(alertManagerId(deviceId, userId))
    }

    private fun keyForMap(deviceId: String, userId: String) = "$deviceId$userId"

    private fun alertManagerId(deviceId: String, userId: String) = "ikr_$deviceId$userId"


    companion object {
        private val LOG_TAG = KeyRequestHandler::class.java.simpleName


        /**
         * Provide the shared instance
         *
         * @return the shared instance
         */
        val sharedInstance: KeyRequestHandler = KeyRequestHandler().apply {
            Matrix.getInstance(VectorApp.getInstance()).defaultSession.crypto?.shortCodeVerificationManager?.addListener(this)
        }

    }

}
