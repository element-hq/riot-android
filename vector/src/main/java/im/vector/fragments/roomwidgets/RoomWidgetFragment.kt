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
package im.vector.fragments.roomwidgets

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import butterknife.BindView
import com.airbnb.mvrx.*
import im.vector.R
import im.vector.activity.HandleBackParticipant
import im.vector.activity.ReviewTermsActivity
import im.vector.activity.util.TERMS_REQUEST_CODE
import im.vector.fragments.VectorBaseMvRxFragment
import im.vector.ui.themes.ThemeUtils
import im.vector.util.openUrlInExternalBrowser
import org.matrix.androidsdk.features.terms.TermsManager

class RoomWidgetFragment : VectorBaseMvRxFragment(), HandleBackParticipant {

    var mWidgetWebView: WebView? = null

    @BindView(R.id.webview_error_layout)
    lateinit var errorContainer: ViewGroup

    @BindView(R.id.webview_error_text)
    lateinit var errorText: TextView

    @BindView(R.id.widget_progress_bar)
    lateinit var webProgressBar: ProgressBar

    val viewModel: RoomWidgetViewModel by activityViewModel()

    override fun getLayoutResId(): Int = R.layout.fragment_room_widget

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        configureWebView()
        setHasOptionsMenu(true)
        viewModel.loadWebURLEvent.observe(this, Observer {
            it?.getContentIfNotHandled()?.let {
                mWidgetWebView?.clearHistory()
                mWidgetWebView?.loadUrl(it)
                mWidgetWebView?.isVisible = true
            }
        })

