/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.http.SslError;
import android.os.Build;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.Log;

import java.net.URLDecoder;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import im.vector.R;
import im.vector.ui.themes.ThemeUtils;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * AccountCreationCaptchaActivity displays a webview to check captchas.
 */
public class AccountCreationCaptchaActivity extends VectorAppCompatActivity {
    private static final String LOG_TAG = AccountCreationCaptchaActivity.class.getSimpleName();

    public static final String EXTRA_HOME_SERVER_URL = "AccountCreationCaptchaActivity.EXTRA_HOME_SERVER_URL";
    public static final String EXTRA_SITE_KEY = "AccountCreationCaptchaActivity.EXTRA_SITE_KEY";

    private static final String mRecaptchaHTMLString = "<html> " +
            " <head> " +
            " <script type=\"text/javascript\"> " +
            " var verifyCallback = function(response) { " +
            // Generic method to make a bridge between JS and the UIWebView
            " var iframe = document.createElement('iframe'); " +
            " iframe.setAttribute('src', 'js:' + JSON.stringify({'action': 'verifyCallback', 'response': response})); " +
            " document.documentElement.appendChild(iframe); " +
            " iframe.parentNode.removeChild(iframe); " +
            " iframe = null; " +
            " }; " +

            " var onloadCallback = function() { " +

            " grecaptcha.render('recaptcha_widget', { " +
            " 'sitekey' : '%s', " +
            " 'callback': verifyCallback " +
            " }); " +
            " }; " +
            " </script> " +
            " </head> " +
            " <body> " +
            " <div id=\"recaptcha_widget\"></div> " +
            " <script src=\"https://www.google.com/recaptcha/api.js?onload=onloadCallback&render=explicit\" async defer> " +
            " </script> " +
            " </body> " +
            " </html> ";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_vector_registration_captcha;
    }

    @Override
    public int getTitleRes() {
        return R.string.create_account;
    }

    @Override
    public void initUiAndData() {
        configureToolbar();

        final WebView webView = findViewById(R.id.account_creation_webview);
        webView.getSettings().setJavaScriptEnabled(true);

        final View loadingView = findViewById(R.id.account_creation_webview_loading);
        final Intent intent = getIntent();

        String homeServerUrl = "https://matrix.org/";

        if (intent.hasExtra(EXTRA_HOME_SERVER_URL)) {
            homeServerUrl = intent.getStringExtra(EXTRA_HOME_SERVER_URL);
        }

        // check the trailing slash
        if (!homeServerUrl.endsWith("/")) {
            homeServerUrl += "/";
        }

        String siteKey = intent.getStringExtra(EXTRA_SITE_KEY);

        String html = (new Formatter()).format(mRecaptchaHTMLString, siteKey).toString();
        String mime = "text/html";
        String encoding = "utf-8";

        webView.loadDataWithBaseURL(homeServerUrl, html, mime, encoding, null);
        webView.requestLayout();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadingView.setVisibility(view.GONE);
            }

            @Override
            public void onReceivedSslError(final WebView view, final SslErrorHandler handler, final SslError error) {
                Log.e(LOG_TAG, "## onReceivedSslError() : " + error.getCertificate());

                new AlertDialog.Builder(AccountCreationCaptchaActivity.this)
                        .setMessage(R.string.ssl_could_not_verify)
                        .setPositiveButton(R.string.ssl_trust, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.d(LOG_TAG, "## onReceivedSslError() : the user trusted");
                                handler.proceed();
                            }
                        })
                        .setNegativeButton(R.string.ssl_do_not_trust, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.d(LOG_TAG, "## onReceivedSslError() : the user did not trust");
                                handler.cancel();
                            }
                        })
                        .setOnKeyListener(new DialogInterface.OnKeyListener() {
                            @Override
                            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                                    handler.cancel();
                                    Log.d(LOG_TAG, "## onReceivedSslError() : the user dismisses the trust dialog.");
                                    dialog.dismiss();
                                    return true;
                                }
                                return false;
                            }
                        })
                        .show();
            }

            // common error message
            private void onError(String errorMessage) {
                Log.e(LOG_TAG, "## onError() : " + errorMessage);
                Toast.makeText(AccountCreationCaptchaActivity.this, errorMessage, Toast.LENGTH_LONG).show();

                // on error case, close this activity
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }

            @Override
            @SuppressLint("NewApi")
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);

                if (request.getUrl().toString().endsWith("favicon.ico")) {
                    // Ignore this error
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    onError(errorResponse.getReasonPhrase());
                } else {
                    onError(errorResponse.toString());
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                onError(description);
            }

            @Override
            public boolean shouldOverrideUrlLoading(android.webkit.WebView view, java.lang.String url) {
                if ((null != url) && url.startsWith("js:")) {
                    String json = url.substring(3);
                    Map<String, String> parameters = null;

                    try {
                        // URL decode
                        json = URLDecoder.decode(json, "UTF-8");
                        parameters = JsonUtils.getBasicGson().fromJson(json, new TypeToken<HashMap<String, String>>() {
                        }.getType());

                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## shouldOverrideUrlLoading() : fromJson failed " + e.getMessage(), e);
                    }

                    // succeeds to parse parameters
                    if (null != parameters) {
                        // check the required parameters
                        if (parameters.containsKey("action") && parameters.containsKey("response")) {
                            String action = parameters.get("action");

                            if (TextUtils.equals(action, "verifyCallback")) {
                                Intent returnIntent = new Intent();
                                returnIntent.putExtra("response", parameters.get("response"));
                                setResult(RESULT_OK, returnIntent);

                                finish();
                            }
                        }
                    }
                    return true;
                }
                return true;
            }
        });

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
            toolbar.setBackgroundColor(ThemeUtils.INSTANCE.getColor(this, R.attr.colorAccent));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            // This is to fix a bug in the v7 support lib. If there is no options menu and you hit MENU, it will crash with a
            // NPE @ android.support.v7.app.ActionBarImplICS.getThemedContext(ActionBarImplICS.java:274)
            // This can safely be removed if we add in menu options on this screen
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
