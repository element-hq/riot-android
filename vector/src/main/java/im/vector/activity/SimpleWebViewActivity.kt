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

import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.annotation.StringRes
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import butterknife.BindView
import im.vector.R

class SimpleWebViewActivity : RiotAppCompatActivity() {

    /* ==========================================================================================
     * UI
     * ========================================================================================== */

    @BindView(R.id.simple_webview)
    lateinit var webView: WebView

    /* ==========================================================================================
     * Life cycle
     * ========================================================================================== */

    override fun getOtherThemes() = Pair(R.style.AppTheme_NoActionBar_Dark, R.style.AppTheme_NoActionBar_Black)

    override fun getLayoutRes() = R.layout.activity_simple_web_view

    override fun initUiAndData() {
        configureToolbar()

        webView.settings.let {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        val url = intent.extras.getString(EXTRA_URL)

        val titleRes = intent.extras.getInt(EXTRA_TITLE_RES_ID, INVALID_RES_ID)

        if (titleRes != INVALID_RES_ID) {
            setTitle(titleRes)
        }

        webView.webViewClient = WebViewClient()

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String) {
                if (titleRes == INVALID_RES_ID) {
                    setTitle(title)
                }
            }
        }

        webView.loadUrl(url)
    }

    /* ==========================================================================================
     * UI event
     * ========================================================================================== */

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /* ==========================================================================================
     * Companion
     * ========================================================================================== */

    companion object {
        private const val EXTRA_URL = "EXTRA_URL"
        private const val EXTRA_TITLE_RES_ID = "EXTRA_TITLE_RES_ID"

        // TODO Move this somewhere else
        private const val INVALID_RES_ID = -1

        fun getIntent(context: Context, url: String, @StringRes titleRes: Int = INVALID_RES_ID): Intent {
            return Intent(context, SimpleWebViewActivity::class.java)
                    .apply {
                        putExtra(EXTRA_URL, url)
                        putExtra(EXTRA_TITLE_RES_ID, titleRes)
                    }
        }
    }
}
