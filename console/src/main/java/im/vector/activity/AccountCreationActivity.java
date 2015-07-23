/*
 * Copyright 2015 OpenMarket Ltd
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
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import im.vector.R;

import java.net.URLDecoder;
import java.util.HashMap;

public class AccountCreationActivity extends Activity {
    public static String EXTRA_HOME_SERVER_ID = "org.matrix.console.activity.EXTRA_HOME_SERVER_ID";

    WebView mWebView = null;
    String mHomeServer = null;

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
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_account_creation);

        mWebView = (WebView) findViewById(R.id.account_creation_webview);
        mWebView.getSettings().setJavaScriptEnabled(true);

        Intent intent = getIntent();

        mHomeServer = "https://matrix.org/";

        if (intent.hasExtra(EXTRA_HOME_SERVER_ID)) {
            mHomeServer = intent.getStringExtra(EXTRA_HOME_SERVER_ID);
        }

        // check the trailing slash
        if (!mHomeServer.endsWith("/")) {
            mHomeServer += "/";
        }

        mWebView.loadUrl(mHomeServer + "_matrix/static/client/register/");

        mWebView.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url) {
                // avoid infinite onPageFinished call
                if (url.startsWith("http")) {
                    // Generic method to make a bridge between JS and the UIWebView
                    final String MXCJavascriptSendObjectMessage = "javascript:window.matrixRegistration.sendObjectMessage = function(parameters) { var iframe = document.createElement('iframe');  iframe.setAttribute('src', 'js:' + JSON.stringify(parameters));  document.documentElement.appendChild(iframe); iframe.parentNode.removeChild(iframe); iframe = null; };";

                    view.loadUrl(MXCJavascriptSendObjectMessage);

                    // The function the fallback page calls when the registration is complete
                    final String MXCJavascriptOnRegistered = "javascript:window.matrixRegistration.onRegistered = function(homeserverUrl, userId, accessToken) { matrixRegistration.sendObjectMessage({ 'action': 'onRegistered', 'homeServer': homeserverUrl,'userId': userId,  'accessToken': accessToken  }); };";

                    view.loadUrl(MXCJavascriptOnRegistered);
                }
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
                    }

                    // succeeds to parse parameters
                    if (null != parameters) {
                        // check the required paramaters
                        if (parameters.containsKey("homeServer") && parameters.containsKey("userId") && parameters.containsKey("accessToken") && parameters.containsKey("action")) {
                            final String userId =  parameters.get("userId");
                            final String accessToken =  parameters.get("accessToken");
                            String action =  parameters.get("action");

                            // remove the trailing /
                            if (mHomeServer.endsWith("/")) {
                                mHomeServer = mHomeServer.substring(0, mHomeServer.length()-1);
                            }

                            // check the action
                            if (action.equals("onRegistered")) {
                                AccountCreationActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Intent returnIntent = new Intent();
                                        returnIntent.putExtra("homeServer", mHomeServer);
                                        returnIntent.putExtra("userId", userId);
                                        returnIntent.putExtra("accessToken", accessToken);
                                        setResult(RESULT_OK, returnIntent);

                                        AccountCreationActivity.this.finish();
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
}
