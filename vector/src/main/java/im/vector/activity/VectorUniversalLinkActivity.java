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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

public class VectorUniversalLinkActivity extends Activity {

    @SuppressLint("LongLogTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String scheme, mimeType, action, packageName, uriString, host;
        Uri intentUri;
        Intent intent;
        final List<String> uriSegments;

        if (null != (intent = getIntent())) {
            scheme = intent.getScheme();
            action = intent.getAction();
            uriString = intent.getDataString();
            packageName = intent.getPackage();
            mimeType = intent.getType();

            Log.d("VectorUniversalLinkActivity", "## onCreate() scheme=" + scheme + " action=" + action + " uri getDataString=" + uriString);
            Log.d("VectorUniversalLinkActivity", "## onCreate() packageName=" + packageName + " mimeType=" + mimeType);

            if (null != (intentUri = intent.getData())) {
                host = intentUri.getHost();
                int port = intentUri.getPort();
                Log.d("VectorUniversalLinkActivity", "## onCreate() intentUri - intentUri.toString()=" + intentUri.toString());
                Log.d("VectorUniversalLinkActivity", "## onCreate() intentUri - host=" + host + " port=" + port + " path=" + intentUri.getPath() + " queryParams=" + intentUri.getQuery());
                Log.d("VectorUniversalLinkActivity", "## onCreate() intentUri - EncodedFragment=" + intentUri.getEncodedFragment()+" DecodedFragment="+intentUri.getFragment());
                Log.d("VectorUniversalLinkActivity", "## onCreate() intentUri - EncodedSchemeSpecificPart=" + intentUri.getEncodedSchemeSpecificPart()+" SchemeSpecificPart="+intentUri.getSchemeSpecificPart());
                Log.d("VectorUniversalLinkActivity", "## onCreate() intentUri - LastPathSegment="+intentUri.getLastPathSegment());

                try {
                    //if (Intent.ACTION_VIEW.equals(action)) {
                    uriSegments = intentUri.getPathSegments();
                    for (String segment : uriSegments) {
                        Log.d("VectorUniversalLinkActivity", "## onCreate() intentUri - uriSeg=" + segment);
                    }
                } catch (Exception ex) {
                    Log.d("VectorUniversalLinkActivity", "##ERROR Msg="+ex.getMessage());
                }
            }

            // forward intent to broadcast receiver..
            //Intent myBroadcastIntent = new Intent(getIntent().getAction(), getIntent().getData());
            //sendBroadcast(myBroadcastIntent);

            finish();
        }
    }
}
