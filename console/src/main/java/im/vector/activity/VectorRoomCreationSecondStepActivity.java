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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.adapters.AdapterUtils;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorAddParticipantsAdapter;
import im.vector.contacts.Contact;
import im.vector.fragments.VectorAddParticipantsFragment;

public class VectorRoomCreationSecondStepActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = "VectorAddActivity";

    // exclude the room ID
    public static final String EXTRA_ROOM_ID = "VectorAddParticipantsActivity.EXTRA_ROOM_ID";

    // creation mode : the members are listed to create a new room
    // edition mode (by default) : the members are dynamically added/removed
    public static final String EXTRA_EDITION_MODE = "VectorAddParticipantsActivity.EXTRA_EDITION_MODE";

    // in creation mode, this is the key to retrieve the users IDs liste
    public static final String RESULT_USERS_ID = "VectorAddParticipantsActivity.RESULT_USERS_ID";

    public MXSession mSession;

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

        setContentView(R.layout.activity_vector_room_creation_second_step);
    }

    public MXSession getSession() {
        return mSession;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vector_room_create_second_step, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_create) {
            VectorRoomCreationSecondStepActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Fragment fragment = getFragmentManager().findFragmentById(R.id.room_creation_add_participants_fragment);

                    if (fragment instanceof VectorAddParticipantsFragment) {
                        VectorAddParticipantsFragment vectorAddParticipantsFragment = (VectorAddParticipantsFragment)fragment;

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
