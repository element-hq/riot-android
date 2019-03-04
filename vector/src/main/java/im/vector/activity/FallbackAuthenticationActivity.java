/*
 * Copyright 2015 OpenMarket Ltd
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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.http.SslError;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import im.vector.R;

/**
 * FallbackAuthenticationActivity is the fallback login or create account activity
 * i.e this activity is created when the client does not support the login flow of the home server
 */
public class FallbackAuthenticationActivity extends VectorAppCompatActivity {
    private static final String LOG_TAG = FallbackAuthenticationActivity.class.getSimpleName();

    private static final int MODE_LOGIN = 1;
    private static final int MODE_REGISTER = 2;

    private static final String EXTRA_MODE = "FallbackAuthenticationActivity.EXTRA_MODE";
    private static final String EXTRA_HOME_SERVER_URL = "FallbackAuthenticationActivity.EXTRA_HOME_SERVER_URL";

    public static Intent getIntentToLogin(Context context, String homeserverUrl) {
        Intent intent = new Intent(context, FallbackAuthenticationActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_LOGIN);
        intent.putExtra(EXTRA_HOME_SERVER_URL, homeserverUrl);
        return intent;
    }

    public static Intent getIntentToRegister(Context context, String homeserverUrl) {
        Intent intent = new Intent(context, FallbackAuthenticationActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_REGISTER);
        intent.putExtra(EXTRA_HOME_SERVER_URL, homeserverUrl);
        return intent;
    }

    @BindView(R.id.fallback_authentication_webview)
    WebView mWebView = null;

    // home server url
    private String mHomeServerUrl = null;

    // Mode (MODE_LOGIN or MODE_REGISTER)
    private int mMode = MODE_LOGIN;

    @Override
    public int getLayoutRes() {
        return R.layout.activity_authentication_fallback;
    }

    @Override
    public int getTitleRes() {
        if (mMode == MODE_LOGIN) {
            return R.string.login;
        }

        // MODE_REGISTER
        return R.string.create_account;
    }

    @Override
    public void initUiAndData() {
        configureToolbar();

        Intent intent = getIntent();

        mMode = intent.getIntExtra(EXTRA_MODE, MODE_LOGIN);

        mWebView.getSettings().setJavaScriptEnabled(true);

        mHomeServerUrl = getString(R.string.default_hs_server_url);

        if (intent.hasExtra(EXTRA_HOME_SERVER_URL)) {
            mHomeServerUrl = intent.getStringExtra(EXTRA_HOME_SERVER_URL);
        }

        // check the trailing slash
        if (!mHomeServerUrl.endsWith("/")) {
            mHomeServerUrl += "/";
        }

        // AppRTC requires third party cookies to work
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();

        // clear the cookies must be cleared
        if (cookieManager == null) {
            launchWebView();
        } else {
            if (!cookieManager.hasCookies()) {
                launchWebView();
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                try {
                    cookieManager.removeAllCookie();
                } catch (Exception e) {
                    Log.e(LOG_TAG, " cookieManager.removeAllCookie() fails " + e.getLocalizedMessage(), e);
                }
                launchWebView();
            } else {
                try {
                    cookieManager.removeAllCookies(new ValueCallback<Boolean>() {
                        @Override
                        public void onReceiveValue(Boolean value) {
                            launchWebView();
                        }
                    });
                } catch (Exception e) {
                    Log.e(LOG_TAG, " cookieManager.removeAllCookie() fails " + e.getLocalizedMessage(), e);
                    launchWebView();
                }
            }
        }
    }

