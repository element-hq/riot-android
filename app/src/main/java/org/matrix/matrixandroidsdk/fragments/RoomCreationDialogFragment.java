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

package org.matrix.matrixandroidsdk.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.activity.CommonActivityUtils;
import org.matrix.matrixandroidsdk.activity.HomeActivity;
import org.matrix.matrixandroidsdk.adapters.AdapterUtils;

import java.util.ArrayList;

/**
 * A dialog fragment showing a list of room members for a given room.
 */
public class RoomCreationDialogFragment extends DialogFragment {
    private static final String LOG_TAG = "RoomCreationDialogFragment";

    private MXSession mSession;

    public static RoomCreationDialogFragment newInstance(MXSession session) {
        RoomCreationDialogFragment f = new RoomCreationDialogFragment();
        Bundle args = new Bundle();
        f.setArguments(args);
        f.setSession(session);
        return f;
    }

    public void setSession(MXSession session) {
        mSession = session;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mSession == null) {
            throw new RuntimeException("No MXSession.");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final Activity activity = getActivity();

        final View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_dialog_create_room, null);
        builder.setView(view);
        builder.setTitle(getString(R.string.room_creation_create_room));

        builder.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {

                        CheckBox publicCheckbox = (CheckBox)view.findViewById(R.id.checkbox_room_creation);
                        final String roomVisibility = publicCheckbox.isChecked() ? RoomState.VISIBILITY_PUBLIC : RoomState.VISIBILITY_PRIVATE;

                        EditText roomNameEdittext = (EditText)view.findViewById(R.id.editText_roomName);
                        String roomName = roomNameEdittext.getText().toString();

                        EditText roomAliasEdittext = (EditText)view.findViewById(R.id.editText_roomAlias);
                        String roomAlias = roomAliasEdittext.getText().toString();

                        // get the user suffix
                        String userID = mSession.getCredentials().userId;
                        String homeServerSuffix = userID.substring(userID.indexOf(":"), userID.length());

                        // check the syntax
                        if (!TextUtils.isEmpty(roomAlias)) {
                            roomAlias = roomAlias.trim();

                            if (!TextUtils.isEmpty(roomAlias)) {
                                // add the missing #
                                if (!roomAlias.startsWith("#")) {
                                    roomAlias = "#" + roomAlias;
                                }

                                if (roomAlias.indexOf(":") < 0) {
                                    roomAlias += ":" + homeServerSuffix;
                                }
                            }
                        }

                        EditText participantsEdittext = (EditText)view.findViewById(R.id.editText_participants);

                        // get the members list
                        final ArrayList<String> userIDsList = CommonActivityUtils.parseUserIDsList(participantsEdittext.getText().toString(), homeServerSuffix);

                        mSession.createRoom(roomName, null, roomVisibility, roomAlias, new SimpleApiCallback<String>(getActivity()) {
                            @Override
                            public void onSuccess(String roomId) {
                                CommonActivityUtils.goToRoomPage(mSession, roomId, activity, null);

                                Room room = mSession.getDataHandler().getRoom(roomId);

                                if (null != room) {
                                    room.invite(userIDsList, new SimpleApiCallback<Void>(getActivity()) {
                                        @Override
                                        public void onSuccess(Void info) {
                                        }
                                    });
                                }
                            }
                        });
                    }
                });

        return builder.create();
    }
}
