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

package im.vector.activity

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.text.TextUtils
import android.view.KeyEvent
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import butterknife.BindView
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import im.vector.R
import org.matrix.androidsdk.core.JsonUtils
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.rest.model.login.Credentials
import java.net.URLDecoder
import java.util.*

/**
 * FallbackAuthenticationActivity is the fallback login or create account activity
 * i.e this activity is created when the client does not support the login flow or register flow of the homeserver
 */
class FallbackAuthenticationActivity : VectorAppCompatActivity() {

    @BindView(R.id.fallback_authentication_webview)
    lateinit var mWebView: WebView

    // home server url
    private var mHomeServerUrl: String? = null

    // Mode (MODE_LOGIN or MODE_REGISTER)
    private var mMode = MODE_LOGIN

    override fun getLayoutRes() = R.layout.activity_authentication_fallback

    override fun getTitleRes() = if (mMode == MODE_LOGIN) R.string.login else R.string.create_account

    override fun initUiAndData() {
        configureToolbar()

        val intent = intent

        mMode = intent.getIntExtra(EXTRA_IN_MODE, MODE_LOGIN)

        mWebView.settings.javaScriptEnabled = true

        // Enable local storage to support SSO with Firefox accounts
        mWebView.settings.domStorageEnabled = true
        mWebView.settings.databaseEnabled = true

        // Due to https://developers.googleblog.com/2016/08/modernizing-oauth-interactions-in-native-apps.html, we hack
        // the user agent to bypass the limitation of Google, as a quick fix (a proper solution will be to use the SSO SDK)
        mWebView.settings.userAgentString = "Mozilla/5.0 Google"

        mHomeServerUrl = getString(R.string.default_hs_server_url)

        if (intent.hasExtra(EXTRA_IN_HOME_SERVER_URL)) {
            mHomeServerUrl = intent.getStringExtra(EXTRA_IN_HOME_SERVER_URL)
        }

        // check the trailing slash
        if (!mHomeServerUrl!!.endsWith("/")) {
            mHomeServerUrl += "/"
        }

        // AppRTC requires third party cookies to work
        val cookieManager = android.webkit.CookieManager.getInstance()

        // clear the cookies must be cleared
        if (cookieManager == null) {
            launchWebView()
        } else {
            if (!cookieManager.hasCookies()) {
                launchWebView()
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                try {
                    cookieManager.removeAllCookie()
                } catch (e: Exception) {
                    Log.e(LOG_TAG, " cookieManager.removeAllCookie() fails " + e.localizedMessage, e)
                }

                launchWebView()
            } else {
                try {
                    cookieManager.removeAllCookies { launchWebView() }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, " cookieManager.removeAllCookie() fails " + e.localizedMessage, e)
                    launchWebView()
                }

            }
        }
    }

    private fun launchWebView() {
        if (mMode == MODE_LOGIN) {
            mWebView.loadUrl(mHomeServerUrl!! + "_matrix/static/client/login/")
        } else {
            // MODE_REGISTER
            mWebView.loadUrl(mHomeServerUrl!! + "_matrix/static/client/register/")
        }

        mWebView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler,
                                            error: SslError) {
                AlertDialog.Builder(this@FallbackAuthenticationActivity)
                        .setMessage(R.string.ssl_could_not_verify)
                        .setPositiveButton(R.string.ssl_trust) { dialog, which -> handler.proceed() }
                        .setNegativeButton(R.string.ssl_do_not_trust) { dialog, which -> handler.cancel() }
                        .setOnKeyListener(DialogInterface.OnKeyListener { dialog, keyCode, event ->
                            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                                handler.cancel()
                                dialog.dismiss()
                                return@OnKeyListener true
                            }
                            false
                        })
                        .show()
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                super.onReceivedError(view, errorCode, description, failingUrl)

                // on error case, close this activity
                runOnUiThread { finish() }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                toolbar.subtitle = url
            }

            override fun onPageFinished(view: WebView, url: String) {
                // avoid infinite onPageFinished call
                if (url.startsWith("http")) {
                    // Generic method to make a bridge between JS and the UIWebView
                    val mxcJavascriptSendObjectMessage = "javascript:window.sendObjectMessage = function(parameters) {" +
                            " var iframe = document.createElement('iframe');" +
                            " iframe.setAttribute('src', 'js:' + JSON.stringify(parameters));" +
                            " document.documentElement.appendChild(iframe);" +
                            " iframe.parentNode.removeChild(iframe); iframe = null;" +
                            " };"

                    view.loadUrl(mxcJavascriptSendObjectMessage)

                    if (mMode == MODE_LOGIN) {
                        // The function the fallback page calls when the login is complete
                        val mxcJavascriptOnRegistered = "javascript:window.matrixLogin.onLogin = function(response) {" +
                                " sendObjectMessage({ 'action': 'onLogin', 'credentials': response });" +
                                " };"

                        view.loadUrl(mxcJavascriptOnRegistered)
                    } else {
                        // MODE_REGISTER
                        // The function the fallback page calls when the registration is complete
                        val mxcJavascriptOnRegistered = "javascript:window.matrixRegistration.onRegistered" +
                                " = function(homeserverUrl, userId, accessToken) {" +
                                " sendObjectMessage({ 'action': 'onRegistered'," +
                                " 'homeServer': homeserverUrl," +
                                " 'userId': userId," +
                                " 'accessToken': accessToken });" +
                                " };"

                        view.loadUrl(mxcJavascriptOnRegistered)
                    }
                }
            }

            /**
             * Example of (formatted) url for MODE_LOGIN:
             * <pre>
             * js:{
             *     "action":"onLogin",
             *     "homeServer":{
             *         "user_id":"@user:matrix.org",
             *         "access_token":"[ACCESS_TOKEN]",
             *         "home_server":"matrix.org",
             *         "device_id":"[DEVICE_ID]",
             *         "well_known":{
             *             "m.homeserver":{
             *                 "base_url":"https://matrix.org/"
             *                 }
             *             }
             *         }
             *    }
             * </pre>
             * @param view
             * @param url
             * @return
             */
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                if (null != url && url.startsWith("js:")) {
                    var json = url.substring(3)
                    var parameters: Map<String, Any>? = null

                    try {
                        // URL decode
                        json = URLDecoder.decode(json, "UTF-8")
                        parameters = JsonUtils.getBasicGson().fromJson<Map<String, Any>>(json, object : TypeToken<HashMap<String, Any>>() {

                        }.type)
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "## shouldOverrideUrlLoading() : fromJson failed " + e.message, e)
                    }

                    // succeeds to parse parameters
                    if (parameters != null) {
                        val action = parameters["action"] as String

                        if (mMode == MODE_LOGIN) {
                            try {
                                val credentials = parameters["credentials"] as LinkedTreeMap<String, String>

                                if (TextUtils.equals("onLogin", action)) {
                                    val userId = credentials["user_id"]
                                    val accessToken = credentials["access_token"]
                                    val homeServer = credentials["home_server"]

                                    // check if the parameters are defined
                                    if (null != homeServer && null != userId && null != accessToken) {
                                        runOnUiThread {
                                            val returnIntent = Intent()
                                            returnIntent.putExtra(EXTRA_OUT_SERIALIZED_CREDENTIALS, JsonUtils.getBasicGson().toJson(credentials))
                                            setResult(RESULT_OK, returnIntent)

                                            finish()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(LOG_TAG, "## shouldOverrideUrlLoading() : failed " + e.message, e)
                            }

                        } else {
                            // MODE_REGISTER
                            // check the required parameters
                            if (action == "onRegistered") {
                                if (parameters.containsKey("homeServer")
                                        && parameters.containsKey("userId")
                                        && parameters.containsKey("accessToken")) {

                                    // We cannot parse Credentials here because of https://github.com/matrix-org/synapse/issues/4756
                                    // Build on object manually
                                    val credentials = Credentials()

                                    credentials.userId = parameters["userId"] as String
                                    credentials.accessToken = parameters["accessToken"] as String
                                    credentials.homeServer = parameters["homeServer"] as String

                                    runOnUiThread {
                                        val returnIntent = Intent()
                                        returnIntent.putExtra(EXTRA_OUT_SERIALIZED_CREDENTIALS, JsonUtils.getBasicGson().toJson(credentials))
                                        setResult(RESULT_OK, returnIntent)

                                        finish()
                                    }
                                }
                            }
                        }
                    }
                    return true
                }

                return super.shouldOverrideUrlLoading(view, url)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_MENU) {
            // This is to fix a bug in the v7 support lib. If there is no options menu and you hit MENU, it will crash with a
            // NPE @ android.support.v7.app.ActionBarImplICS.getThemedContext(ActionBarImplICS.java:274)
            // This can safely be removed if we add in menu options on this screen
            true
        } else super.onKeyDown(keyCode, event)
    }

    /* ==========================================================================================
     * UI event
     * ========================================================================================== */

    override fun onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private val LOG_TAG = FallbackAuthenticationActivity::class.java.simpleName

        private const val MODE_LOGIN = 1
        private const val MODE_REGISTER = 2

        private const val EXTRA_IN_MODE = "FallbackAuthenticationActivity.EXTRA_IN_MODE"
        private const val EXTRA_IN_HOME_SERVER_URL = "FallbackAuthenticationActivity.EXTRA_IN_HOME_SERVER_URL"
        private const val EXTRA_OUT_SERIALIZED_CREDENTIALS = "FallbackAuthenticationActivity.EXTRA_OUT_SERIALIZED_CREDENTIALS"

        /* ==========================================================================================
         * IN
         * ========================================================================================== */

        fun getIntentToLogin(context: Context, homeserverUrl: String): Intent {
            val intent = Intent(context, FallbackAuthenticationActivity::class.java)
            intent.putExtra(EXTRA_IN_MODE, MODE_LOGIN)
            intent.putExtra(EXTRA_IN_HOME_SERVER_URL, homeserverUrl)
            return intent
        }

        fun getIntentToRegister(context: Context, homeserverUrl: String): Intent {
            val intent = Intent(context, FallbackAuthenticationActivity::class.java)
            intent.putExtra(EXTRA_IN_MODE, MODE_REGISTER)
            intent.putExtra(EXTRA_IN_HOME_SERVER_URL, homeserverUrl)
            return intent
        }

        /* ==========================================================================================
         * OUT
         * ========================================================================================== */

        fun getResultCredentials(data: Intent): Credentials {
            val serializedCredentials = data.getStringExtra(EXTRA_OUT_SERIALIZED_CREDENTIALS)

            return JsonUtils.getBasicGson().fromJson(serializedCredentials, Credentials::class.java)
        }
    }
}