    private void launchWebView() {
        if (mMode == MODE_LOGIN) {
            mWebView.loadUrl(mHomeServerUrl + "_matrix/static/client/login/");
        } else {
            // MODE_REGISTER
            mWebView.loadUrl(mHomeServerUrl + "_matrix/static/client/register/");
        }

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           SslError error) {
                final SslErrorHandler fHander = handler;

                new AlertDialog.Builder(FallbackAuthenticationActivity.this)
                        .setMessage(R.string.ssl_could_not_verify)
                        .setPositiveButton(R.string.ssl_trust, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                fHander.proceed();
                            }
                        })
                        .setNegativeButton(R.string.ssl_do_not_trust, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                fHander.cancel();
                            }
                        })
                        .setOnKeyListener(new DialogInterface.OnKeyListener() {
                            @Override
                            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                                    fHander.cancel();
                                    dialog.dismiss();
                                    return true;
                                }
                                return false;
                            }
                        })
                        .show();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);

                // on error case, close this activity
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // avoid infinite onPageFinished call
                if (url.startsWith("http")) {
                    if (mMode == MODE_LOGIN) {
                        // Generic method to make a bridge between JS and the UIWebView
                        final String MXCJavascriptSendObjectMessage = "javascript:window.matrixLogin.sendObjectMessage = function(parameters) { var i" +
                                "frame = document.createElement('iframe');  iframe.setAttribute('src', 'js:' + JSON.stringify(parameters));  document" +
                                ".documentElement.appendChild(iframe); iframe.parentNode.removeChild(iframe); iframe = null; };";

                        view.loadUrl(MXCJavascriptSendObjectMessage);

                        // The function the fallback page calls when the login is complete
                        final String MXCJavascriptOnRegistered = "javascript:window.matrixLogin.onLogin = function(homeserverUrl, userId, accessToken" +
                                ") { matrixLogin.sendObjectMessage({ 'action': 'onLogin', 'homeServer': homeserverUrl,'userId': userId,  'accessToken" +
                                "': accessToken  }); };";

                        view.loadUrl(MXCJavascriptOnRegistered);
                    } else {
                        // MODE_REGISTER
                        // Generic method to make a bridge between JS and the UIWebView
                        final String MXCJavascriptSendObjectMessage = "javascript:window.matrixRegistration.sendObjectMessage = function(parameters)" +
                                " { var iframe = document.createElement('iframe');  iframe.setAttribute('src', 'js:' + JSON.stringify(parameters)); " +
                                " document.documentElement.appendChild(iframe); iframe.parentNode.removeChild(iframe); iframe = null; };";

                        view.loadUrl(MXCJavascriptSendObjectMessage);

                        // The function the fallback page calls when the registration is complete
                        final String MXCJavascriptOnRegistered = "javascript:window.matrixRegistration.onRegistered = function(homeserverUrl, userId" +
                                ", accessToken) { matrixRegistration.sendObjectMessage({ 'action': 'onRegistered', 'homeServer': homeserverUrl,'user" +
                                "Id': userId,  'accessToken': accessToken  }); };";

                        view.loadUrl(MXCJavascriptOnRegistered);
                    }
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if ((null != url) && url.startsWith("js:")) {
                    String json = url.substring(3);
                    Map<String, Object> parameters = null;

                    try {
                        // URL decode
                        json = URLDecoder.decode(json, "UTF-8");
                        parameters = JsonUtils.getBasicGson().fromJson(json, new TypeToken<HashMap<String, Object>>() {
                        }.getType());

                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## shouldOverrideUrlLoading() : fromJson failed " + e.getMessage(), e);
                    }

                    // succeeds to parse parameters
                    if (null != parameters) {
                        if (mMode == MODE_LOGIN) {
                            try {
                                String action = (String) parameters.get("action");
                                LinkedTreeMap<String, String> parametersMap = (LinkedTreeMap<String, String>) parameters.get("homeServer");

                                if (TextUtils.equals("onLogin", action)) {
                                    final String userId = parametersMap.get("user_id");
                                    final String accessToken = parametersMap.get("access_token");
                                    final String homeServer = parametersMap.get("home_server");

                                    // remove the trailing /
                                    if (mHomeServerUrl.endsWith("/")) {
                                        mHomeServerUrl = mHomeServerUrl.substring(0, mHomeServerUrl.length() - 1);
                                    }

                                    // check if the parameters are defined
                                    if ((null != homeServer) && (null != userId) && (null != accessToken)) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Intent returnIntent = new Intent();
                                                returnIntent.putExtra("homeServerUrl", mHomeServerUrl);
                                                returnIntent.putExtra("homeServer", homeServer);
                                                returnIntent.putExtra("userId", userId);
                                                returnIntent.putExtra("accessToken", accessToken);
                                                setResult(RESULT_OK, returnIntent);

                                                finish();
                                            }
                                        });
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## shouldOverrideUrlLoading() : failed " + e.getMessage(), e);
                            }
                        } else {
                            // MODE_REGISTER
                            // check the required parameters
                            if (parameters.containsKey("homeServer")
                                    && parameters.containsKey("userId")
                                    && parameters.containsKey("accessToken")
                                    && parameters.containsKey("action")) {
                                final String userId2 = (String) parameters.get("userId");
                                final String accessToken2 = (String) parameters.get("accessToken");
                                final String homeServer2 = (String) parameters.get("homeServer");
                                String action2 = (String) parameters.get("action");

                                // remove the trailing /
                                if (mHomeServerUrl.endsWith("/")) {
                                    mHomeServerUrl = mHomeServerUrl.substring(0, mHomeServerUrl.length() - 1);
                                }

                                // check the action
                                if (action2.equals("onRegistered")) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Intent returnIntent = new Intent();
                                            returnIntent.putExtra("homeServerUrl", mHomeServerUrl);
                                            returnIntent.putExtra("homeServer", homeServer2);
                                            returnIntent.putExtra("userId", userId2);
                                            returnIntent.putExtra("accessToken", accessToken2);
                                            setResult(RESULT_OK, returnIntent);

                                            finish();
                                        }
                                    });
                                }
                            }
                        }
                    }
                    return true;
                }

                return super.shouldOverrideUrlLoading(view, url);
            }
        });
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
