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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
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
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.preference.RoomAvatarPreference;
import im.vector.util.ResourceUtils;
import im.vector.util.VectorUtils;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class VectorRoomSettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    // internal constants values
    private static final String LOG_TAG = "VectorRoomSetFragment";
    private static final boolean UPDATE_UI = true;
    private static final boolean DO_NOT_UPDATE_UI = false;
    private static final int REQ_CODE_UPDATE_ROOM_AVATAR = 0x10;

    // Room access rules values
    public static final String ACCESS_RULES_ONLY_PEOPLE_INVITED = "1";
    public static final String ACCESS_RULES_ANYONE_WITH_LINK_APART_GUEST = "2";
    public static final String ACCESS_RULES_ANYONE_WITH_LINK_INCLUDING_GUEST = "3";

    // fragment extra args keys
    private static final String EXTRA_MATRIX_ID = "KEY_EXTRA_MATRIX_ID";
    private static final String EXTRA_ROOM_ID = "KEY_EXTRA_ROOM_ID";

    // preference keys: public API to access preference
    public static final String PREF_KEY_ROOM_PHOTO_AVATAR = "roomPhotoAvatar";
    public static final String PREF_KEY_ROOM_NAME = "roomNameEditText";
    public static final String PREF_KEY_ROOM_TOPIC = "roomTopicEditText";
    public static final String PREF_KEY_ROOM_DIRECTORY_VISIBILITY_SWITCH = "roomNameListedInDirectorySwitch";
    public static final String PREF_KEY_ROOM_TAG_LIST = "roomTagList";
    public static final String PREF_KEY_ROOM_ACCESS_RULES_LIST = "roomAccessRulesList";
    public static final String PREF_KEY_ROOM_HISTORY_READABILITY_LIST = "roomReadHistoryRulesList";
    public static final String PREF_KEY_ROOM_MUTE_NOTIFICATIONS_SWITCH = "muteNotificationsSwitch";

    private static final String UNKNOWN_VALUE = "UNKNOWN_VALUE";

    // business code
    private MXSession mSession;
    private Room mRoom;
    private BingRulesManager mBingRulesManager;
    private boolean mIsUiUpdateSkipped;

    // UI elements
    private RoomAvatarPreference mRoomPhotoAvatar;
    private EditTextPreference mRoomNameEditTxt;
    private EditTextPreference mRoomTopicEditTxt;
    private SwitchPreference mRoomDirectoryVisibilitySwitch;
    private SwitchPreference mRoomMuteNotificationsSwitch;
    private ListPreference mRoomTagListPreference;
    private ListPreference mRoomAccessRulesListPreference;
    private ListPreference mRoomHistoryReadabilityRulesListPreference;
    private View mParentLoadingView;
    private View mParentFragmentContainerView;

    // disable some updates if there is
    private final IMXNetworkEventListener mNetworkListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            updateUi();
        }
    };

    // update field listener
    private final ApiCallback<Void> mUpdateCallback = new ApiCallback<Void>() {
        /**
         * refresh the fragment.
         * @param mode true to force refresh
         */
        private void onDone(final String message, final boolean mode) {
            if (null != getActivity()) {
                if (!TextUtils.isEmpty(message)) {
                    Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                }

                // ensure that the response has been sent in the UI thread
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideLoadingView(mode);
                    }
                });
            }
        }

        @Override
        public void onSuccess(Void info) {
            Log.d(LOG_TAG, "##update succeed");
            onDone(null, UPDATE_UI);
        }

        @Override
        public void onNetworkError(Exception e) {
            Log.w(LOG_TAG, "##NetworkError " + e.getLocalizedMessage());
            onDone(e.getLocalizedMessage(), DO_NOT_UPDATE_UI);
        }

        @Override
        public void onMatrixError(MatrixError e) {
            Log.w(LOG_TAG, "##MatrixError " + e.getLocalizedMessage());
            onDone(e.getLocalizedMessage(), DO_NOT_UPDATE_UI);
        }

        @Override
        public void onUnexpectedError(Exception e) {
            Log.w(LOG_TAG, "##UnexpectedError " + e.getLocalizedMessage());
            onDone(e.getLocalizedMessage(), DO_NOT_UPDATE_UI);
        }
    };

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
                            || Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(event.type)
                            || Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES.equals(event.type)    // room access rules
                            || Event.EVENT_TYPE_STATE_ROOM_GUEST_ACCESS.equals(event.type)  // room access rules
                            )
                    {
                        Log.d(LOG_TAG, "## onLiveEvent() event = " + event.type);
                        updateUi();
                    }
                }
            });
        }

        @Override
        public void onRoomTagEvent(String roomId) {
            Log.d(LOG_TAG, "## onRoomTagEvent()");
            updateUi();
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
        Log.d(LOG_TAG,"## onCreate() IN");

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
        mRoomDirectoryVisibilitySwitch = (SwitchPreference)findPreference(PREF_KEY_ROOM_DIRECTORY_VISIBILITY_SWITCH);
        mRoomMuteNotificationsSwitch = (SwitchPreference)findPreference(PREF_KEY_ROOM_MUTE_NOTIFICATIONS_SWITCH);
        mRoomTagListPreference = (ListPreference)findPreference(PREF_KEY_ROOM_TAG_LIST);
        mRoomAccessRulesListPreference = (ListPreference)findPreference(PREF_KEY_ROOM_ACCESS_RULES_LIST);
        mRoomHistoryReadabilityRulesListPreference = (ListPreference)findPreference(PREF_KEY_ROOM_HISTORY_READABILITY_LIST);

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

        // listen to preference changes
        enableSharedPreferenceListener(true);

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
            Matrix.getInstance(getActivity()).removeNetworkEventListener(mNetworkListener);
            mRoom.removeEventListener(mEventListener);
        }

        // remove preference changes listener
        enableSharedPreferenceListener(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (null != mRoom) {
            Matrix.getInstance(getActivity()).addNetworkEventListener(mNetworkListener);
            mRoom.addEventListener(mEventListener);
            updateUi();

            updateRoomDirectoryVisibilityAsync();
        }
    }


    /**
     * Enable the preference listener according to the aIsListenerEnabled value.
     * @param aIsListenerEnabled true to enable the listener, false otherwise
     */
    private void enableSharedPreferenceListener(boolean aIsListenerEnabled) {
        Log.d(LOG_TAG, "## enableSharedPreferenceListener(): aIsListenerEnabled=" + aIsListenerEnabled);

        mIsUiUpdateSkipped = !aIsListenerEnabled;

        try {
            //SharedPreferences prefMgr = getActivity().getSharedPreferences("VectorSettingsFile", Context.MODE_PRIVATE);
            SharedPreferences prefMgr = getDefaultSharedPreferences(getActivity());

            if (aIsListenerEnabled) {
                prefMgr.registerOnSharedPreferenceChangeListener(this);
            } else {
                prefMgr.unregisterOnSharedPreferenceChangeListener(this);
            }
        } catch (Exception ex){
            Log.e(LOG_TAG, "## enableSharedPreferenceListener(): Exception Msg="+ex.getMessage());
        }
    }

    /**
     * Update the preferences according to the power levels and its values.
     * To prevent the preference change listener to be triggered, the listener
     * is removed when the preferences are updated.
     */
    private void updateUi(){
        // configure the preferences that are allowed to be modified by the user
        updatePreferenceAccessFromPowerLevel();

        // need to run on the UI thread to be taken into account
        // when updatePreferenceUiValues() will be performed
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // disable listener during preferences update, otherwise it will
                // be seen as a user action..
                enableSharedPreferenceListener(false);
            }
        });

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // set settings UI values
                updatePreferenceUiValues();

                // re enable preferences listener..
                enableSharedPreferenceListener(true);
            }
        });
    }

    /**
     * delayed refresh the preferences items.
     */
    private void updateUiOnUiThread() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUi();
            }
        });
    }

    /**
     * Retrieve the room visibility directory value and update the corresponding preference.
     * This is an asynchronous request: the call-back response will be processed on the UI thread.
     * For now, the room visibility directory value is not provided in the sync API, a specific request
     * must performed.
     */
    private void updateRoomDirectoryVisibilityAsync() {
        if((null == mRoom) || (null == mRoomDirectoryVisibilitySwitch)) {
            Log.w(LOG_TAG,"## updateRoomDirectoryVisibilityUi(): not processed due to invalid parameters");
        } else {
            displayLoadingView();

            // server request: is the room listed in the room directory?
            mRoom.getDirectoryVisibility(mRoom.getRoomId(), new ApiCallback<String>() {

                private void handleResponseOnUiThread(final String aVisibilityValue){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // only stop loading screen and do not update UI since the
                            // update is done here below..
                            hideLoadingView(DO_NOT_UPDATE_UI);

                            // set checked status
                            // Note: the preference listener is disabled when the switch is updated, otherwise it will be seen
                            // as a user action on the preference
                            boolean isChecked = RoomState.DIRECTORY_VISIBILITY_PUBLIC.equals(aVisibilityValue);
                            enableSharedPreferenceListener(false);
                            mRoomDirectoryVisibilitySwitch.setChecked(isChecked);
                            enableSharedPreferenceListener(true);
                        }
                    });
                }

                @Override
                public void onSuccess(String visibility) {
                    handleResponseOnUiThread(visibility);
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.w(LOG_TAG, "## getDirectoryVisibility(): onNetworkError Msg="+e.getLocalizedMessage());
                    handleResponseOnUiThread(null);
                }

                @Override
                public void onMatrixError(MatrixError matrixError) {
                    Log.w(LOG_TAG, "## getDirectoryVisibility(): onMatrixError Msg="+matrixError.getLocalizedMessage());
                    handleResponseOnUiThread(null);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.w(LOG_TAG, "## getDirectoryVisibility(): onUnexpectedError Msg="+e.getLocalizedMessage());
                    handleResponseOnUiThread(null);
                }
            });
        }
    }

    /**
     * Enable / disable preferences according to the power levels.
     */
    private void updatePreferenceAccessFromPowerLevel(){
        boolean canUpdateAvatar = false;
        boolean canUpdateName = false;
        boolean canUpdateTopic = false;
        boolean isAdmin = false;
        boolean isConnected = Matrix.getInstance(getActivity()).isConnected();

        // cannot refresh if there is no valid session / room
        if ((null != mRoom) && (null != mSession)) {
            PowerLevels powerLevels =  mRoom.getLiveState().getPowerLevels();

            if (null != powerLevels) {
                int powerLevel = powerLevels.getUserPowerLevel(mSession.getMyUserId());
                canUpdateAvatar = powerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_AVATAR);
                canUpdateName = powerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_NAME);
                canUpdateTopic = powerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_TOPIC);
                isAdmin = (powerLevel >= CommonActivityUtils.UTILS_POWER_LEVEL_ADMIN);
            }
        }
        else {
            Log.w(LOG_TAG, "## updatePreferenceAccessFromPowerLevel(): session or room may be missing");
        }

        if(null != mRoomPhotoAvatar)
            mRoomPhotoAvatar.setEnabled(canUpdateAvatar && isConnected);


        if(null != mRoomNameEditTxt)
            mRoomNameEditTxt.setEnabled(canUpdateName && isConnected);

        if(null != mRoomTopicEditTxt)
            mRoomTopicEditTxt.setEnabled(canUpdateTopic && isConnected);

        // room present in the directory list: admin only
        if(null != mRoomDirectoryVisibilitySwitch)
            mRoomDirectoryVisibilitySwitch.setEnabled(isAdmin && isConnected);

        // room notification mute setting: no power condition
        if(null != mRoomMuteNotificationsSwitch)
            mRoomMuteNotificationsSwitch.setEnabled(isConnected);

        // room tagging: no power condition
        if(null != mRoomTagListPreference)
            mRoomTagListPreference.setEnabled(isConnected);

        // room access rules: admin only
        if(null != mRoomAccessRulesListPreference)
            mRoomAccessRulesListPreference.setEnabled(isAdmin && isConnected);

        // room read history: admin only
        if(null != mRoomHistoryReadabilityRulesListPreference)
            mRoomHistoryReadabilityRulesListPreference.setEnabled(isAdmin && isConnected);
    }


    /**
     * Update the UI preference from the values taken from
     * the SDK layer.
     */
    private void updatePreferenceUiValues() {
        String value="";
        String summary="";
        Resources resources;

        if ((null == mSession) || (null == mRoom)){
            Log.w(LOG_TAG, "## updatePreferenceUiValues(): session or room may be missing");
            return;
        }

        if(null != mRoomPhotoAvatar){
            mRoomPhotoAvatar.refreshAvatar();
        }

        // update the room name preference
        if(null != mRoomNameEditTxt) {
            value = mRoom.getLiveState().name;
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

        // update room directory visibility
//        if(null != mRoomDirectoryVisibilitySwitch) {
//            boolean isRoomPublic = TextUtils.equals(mRoom.getVisibility()/*getLiveState().visibility ou .isPublic()*/, RoomState.DIRECTORY_VISIBILITY_PUBLIC);
//            if(isRoomPublic !isRoomPublic= mRoomDirectoryVisibilitySwitch.isChecked())
//                mRoomDirectoryVisibilitySwitch.setChecked(isRoomPublic);
//        }

        // check if fragment is added to its Activity before calling getResources().
        // getResources() may throw an exception ".. not attached to Activity"
        if (!isAdded()){
            Log.e(LOG_TAG,"## updatePreferenceUiValues(): fragment not added to Activity - isAdded()=false");
            return;
        } else {
            // in some weird cases, even if isAdded() = true, sometimes getResources() may fail,
            // so we need to catch the exception
            try {
                resources = getResources();
            } catch (Exception ex) {
                Log.e(LOG_TAG,"## updatePreferenceUiValues(): Exception in getResources() - Msg="+ex.getLocalizedMessage());
                return;
            }
        }

        // room guest access rules
        if((null != mRoomAccessRulesListPreference)&& (null != resources)) {
            String joinRule = mRoom.getLiveState().join_rule;
            String guestAccessRule = mRoom.getLiveState().getGuestAccess();

            if(RoomState.JOIN_RULE_INVITE.equals(joinRule)/* && RoomState.GUEST_ACCESS_CAN_JOIN.equals(guestAccessRule)*/) {
                // "Only people who have been invited" requires: {join_rule: "invite"} and {guest_access: "can_join"}
                value = ACCESS_RULES_ONLY_PEOPLE_INVITED;
                summary = resources.getString(R.string.room_settings_room_access_entry_only_invited);
            } else if(RoomState.JOIN_RULE_PUBLIC.equals(joinRule) && RoomState.GUEST_ACCESS_FORBIDDEN.equals(guestAccessRule)) {
                // "Anyone who knows the room's link, apart from guests" requires: {join_rule: "public"} and {guest_access: "forbidden"}
                value = ACCESS_RULES_ANYONE_WITH_LINK_APART_GUEST;
                summary = resources.getString(R.string.room_settings_room_access_entry_anyone_with_link_apart_guest);
            } else if(RoomState.JOIN_RULE_PUBLIC.equals(joinRule) && RoomState.GUEST_ACCESS_CAN_JOIN.equals(guestAccessRule)) {
                // "Anyone who knows the room's link, including guests" requires: {join_rule: "public"} and {guest_access: "can_join"}
                value = ACCESS_RULES_ANYONE_WITH_LINK_INCLUDING_GUEST;
                summary = resources.getString(R.string.room_settings_room_access_entry_anyone_with_link_including_guest);
            } else {
                // unknown combination value
                value = null;
                summary = null;
                Log.w(LOG_TAG, "## updatePreferenceUiValues(): unknown room access configuration joinRule=" + joinRule + " and guestAccessRule="+guestAccessRule);
            }

            if(null != value){
                mRoomAccessRulesListPreference.setValue(value);
                mRoomAccessRulesListPreference.setSummary(summary);
            } else {
                mRoomHistoryReadabilityRulesListPreference.setValue(UNKNOWN_VALUE);
                mRoomHistoryReadabilityRulesListPreference.setSummary("");
            }
        }

        // update the room tag preference
        if(null != mRoomTagListPreference) {

            if(null != mRoom.getAccountData() && (null != resources)) {
                //Set<String> custoTagList = mRoom.getAccountData().getKeys();

                if (null != mRoom.getAccountData().roomTag(RoomTag.ROOM_TAG_FAVOURITE)) {
                    value = resources.getString(R.string.room_settings_tag_pref_entry_value_favourite);
                    summary = resources.getString(R.string.room_settings_tag_pref_entry_favourite);
                } else if (null != mRoom.getAccountData().roomTag(RoomTag.ROOM_TAG_LOW_PRIORITY)) {
                    value = resources.getString(R.string.room_settings_tag_pref_entry_value_low_priority);
                    summary = resources.getString(R.string.room_settings_tag_pref_entry_low_priority);
                /* For further use in case of multiple tags support
                } else if(!mRoom.getAccountData().getKeys().isEmpty()) {
                    for(String tag : custoTagList){
                        summary += (!summary.isEmpty()?" ":"") + tag;
                    }*/
                } else {
                    // no tag associated to the room
                    value = resources.getString(R.string.room_settings_tag_pref_entry_value_none);
                    summary = Html.fromHtml("<i>"+getResources().getString(R.string.room_settings_tag_pref_no_tag)+ "</i>").toString();
                }

                mRoomTagListPreference.setValue(value);
                mRoomTagListPreference.setSummary(summary);
            }
        }

        // room history readability
        if (null != mRoomHistoryReadabilityRulesListPreference) {
            value = mRoom.getLiveState().getHistoryVisibility();
            summary = null;

            if((null != value) && (null != resources)) {
                // get summary value
                if (value.equals(resources.getString(R.string.room_settings_read_history_entry_value_anyone))) {
                    summary = resources.getString(R.string.room_settings_read_history_entry_anyone);
                } else if (value.equals(resources.getString(R.string.room_settings_read_history_entry_value_members_only_option_time_shared))) {
                    summary = resources.getString(R.string.room_settings_read_history_entry_members_only_option_time_shared);
                } else if (value.equals(resources.getString(R.string.room_settings_read_history_entry_value_members_only_invited))) {
                    summary = resources.getString(R.string.room_settings_read_history_entry_members_only_invited);
                } else if (value.equals(resources.getString(R.string.room_settings_read_history_entry_value_members_only_joined))) {
                    summary = resources.getString(R.string.room_settings_read_history_entry_members_only_joined);
                } else {
                    // unknown value
                    Log.w(LOG_TAG, "## updatePreferenceUiValues(): unknown room read history value=" + value);
                    summary = null;
                }
            }

            if(null != summary) {
                mRoomHistoryReadabilityRulesListPreference.setValue(value);
                mRoomHistoryReadabilityRulesListPreference.setSummary(summary);
            } else {
                mRoomHistoryReadabilityRulesListPreference.setValue(UNKNOWN_VALUE);
                mRoomHistoryReadabilityRulesListPreference.setSummary("");
            }
        }
    }

    // OnSharedPreferenceChangeListener implementation
    /**
     * Main entry point handler for any preference changes. For each setting a dedicated handler is
     * called to process the setting.
     *
     * @param aSharedPreferences preference instance
     * @param aKey preference key as it is defined in the XML
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences aSharedPreferences, String aKey) {

        if(mIsUiUpdateSkipped){
            Log.d(LOG_TAG,"## onSharedPreferenceChanged(): Skipped");
            return;
        }

        if (aKey.equals(PREF_KEY_ROOM_PHOTO_AVATAR)) {
            // unused flow: onSharedPreferenceChanged not triggered for room avatar photo
            onRoomAvatarPreferenceChanged();
        }
        else if(aKey.equals(PREF_KEY_ROOM_NAME)) {
            onRoomNamePreferenceChanged();
        }
        else if(aKey.equals(PREF_KEY_ROOM_TOPIC)) {
            onRoomTopicPreferenceChanged();
        } else if(aKey.equals(PREF_KEY_ROOM_MUTE_NOTIFICATIONS_SWITCH)) {
            onRoomMuteNotificationsPreferenceChanged();
        }
        else if(aKey.equals(PREF_KEY_ROOM_DIRECTORY_VISIBILITY_SWITCH)) {
            onRoomDirectoryVisibilityPreferenceChanged(); // TBT
        }
        else if(aKey.equals(PREF_KEY_ROOM_TAG_LIST)) {
            onRoomTagPreferenceChanged(); // TBT
        }
        else if(aKey.equals(PREF_KEY_ROOM_ACCESS_RULES_LIST)) {
            onRoomAccessPreferenceChanged();
        }
        else if(aKey.equals(PREF_KEY_ROOM_HISTORY_READABILITY_LIST)) {
            onRoomHistoryReadabilityPreferenceChanged(); // TBT
        }
        else {
            Log.w(LOG_TAG,"## onSharedPreferenceChanged(): unknown aKey = "+ aKey);
        }
    }

    private void onRoomHistoryReadabilityPreferenceChanged() {
        // sanity check
        if ((null == mRoom) || (null == mRoomHistoryReadabilityRulesListPreference)) {
            Log.w(LOG_TAG,"## onRoomHistoryReadabilityPreferenceChanged(): not processed due to invalid parameters");
            return;
        }

        // get new and previous values
        String previousValue = mRoom.getLiveState().history_visibility;
        String newValue = mRoomHistoryReadabilityRulesListPreference.getValue();

        if(!TextUtils.equals(newValue, previousValue)) {
            String historyVisibility;

            if(newValue.equals(getResources().getString(R.string.room_settings_read_history_entry_value_anyone))) {
                historyVisibility = RoomState.HISTORY_VISIBILITY_WORLD_READABLE;
            } else if(newValue.equals(getResources().getString(R.string.room_settings_read_history_entry_value_members_only_option_time_shared))) {
                historyVisibility = RoomState.HISTORY_VISIBILITY_SHARED;
            } else if(newValue.equals(getResources().getString(R.string.room_settings_read_history_entry_value_members_only_invited))) {
                historyVisibility = RoomState.HISTORY_VISIBILITY_INVITED;
            } else if(newValue.equals(getResources().getString(R.string.room_settings_read_history_entry_value_members_only_joined))) {
                historyVisibility = RoomState.HISTORY_VISIBILITY_JOINED;
            } else {
                // unknown value
                Log.w(LOG_TAG,"## onRoomHistoryReadabilityPreferenceChanged(): unknown value:"+newValue);
                historyVisibility = null;
            }

            if(null != historyVisibility) {
                displayLoadingView();
                mRoom.updateHistoryVisibility(historyVisibility, mUpdateCallback);
            }
        }
    }

    private void onRoomTagPreferenceChanged() {
        boolean isSupportedTag = true;

        // sanity check
        if((null == mRoom) || (null == mRoomTagListPreference)) {
            Log.w(LOG_TAG,"## onRoomTagPreferenceChanged(): not processed due to invalid parameters");
        } else {
            String newTag = mRoomTagListPreference.getValue();
            String currentTag = null;
            Double tagOrder = 0.0;

            // retrieve the tag from the room info
            RoomAccountData accountData = mRoom.getAccountData();
            if ((null != accountData) && accountData.hasTags()) {
                currentTag = accountData.getKeys().iterator().next();
            }

            if(!newTag.equals(currentTag)) {
                if(newTag.equals(getResources().getString(R.string.room_settings_tag_pref_entry_value_favourite))) {
                    newTag = RoomTag.ROOM_TAG_FAVOURITE;
                } else if(newTag.equals(getResources().getString(R.string.room_settings_tag_pref_entry_value_low_priority))) {
                    newTag = RoomTag.ROOM_TAG_LOW_PRIORITY;
                } else if(newTag.equals(getResources().getString(R.string.room_settings_tag_pref_entry_value_none))) {
                    newTag = null;
                } else {
                    // unknown tag.. very unlikely
                    isSupportedTag = false;
                    Log.w(LOG_TAG, "## onRoomTagPreferenceChanged() not supported tag = " + newTag);
                }
            }

            if(isSupportedTag) {
                displayLoadingView();
                mRoom.replaceTag(currentTag, newTag, tagOrder, mUpdateCallback);
            }
        }
    }

    private void onRoomAccessPreferenceChanged() {

        if((null == mRoom) || (null == mRoomAccessRulesListPreference)) {
            Log.w(LOG_TAG,"## onRoomAccessPreferenceChanged(): not processed due to invalid parameters");
        } else {
            String joinRuleToApply = null;
            String guestAccessRuleToApply = null;

            // get new and previous values
            String previousJoinRule = mRoom.getLiveState().join_rule;
            String previousGuestAccessRule = mRoom.getLiveState().getGuestAccess();
            String newValue = mRoomAccessRulesListPreference.getValue();

            if(ACCESS_RULES_ONLY_PEOPLE_INVITED.equals(newValue)) {
                // requires: {join_rule: "invite"} and {guest_access: "can_join"}
                joinRuleToApply = !RoomState.JOIN_RULE_INVITE.equals(previousJoinRule)?RoomState.JOIN_RULE_INVITE:null;
                guestAccessRuleToApply = !RoomState.GUEST_ACCESS_CAN_JOIN.equals(previousGuestAccessRule)?RoomState.GUEST_ACCESS_CAN_JOIN:null;
            } else if(ACCESS_RULES_ANYONE_WITH_LINK_APART_GUEST.equals(newValue)) {
                // requires: {join_rule: "public"} and {guest_access: "forbidden"}
                joinRuleToApply = !RoomState.JOIN_RULE_PUBLIC.equals(previousJoinRule)?RoomState.JOIN_RULE_PUBLIC:null;
                guestAccessRuleToApply = !RoomState.GUEST_ACCESS_FORBIDDEN.equals(previousGuestAccessRule)?RoomState.GUEST_ACCESS_FORBIDDEN:null;
            } else if(ACCESS_RULES_ANYONE_WITH_LINK_INCLUDING_GUEST.equals(newValue)) {
                // requires: {join_rule: "public"} and {guest_access: "can_join"}
                joinRuleToApply = !RoomState.JOIN_RULE_PUBLIC.equals(previousJoinRule)?RoomState.JOIN_RULE_PUBLIC:null;
                guestAccessRuleToApply = !RoomState.GUEST_ACCESS_CAN_JOIN.equals(previousGuestAccessRule)?RoomState.GUEST_ACCESS_CAN_JOIN:null;
            } else {
                // unknown value
                Log.d(LOG_TAG,"## onRoomAccessPreferenceChanged(): unknown selected value = "+newValue);
            }

            if(null != joinRuleToApply) {
                displayLoadingView();
                mRoom.updateJoinRules(joinRuleToApply, mUpdateCallback);
            }

            if(null != guestAccessRuleToApply) {
                displayLoadingView();
                mRoom.updateGuestAccess(guestAccessRuleToApply, mUpdateCallback);
            }
        }
    }

    private void onRoomDirectoryVisibilityPreferenceChanged() {
        String visibility;

        if((null == mRoom) || (null == mRoomDirectoryVisibilitySwitch)) {
            Log.w(LOG_TAG,"## onRoomDirectoryVisibilityPreferenceChanged(): not processed due to invalid parameters");
            visibility = null;
        } else if(mRoomDirectoryVisibilitySwitch.isChecked()) {
            visibility = RoomState.DIRECTORY_VISIBILITY_PUBLIC;
        } else {
            visibility = RoomState.DIRECTORY_VISIBILITY_PRIVATE;
        }

        if(null != visibility) {
            Log.d(LOG_TAG, "## onRoomDirectoryVisibilityPreferenceChanged(): directory visibility set to "+visibility);
            displayLoadingView();
            mRoom.updateDirectoryVisibility(visibility, mUpdateCallback);
        }
    }

    /**
     * Action when enabling / disabling the rooms notifications.
     */
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

    /**
     * Action when updating the room name.
     */
    private void onRoomNamePreferenceChanged(){
        // sanity check
        if((null == mRoom) || (null == mSession) || (null == mRoomNameEditTxt)){
            return;
        }

        // get new and previous values
        String previousName = mRoom.getLiveState().name;
        String newName = mRoomNameEditTxt.getText();
        // update only, if values are different
        if (!TextUtils.equals(previousName, newName)) {
            displayLoadingView();

            Log.d(LOG_TAG, "##onRoomNamePreferenceChanged to " + newName);
            mRoom.updateName(newName, mUpdateCallback);
        }
    }

    /**
     * Action when updating the room topic.
     */
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
            Log.d(LOG_TAG, "## update topic to " + newTopic);
            mRoom.updateTopic(newTopic, mUpdateCallback);
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
                intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true);
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

    /**
     * Update the avatar from the data provided the medias picker.
     * @param aResultCode the result code.
     * @param aData the provided data.
     */
    private void onActivityResultRoomAvatarUpdate(int aResultCode, final Intent aData) {
        // sanity check
        if(null == mSession){
            return;
        }

        if (aResultCode == Activity.RESULT_OK) {
            Uri thumbnailUri = VectorUtils.getThumbnailUriFromIntent(getActivity(), aData, mSession.getMediasCache());

            if (null != thumbnailUri) {
                displayLoadingView();

                // save the bitmap URL on the server
                ResourceUtils.Resource resource = ResourceUtils.openResource(getActivity(), thumbnailUri, null);
                if(null != resource) {
                    mSession.getContentManager().uploadContent(resource.mContentStream, null, resource.mMimeType, null, new ContentManager.UploadCallback() {
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
                                        Log.d(LOG_TAG, "The avatar has been uploaded, update the room avatar");
                                        mRoom.updateAvatarUrl(uploadResponse.contentUri, mUpdateCallback);
                                    } else {
                                        Log.e(LOG_TAG, "Fail to upload the avatar");
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
                    //enablePreferenceWidgets(false);

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
                //enablePreferenceWidgets(true);

                if (null != mParentLoadingView) {
                    mParentLoadingView.setVisibility(View.GONE);
                }
            }
        });

        if(aIsUiRefreshRequired){
            updateUiOnUiThread();
        }
    }

    /**
     * Enable / disable the widgets.
     * @param aIsEnabled true to enable them all.
     */
    private void enablePreferenceWidgets(boolean aIsEnabled) {
        aIsEnabled &= Matrix.getInstance(getActivity()).isConnected();

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

        if(null != mRoomDirectoryVisibilitySwitch) {
            mRoomDirectoryVisibilitySwitch.setEnabled(aIsEnabled);
            mRoomDirectoryVisibilitySwitch.setShouldDisableView(aIsEnabled);
        }

        if(null != mRoomMuteNotificationsSwitch) {
            mRoomMuteNotificationsSwitch.setEnabled(aIsEnabled);
            mRoomMuteNotificationsSwitch.setShouldDisableView(aIsEnabled);
        }

        if(null != mRoomTagListPreference) {
            mRoomTagListPreference.setEnabled(aIsEnabled);
            mRoomTagListPreference.setShouldDisableView(aIsEnabled);
        }

        if(null != mRoomAccessRulesListPreference) {
            mRoomAccessRulesListPreference.setEnabled(aIsEnabled);
            mRoomAccessRulesListPreference.setShouldDisableView(aIsEnabled);
        }

        if(null != mRoomHistoryReadabilityRulesListPreference) {
            mRoomHistoryReadabilityRulesListPreference.setEnabled(aIsEnabled);
            mRoomHistoryReadabilityRulesListPreference.setShouldDisableView(aIsEnabled);
        }
    }
}
