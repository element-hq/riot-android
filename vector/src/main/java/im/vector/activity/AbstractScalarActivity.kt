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
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.support.annotation.CallSuper
import android.webkit.*
import butterknife.BindView
import com.google.gson.reflect.TypeToken
import im.vector.Matrix
import im.vector.R
import im.vector.types.ScalarEventData
import im.vector.util.toJsonMap
import im.vector.widgets.WidgetsManager
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.util.JsonUtils
import org.matrix.androidsdk.util.Log
import java.io.InputStreamReader
import java.util.*

/**
 * Parent class for all Activities managing Scalar Webview
 *
 * This class manage the communication (JS Bridge) with the WebView.
 *
 * Layout MUST contains a WebView with ID 'scalar_webview'
 */
abstract class AbstractScalarActivity : RiotAppCompatActivity() {

    /* ==========================================================================================
     * UI
     * ========================================================================================== */

    @BindView(R.id.scalar_webview)
    lateinit var mWebView: WebView

    /* ==========================================================================================
     * parameters
     * ========================================================================================== */

    protected var mSession: MXSession? = null
    protected var mRoom: Room? = null

    /* ==========================================================================================
     * DATA
     * ========================================================================================== */

    // success result
    // must be copied else the conversion to string does not work
    protected val mSucceedResponse = HashMap<String, Boolean>().apply {
        put("success", true)
    }


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

