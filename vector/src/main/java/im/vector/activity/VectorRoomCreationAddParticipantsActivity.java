/*
 * Copyright 2014 OpenMarket Ltd
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

import android.support.v4.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;

import im.vector.Matrix;
import im.vector.R;
import im.vector.fragments.VectorAddParticipantsFragment;

public class VectorRoomCreationAddParticipantsActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = "VectorRCAddParticipantsActivity";

    // exclude the room ID
    public static final String EXTRA_ROOM_ID = "VectorAddParticipantsActivity.EXTRA_ROOM_ID";

    // in creation mode, this is the key to retrieve the users IDs liste
    public static final String RESULT_USERS_ID = "VectorAddParticipantsActivity.RESULT_USERS_ID";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        String matrixId = null;
        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            matrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        mSession = Matrix.getInstance(getApplicationContext()).getSession(matrixId);

        setContentView(R.layout.activity_vector_room_creation_add_participants);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vector_room_create_add_participants, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_create) {
            VectorRoomCreationAddParticipantsActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.room_creation_add_participants_fragment);

                    if (fragment instanceof VectorAddParticipantsFragment) {
                        VectorAddParticipantsFragment vectorAddParticipantsFragment = (VectorAddParticipantsFragment)fragment;

                        vectorAddParticipantsFragment.dismissKeyboard();

                        Intent intent = new Intent();

                        ArrayList<String> users = new ArrayList<String>();
                        intent.putStringArrayListExtra(RESULT_USERS_ID, vectorAddParticipantsFragment.getUserIdsList());
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                }
            });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
