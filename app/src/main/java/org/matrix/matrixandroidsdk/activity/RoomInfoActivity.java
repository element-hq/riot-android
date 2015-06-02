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
package org.matrix.matrixandroidsdk.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.util.UIUtils;

/**
 * Activity for displaying and editing room information.
 */
public class RoomInfoActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "RoomInfoActivity";

    private MXSession mSession;
    private Room mRoom;

    // Views
    private EditText mNameField;
    private EditText mTopicField;
    private TextView mAliasesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_room_info);

        Intent intent = getIntent();
        if (!intent.hasExtra(RoomActivity.EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }
        String roomId = intent.getStringExtra(RoomActivity.EXTRA_ROOM_ID);
        Log.i(LOG_TAG, "Displaying "+roomId);

        // make sure we're logged in.
        mSession = getSession(intent);

        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        mRoom = mSession.getDataHandler().getRoom(roomId);
        RoomState roomState = mRoom.getLiveState();

        mNameField = (EditText) findViewById(R.id.editText_name);
        mNameField.setText(roomState.name);

        mTopicField = (EditText) findViewById(R.id.editText_topic);
        mTopicField.setText(roomState.topic);

        mAliasesView = (TextView) findViewById(R.id.textView_aliases);
        String aliasesText = "";
        if (roomState.aliases != null) {
            for (String alias : roomState.aliases) {
                if (!aliasesText.equals("")) {
                    aliasesText += "\n";
                }
                aliasesText += alias;
            }
            mAliasesView.setText(aliasesText);
        }
    }

    @Override
    protected void onDestroy() {
        // Save changes when we leave
        saveChanges();

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyPresenceManager.getInstance(this, mSession).advertiseUnavailableAfterDelay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyPresenceManager.getInstance(this, mSession).advertiseOnline();
    }

    private void saveChanges() {
        // Save things
        RoomState roomState = mRoom.getLiveState();
        String nameFromForm = mNameField.getText().toString();
        String topicFromForm = mTopicField.getText().toString();

        ApiCallback<Void> changeCallback = UIUtils.buildOnChangeCallback(this);

        if (UIUtils.hasFieldChanged(roomState.name, nameFromForm)) {
            mRoom.updateName(nameFromForm, changeCallback);
        }

        if (UIUtils.hasFieldChanged(roomState.topic, topicFromForm)) {
            mRoom.updateTopic(topicFromForm, changeCallback);
        }
    }
}