        WidgetsManager.getScalarToken(this, mSession!!, object : ApiCallback<String> {
            override fun onSuccess(scalarToken: String) {
                hideWaitingView()
                launchUrl(scalarToken)
            }

            private fun onError(errorMessage: String) {
                CommonActivityUtils.displayToast(this@AbstractScalarActivity, errorMessage)
                finish()
            }

            override fun onNetworkError(e: Exception) {
                onError(e.localizedMessage)
            }

            override fun onMatrixError(e: MatrixError) {
                onError(e.localizedMessage)
            }

            override fun onUnexpectedError(e: Exception) {
                onError(e.localizedMessage)
            }
        })
    }

    /* ==========================================================================================
     * PRIVATE
     * ========================================================================================== */

    @SuppressLint("NewApi")
    private fun initWebView() {
        mWebView.let {
            it.addJavascriptInterface(IntegrationWebAppInterface(), "Android")

            // Permission requests
            it.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread { request.grant(request.resources) }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.e(LOG_TAG, "## onConsoleMessage() : " + consoleMessage.message() + " line " + consoleMessage.lineNumber() + " source Id " + consoleMessage.sourceId())
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
                    Log.d(LOG_TAG, "## onPageStarted - Url: $url")

                    showWaitingView()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    hideWaitingView()

                    val js = getJSCodeToInject(this@AbstractScalarActivity)

                    if (null != js) {
                        runOnUiThread { mWebView.loadUrl("javascript:$js") }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptThirdPartyCookies(mWebView, true)
            }
        }
    }

    private fun launchUrl(scalarToken: String) {
        val url = buildInterfaceUrl(scalarToken)

        if (null == url) {
            finish()
            return
        }

        mWebView.loadUrl(url)
    }

    abstract fun buildInterfaceUrl(scalarToken: String): String?

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Read the JS code to inject from the resource directory.
     *
     * @param context the context
     * @return the JS code to inject
     */
    private fun getJSCodeToInject(context: Context): String? {
        var code: String? = null

        try {
            val inputStream = context.assets.open("postMessageAPI.js")
            val buffer = CharArray(1024)
            val out = StringBuilder()

            val inputStreamReader = InputStreamReader(inputStream, "UTF-8")
            while (true) {
                val rsz = inputStreamReader.read(buffer, 0, buffer.size)
                if (rsz < 0)
                    break
                out.append(buffer, 0, rsz)
            }
            code = out.toString()

            inputStreamReader.close()
            inputStream.close()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## getJSCodeToInject() failed : " + e.message)
        }

        return code
    }

    /**
     * Manage the modular requests
     *
     * @param JSData the js data request
     */
    private fun onScalarMessage(JSData: ScalarEventData?) {
        if (null == JSData) {
            Log.e(LOG_TAG, "## onScalarMessage() : invalid JSData")
            return
        }

        val eventData = JSData["event.data"]

        if (null == eventData) {
            Log.e(LOG_TAG, "## onScalarMessage() : invalid JSData")
            return
        }

        try {
            dealsWithScalarMessage(eventData)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## onScalarMessage() : failed " + e.message)
            sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
        }
    }

    /**
     * A Scalar message has been received, deals with it and send the response
     */
    abstract fun dealsWithScalarMessage(eventData: Map<String, Any>)

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
    private fun sendResponse(jsString: String, eventData: Map<String, Any>) {
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
            Log.e(LOG_TAG, "## sendResponse() failed " + e.message)
        }
    }

    /**
     * Send a boolean response
     *
     * @param response  the response
     * @param eventData the modular data
     */
    protected fun sendBoolResponse(response: Boolean, eventData: Map<String, Any>) {
        sendResponse(if (response) "true" else "false", eventData)
    }

    /**
     * Send an integer response
     *
     * @param response  the response
     * @param eventData the modular data
     */
    protected fun sendIntegerResponse(response: Int, eventData: Map<String, Any>) {
        sendResponse(response.toString() + "", eventData)
    }

    /**
     * Send an object response
     *
     * @param response  the response
     * @param eventData the modular data
     */
    protected fun sendObjectResponse(response: Any?, eventData: Map<String, Any>) {
        var jsString: String? = null

        if (null != response) {
            try {
                jsString = "JSON.parse('" + JsonUtils.getGson(false).toJson(response) + "')"
            } catch (e: Exception) {
                Log.e(LOG_TAG, "## sendObjectResponse() : toJson failed " + e.message)
            }

        }

        if (null == jsString) {
            jsString = "null"
        }

        sendResponse(jsString, eventData)
    }

    /**
     * Send an error
     *
     * @param message   the error message
     * @param eventData the modular data
     */
    protected fun sendError(message: String, eventData: Map<String, Any>) {
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
    protected fun sendObjectAsJsonMap(any: Any, eventData: Map<String, Any>) {
        sendObjectResponse(any.toJsonMap(), eventData)
    }

    /* ==========================================================================================
     * INNER CLASSES
     * ========================================================================================== */

    private inner class IntegrationWebAppInterface internal constructor() {
        @JavascriptInterface
        fun onScalarEvent(eventData: String) {
            Log.d(LOG_TAG, "BRIDGE onScalarEvent : $eventData")

            try {
                val objectAsMap = JsonUtils.getGson(false)
                        .fromJson<ScalarEventData>(eventData, object : TypeToken<ScalarEventData>() {}.type)

                runOnUiThread {
                    onScalarMessage(objectAsMap)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "## onScalarEvent() failed " + e.message)
            }

        }
    }

    /**
     * Api callbacks
     *
     * @param <T> the callback type
     */
    protected inner class IntegrationManagerApiCallback<T>(private val mEventData: Map<String, Any>,
                                                           private val mDescription: String) :
            ApiCallback<T> {

        override fun onSuccess(info: T) {
            Log.d(LOG_TAG, "$mDescription succeeds")
            sendObjectResponse(HashMap(mSucceedResponse), mEventData)
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
        private val LOG_TAG = AbstractScalarActivity::class.java.simpleName

        /**
         * the parameters
         */
        internal const val EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID"
        internal const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"

        fun getIntent(context: Context, matrixId: String, roomId: String): Intent {
            return Intent(context, AbstractScalarActivity::class.java)
                    .apply {
                        putExtra(EXTRA_MATRIX_ID, matrixId)
                        putExtra(EXTRA_ROOM_ID, roomId)
                    }
        }
    }
}