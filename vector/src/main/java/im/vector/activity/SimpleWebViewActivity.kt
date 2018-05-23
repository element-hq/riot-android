package im.vector.activity

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v7.widget.Toolbar
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import butterknife.BindView
import im.vector.R

class SimpleWebViewActivity : RiotAppCompatActivity() {

    /* ==========================================================================================
     * UI
     * ========================================================================================== */

    // TODO UC: use tool bar mapped in parent class
    @BindView(R.id.simple_webview_toolbar)
    lateinit var toolbar: Toolbar

    @BindView(R.id.simple_webview)
    lateinit var webView: WebView

    /* ==========================================================================================
     * Life cycle
     * ========================================================================================== */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_web_view)

        // TODO Call
        // configureToolbar()
        // TODO Remove this
        setSupportActionBar(findViewById(R.id.simple_webview_toolbar))

        // TODO Add back in toolbar


        // TODO UC When Sticker will be merged: remove this line
        webView = findViewById(R.id.simple_webview)


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
