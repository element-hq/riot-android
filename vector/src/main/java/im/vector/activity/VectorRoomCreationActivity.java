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
import android.support.design.widget.FloatingActionButton;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import java.util.ArrayList;

import im.vector.R;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorRoomCreationAdapter;


public class VectorRoomCreationActivity extends MXCActionBarActivity {
    private final String LOG_TAG = "VRoomCreationActivity";

    ListView mMembersListView;
    VectorRoomCreationAdapter mAdapter;
    FloatingActionButton mAddMembersButton;

    ArrayList<ParticipantAdapterItem> mParticipants = new ArrayList<>();

    private static final int INVITE_USER_REQUEST_CODE = 777;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_room_creation);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "onCreate : Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        final Intent intent = getIntent();

        mSession = getSession(intent);

        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        mMembersListView = (ListView) findViewById(R.id.room_creation_members_list_view);
        mAdapter = new VectorRoomCreationAdapter(this, R.layout.adapter_item_vector_add_participants, mSession);
        mMembersListView.setAdapter(mAdapter);

        mAdapter.setRoomCreationAdapterListener(new VectorRoomCreationAdapter.IRoomCreationAdapterListener() {
            @Override
            public void OnRemoveParticipantClick(ParticipantAdapterItem item) {
                mParticipants.remove(item);
                mAdapter.remove(item);
            }
        });

        mAddMembersButton = (FloatingActionButton) findViewById(R.id.room_creation_add_members_view);

        mAddMembersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(VectorRoomCreationActivity.this, VectorRoomInviteMembersActivity.class);
                intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_HIDDEN_PARTICIPANT_ITEMS, mParticipants);
                VectorRoomCreationActivity.this.startActivityForResult(intent, INVITE_USER_REQUEST_CODE);
            }
        });

        Log.d(LOG_TAG, "End of create");
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == INVITE_USER_REQUEST_CODE) && (resultCode == Activity.RESULT_OK)) {
            ParticipantAdapterItem item = (ParticipantAdapterItem)data.getSerializableExtra(VectorRoomInviteMembersActivity.EXTRA_SELECTED_PARTICIPANT_ITEM);
            mParticipants.add(item);

            mAdapter.add(item);
            mAdapter.sort(ParticipantAdapterItem.alphaComparator);
        }
    }
}
