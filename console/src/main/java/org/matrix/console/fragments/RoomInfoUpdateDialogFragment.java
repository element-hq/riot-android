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

package org.matrix.console.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.EditText;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.console.Matrix;
import org.matrix.console.R;
import org.matrix.console.util.UIUtils;


/**
 * A dialog fragment to update the roominfo
 */
public class RoomInfoUpdateDialogFragment extends DialogFragment {
    private static final String LOG_TAG = "RoomInfoUpdateDialogFragment";

    public static final String EXTRA_MATRIX_ID = "org.matrix.console.fragments.RoomInfoUpdateDialogFragment.EXTRA_MATRIX_ID";
    public static final String EXTRA_ROOM_ID = "org.matrix.console.fragments.RoomInfoUpdateDialogFragment.EXTRA_ROOM_ID";

    public static RoomInfoUpdateDialogFragment newInstance(String matrixId, String roomId) {
        RoomInfoUpdateDialogFragment f = new RoomInfoUpdateDialogFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_MATRIX_ID, matrixId);
        args.putString(EXTRA_ROOM_ID, roomId);
        f.setArguments(args);
        return f;
    }

    private String mMatrixId = null;
    private String mRoomId = null;
    private Room mRoom = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMatrixId = getArguments().getString(EXTRA_MATRIX_ID);
        mRoomId = getArguments().getString(EXTRA_ROOM_ID);

        MXSession session = Matrix.getInstance(getActivity()).getSession(mMatrixId);

        if (null != session) {
            mRoom = session.getDataHandler().getRoom(mRoomId);
        }

        if (null == mRoom) {
            dismiss();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final Activity activity = getActivity();

        final View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_dialog_activity_room_info, null);
        builder.setView(view);
        builder.setTitle(getString(R.string.action_room_info));

        final EditText editTextName =  (EditText)view.findViewById(R.id.editText_name);
        final EditText editTextTopic = (EditText)view.findViewById(R.id.editText_topic);

        editTextName.setText(mRoom.getLiveState().name);
        editTextTopic.setText(mRoom.getLiveState().topic);

        builder.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        // Save things
                        RoomState roomState = mRoom.getLiveState();
                        String nameFromForm = editTextName.getText().toString();
                        String topicFromForm = editTextTopic.getText().toString();

                        ApiCallback<Void> changeCallback = UIUtils.buildOnChangeCallback(activity);

                        if (UIUtils.hasFieldChanged(roomState.name, nameFromForm)) {
                            mRoom.updateName(nameFromForm, changeCallback);
                        }

                        if (UIUtils.hasFieldChanged(roomState.topic, topicFromForm)) {
                            mRoom.updateTopic(topicFromForm, changeCallback);
                        }
                    }
                });

        return builder.create();
    }
}
