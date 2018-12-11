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
import android.webkit.WebChromeClient
import android.webkit.WebView
import butterknife.BindView
import im.vector.R
import im.vector.webview.VectorWebViewClient
import im.vector.webview.WebViewMode

/**
 * This class is responsible for managing a WebView
 * It does also have a loading view and a toolbar
 * It relies on the VectorWebViewClient
 * This class shouldn't be extended. To add new behaviors, you might create a new WebViewMode and a new WebViewEventListener
 */
class VectorWebViewActivity : VectorAppCompatActivity() {

    /* ==========================================================================================
     * UI
     * ========================================================================================== */

    @BindView(R.id.simple_webview)
    lateinit var webView: WebView


    /* =====================================================================@=====================
     * Life cycle
     * ========================================================================================== */

    override fun getLayoutRes() = R.layout.activity_vector_web_view

    override fun initUiAndData() {
        configureToolbar()
        waitingView = findViewById(R.id.simple_webview_loader)

        webView.settings.apply {
            // Enable Javascript
            javaScriptEnabled = true

            // Use WideViewport and Zoom out if there is no viewport defined
            useWideViewPort = true
            loadWithOverviewMode = true

            // Enable pinch to zoom without the zoom buttons
            builtInZoomControls = true

            // Allow use of Local Storage
            domStorageEnabled = true

            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true

            displayZoomControls = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        val url = intent.extras.getString(EXTRA_URL)
        val title = intent.extras.getString(EXTRA_TITLE, USE_TITLE_FROM_WEB_PAGE)
        if (title != USE_TITLE_FROM_WEB_PAGE) {
            setTitle(title)
        }

        val webViewMode = intent.extras.getSerializable(EXTRA_MODE) as WebViewMode
        val eventListener = webViewMode.eventListener(this)
        webView.webViewClient = VectorWebViewClient(eventListener)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String) {
                if (title == USE_TITLE_FROM_WEB_PAGE) {
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
        private const val EXTRA_TITLE = "EXTRA_TITLE"
        private const val EXTRA_MODE = "EXTRA_MODE"

        private const val USE_TITLE_FROM_WEB_PAGE = ""

        fun getIntent(context: Context,
                      url: String,
                      title: String = USE_TITLE_FROM_WEB_PAGE,
                      mode: WebViewMode = WebViewMode.DEFAULT): Intent {
            return Intent(context, VectorWebViewActivity::class.java)
                    .apply {
                        putExtra(EXTRA_URL, url)
                        putExtra(EXTRA_TITLE, title)
                        putExtra(EXTRA_MODE, mode)
                    }
        }
    }
}

