/*
 * Copyright 2017 Vector Creations Ltd
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

package im.vector.activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
import im.vector.widgets.Widget;
import im.vector.widgets.WidgetsManager;

/*
 * This class displays a widget
 */
public class WidgetActivity extends RiotAppCompatActivity {
    private static final String LOG_TAG = WidgetActivity.class.getSimpleName();

    /**
     * The linked widget
     */
    public static final String EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID";

    // the linked widget
    private Widget mWidget = null;

    // the session
    private MXSession mSession;

    // the room
    private Room mRoom;

    @BindView(R.id.back_icon_container)
    View mBackToAppIcon;

    @BindView(R.id.close_widget_icon_container)
    View mCloseWidgetIcon;

    @BindView(R.id.widget_web_view)
    WebView mWidgetWebView;

    @BindView(R.id.widget_type_text_view)
    TextView mWidgetTypeTextView;

    @BindView(R.id.widget_progress_layout)
    View waitingView;

    /**
     * Widget events listener
     */
    private final WidgetsManager.onWidgetUpdateListener mWidgetListener = new WidgetsManager.onWidgetUpdateListener() {
        @Override
        public void onWidgetUpdate(Widget widget) {
            if (TextUtils.equals(widget.getWidgetId(), mWidget.getWidgetId())) {
                if (!widget.isActive()) {
                    WidgetActivity.this.finish();
                }
            }
        }
    };

    @Override
    @SuppressLint("NewApi")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_widget);
        ButterKnife.bind(this);

        mWidget = (Widget) getIntent().getSerializableExtra(EXTRA_WIDGET_ID);

        if ((null == mWidget) || (null == mWidget.getUrl())) {
            Log.e(LOG_TAG, "## onCreate() : invalid widget");
            finish();
            return;
        }

        mSession = Matrix.getMXSession(this, mWidget.getSessionId());

        if (null == mSession) {
            Log.e(LOG_TAG, "## onCreate() : invalid session");
            finish();
            return;
        }

        mRoom = mSession.getDataHandler().getRoom(mWidget.getRoomId());

        if (null == mRoom) {
            Log.e(LOG_TAG, "## onCreate() : invalid room");
            finish();
            return;
        }

        mWidgetTypeTextView.setText(mWidget.getHumanName());
        refreshStatusBar();
        loadURL();
    }

    /**
     * Refresh the status bar
     */
    private void refreshStatusBar() {
        boolean canCloseWidget = (null == WidgetsManager.getSharedInstance().checkWidgetPermission(mSession, mRoom));

        // close widget button
        mCloseWidgetIcon.setVisibility(canCloseWidget ? View.VISIBLE : View.GONE);
        mCloseWidgetIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(WidgetActivity.this)
                        .setMessage(R.string.widget_delete_message_confirmation)
                        .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                showWaitingView();
                                WidgetsManager.getSharedInstance().closeWidget(mSession, mRoom, mWidget.getWidgetId(), new ApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                        WidgetActivity.this.finish();
                                    }

                                    private void onError(String errorMessage) {
                                        stopWaitingView();
                                        CommonActivityUtils.displayToast(WidgetActivity.this, errorMessage);
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        onError(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        onError(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        onError(e.getLocalizedMessage());
                                    }
                                });
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
            }
        });

        mBackToAppIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mWidgetWebView.canGoBack()) {
                    mWidgetWebView.goBack();
                } else {
                    WidgetActivity.this.finish();
                }
            }
        });
    }

    /**
     * Load the widget call
     */
    @SuppressLint("NewApi")
    private void loadURL() {

        // xml value seems ignored
        mWidgetWebView.setBackgroundColor(0);

        // clear caches
        mWidgetWebView.clearHistory();
        mWidgetWebView.clearFormData();
        mWidgetWebView.clearCache(true);

        WebSettings settings = mWidgetWebView.getSettings();

        // does not cache the data
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Enable Javascript
        settings.setJavaScriptEnabled(true);

        // Use WideViewport and Zoom out if there is no viewport defined
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Enable pinch to zoom without the zoom buttons
        settings.setBuiltInZoomControls(true);

        // Allow use of Local Storage
        settings.setDomStorageEnabled(true);

        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        settings.setDisplayZoomControls(false);

        // Permission requests
        mWidgetWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        request.grant(request.getResources());
                    }
                });
            }
        });

        mWidgetWebView.setWebViewClient(new WebViewClient());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
            cookieManager.setAcceptThirdPartyCookies(mWidgetWebView, true);
        }

        showWaitingView();
        WidgetsManager.getFormattedWidgetUrl(this, mWidget, new ApiCallback<String>() {
            @Override
            public void onSuccess(String url) {
                stopWaitingView();
                mWidgetWebView.loadUrl(url);
            }

            private void onError(String errorMessage) {
                CommonActivityUtils.displayToast(WidgetActivity.this, errorMessage);
                WidgetActivity.this.finish();
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }


    @Override
    protected void onPause() {
        super.onPause();
        mWidgetWebView.pauseTimers();
        mWidgetWebView.onPause();

        WidgetsManager.removeListener(mWidgetListener);
    }

    /**
     * Force to render the activity in fullscreen
     */
    private void displayInFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayInFullScreen();
        WidgetsManager.addListener(mWidgetListener);

        mWidgetWebView.resumeTimers();
        mWidgetWebView.onResume();

        refreshStatusBar();
    }

    @Override
    protected void onDestroy() {
        if (null != mWidgetWebView) {
            ((ViewGroup) (mWidgetWebView.getParent())).removeView(mWidgetWebView);
            mWidgetWebView.removeAllViews();
            mWidgetWebView.destroy();
            mWidgetWebView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            displayInFullScreen();
        }
    }
}