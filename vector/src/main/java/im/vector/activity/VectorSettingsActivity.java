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

import android.content.Intent;
import android.os.Bundle;

import org.matrix.androidsdk.MXSession;

import im.vector.Matrix;
import im.vector.R;
import im.vector.fragments.VectorSettingsPreferencesFragment;

/**
 * Displays the client settings.
 */
public class VectorSettingsActivity extends MXCActionBarActivity {
    // session
    private MXSession mSession;

    // the UI items
    private VectorSettingsPreferencesFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mSession = getSession(intent);

        if (null == mSession) {
            mSession = Matrix.getInstance(VectorSettingsActivity.this).getDefaultSession();
        }

        if (mSession == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_vector_settings);

        // display the fragment
        mFragment = VectorSettingsPreferencesFragment.newInstance(mSession.getMyUserId());
        getFragmentManager().beginTransaction().replace(R.id.vector_settings_page, mFragment).commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // pass the result to the fragment
        mFragment.onActivityResult(requestCode, resultCode, data);
    }
}