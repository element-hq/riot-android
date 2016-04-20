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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import im.vector.R;
import im.vector.util.VectorUtils;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomEmailInvitation;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.HashMap;

// displays a preview of a dedicated room.
// and offer to join the room
public class VectorRoomPreviewActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = "VectorRoomPrevAct";

    // the paramater is too big to be sent by the intent
    // so use a static variable to send it
    public static RoomPreviewData sRoomPreviewData = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        if (null == sRoomPreviewData) {
            finish();
            return;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_room_preview);

        TextView actionBarHeaderRoomTopic = (TextView)findViewById(R.id.action_bar_header_room_topic);
        TextView actionBarHeaderRoomName = (TextView)findViewById(R.id.action_bar_header_room_title);
        TextView actionBarHeaderActiveMembers = (TextView)findViewById(R.id.action_bar_header_room_members);
        ImageView actionBarHeaderRoomAvatar = (ImageView)findViewById(R.id.avatar_img);
        TextView infoMessageTextView = (TextView)findViewById(R.id.room_preview_info_message);
        final View progressLayout = findViewById(R.id.room_preview_info_message);

        Button joinButton = (Button)findViewById(R.id.button_join_room);
        Button declineButton = (Button)findViewById(R.id.button_decline);

        RoomState roomState = sRoomPreviewData.getRoomState();

        if (null != roomState) {
            actionBarHeaderRoomTopic.setText(roomState.topic);

            // compute the number of joined members
            int joined = 0;
            for (RoomMember member : roomState.getMembers()) {
                if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                    joined++;
                }
            }

            if (joined == 1) {
                actionBarHeaderActiveMembers.setText(getResources().getString(R.string.room_title_one_member));
            } else {
                actionBarHeaderActiveMembers.setText(getResources().getString(R.string.room_title_members, joined));
            }
        } else {
            actionBarHeaderRoomTopic.setVisibility(View.GONE);
            actionBarHeaderActiveMembers.setVisibility(View.GONE);
        }

        final RoomEmailInvitation roomEmailInvitation =  sRoomPreviewData.getRoomEmailInvitation();

        String roomName = sRoomPreviewData.getRoomName();
        if (TextUtils.isEmpty(roomName)) {
            roomName = getResources().getString(R.string.room_preview_try_join_an_unknown_room_default);
        }

        if ((null != roomEmailInvitation) && !TextUtils.isEmpty(roomEmailInvitation.email)) {
            infoMessageTextView.setText(getResources().getString(R.string.room_preview_invitation_format, roomEmailInvitation.inviterName));
        } else {
            infoMessageTextView.setText(getResources().getString(R.string.room_preview_try_join_an_unknown_room, roomName));
        }

        // common items
        actionBarHeaderRoomName.setText(roomName);
        VectorUtils.loadUserAvatar(this, sRoomPreviewData.getSession(), actionBarHeaderRoomAvatar, sRoomPreviewData.getRoomAvatarUrl(), sRoomPreviewData.getRoomId(), roomName);

        joinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Room room = sRoomPreviewData.getSession().getDataHandler().getRoom(sRoomPreviewData.getRoomId());

                String signUrl = null;

                if (null != roomEmailInvitation) {
                    signUrl = roomEmailInvitation.signUrl;
                }

                progressLayout.setVisibility(View.VISIBLE);

                room.joinWithThirdPartySigned(signUrl, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        HashMap<String, Object> params = new HashMap<String, Object>();

                        params.put(VectorRoomActivity.EXTRA_MATRIX_ID, sRoomPreviewData.getSession().getMyUserId());
                        params.put(VectorRoomActivity.EXTRA_ROOM_ID, sRoomPreviewData.getRoomId());

                        // clear the activity stack to home activity
                        Intent intent = new Intent(VectorRoomPreviewActivity.this, VectorHomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, params);
                        VectorRoomPreviewActivity.this.startActivity(intent);
                    }

                    private void onError(String errorMessage) {
                        CommonActivityUtils.displayToast(VectorRoomPreviewActivity.this, errorMessage);
                        progressLayout.setVisibility(View.GONE);
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onError(e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        onError(e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onError(e.getLocalizedMessage());
                    }
                });

            }
        });

        declineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorRoomPreviewActivity.this.finish();
            }
        });
    }
}
