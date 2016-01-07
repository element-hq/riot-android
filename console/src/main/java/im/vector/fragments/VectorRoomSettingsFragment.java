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
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;

import java.util.HashMap;

import im.vector.R;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.activity.VectorRoomDetailsActivity;
import im.vector.util.ResourceUtils;


public class VectorRoomSettingsFragment extends Fragment {
    private static final String LOG_TAG = "VectorRoomSettingsFragment";

    private static final int TAKE_IMAGE = "VectorRoomSettingsFragment_TAKE_IMAGE".hashCode();

    private MXSession mSession;
    private Room mRoom;
    private MXMediasCache mMediasCache = null;
    private BingRulesManager mBingRulesManager = null;

    // UI elements
    private ImageView mRoomAvatarImageView;
    private EditText mRoomLabelEditText;
    private EditText mRoomTopicEditText;
    private TextView mRoomStatusText;
    private CheckBox mRoomMuteNotificationCheckBox;

    // top view
    private View mViewHierarchy;

    // the save button is disabled until there is an updated items
    private MenuItem mSaveMenuItem;

    //
    private HashMap<Integer, Object> mUpdatedItemsByResourceId = new HashMap<Integer, Object>();
    private String mServerAvatarUri = null;

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
            refresh();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (isVisible()) {
            // Inflate the menu; this adds items to the action bar if it is present.
            inflater.inflate(R.menu.vector_room_settings, menu);

            mSaveMenuItem = menu.findItem(R.id.ic_action_room_details_save);
            refreshSaveButtonDisplay();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_room_details_save) {
            saveUpdates();
            return true;
        }

        return super.onOptionsItemSelected(item);
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
            mBingRulesManager = mSession.getDataHandler().getBingRulesManager();

            finalizeInit();
        }

        setHasOptionsMenu(true);

        return mViewHierarchy;
    }

    /**
     * Refresh the save button display.
     */
    private void refreshSaveButtonDisplay() {
        if (null != mSaveMenuItem) {
            Boolean hasUpdatedItems = mUpdatedItemsByResourceId.size() > 0;
            mSaveMenuItem.setEnabled(hasUpdatedItems);
            mSaveMenuItem.getIcon().setAlpha(hasUpdatedItems ? 255 : 70);
        }
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
            mRoomMuteNotificationCheckBox.setChecked(mBingRulesManager.isRoomNotificationsDisabled(mRoom));
        }

        refreshSaveButtonDisplay();
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

                String roomName = mRoom.getName(mSession.getMyUser().userId);

                if (null == roomName) {
                    roomName = "";
                }

                // save only if there is an update
                if (!TextUtils.equals(value, roomName)) {
                    mUpdatedItemsByResourceId.put(R.id.room_settings_room_name_edit_text, value);
                } else {
                    mUpdatedItemsByResourceId.remove(R.id.room_settings_room_name_edit_text);
                }

                refreshSaveButtonDisplay();
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

                String topic = (null != mRoom.getTopic()) ? mRoom.getTopic() : "";

                // save only if there is an update
                if (!TextUtils.equals(value, topic)) {
                    mUpdatedItemsByResourceId.put(R.id.room_settings_room_topic_edit_text, value);
                } else {
                    mUpdatedItemsByResourceId.remove(R.id.room_settings_room_topic_edit_text);
                }

                refreshSaveButtonDisplay();
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

                // add the updated item if the value is new
                if (mBingRulesManager.isRoomNotificationsDisabled(mRoom) != mRoomMuteNotificationCheckBox.isChecked()) {
                    mUpdatedItemsByResourceId.put(R.id.room_settings_push_checkbox, mRoomMuteNotificationCheckBox.isChecked());
                } else {
                    mUpdatedItemsByResourceId.remove(R.id.room_settings_push_checkbox);
                }

                refreshSaveButtonDisplay();
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

    /**
     * Save the room updates.
     */
    private void saveUpdates() {

        if (mUpdatedItemsByResourceId.containsKey(R.id.room_settings_room_avatar)) {
            Bitmap bitmap = (Bitmap) mUpdatedItemsByResourceId.get(R.id.room_settings_room_avatar);
            String thumbnailURL = mMediasCache.saveBitmap(bitmap, null);

            ResourceUtils.Resource resource = ResourceUtils.openResource(getActivity(), Uri.parse(thumbnailURL));

            mSession.getContentManager().uploadContent(resource.contentStream, mRoom.getRoomId(), "image/jpeg", thumbnailURL, new ContentManager.UploadCallback() {
                @Override
                public void onUploadStart(String uploadId) {
                }

                @Override
                public void onUploadProgress(String anUploadId, int percentageProgress) {
                }

                @Override
                public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse, final int serverReponseCode, final String serverErrorMessage) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mUpdatedItemsByResourceId.remove(R.id.room_settings_room_avatar);

                            if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                                mServerAvatarUri = uploadResponse.contentUri;
                            }

                            saveUpdates();
                        }
                    });
                }
            });

            return;
        }

        if (null != mServerAvatarUri) {
            mRoom.updateAvatarUrl(mServerAvatarUri, new ApiCallback<Void>() {

                private void onDone() {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mServerAvatarUri = null;
                            saveUpdates();
                        }
                    });
                }

                @Override
                public void onSuccess(Void info) {
                    onDone();
                }

                @Override
                public void onNetworkError(Exception e) {
                    onDone();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onDone();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onDone();
                }
            });

            return;
        }

        if (mUpdatedItemsByResourceId.containsKey(R.id.room_settings_room_name_edit_text)) {
            String roomName = (String) mUpdatedItemsByResourceId.get(R.id.room_settings_room_name_edit_text);

            mRoom.updateName(roomName, new ApiCallback<Void>() {

                private void onDone() {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mUpdatedItemsByResourceId.remove(R.id.room_settings_room_name_edit_text);
                            saveUpdates();
                        }
                    });
                }

                @Override
                public void onSuccess(Void info) {
                    onDone();
                }

                @Override
                public void onNetworkError(Exception e) {
                    onDone();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onDone();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onDone();
                }
            });

            return;
        }

        if (mUpdatedItemsByResourceId.containsKey(R.id.room_settings_room_topic_edit_text)) {
            String topic = (String) mUpdatedItemsByResourceId.get(R.id.room_settings_room_topic_edit_text);

            mRoom.updateTopic(topic, new ApiCallback<Void>() {

                private void onDone() {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mUpdatedItemsByResourceId.remove(R.id.room_settings_room_topic_edit_text);
                            saveUpdates();
                        }
                    });
                }

                @Override
                public void onSuccess(Void info) {
                    onDone();
                }

                @Override
                public void onNetworkError(Exception e) {
                    onDone();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onDone();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onDone();
                }
            });

            return;
        }

        if (mUpdatedItemsByResourceId.containsKey(R.id.room_settings_push_checkbox)) {
            Boolean isMuted = (Boolean)mUpdatedItemsByResourceId.get(R.id.room_settings_push_checkbox);

            mBingRulesManager.muteRoomNotifications(mRoom, isMuted, new BingRulesManager.onBingRuleUpdateListener() {

                private void onDone() {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mUpdatedItemsByResourceId.remove(R.id.room_settings_push_checkbox);
                            saveUpdates();
                        }
                    });
                }

                @Override
                public void onBingRuleUpdateSuccess() {
                    onDone();
                }

                @Override
                public void onBingRuleUpdateFailure(String errorMessage) {
                    onDone();
                }
            });

            return;
        }

        getActivity().finish();
    }
}
