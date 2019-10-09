/*
 * Copyright 2018 New Vector Ltd
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

package im.vector.webview

import im.vector.Matrix
import im.vector.activity.VectorAppCompatActivity
import im.vector.util.weak
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.core.model.MatrixError

private const val SUCCESS_URL_SUFFIX = "/_matrix/consent"
private const val RIOT_BOT_ID = "@riot-bot:matrix.org"
private const val LOG_TAG = "ConsentWebViewEventListener"

/**
 * This class is the Consent implementation of WebViewEventListener.
 * It is used to manage the consent agreement flow.
 */
class ConsentWebViewEventListener(activity: VectorAppCompatActivity, private val delegate: WebViewEventListener)
    : WebViewEventListener by delegate {

    private val safeActivity: VectorAppCompatActivity? by weak(activity)

    override fun onPageFinished(url: String) {
        delegate.onPageFinished(url)
        if (url.endsWith(SUCCESS_URL_SUFFIX)) {
            createRiotBotRoomIfNeeded()
        }
    }

    /**
     * This methods try to create the RiotBot room when the user gives his agreement
     */
    private fun createRiotBotRoomIfNeeded() {
        safeActivity?.let {
            val session = Matrix.getInstance(it).defaultSession
            val joinedRooms = session.dataHandler.store?.rooms?.filter {
                it.isJoined
            }
            if (joinedRooms?.isEmpty() == true) {
                it.showWaitingView()
                // Ensure we can create a Room with riot-bot. Error can be a MatrixError: "Federation denied with matrix.org.", or any other error.
                session.profileApiClient
                        .displayname(RIOT_BOT_ID, object : SimpleApiCallback<String>(createRiotBotRoomCallback) {
                            override fun onSuccess(info: String?) {
                                // Ok, the Home Server knows riot-Bot, so create a Room with him
                                session.createDirectMessageRoom(RIOT_BOT_ID, createRiotBotRoomCallback)
                            }
                        })
            } else {
                it.finish()
            }
        }
    }

    /**
     * APICallback instance
     */
    private val createRiotBotRoomCallback = object : ApiCallback<String> {
        override fun onSuccess(info: String) {
            Log.d(LOG_TAG, "## On success : succeed to invite riot-bot")
            safeActivity?.finish()
        }

        private fun onError(error: String?) {
            Log.e(LOG_TAG, "## On error : failed  to invite riot-bot $error")
            safeActivity?.finish()
        }

        override fun onNetworkError(e: Exception) {
            onError(e.message)
        }

        override fun onMatrixError(e: MatrixError) {
            onError(e.message)
        }

        override fun onUnexpectedError(e: Exception) {
            onError(e.message)
        }
    }

}