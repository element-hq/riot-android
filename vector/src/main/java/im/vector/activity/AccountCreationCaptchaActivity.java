/*
 * Copyright 2016 OpenMarket Ltd
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.http.SslError;
import android.os.Bundle;
import android.text.TextUtils;
import org.matrix.androidsdk.util.Log;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import im.vector.R;

import java.net.URLDecoder;
import java.util.Formatter;
import java.util.HashMap;

/**
 * AccountCreationCaptchaActivity displays a webview to check captchas.
 */
public class AccountCreationCaptchaActivity extends VectorActivity {
    private static String LOG_TAG = "ACCCaptchaActivity";

    public static String EXTRA_HOME_SERVER_URL = "AccountCreationCaptchaActivity.EXTRA_HOME_SERVER_URL";
    public static String EXTRA_SITE_KEY = "AccountCreationCaptchaActivity.EXTRA_SITE_KEY";

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
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_vector_registration_captcha);

        final WebView webView = (WebView) findViewById(R.id.account_creation_webview);
        webView.getSettings().setJavaScriptEnabled(true);

        Intent intent = getIntent();

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

        webView.setWebViewClient(new WebViewClient(){
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           SslError error) {
                final SslErrorHandler fHander = handler;

                AlertDialog.Builder builder = new AlertDialog.Builder(AccountCreationCaptchaActivity.this);

                builder.setMessage(R.string.ssl_could_not_verify);

                builder.setPositiveButton(R.string.ssl_trust, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        fHander.proceed();
                    }
                });

                builder.setNegativeButton(R.string.ssl_do_not_trust, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        fHander.cancel();
                    }
                });

                builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                            fHander.cancel();
                            dialog.dismiss();
                            return true;
                        }
                        return false;
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);

                // on error case, close this activity
                AccountCreationCaptchaActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AccountCreationCaptchaActivity.this.finish();
                    }
                });
            }

            @Override
            public boolean shouldOverrideUrlLoading(android.webkit.WebView view, java.lang.String url) {
                if ((null != url) &&  url.startsWith("js:")) {
                    String json = url.substring(3);
                    HashMap<String, String> parameters = null;

                    try {
                        // URL decode
                        json = URLDecoder.decode(json, "UTF-8");
                        parameters = new Gson().fromJson(json, new TypeToken<HashMap<String, String>>() {}.getType());

                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## shouldOverrideUrlLoading() : fromJson failed " + e.getMessage());
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

                                AccountCreationCaptchaActivity.this.finish();
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
}
