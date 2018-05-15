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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MenuItem;

import org.matrix.androidsdk.MXSession;

import im.vector.Matrix;
import im.vector.R;
import im.vector.fragments.VectorSettingsPreferencesFragment;
import im.vector.util.VectorUtils;

/**
 * Displays the client settings.
 */
public class VectorSettingsActivity extends MXCActionBarActivity {
    // the UI items
    private VectorSettingsPreferencesFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // required to have the right translated title
        setTitle(R.string.title_activity_settings);

        Intent intent = getIntent();
        MXSession session = getSession(intent);

        if (null == session) {
            session = Matrix.getInstance(VectorSettingsActivity.this).getDefaultSession();
        }

        if (session == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_vector_settings);

        // display the fragment
        mFragment = VectorSettingsPreferencesFragment.newInstance(session.getMyUserId());
        getFragmentManager().beginTransaction().replace(R.id.vector_settings_page, mFragment).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // pass the result to the fragment
        mFragment.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode, @NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO) {
            boolean granted = false;

            for (int i = 0; i < aGrantResults.length; i++) {
                granted |= (PackageManager.PERMISSION_GRANTED == aGrantResults[i]);
            }

            if (granted) {
                Intent intent = new Intent(this, VectorMediasPickerActivity.class);
                intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true);
                startActivityForResult(intent, VectorUtils.TAKE_IMAGE);
            }
        }
    }
}
