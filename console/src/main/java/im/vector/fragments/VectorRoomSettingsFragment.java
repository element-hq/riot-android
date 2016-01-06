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

package im.vector.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.HashMap;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.activity.VectorRoomDetailsActivity;
import im.vector.util.ResourceUtils;


public class VectorRoomSettingsFragment extends Fragment {
    private static final String LOG_TAG = "VectorRoomSettingsFragment";

    private static final int TAKE_IMAGE = "VectorRoomSettingsFragment_TAKE_IMAGE".hashCode();

    private MXSession mSession;
    private Room mRoom;
    private MXMediasCache mMediasCache = null;

    // UI elements
    private ImageView mRoomAvatarImageView;
    private EditText mRoomLabelEditText;
    private EditText mRoomTopicEditText;
    private TextView mRoomStatusText;
    private CheckBox mRoomMuteNotificationCheckBox;

    // top view
    private View mViewHierarchy;

    //
    private HashMap<Integer, Object> mUpdatedItemsByResourceId = new HashMap<Integer, Object>();

    private MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    // The various events that could possibly change the fragment items
                    if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(event.type)) {
                        refresh();
                    }
                }
            });
        }

        @Override
        public void onBingRulesUpdate() {
            refresh();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // check if there is any
        if (null != savedInstanceState) {
            mUpdatedItemsByResourceId = (HashMap<Integer, Object>) savedInstanceState.getSerializable("mUpdatedItemsByResourceId");

            if (null == mUpdatedItemsByResourceId) {
                mUpdatedItemsByResourceId = new HashMap<Integer, Object>();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (null != mRoom) {
            mRoom.removeEventListener(mEventListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (null != mRoom) {
            mRoom.addEventListener(mEventListener);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("mUpdatedItemsByResourceId", mUpdatedItemsByResourceId);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mViewHierarchy = inflater.inflate(R.layout.fragment_vector_room_settings, container, false);

        Activity activity = getActivity();

        if (activity instanceof VectorRoomDetailsActivity) {
            VectorRoomDetailsActivity vectorRoomDetailsActivity = (VectorRoomDetailsActivity)activity;

            mRoom = vectorRoomDetailsActivity.getRoom();
            mSession = vectorRoomDetailsActivity.getSession();
            mMediasCache = mSession.getMediasCache();

            finalizeInit();
        }

        return mViewHierarchy;
    }

    /**
     * Refresh the fragment items
     */
    private void refresh() {
        // cannot refresh if there is no valid session / room
        if ((null == mRoom) || (null == mSession)) {
            return;
        }

        PowerLevels powerLevels =  mRoom.getLiveState().getPowerLevels();
        int powerLevel = powerLevels.getUserPowerLevel(mSession.getMyUser().userId);
        boolean canUpdateAvatar = powerLevel >=  powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_AVATAR);
        boolean canUpdateName = powerLevel >=  powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_NAME);
        boolean canUpdateTopic = powerLevel >=  powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_TOPIC);


        // room avatar
        mRoomAvatarImageView.setEnabled(canUpdateAvatar);
        mRoomAvatarImageView.setAlpha(canUpdateAvatar ? 1.0f : 0.5f);

        if (canUpdateAvatar && mUpdatedItemsByResourceId.containsKey(R.id.room_settings_room_avatar)) {
            mRoomAvatarImageView.setImageBitmap((Bitmap) mUpdatedItemsByResourceId.get(R.id.room_settings_room_avatar));
        } else {
            // TODO use the generated vector image
            mRoomAvatarImageView.setImageResource(org.matrix.androidsdk.R.drawable.ic_contact_picture_holo_light);

            String roomAvatarUrl = mRoom.getLiveState().getAvatarUrl();

            if (null != roomAvatarUrl) {
                int size = getActivity().getResources().getDimensionPixelSize(org.matrix.androidsdk.R.dimen.chat_avatar_size);
                mMediasCache.loadAvatarThumbnail(mSession.getHomeserverConfig(), mRoomAvatarImageView, roomAvatarUrl, size);
            }
        }

        // room name
        mRoomLabelEditText.setEnabled(canUpdateName);
        mRoomLabelEditText.setAlpha(canUpdateName ? 1.0f : 0.5f);

        if (mUpdatedItemsByResourceId.containsKey(R.id.room_settings_room_name_edit_text)) {
            mRoomLabelEditText.setText((String) mUpdatedItemsByResourceId.get(R.id.room_settings_room_name_edit_text));
        } else {
            mRoomLabelEditText.setText(mRoom.getName(mSession.getMyUser().userId));
        }

        // room topic
        mRoomTopicEditText.setEnabled(canUpdateTopic);
        mRoomTopicEditText.setAlpha(canUpdateTopic ? 1.0f : 0.5f);

        if (mUpdatedItemsByResourceId.containsKey(R.id.room_settings_room_topic_edit_text)) {
            mRoomTopicEditText.setText((String)mUpdatedItemsByResourceId.get(R.id.room_settings_room_topic_edit_text));
        } else {
            mRoomTopicEditText.setText(mRoom.getTopic());
        }

        // room state
        if (TextUtils.equals(mRoom.getLiveState().visibility, RoomState.VISIBILITY_PUBLIC)) {
            mRoomStatusText.setText(R.string.room_details_room_is_public);
        } else {
            mRoomStatusText.setText(R.string.room_details_room_is_private);
        }

        // room push rule
        if (mUpdatedItemsByResourceId.containsKey(R.id.room_settings_push_checkbox)) {
            mRoomMuteNotificationCheckBox.setChecked((Boolean)mUpdatedItemsByResourceId.get(R.id.room_settings_push_checkbox));
        } else {
            // TODO
        }
    }

    /**
     * Finalize the fragment initialization.
     */
    private void finalizeInit() {
        mRoomAvatarImageView = (ImageView) (mViewHierarchy.findViewById(R.id.room_settings_room_avatar).findViewById(R.id.avatar_img));
        mRoomLabelEditText = (EditText) mViewHierarchy.findViewById(R.id.room_settings_room_name_edit_text);
        mRoomTopicEditText = (EditText) mViewHierarchy.findViewById(R.id.room_settings_room_topic_edit_text);
        mRoomStatusText = (TextView) mViewHierarchy.findViewById(R.id.room_settings_room_status);
        mRoomMuteNotificationCheckBox = (CheckBox) mViewHierarchy.findViewById(R.id.room_settings_push_checkbox);

        mRoomLabelEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                String value = mRoomLabelEditText.getText().toString();

                if (null == value) {
                    value = "";
                }

                // save only if there is an update
                if (!TextUtils.equals(value, mRoom.getName(mSession.getMyUser().userId))) {
                    mUpdatedItemsByResourceId.put(R.id.room_settings_room_name_edit_text, value);
                } else {
                    mUpdatedItemsByResourceId.remove(R.id.room_settings_room_name_edit_text);
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mRoomTopicEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                String value = mRoomTopicEditText.getText().toString();

                if (null == value) {
                    value = "";
                }

                // save only if there is an update
                if (!TextUtils.equals(value, mRoom.getTopic())) {
                    mUpdatedItemsByResourceId.put(R.id.room_settings_room_topic_edit_text, value);
                } else {
                    mUpdatedItemsByResourceId.remove(R.id.room_settings_room_topic_edit_text);
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mRoomAvatarImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), VectorMediasPickerActivity.class);
                intent.putExtra(VectorMediasPickerActivity.EXTRA_SINGLE_IMAGE_MODE, "");
                startActivityForResult(intent, TAKE_IMAGE);
            }
        });

        mRoomMuteNotificationCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mUpdatedItemsByResourceId.put(R.id.room_settings_push_checkbox, mRoomMuteNotificationCheckBox.isChecked());
            }
        });

        mRoom.addEventListener(mEventListener);

        refresh();
    }

    @Override
    @SuppressLint("NewApi")
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == TAKE_IMAGE) {
                Uri mediaUri = null;

                if (null != data) {
                    mediaUri = data.getData();
                }

                Bitmap thumbnail = ResourceUtils.getThumbnailBitmap(getActivity(), mediaUri);

                if (null != thumbnail) {
                    mUpdatedItemsByResourceId.put(R.id.room_settings_room_avatar, thumbnail);
                    refresh();
                }
            }
        }
    }
}
