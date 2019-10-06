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

package im.vector.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.webkit.*
import androidx.annotation.CallSuper
import androidx.core.view.isVisible
import butterknife.BindView
import com.google.gson.reflect.TypeToken
import im.vector.Matrix
import im.vector.R
import im.vector.activity.util.INTEGRATION_MANAGER_ACTIVITY_REQUEST_CODE
import im.vector.activity.util.TERMS_REQUEST_CODE
import im.vector.types.JsonDict
import im.vector.types.WidgetEventData
import im.vector.util.AssetReader
import im.vector.util.toJsonMap
import im.vector.widgets.WidgetManagerProvider
import im.vector.widgets.WidgetsManager
import org.jetbrains.anko.toast
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.JsonUtils
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.features.terms.TermsManager
import org.matrix.androidsdk.features.terms.TermsNotSignedException
import java.util.*
import javax.net.ssl.HttpsURLConnection

/**
 * Parent class for all Activities managing Widget Webview
 *
 * This class manage the communication (JS Bridge) with the WebView.
 *
 * Layout MUST contains a WebView with ID 'widget_webview'
 */
abstract class AbstractWidgetActivity : VectorAppCompatActivity() {

    /* ==========================================================================================
     * UI
     * ========================================================================================== */

    @BindView(R.id.widget_webview)
    lateinit var mWebView: WebView

    /* ==========================================================================================
     * parameters
     * ========================================================================================== */

    protected var mSession: MXSession? = null
    protected var mRoom: Room? = null

    /* ==========================================================================================
     * Data
     * ========================================================================================== */

    private var mIsRefreshingToken = false
    private var mTokenAlreadyRefreshed = false
    private var mHistoryAlreadyCleared = false


    lateinit var widgetManager: WidgetsManager
    /* ==========================================================================================
     * LIFE CYCLE
     * ========================================================================================== */

    @CallSuper
    override fun initUiAndData() {
        mSession = Matrix.getInstance(this).getSession(intent.getStringExtra(EXTRA_MATRIX_ID))

        if (null == mSession || !mSession!!.isAlive) {
            Log.e(LOG_TAG, "## onCreate() : invalid session")
            finish()
            return
        }

        initWebView()

        mRoom = mSession!!.dataHandler.getRoom(intent.getStringExtra(EXTRA_ROOM_ID))

        widgetManager = WidgetManagerProvider.getWidgetManager(this) ?: run {
            finish()
            return
        }

        getScalarTokenAndLoadUrl()
    }

    private fun getScalarTokenAndLoadUrl() {
        if (canScalarTokenBeProvided()) {
            showWaitingView()

            widgetManager.getScalarToken(this, mSession!!, object : ApiCallback<String> {
                override fun onSuccess(scalarToken: String) {
                    mIsRefreshingToken = false
                    hideWaitingView()
                    launchUrl(scalarToken)
                }

                private fun onError(errorMessage: String) {
                    toast(errorMessage)
                    finish()
                }

                override fun onNetworkError(e: Exception) {
                    onError(e.localizedMessage)
                }

                override fun onMatrixError(e: MatrixError) {
                    onError(e.localizedMessage)
                }

                override fun onUnexpectedError(e: Exception) {
                    if (e is TermsNotSignedException) {
                        mIsRefreshingToken = false
                        hideWaitingView()
                        presentTermsForServices(e.token)
                    } else {
                        onError(e.localizedMessage)
                    }
                }
            })
        } else {
            // Scalar token cannot be provided
            launchUrl(null)
        }
    }

    private fun presentTermsForServices(token: String) {
        val wm = WidgetManagerProvider.getWidgetManager(this)
        if (wm == null) {  // should not happen
            finish()
            return
        }
        startActivityForResult(ReviewTermsActivity.intent(this,
                TermsManager.ServiceType.IntegrationManager, wm.uiUrl, token),
                TERMS_REQUEST_CODE)
    }

    /* ==========================================================================================
     * UI Events
     * ========================================================================================== */

