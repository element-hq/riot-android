/*
 * Copyright 2017 Vector Creations Ltd
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
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.TextView
import androidx.core.view.isInvisible
import androidx.core.widget.toast
import butterknife.BindView
import butterknife.OnClick
import im.vector.Matrix
import im.vector.R
import im.vector.widgets.Widget
import im.vector.widgets.WidgetsManager
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.util.Log

/*
 * This class displays a widget
 */
class WidgetActivity : RiotAppCompatActivity() {

    // the linked widget
    private var mWidget: Widget? = null

    // the session
    private var mSession: MXSession? = null

    // the room
    private var mRoom: Room? = null

    @BindView(R.id.widget_close_icon)
    lateinit var mCloseWidgetIcon: View

    @BindView(R.id.widget_web_view)
    lateinit var mWidgetWebView: WebView

    @BindView(R.id.widget_title)
    lateinit var mWidgetTypeTextView: TextView

    /**
     * Widget events listener
     */
    private val mWidgetListener = WidgetsManager.onWidgetUpdateListener { widget ->
        if (TextUtils.equals(widget.widgetId, mWidget!!.widgetId)) {
            if (!widget.isActive) {
                finish()
            }
        }
    }

    /* ==========================================================================================
     * LIFE CYCLE
     * ========================================================================================== */

    override fun getOtherThemes() = Pair(R.style.AppTheme_NoActionBar_Dark, R.style.AppTheme_NoActionBar_Black)

    override fun getLayoutRes() = R.layout.activity_widget

    @SuppressLint("NewApi")
    override fun initUiAndData() {
        waitingView = findViewById(R.id.widget_progress_layout)

        mWidget = intent.getSerializableExtra(EXTRA_WIDGET_ID) as Widget

        if (null == mWidget || null == mWidget!!.url) {
            Log.e(LOG_TAG, "## onCreate() : invalid widget")
            finish()
            return
        }

        mSession = Matrix.getMXSession(this, mWidget!!.sessionId)

        if (null == mSession) {
            Log.e(LOG_TAG, "## onCreate() : invalid session")
            finish()
            return
        }

        mRoom = mSession!!.dataHandler.getRoom(mWidget!!.roomId)

        if (null == mRoom) {
            Log.e(LOG_TAG, "## onCreate() : invalid room")
            finish()
            return
        }

        mWidgetTypeTextView.text = mWidget!!.humanName

        configureWebView()

        loadUrl()
    }

    override fun onDestroy() {
        mWidgetWebView.let {
            (it.parent as ViewGroup).removeView(it)
            it.removeAllViews()
            it.destroy()
        }

        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        WidgetsManager.addListener(mWidgetListener)

        mWidgetWebView.let {
            it.resumeTimers()
            it.onResume()
        }

        refreshStatusBar()
    }

    override fun onPause() {
        super.onPause()

        mWidgetWebView.let {
            it.pauseTimers()
            it.onPause()
        }

        WidgetsManager.removeListener(mWidgetListener)
    }

    /* ==========================================================================================
     * UI EVENTS
     * ========================================================================================== */

    @OnClick(R.id.widget_close_icon)
    internal fun onCloseClick() {
        AlertDialog.Builder(this)
                .setMessage(R.string.widget_delete_message_confirmation)
                .setPositiveButton(R.string.remove) { dialog, which ->
                    dialog.dismiss()
                    showWaitingView()
                    WidgetsManager.getSharedInstance().closeWidget(mSession, mRoom, mWidget!!.widgetId, object : ApiCallback<Void> {
                        override fun onSuccess(info: Void?) {
                            hideWaitingView()
                            finish()
                        }

                        private fun onError(errorMessage: String) {
                            hideWaitingView()
                            toast(errorMessage)
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
                .setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }
                .show()
    }

    @OnClick(R.id.widget_back_icon)
    internal fun onBackClicked() {
        if (mWidgetWebView.canGoBack()) {
            mWidgetWebView.goBack()
        } else {
            finish()
        }
    }

    /* ==========================================================================================
     * PRIVATE
     * ========================================================================================== */

    /**
     * Refresh the status bar
     */
    private fun refreshStatusBar() {
        val canCloseWidget = null == WidgetsManager.getSharedInstance().checkWidgetPermission(mSession, mRoom)

        // close widget button
        mCloseWidgetIcon.isInvisible = !canCloseWidget
    }

    /**
     * Load the widget call
     */
    @SuppressLint("NewApi")
    private fun configureWebView() {
        mWidgetWebView.let {
            // xml value seems ignored
            it.setBackgroundColor(0)

            // clear caches
            it.clearHistory()
            it.clearFormData()
            it.clearCache(true)

            it.settings.let {
                // does not cache the data
                it.cacheMode = WebSettings.LOAD_NO_CACHE

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

            // Permission requests
            it.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    Handler(Looper.getMainLooper()).post { request.grant(request.resources) }
                }
            }

            it.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    showWaitingView()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    hideWaitingView()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptThirdPartyCookies(mWidgetWebView, true)
            }
        }
    }

    private fun loadUrl() {
        showWaitingView()
        WidgetsManager.getFormattedWidgetUrl(this, mWidget!!, object : ApiCallback<String> {
            override fun onSuccess(url: String) {
                hideWaitingView()
                mWidgetWebView.loadUrl(url)
            }

            private fun onError(errorMessage: String) {
                hideWaitingView()
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
                onError(e.localizedMessage)
            }
        })
    }

    /* ==========================================================================================
     * companion
     * ========================================================================================== */

    companion object {
        private val LOG_TAG = WidgetActivity::class.java.simpleName

        /**
         * The linked widget
         */
        private const val EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID"

        fun getIntent(context: Context, widget: Widget): Intent {
            return Intent(context, WidgetActivity::class.java)
                    .apply {
                        putExtra(EXTRA_WIDGET_ID, widget)
                    }
        }
    }
}