        viewModel.termsNotSignedEvent.observe(this, Observer { termsEvent ->
            termsEvent?.getContentIfNotHandled()?.let {
                viewModel.widgetsManager?.uiUrl?.let { uiUrl ->
                    startActivityForResult(ReviewTermsActivity.intent(requireContext(),
                            TermsManager.ServiceType.IntegrationManager, uiUrl, it.token),
                            TERMS_REQUEST_CODE)
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == TERMS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                viewModel.refreshAfterTermsAccepted()
            } else {
                viewModel.doFinish()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mWidgetWebView = view.findViewById(R.id.widget_web_view)
    }

    override fun onDestroy() {
        //Looks like WebView creates memory leaks (?) according to legacy code
        //We have to take additional steps to clear memory
        destroyWebView()
        super.onDestroy()
    }

    private fun destroyWebView() {

        val webview = mWidgetWebView ?: return
        // Make sure you remove the WebView from its parent view before doing anything.
        (webview.parent as? ViewGroup)?.removeAllViews()

        webview.webViewClient = null
        webview.clearHistory()

        // NOTE: clears RAM cache, if you pass true, it will also clear the disk cache.
        webview.clearCache(true)

        // Loading a blank page is optional, but will ensure that the WebView isn't doing anything when you destroy it.
        webview.loadUrl("about:blank")

        webview.onPause()
        webview.removeAllViews()

        // NOTE: This pauses JavaScript execution for ALL WebViews,
        // do not use if you have other WebViews still alive.
        // If you create another WebView after calling this,
        // make sure to call mWebView.resumeTimers().
        webview.pauseTimers()

        // NOTE: This can occasionally cause a segfault below API 17 (4.2)
        webview.destroy()

        // Null out the reference so that you don't end up re-using it.
        mWidgetWebView = null
    }

    private var webViewClient = object : WebViewClient() {

        var isInError = false
        var currentPage: String? = null

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
//            Log.d("WEBVIEW", "onPageStarted $url")
            isInError = false
            currentPage = url
            viewModel.webviewStartedToLoad(url)
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
//            Log.d("WEBVIEW", "onReceivedHttpError ${request?.url}")
            if (request?.url.toString() != currentPage) {
                // Ignore this error
                return
            }
            isInError = true
            viewModel.webviewLoadingError(request?.url?.toString(),
                    errorResponse?.reasonPhrase?.takeIf { it.isNotBlank() }
                            ?: errorResponse?.statusCode.toString())
        }

        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            isInError = true
            viewModel.webviewLoadingError(failingUrl, description)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
//            Log.d("WEBVIEW", "onPageFinished ${url}")
            if (!isInError) {
                viewModel.webviewLoadSuccess(url)
            }
        }
    }


    override fun invalidate() = withState(viewModel) { state ->
        //            mWidgetTypeTextView.text = it.widgetName
        when (state.status) {
            WidgetState.UNKNOWN            -> {
                //Hide all?
                mWidgetWebView?.isVisible = false
            }
            WidgetState.WIDGET_NOT_ALLOWED -> {
                mWidgetWebView?.isVisible = false
            }
            WidgetState.WIDGET_ALLOWED     -> {
                mWidgetWebView?.isVisible = true
                when (state.formattedURL) {
                    Uninitialized -> {
                    }
                    is Loading    -> {
                        setError(null)
                        webProgressBar.isIndeterminate = true
                        webProgressBar.isVisible = true
                    }
                    is Success    -> {
                        setError(null)
                        when (state.webviewLoadedUrl) {
                            Uninitialized -> {
                                mWidgetWebView?.isInvisible = true
                            }
                            is Loading    -> {
                                setError(null)
                                mWidgetWebView?.isInvisible = false
                                webProgressBar.isIndeterminate = true
                                webProgressBar.isVisible = true
                            }
                            is Success    -> {
                                mWidgetWebView?.isInvisible = false
                                webProgressBar.isVisible = false
                                setError(null)
                            }
                            is Fail       -> {
                                webProgressBar.isInvisible = true
                                setError(state.webviewLoadedUrl.error.message)
                            }
                        }
                    }
                    is Fail       -> {
                        //we need to show Error
                        mWidgetWebView?.isInvisible = true
                        webProgressBar.isVisible = false
                        setError(state.formattedURL.error.message)
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = withState(viewModel) { state ->
        when (item.itemId) {
            R.id.action_close           -> {
                viewModel.doCloseWidget(requireContext())
                return@withState true
            }
            R.id.action_refresh         -> if (state.formattedURL.complete) {
                mWidgetWebView?.reload()
                return@withState true
            }
            R.id.action_widget_open_ext -> if (state.formattedURL.complete) {
                openUrlInExternalBrowser(requireContext(), state.formattedURL.invoke())
                return@withState true
            }
            R.id.action_revoke -> if (state.status == WidgetState.WIDGET_ALLOWED) {
                viewModel.revokeWidget()
                viewModel.doFinish()
                return@withState true
            }
        }
        return@withState super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu?) = withState(viewModel) { state ->
        menu?.findItem(R.id.action_close)?.isVisible = state.canManageWidgets
        menu?.findItem(R.id.action_revoke)?.isVisible = state.status == WidgetState.WIDGET_ALLOWED && !state.createdByMe
        super.onPrepareOptionsMenu(menu)
    }

    override fun onBackPressed(): Boolean = withState(viewModel) { state ->
        if (state.formattedURL.complete) {
            if (mWidgetWebView?.canGoBack() == true) {
                mWidgetWebView?.goBack()
                return@withState true
            }
        }
        return@withState false
    }

    private fun setError(message: String?) {
        if (message == null) {
            errorContainer.isVisible = false
            errorText.text = null
        } else {
            webProgressBar.isVisible = false
            errorContainer.isVisible = true
            mWidgetWebView?.isInvisible = true
            errorText.text = getString(R.string.room_widget_failed_to_load, message)
        }
    }

    /**
     * Load the widget call
     */
    @SuppressLint("NewApi")
    private fun configureWebView() {
        mWidgetWebView?.let { webview ->
            // xml value seems ignored
            webview.setBackgroundColor(ThemeUtils.getColor(requireContext(), R.attr.vctr_bottom_nav_background_color))

            // clear caches
            webview.clearHistory()
            webview.clearFormData()
            webview.clearCache(true)

            webview.settings.let { settings ->
                // does not cache the data
                settings.cacheMode = WebSettings.LOAD_NO_CACHE

                // Enable Javascript
                settings.javaScriptEnabled = true

                // Use WideViewport and Zoom out if there is no viewport defined
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true

                // Enable pinch to zoom without the zoom buttons
                settings.builtInZoomControls = true

                // Allow use of Local Storage
                settings.domStorageEnabled = true

                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true

                settings.displayZoomControls = false
            }

            // Permission requests
            webview.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    activity?.let {
                        WebviewPermissionUtils.promptForPermissions(R.string.room_widget_resource_permission_title, request, it)
                    }
                }
            }

            webview.webViewClient = webViewClient

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptThirdPartyCookies(mWidgetWebView, false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mWidgetWebView?.let {
            it.resumeTimers()
            it.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        mWidgetWebView?.let {
            it.pauseTimers()
            it.onPause()
        }
    }
}