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

import android.app.Activity;
//
import android.content.ClipData;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.preference.RoomAvatarPreference;
import im.vector.util.ResourceUtils;
import im.vector.util.VectorUtils;

public class VectorRoomSettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    // internal constants values
    private static final String LOG_TAG = "VectorRoomSetFragment";
    private static final boolean UPDATE_UI = true;
    private static final boolean DO_NOT_UPDATE_UI = false;
    private static final int REQ_CODE_UPDATE_ROOM_AVATAR = 0x10;

    // fragment extra args keys
    private static final String EXTRA_MATRIX_ID = "KEY_EXTRA_MATRIX_ID";
    private static final String EXTRA_ROOM_ID = "KEY_EXTRA_ROOM_ID";

    // preference keys: public API to access preference
    public static final String PREF_KEY_ROOM_PHOTO_AVATAR = "roomPhotoAvatar";
    public static final String PREF_KEY_ROOM_NAME = "roomNameEditText";
    public static final String PREF_KEY_ROOM_TOPIC = "roomTopicEditText";
    public static final String PREF_KEY_ROOM_PRIVACY_SWITCH = "roomPrivacySwitch";
    // for further use: public static final String PREF_KEY_ROOM_PRIVACY_INFO = "roomPrivacyInfo";
    public static final String PREF_KEY_ROOM_MUTE_NOTIFICATIONS_SWITCH = "muteNotificationsSwitch";

    // business code
    private MXSession mSession;
    private Room mRoom;
    private BingRulesManager mBingRulesManager;

    // UI elements
    private RoomAvatarPreference mRoomPhotoAvatar;
    private EditTextPreference mRoomNameEditTxt;
    private EditTextPreference mRoomTopicEditTxt;
    private SwitchPreference mRoomPrivacySwitch;
    private SwitchPreference mRoomMuteNotificationsSwitch;
    // for further use: private Preference mPrivacyInfoPreference;
    private View mParentLoadingView;
    private View mParentFragmentContainerView;

    // MX system events listener
    private final MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    // The various events that could possibly change the fragment items
                    if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)) {
                        Log.d(LOG_TAG, "## onLiveEvent() event=" + event.type);
                        updateUi();
                    }
                }
            });
        }

        @Override
        public void onBingRulesUpdate() {
            updateUi();
        }
    };

    public static VectorRoomSettingsFragment newInstance(String aMatrixId,String aRoomId) {
        VectorRoomSettingsFragment theFragment = new VectorRoomSettingsFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_MATRIX_ID, aMatrixId);
        args.putString(EXTRA_ROOM_ID, aRoomId);
        theFragment.setArguments(args);

        return theFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // retrieve fragment extras
        String matrixId = getArguments().getString(EXTRA_MATRIX_ID);
        String roomId = getArguments().getString(EXTRA_ROOM_ID);

        if(TextUtils.isEmpty(matrixId) || TextUtils.isEmpty(roomId)){
            Log.e(LOG_TAG, "## onCreate(): fragment extras (MatrixId or RoomId) are missing");
            getActivity().finish();
        }
        else {
            mSession = Matrix.getInstance(getActivity()).getSession(matrixId);
            if (null != mSession) {
                mRoom = mSession.getDataHandler().getRoom(roomId);
                mBingRulesManager = mSession.getDataHandler().getBingRulesManager();
            }

            if (null == mRoom) {
                Log.e(LOG_TAG, "## onCreate(): unable to retrieve Room object");
                getActivity().finish();
            }
        }

        // load preference xml file
        addPreferencesFromResource(R.xml.vector_room_settings_preferences);

        // init preference fields
        mRoomPhotoAvatar = (RoomAvatarPreference)findPreference(PREF_KEY_ROOM_PHOTO_AVATAR);
        mRoomNameEditTxt = (EditTextPreference)findPreference(PREF_KEY_ROOM_NAME);
        mRoomTopicEditTxt = (EditTextPreference)findPreference(PREF_KEY_ROOM_TOPIC);
        mRoomPrivacySwitch = (SwitchPreference)findPreference(PREF_KEY_ROOM_PRIVACY_SWITCH);
        //mPrivacyInfoPreference = (Preference)findPreference(PREF_KEY_ROOM_PRIVACY_INFO); further use
        mRoomMuteNotificationsSwitch = (SwitchPreference)findPreference(PREF_KEY_ROOM_MUTE_NOTIFICATIONS_SWITCH);

        // init the room avatar: session and room
        mRoomPhotoAvatar.setConfiguration(mSession, mRoom);
        mRoomPhotoAvatar.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if ((null != mRoomPhotoAvatar) && mRoomPhotoAvatar.isEnabled()) {
                    onRoomAvatarPreferenceChanged();
                    return true; //True if the click was handled.
                } else
                    return false;
            }
        });

        // update the UI preference screen: values & access(disable/enable widgets)
        updateUi();

        // listen to preference changes
        SharedPreferences prefMgr = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefMgr.registerOnSharedPreferenceChangeListener(this);

        setRetainInstance(true);
    }


    /**
     * This method expects a view with the id "settings_loading_layout",
     * that is present in the parent activity layout.
     * @param view fragment view
     * @param savedInstanceState bundle instance state
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // retrieve the loading screen in the parent view
        View parent = getView();
        if (null == mParentLoadingView) {
            while ((null != parent) && (null == mParentLoadingView)) {
                mParentLoadingView = parent.findViewById(R.id.settings_loading_layout);
                parent = (View) parent.getParent();
            }
        }

        // retrieve the parent fragment container view to disable access to the settings
        // while the loading screen is enabled
        parent = getView();
        if (null == mParentFragmentContainerView) {
            while ((null != parent) && (null == mParentFragmentContainerView)) {
                mParentFragmentContainerView = parent.findViewById(R.id.room_details_fragment_container);
                parent = (View) parent.getParent();
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
            updateUi();
        }
    }

    private void updateUi(){
        // configure the preferences that are allowed to be modified by the user
        updatePreferenceAccessFromPowerLevel();

        // set settings UI values
        updatePreferenceUiValues();
    }

    private void updateUiOnUiThread() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUi();
            }
        });
    }

    private void updatePreferenceAccessFromPowerLevel(){
        boolean canUpdateAvatar = false;
        boolean canUpdateName = false;
        boolean canUpdateTopic = false;

        // cannot refresh if there is no valid session / room
        if ((null != mRoom) && (null != mSession)) {
            PowerLevels powerLevels =  mRoom.getLiveState().getPowerLevels();
            int powerLevel = powerLevels.getUserPowerLevel(mSession.getMyUser().userId);
            canUpdateAvatar = powerLevel >=  powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_AVATAR);
            canUpdateName = powerLevel >=  powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_NAME);
            canUpdateTopic = powerLevel >=  powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_TOPIC);
        }
        else {
            Log.w(LOG_TAG, "## updatePreferenceAccessFromPowerLevel(): session or room may be missing");
        }

        if(null != mRoomPhotoAvatar)
            mRoomPhotoAvatar.setEnabled(canUpdateAvatar);

        if(null != mRoomNameEditTxt)
            mRoomNameEditTxt.setEnabled(canUpdateName);

        if(null != mRoomTopicEditTxt)
            mRoomTopicEditTxt.setEnabled(canUpdateTopic);

        // use the room name power to enable the privacy switch
        if(null != mRoomPrivacySwitch)
            mRoomPrivacySwitch.setEnabled(false);

        // use the room name power to enable the room notification mute setting
        if(null != mRoomMuteNotificationsSwitch)
            mRoomMuteNotificationsSwitch.setEnabled(canUpdateName);
    }


    /**
     * Update the UI preference from the values taken from
     * the SDK layer.
     */
    private void updatePreferenceUiValues() {
        String value;

        if ((null == mSession) || (null == mRoom)){
            Log.w(LOG_TAG,"## updatePreferenceUiValues(): session or room may be missing");
            return;
        }

        if(null != mRoomPhotoAvatar){
            mRoomPhotoAvatar.refreshAvatar();
        }

        // update the room name preference
        if(null != mRoomNameEditTxt) {
            value = VectorUtils.getRoomDisplayname(getActivity(), mSession, mRoom);
            mRoomNameEditTxt.setSummary(value);
            mRoomNameEditTxt.setText(value);
        }

        // update the room topic preference
        if(null != mRoomTopicEditTxt) {
            value = mRoom.getTopic();
            mRoomTopicEditTxt.setSummary(value);
            mRoomTopicEditTxt.setText(value);
        }

        // update the mute notifications preference
        if(null != mRoomMuteNotificationsSwitch) {
            boolean isChecked = mBingRulesManager.isRoomNotificationsDisabled(mRoom);
            mRoomMuteNotificationsSwitch.setChecked(isChecked);
        }

        // update room visibility
        boolean isRoomPublic = TextUtils.equals(mRoom.getLiveState().visibility, RoomState.VISIBILITY_PUBLIC);
        if(null != mRoomPrivacySwitch) {
            mRoomPrivacySwitch.setChecked(isRoomPublic);
        }
        /* further use: display if the room is public or private
        if(null != mPrivacyInfoPreference) {
            if (isRoomPublic)
                mPrivacyInfoPreference.setSummary(R.string.room_details_room_is_public);
            else
                mPrivacyInfoPreference.setSummary(R.string.room_details_room_is_private);
        }*/
    }

    // OnSharedPreferenceChangeListener implementation
    @Override
    public void onSharedPreferenceChanged(SharedPreferences aSharedPreferences, String aKey) {

        if (aKey.equals(PREF_KEY_ROOM_PHOTO_AVATAR)) {
            // unused flow: onSharedPreferenceChanged not triggered for room avatar photo
            onRoomAvatarPreferenceChanged();
        }
        else if(aKey.equals(PREF_KEY_ROOM_NAME)) {
            onRoomNamePreferenceChanged();
        }
        else if(aKey.equals(PREF_KEY_ROOM_TOPIC)) {
            onRoomTopicPreferenceChanged();
        }
        else if(aKey.equals(PREF_KEY_ROOM_MUTE_NOTIFICATIONS_SWITCH)) {
            onRoomMuteNotificationsPreferenceChanged();
        }
        else if(aKey.equals(PREF_KEY_ROOM_PRIVACY_SWITCH)) {
            // not yet implemented
            Activity parent = getActivity();
            if(null != parent)
                Toast.makeText(parent,"Not yet implemented",Toast.LENGTH_SHORT).show();
        }
        else {
            Log.w(LOG_TAG,"## onSharedPreferenceChanged(): unknown preference detected");
        }
    }

    private void onRoomMuteNotificationsPreferenceChanged(){
        // sanity check
        if((null == mRoom) || (null == mBingRulesManager) || (null == mRoomMuteNotificationsSwitch)){
            return;
        }

        // get new and previous values
        boolean isNotificationsMuted = mRoomMuteNotificationsSwitch.isChecked();
        boolean previousValue = mBingRulesManager.isRoomNotificationsDisabled(mRoom);

        // update only, if values are different
        if(isNotificationsMuted != previousValue) {
            displayLoadingView();
            mBingRulesManager.muteRoomNotifications(mRoom, isNotificationsMuted, new BingRulesManager.onBingRuleUpdateListener() {
                @Override
                public void onBingRuleUpdateSuccess() {
                    Log.d(LOG_TAG, "##onRoomMuteNotificationsPreferenceChanged(): update succeed");
                    hideLoadingView(UPDATE_UI);
                }

                @Override
                public void onBingRuleUpdateFailure(String errorMessage) {
                    Log.w(LOG_TAG, "##onRoomMuteNotificationsPreferenceChanged(): BingRuleUpdateFailure");
                    hideLoadingView(DO_NOT_UPDATE_UI);
                }
            });
        }
    }

    private void onRoomNamePreferenceChanged(){
        // sanity check
        if((null == mRoom) || (null == mSession) || (null == mRoomNameEditTxt)){
            return;
        }

        // get new and previous values
        String previousName = mRoom.getName(mSession.getMyUser().userId);
        String newName = mRoomNameEditTxt.getText();
        // update only, if values are different
        if (!TextUtils.equals(previousName, newName)) {
            displayLoadingView();
            mRoom.updateName(newName, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    Log.d(LOG_TAG, "##onRoomNamePreferenceChanged(): update succeed");
                    hideLoadingView(UPDATE_UI);
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.w(LOG_TAG, "##onRoomNamePreferenceChanged(): room name update failure - NetworkError");
                    hideLoadingView(DO_NOT_UPDATE_UI);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.w(LOG_TAG, "##onRoomNamePreferenceChanged(): room name update failure - MatrixError");
                    hideLoadingView(DO_NOT_UPDATE_UI);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.w(LOG_TAG, "##onRoomNamePreferenceChanged(): room name update failure - UnexpectedError");
                    hideLoadingView(DO_NOT_UPDATE_UI);
                }
            });
        }
    }

    private void onRoomTopicPreferenceChanged() {
        // sanity check
        if(null == mRoom){
            return;
        }

        // get new and previous values
        String previousTopic = mRoom.getTopic();
        String newTopic = mRoomTopicEditTxt.getText();
        // update only, if values are different
        if (!TextUtils.equals(previousTopic, newTopic)) {
            displayLoadingView();
            mRoom.updateTopic(newTopic, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    Log.d(LOG_TAG, "##onRoomTopicPreferenceChanged(): update succeed");
                    hideLoadingView(UPDATE_UI);
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.w(LOG_TAG, "##onRoomTopicPreferenceChanged(): update failure - NetworkError");
                    hideLoadingView(DO_NOT_UPDATE_UI);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.w(LOG_TAG, "##onRoomTopicPreferenceChanged(): update failure - MatrixError");
                    hideLoadingView(DO_NOT_UPDATE_UI);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.w(LOG_TAG, "##onRoomTopicPreferenceChanged(): update failure - UnexpectedError");
                    hideLoadingView(DO_NOT_UPDATE_UI);
                }
            });
        }

    }
    /**
     * Update the room avatar.
     * Start the camera activity to take the avatar picture.
     */
    private void onRoomAvatarPreferenceChanged() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(getActivity(), VectorMediasPickerActivity.class);
                intent.putExtra(VectorMediasPickerActivity.EXTRA_SINGLE_IMAGE_MODE, "");
                startActivityForResult(intent, REQ_CODE_UPDATE_ROOM_AVATAR);
            }
        });
    }

    /**
     * Process the result of the room avatar picture.
     *
     * @param aRequestCode request ID
     * @param aResultCode request status code
     * @param aData result data
     */
    @Override
    public void onActivityResult(int aRequestCode, int aResultCode, final Intent aData) {
        super.onActivityResult(aRequestCode, aResultCode, aData);

        if (REQ_CODE_UPDATE_ROOM_AVATAR == aRequestCode) {
            onActivityResultRoomAvatarUpdate(aResultCode, aData);
        }
    }

    private void onActivityResultRoomAvatarUpdate(int aResultCode, final Intent aData){
        Uri thumbnailUri = null;
        ClipData clipData = null;
        Bitmap thumbnailBitmap = null;

        // sanity check
        if(null == mSession){
            return;
        }

        if (aResultCode == Activity.RESULT_OK) {
            if (null != aData) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    clipData = aData.getClipData();
                }

                // get thumbnail URI
                if (null != clipData) { // multiple data
                    if (clipData.getItemCount() > 0) {
                        thumbnailUri = clipData.getItemAt(0).getUri();
                    }
                }
                else if (null != aData.getData()) {
                    thumbnailUri = aData.getData();
                }

                if (null != thumbnailUri) {
                    thumbnailBitmap = VectorUtils.getBitmapFromuri(getActivity(), thumbnailUri);
                }
                else {
                    // no thumbnail URI found, just abort here
                    return;
                }

                // save the bitmap into the cache and retrieve its URL
                String thumbnailUrl = mSession.getMediasCache().saveBitmap(thumbnailBitmap, null);

                if (null != thumbnailUrl) {
                    displayLoadingView();

                    // save the bitmap URL on the server
                    ResourceUtils.Resource resource = ResourceUtils.openResource(getActivity(), Uri.parse(thumbnailUrl));
                    if(null != resource) {
                        mSession.getContentManager().uploadContent(resource.contentStream, null, resource.mimeType, null, new ContentManager.UploadCallback() {
                            @Override
                            public void onUploadStart(String uploadId) {
                            }

                            @Override
                            public void onUploadProgress(String anUploadId, int percentageProgress) {
                            }

                            @Override
                            public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse, final int serverResponseCode, final String serverErrorMessage) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                                            mRoom.updateAvatarUrl(uploadResponse.contentUri, new ApiCallback<Void>() {
                                                @Override
                                                public void onSuccess(Void info) {
                                                    Log.d(LOG_TAG, "##onActivityResultRoomAvatarUpdate(): update succeed");
                                                    hideLoadingView(UPDATE_UI);
                                                }

                                                @Override
                                                public void onNetworkError(Exception e) {
                                                    Log.w(LOG_TAG, "##onActivityResultRoomAvatarUpdate(): update failure - NetworkError");
                                                    hideLoadingView(DO_NOT_UPDATE_UI);
                                                }

                                                @Override
                                                public void onMatrixError(MatrixError e) {
                                                    Log.w(LOG_TAG, "##onActivityResultRoomAvatarUpdate(): update failure - MatrixError");
                                                    hideLoadingView(DO_NOT_UPDATE_UI);
                                                }

                                                @Override
                                                public void onUnexpectedError(Exception e) {
                                                    Log.w(LOG_TAG, "##onActivityResultRoomAvatarUpdate(): update failure - UnexpectedError");
                                                    hideLoadingView(DO_NOT_UPDATE_UI);
                                                }
                                            });
                                        } else {
                                            hideLoadingView(DO_NOT_UPDATE_UI);
                                        }
                                    }
                                });
                            }
                        });
                    }
                }
            }
        }
    }

    /**
     * Display the loading view in the parent activity layout.
     * This view is disabled/enabled to achieve a waiting screen.
     */
    private void displayLoadingView() {

        Activity parentActivity = getActivity();
        if(null != parentActivity) {
            parentActivity.runOnUiThread(new Runnable() {
                public void run() {

                    // disable the fragment container view to disable preferences access
                    enablePreferenceWidgets(false);

                    // disable preference screen during server updates
                    if(null != mParentFragmentContainerView)
                        mParentFragmentContainerView.setEnabled(false);

                    // display the loading progress bar screen
                    if (null != mParentLoadingView) {
                        mParentLoadingView.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    }

    /**
     * Hide the loading progress bar screen and
     * update the UI if required.
     */
    private void hideLoadingView(boolean aIsUiRefreshRequired) {
        getActivity().runOnUiThread(new Runnable(){
            public void run() {

                // enable preference screen after server updates finished
                if(null != mParentFragmentContainerView)
                    mParentFragmentContainerView.setEnabled(true);

                // enable preference widgets
                enablePreferenceWidgets(true);

                if (null != mParentLoadingView) {
                    mParentLoadingView.setVisibility(View.GONE);
                }
            }
        });

        if(aIsUiRefreshRequired){
            updateUiOnUiThread();
        }
    }

    private void enablePreferenceWidgets(boolean aIsEnabled){
        if(null != mRoomPhotoAvatar) {
            mRoomPhotoAvatar.setEnabled(aIsEnabled);
            mRoomPhotoAvatar.setShouldDisableView(aIsEnabled);
        }

        if(null != mRoomNameEditTxt) {
            mRoomNameEditTxt.setEnabled(aIsEnabled);
            mRoomNameEditTxt.setShouldDisableView(aIsEnabled);
        }

        if(null != mRoomTopicEditTxt) {
            mRoomTopicEditTxt.setEnabled(aIsEnabled);
            mRoomTopicEditTxt.setShouldDisableView(aIsEnabled);
        }

        if(null != mRoomPrivacySwitch) {
            mRoomPrivacySwitch.setEnabled(aIsEnabled);
            mRoomPrivacySwitch.setShouldDisableView(aIsEnabled);
        }

        if(null != mRoomMuteNotificationsSwitch) {
            mRoomMuteNotificationsSwitch.setEnabled(aIsEnabled);
            mRoomMuteNotificationsSwitch.setShouldDisableView(aIsEnabled);
        }
    }
}
