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

package im.vector.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import im.vector.Matrix;
import im.vector.R;
import im.vector.util.UIUtils;


/**
 * A dialog fragment to update the roominfo
 */
public class RoomInfoUpdateDialogFragment extends DialogFragment {

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

    private EditText mEditTextName;
    private EditText mEditTextTopic;
    private EditText mEditTextCanonical;

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

    private void saveRoomInfo() {
        // Save things
        RoomState roomState = mRoom.getLiveState();
        String nameFromForm = mEditTextName.getText().toString();
        String topicFromForm = mEditTextTopic.getText().toString();
        String canonicalFromForm = mEditTextCanonical.getText().toString();

        ApiCallback<Void> changeCallback = UIUtils.buildOnChangeCallback(null);

        if (UIUtils.hasFieldChanged(roomState.name, nameFromForm)) {
            mRoom.updateName(nameFromForm, changeCallback);
        }

        if (UIUtils.hasFieldChanged(roomState.topic, topicFromForm)) {
            mRoom.updateTopic(topicFromForm, changeCallback);
        }

        if (UIUtils.hasFieldChanged(roomState.alias, canonicalFromForm)) {
            mRoom.updateCanonicalAlias(canonicalFromForm, changeCallback);
        }
    }

    private boolean hasChanges() {
        // Save things
        RoomState roomState = mRoom.getLiveState();
        String nameFromForm = mEditTextName.getText().toString();
        String topicFromForm = mEditTextTopic.getText().toString();
        String canonicalFromForm = mEditTextCanonical.getText().toString();

        return UIUtils.hasFieldChanged(roomState.alias, canonicalFromForm) || UIUtils.hasFieldChanged(roomState.name, nameFromForm) || UIUtils.hasFieldChanged(roomState.topic, topicFromForm);
    }

    private void manageOkButton(final Button okButton) {
        okButton.setEnabled(hasChanges());
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_dialog_room_info, null);
        builder.setView(view);
        builder.setTitle(getString(R.string.action_room_info));

        mEditTextName =  (EditText)view.findViewById(R.id.editText_name);
        mEditTextTopic = (EditText)view.findViewById(R.id.editText_topic);
        mEditTextCanonical = (EditText)view.findViewById(R.id.editText_canonical);

        mEditTextName.setText(mRoom.getLiveState().name);
        mEditTextTopic.setText(mRoom.getLiveState().topic);
        mEditTextCanonical.setText(mRoom.getLiveState().alias);

        final Button okButton = (Button) view.findViewById(R.id.room_info_ok);
        final Button cancelButton = (Button) view.findViewById(R.id.room_info_cancel);

        mEditTextName.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                manageOkButton(okButton);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mEditTextTopic.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                manageOkButton(okButton);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mEditTextCanonical.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                manageOkButton(okButton);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveRoomInfo();
                RoomInfoUpdateDialogFragment.this.dismiss();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // something has been updated ?
                if (hasChanges()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setMessage(
                            R.string.room_info_room_discard_changes)
                            .setCancelable(false)
                            .setPositiveButton(R.string.room_info_room_discard,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int id) {
                                            RoomInfoUpdateDialogFragment.this.dismiss();
                                        }
                                    })
                            .setNegativeButton(R.string.save,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int id) {
                                            saveRoomInfo();
                                            RoomInfoUpdateDialogFragment.this.dismiss();
                                        }
                                    });

                    AlertDialog alert = builder.create();
                    alert.show();
                } else {
                    RoomInfoUpdateDialogFragment.this.dismiss();
                }
            }
        });

        return builder.create();
    }
}
