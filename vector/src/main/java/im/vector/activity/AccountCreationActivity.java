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

import android.content.DialogInterface;
import android.content.Intent;
import android.net.http.SslError;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.util.Log;

import java.net.URLDecoder;
import java.util.HashMap;

import im.vector.R;

/**
 * AccountCreationActivity is the fallback account creation activity
 */
public class AccountCreationActivity extends RiotAppCompatActivity {
    private static final String LOG_TAG = AccountCreationActivity.class.getSimpleName();

    public static final String EXTRA_HOME_SERVER_ID = "AccountCreationActivity.EXTRA_HOME_SERVER_ID";

    // home server url
    private String mHomeServerUrl;

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        CommonActivityUtils.onLowMemory(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        CommonActivityUtils.onTrimMemory(this, level);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_account_creation;
    }

    @Override
    public int getTitleRes() {
        return R.string.create_account;
    }

    @Override
    public void initUiAndData() {
        final WebView webView = findViewById(R.id.account_creation_webview);
        webView.getSettings().setJavaScriptEnabled(true);

        Intent intent = getIntent();

        mHomeServerUrl = "https://matrix.org/";

        if (intent.hasExtra(EXTRA_HOME_SERVER_ID)) {
            mHomeServerUrl = intent.getStringExtra(EXTRA_HOME_SERVER_ID);
        }

        // check the trailing slash
        if (!mHomeServerUrl.endsWith("/")) {
            mHomeServerUrl += "/";
        }

        webView.loadUrl(mHomeServerUrl + "_matrix/static/client/register/");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           SslError error) {
                final SslErrorHandler fHander = handler;

                new AlertDialog.Builder(AccountCreationActivity.this)
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

            @Override
            public boolean shouldOverrideUrlLoading(android.webkit.WebView view, java.lang.String url) {

                if ((null != url) && url.startsWith("js:")) {
                    String json = url.substring(3);
                    HashMap<String, String> parameters = null;

                    try {
                        // URL decode
                        json = URLDecoder.decode(json, "UTF-8");
                        parameters = new Gson().fromJson(json, new TypeToken<HashMap<String, String>>() {
                        }.getType());

                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## shouldOverrideUrlLoading() : fromJson failed " + e.getMessage());
                    }

                    // succeeds to parse parameters
                    if (null != parameters) {
                        // check the required paramaters
                        if (parameters.containsKey("homeServer")
                                && parameters.containsKey("userId")
                                && parameters.containsKey("accessToken")
                                && parameters.containsKey("action")) {
                            final String userId = parameters.get("userId");
                            final String accessToken = parameters.get("accessToken");
                            final String homeServer = parameters.get("homeServer");
                            String action = parameters.get("action");

                            // remove the trailing /
                            if (mHomeServerUrl.endsWith("/")) {
                                mHomeServerUrl = mHomeServerUrl.substring(0, mHomeServerUrl.length() - 1);
                            }

                            // check the action
                            if (action.equals("onRegistered")) {
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
                    }
                    return true;
                }
                return true;
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