    override fun onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == TERMS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                getScalarTokenAndLoadUrl()
            } else {
                finish()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
    /* ==========================================================================================
     * PRIVATE
     * ========================================================================================== */

    @SuppressLint("NewApi")
    private fun initWebView() {
        mWebView.let {
            it.addJavascriptInterface(WidgetWebAppInterface(), "Android")

            // Permission requests
            it.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread { request.grant(request.resources) }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.e(LOG_TAG, "## onConsoleMessage() : " + consoleMessage.message()
                                + " line " + consoleMessage.lineNumber() + " source Id " + consoleMessage.sourceId())
                    } else {
                        Log.d(LOG_TAG, "## onConsoleMessage() : " + consoleMessage.message()
                                + " line " + consoleMessage.lineNumber() + " source Id " + consoleMessage.sourceId())
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            it.settings.let {
                // Enable Javascript
                it.javaScriptEnabled = true

                // Use WideViewport and Zoom out if there is no viewport defined
                it.useWideViewPort = true
                it.loadWithOverviewMode = true

                // Enable pinch to zoom without the zoom buttons
                it.builtInZoomControls = true

                // Allow use of Local Storage
                it.domStorageEnabled = true

                it.allowFileAccessFromFileURLs = true
                it.allowUniversalAccessFromFileURLs = true

                it.displayZoomControls = false
            }

            it.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    // Do not log url, it can contains token
                    Log.d(LOG_TAG, "## onPageStarted")

                    showWaitingView()
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    // In case of 403, try to refresh the scalar token
                    if (errorResponse?.statusCode == HttpsURLConnection.HTTP_FORBIDDEN
                            && canScalarTokenBeProvided()
                            && !mTokenAlreadyRefreshed) {
                        mTokenAlreadyRefreshed = true
                        mIsRefreshingToken = true
                        widgetManager.clearScalarToken(this@AbstractWidgetActivity, mSession)

                        // Hide the webview because it's displaying an error message we try to fix by refreshing the token
                        mWebView.isVisible = false

                        getScalarTokenAndLoadUrl()
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    // Check that the Activity is still alive
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed) {
                        return
                    }

                    if (mIsRefreshingToken) {
                        // We are waiting for a scalar token refresh
                        return
                    }

                    hideWaitingView()

                    // Ensure the webview is visible, it may have been hidden during token refresh
                    mWebView.isVisible = true

                    val js = AssetReader.readAssetFile(this@AbstractWidgetActivity, "postMessageAPI.js")

                    if (null != js) {
                        runOnUiThread { mWebView.loadUrl("javascript:$js") }
                    }

                    if (mTokenAlreadyRefreshed && !mHistoryAlreadyCleared) {
                        // Also clear WebView history, for the scenario when the scalar token was invalid, to avoid loading again the url with the invalid token
                        // It has to be done when page has finished to be loaded
                        mHistoryAlreadyCleared = true
                        mWebView.clearHistory()
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptThirdPartyCookies(mWebView, true)
            }
        }
    }

    private fun launchUrl(scalarToken: String?) {
        val url = buildInterfaceUrl(scalarToken)

        if (null == url) {
            finish()
            return
        }

        mWebView.loadUrl(url)
    }

    abstract fun canScalarTokenBeProvided(): Boolean

    abstract fun buildInterfaceUrl(scalarToken: String?): String?

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Manage the request from the Javascript
     *
     * @param JSData the js data request
     */
    private fun onWidgetMessage(JSData: WidgetEventData?) {
        if (null == JSData) {
            Log.e(LOG_TAG, "## onWidgetMessage() : invalid JSData")
            return
        }

        val eventData = JSData["event.data"]

        if (null == eventData) {
            Log.e(LOG_TAG, "## onWidgetMessage() : invalid JSData")
            return
        }

        try {
            if (!dealsWithWidgetRequest(eventData)) {
                sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## onWidgetMessage() : failed " + e.message, e)
            sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
        }
    }

    /**
     * A Widget message has been received, deals with it and send the response
     *
     * @return true if the message is handled (it means an answer has been sent), false if not
     */
    @CallSuper
    open fun dealsWithWidgetRequest(eventData: JsonDict<Any>): Boolean {
        val action = eventData["action"] as String?

        when (action) {
            "integration_manager_open" -> {
                var integType: String? = null
                var integId: String? = null

                val data = eventData["data"]

                data
                        .takeIf { it is Map<*, *> }
                        ?.let {
                            val dict = data as Map<*, *>

                            dict["integType"]
                                    .takeIf { it is String }
                                    ?.let { integType = it as String }

                            dict["integId"]
                                    .takeIf { it is String }
                                    ?.let { integId = it as String }

                            // Add "type_" as a prefix
                            integType?.let { integType = "type_$integType" }
                        }

                openIntegrationManager(integId, integType)
                return true
            }
        }

        // Not handled
        return false
    }

    /*
     * *********************************************************************************************
     * Message sending methods
     * *********************************************************************************************
     */

    /**
     * Send the response to the javascript
     *
     * @param jsString  the response data
     * @param eventData the modular data
     */
    private fun sendResponse(jsString: String, eventData: JsonDict<Any>) {
        try {
            val functionLine = "sendResponseFromRiotAndroid('" + eventData["_id"] + "' , " + jsString + ");"

            Log.v(LOG_TAG, "BRIDGE sendResponse: $functionLine")

            // call the javascript method
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                mWebView.loadUrl("javascript:$functionLine")
            } else {
                mWebView.evaluateJavascript(functionLine, null)
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## sendResponse() failed " + e.message, e)
        }
    }

    /**
     * Send a boolean response
     *
     * @param response  the response
     * @param eventData the modular data
     */
    protected fun sendBoolResponse(response: Boolean, eventData: JsonDict<Any>) {
        sendResponse(if (response) "true" else "false", eventData)
    }

    /**
     * Send an integer response
     *
     * @param response  the response
     * @param eventData the modular data
     */
    protected fun sendIntegerResponse(response: Int, eventData: JsonDict<Any>) {
        sendResponse(response.toString() + "", eventData)
    }

    /**
     * Send an object response
     *
     * @param response  the response
     * @param eventData the modular data
     */
    protected fun sendObjectResponse(response: Any?, eventData: JsonDict<Any>) {
        var jsString: String? = null

        if (null != response) {
            try {
                jsString = "JSON.parse('" + JsonUtils.getGson(false).toJson(response) + "')"
            } catch (e: Exception) {
                Log.e(LOG_TAG, "## sendObjectResponse() : toJson failed " + e.message, e)
            }

        }

        if (null == jsString) {
            jsString = "null"
        }

        sendResponse(jsString, eventData)
    }

    /**
     * Send success
     *
     * @param eventData the modular data
     */
    protected fun sendSuccess(eventData: JsonDict<Any>) {
        sendObjectResponse(HashMap<String, Boolean>().apply { put("success", true) },
                eventData)
    }

    /**
     * Send an error
     *
     * @param message   the error message
     * @param eventData the modular data
     */
    protected fun sendError(message: String, eventData: JsonDict<Any>) {
        Log.e(LOG_TAG, "## sendError() : eventData $eventData failed $message")

        // TODO: JS has an additional optional parameter: nestedError
        val params = HashMap<String, Map<String, String>>()
        val subMap = HashMap<String, String>()
        subMap["message"] = message
        params["error"] = subMap

        sendObjectResponse(params, eventData)
    }

    /**
     * Send an object as a JSON map
     *
     * @param object    the object to send
     * @param eventData the modular data
     */
    protected fun sendObjectAsJsonMap(any: Any, eventData: JsonDict<Any>) {
        sendObjectResponse(any.toJsonMap(), eventData)
    }

    /* ==========================================================================================
     * Protected methods
     * ========================================================================================== */

    /**
     * Open integration manager
     */
    protected fun openIntegrationManager(widgetId: String?, screenId: String?) {
        val intent = IntegrationManagerActivity.getIntent(context = this,
                matrixId = mSession!!.myUserId,
                roomId = mRoom!!.roomId,
                widgetId = widgetId,
                screenId = screenId)

        startActivityForResult(intent, INTEGRATION_MANAGER_ACTIVITY_REQUEST_CODE)
    }

    /* ==========================================================================================
     * INNER CLASSES
     * ========================================================================================== */

    private inner class WidgetWebAppInterface internal constructor() {
        @JavascriptInterface
        fun onWidgetEvent(eventData: String) {
            Log.d(LOG_TAG, "BRIDGE onWidgetEvent : $eventData")

            try {
                val objectAsMap = JsonUtils.getGson(false)
                        .fromJson<WidgetEventData>(eventData, object : TypeToken<WidgetEventData>() {}.type)

                runOnUiThread {
                    onWidgetMessage(objectAsMap)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "## onWidgetEvent() failed " + e.message, e)
            }

        }
    }

    /**
     * Api callbacks
     *
     * @param <T> the callback type
     */
    protected inner class WidgetApiCallback<T>(private val mEventData: JsonDict<Any>,
                                               private val mDescription: String) :
            ApiCallback<T> {

        override fun onSuccess(info: T?) {
            Log.d(LOG_TAG, "$mDescription succeeds")
            sendSuccess(mEventData)
        }

        private fun onError(error: String) {
            Log.e(LOG_TAG, "$mDescription failed with error $error")
            sendError(getString(R.string.widget_integration_failed_to_send_request), mEventData)
        }

        override fun onNetworkError(e: Exception) {
            onError(e.message!!)
        }

        override fun onMatrixError(e: MatrixError) {
            onError(e.message)
        }

        override fun onUnexpectedError(e: Exception) {
            onError(e.message!!)
        }
    }

    /* ==========================================================================================
     * companion
     * ========================================================================================== */

    companion object {
        private val LOG_TAG = AbstractWidgetActivity::class.java.simpleName

        /**
         * the parameters
         */
        internal const val EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID"
        internal const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"

        fun getIntent(context: Context, matrixId: String, roomId: String): Intent {
            return Intent(context, AbstractWidgetActivity::class.java)
                    .apply {
                        putExtra(EXTRA_MATRIX_ID, matrixId)
                        putExtra(EXTRA_ROOM_ID, roomId)
                    }
        }
    }
}