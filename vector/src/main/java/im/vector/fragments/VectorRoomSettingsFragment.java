/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCryptoAlgorithms;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.util.ResourceUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.preference.AddressPreference;
import im.vector.preference.RoomAvatarPreference;
import im.vector.preference.VectorCustomActionEditTextPreference;
import im.vector.preference.VectorListPreference;
import im.vector.preference.VectorSwitchPreference;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorUtils;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class VectorRoomSettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    // internal constants values
    private static final String LOG_TAG = "VectorRoomSetFragment";
    private static final boolean UPDATE_UI = true;
    private static final boolean DO_NOT_UPDATE_UI = false;
    private static final int REQ_CODE_UPDATE_ROOM_AVATAR = 0x10;

    // Room access rules values
    private static final String ACCESS_RULES_ONLY_PEOPLE_INVITED = "1";
    private static final String ACCESS_RULES_ANYONE_WITH_LINK_APART_GUEST = "2";
    private static final String ACCESS_RULES_ANYONE_WITH_LINK_INCLUDING_GUEST = "3";

    // fragment extra args keys
    private static final String EXTRA_MATRIX_ID = "KEY_EXTRA_MATRIX_ID";
    private static final String EXTRA_ROOM_ID = "KEY_EXTRA_ROOM_ID";

    // preference keys: public API to access preference
    private static final String PREF_KEY_ROOM_PHOTO_AVATAR = "roomPhotoAvatar";
    private static final String PREF_KEY_ROOM_NAME = "roomNameEditText";
    private static final String PREF_KEY_ROOM_TOPIC = "roomTopicEditText";
    private static final String PREF_KEY_ROOM_DIRECTORY_VISIBILITY_SWITCH = "roomNameListedInDirectorySwitch";
    private static final String PREF_KEY_ROOM_TAG_LIST = "roomTagList";
    private static final String PREF_KEY_ROOM_ACCESS_RULES_LIST = "roomAccessRulesList";
    private static final String PREF_KEY_ROOM_HISTORY_READABILITY_LIST = "roomReadHistoryRulesList";
    private static final String PREF_KEY_ROOM_MUTE_NOTIFICATIONS_SWITCH = "muteNotificationsSwitch";
    private static final String PREF_KEY_ROOM_LEAVE = "roomLeave";
    private static final String PREF_KEY_ROOM_INTERNAL_ID = "roomInternalId";
    private static final String PREF_KEY_ADDRESSES = "addresses";
    private static final String PREF_KEY_ADVANCED = "advanced";

    private static final String PREF_KEY_BANNED = "banned";
    private static final String PREF_KEY_BANNED_DIVIDER = "banned_divider";
    private static final String PREF_KEY_ENCRYPTION = "encryptionKey";

    private static final String ADDRESSES_PREFERENCE_KEY_BASE = "ADDRESSES_PREFERENCE_KEY_BASE";
    private static final String NO_LOCAL_ADDRESS_PREFERENCE_KEY = "NO_LOCAL_ADDRESS_PREFERENCE_KEY";
    private static final String ADD_ADDRESSES_PREFERENCE_KEY = "ADD_ADDRESSES_PREFERENCE_KEY";

    private static final String BANNED_PREFERENCE_KEY_BASE = "BANNED_PREFERENCE_KEY_BASE";

    private static final String UNKNOWN_VALUE = "UNKNOWN_VALUE";

    // business code
    private MXSession mSession;
    private Room mRoom;
    private BingRulesManager mBingRulesManager;
    private boolean mIsUiUpdateSkipped;

    // addresses
    private PreferenceCategory mAddressesSettingsCategory;

    // other
    private PreferenceCategory mAdvandceSettingsCategory;

    // banned members
    private PreferenceCategory mBannedMembersSettingsCategory;
    private PreferenceCategory mBannedMembersSettingsCategoryDivider;

    // UI elements
    private RoomAvatarPreference mRoomPhotoAvatar;
    private EditTextPreference mRoomNameEditTxt;
    private EditTextPreference mRoomTopicEditTxt;
    private CheckBoxPreference mRoomDirectoryVisibilitySwitch;
    private CheckBoxPreference mRoomMuteNotificationsSwitch;
    private ListPreference mRoomTagListPreference;
    private VectorListPreference mRoomAccessRulesListPreference;
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
                    String eventType = event.getType();

                    // The various events that could possibly change the fragment items
                    if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(eventType)
                            || Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES.equals(eventType)    // room access rules
                            || Event.EVENT_TYPE_STATE_ROOM_GUEST_ACCESS.equals(eventType)  // room access rules
                            )
                    {
                        Log.d(LOG_TAG, "## onLiveEvent() event = " + eventType);
                        updateUi();
                    }

                    if (Event.EVENT_TYPE_MESSAGE_ENCRYPTION.equals(eventType)) {
                        refreshEndToEnd();
                    }

                    // aliases
                    if (Event.EVENT_TYPE_STATE_CANONICAL_ALIAS.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(eventType)
                            ) {
                        Log.d(LOG_TAG, "## onLiveEvent() refresh the addresses list");
                        refreshAddresses();
                    }

                    if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType)) {
                        Log.d(LOG_TAG, "## onLiveEvent() refresh the banned members list");
                        refreshBannedMembersList();
                    }
                }
            });
        }

        @Override
        public void onRoomFlush(String roomId) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateUi();
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
            if ((null != mSession) && mSession.isAlive()) {
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
        mRoomDirectoryVisibilitySwitch = (CheckBoxPreference)findPreference(PREF_KEY_ROOM_DIRECTORY_VISIBILITY_SWITCH);
        mRoomMuteNotificationsSwitch = (CheckBoxPreference)findPreference(PREF_KEY_ROOM_MUTE_NOTIFICATIONS_SWITCH);
        mRoomTagListPreference = (ListPreference)findPreference(PREF_KEY_ROOM_TAG_LIST);
        mRoomAccessRulesListPreference = (VectorListPreference)findPreference(PREF_KEY_ROOM_ACCESS_RULES_LIST);
        mRoomHistoryReadabilityRulesListPreference = (ListPreference)findPreference(PREF_KEY_ROOM_HISTORY_READABILITY_LIST);
        mAddressesSettingsCategory = (PreferenceCategory)getPreferenceManager().findPreference(PREF_KEY_ADDRESSES);
        mAdvandceSettingsCategory = (PreferenceCategory)getPreferenceManager().findPreference(PREF_KEY_ADVANCED);
        mBannedMembersSettingsCategory = (PreferenceCategory)getPreferenceManager().findPreference(PREF_KEY_BANNED);
        mBannedMembersSettingsCategoryDivider = (PreferenceCategory)getPreferenceManager().findPreference(PREF_KEY_BANNED_DIVIDER);

        mRoomAccessRulesListPreference.setOnPreferenceWarningIconClickListener(new VectorListPreference.OnPreferenceWarningIconClickListener() {
            @Override
            public void onWarningIconClick(Preference preference) {
                displayAccessRoomWarning();
            }
        });

        // display the room Id.
        EditTextPreference roomInternalIdPreference = (EditTextPreference)findPreference(PREF_KEY_ROOM_INTERNAL_ID);
        if (null != roomInternalIdPreference) {
            roomInternalIdPreference.setSummary(mRoom.getRoomId());

            roomInternalIdPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    VectorUtils.copyToClipboard(getActivity(), mRoom.getRoomId());
                  return false;
                }
            });
        }

        // leave room
        EditTextPreference leaveRoomPreference = (EditTextPreference)findPreference(PREF_KEY_ROOM_LEAVE);

        if (null != leaveRoomPreference) {
            leaveRoomPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // leave room
                    new AlertDialog.Builder(VectorApp.getCurrentActivity())
                            .setTitle(R.string.room_participants_leave_prompt_title)
                            .setMessage(getString(R.string.room_participants_leave_prompt_msg))
                            .setPositiveButton(R.string.leave, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    displayLoadingView();

                                    mRoom.leave(new ApiCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void info) {
                                            if (null != getActivity()) {
                                                getActivity().runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        getActivity().finish();
                                                    }
                                                });
                                            }
                                        }

                                        private void onError(final String errorMessage) {
                                            if (null != getActivity()) {
                                                getActivity().runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        hideLoadingView(true);
                                                        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            }
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
                            })
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .create()
                            .show();
                    return true;
                }
            });
        }

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        View listView = view.findViewById(android.R.id.list);

        if (null != listView) {
            listView.setPadding(0, 0, 0, 0);
        }

        // seems known issue that the preferences screen does not use the activity theme
        view.setBackgroundColor(ThemeUtils.getColor(getActivity(), R.attr.riot_primary_background_color));
        return view;
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

            refreshAddresses();
            refreshBannedMembersList();
            refreshEndToEnd();
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
     * Display the access room warning.
     */
    private void displayAccessRoomWarning () {
        Toast.makeText(getActivity(), R.string.room_settings_room_access_warning, Toast.LENGTH_SHORT).show();
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
        if(null != mRoomAccessRulesListPreference) {
            mRoomAccessRulesListPreference.setEnabled(isAdmin && isConnected);
            mRoomAccessRulesListPreference.setWarningIconVisible((0 == mRoom.getAliases().size()) && !TextUtils.equals(RoomState.JOIN_RULE_INVITE, mRoom.getLiveState().join_rule));
        }

        // room read history: admin only
        if(null != mRoomHistoryReadabilityRulesListPreference)
            mRoomHistoryReadabilityRulesListPreference.setEnabled(isAdmin && isConnected);
    }


    /**
     * Update the UI preference from the values taken from
     * the SDK layer.
     */
    private void updatePreferenceUiValues() {
        String value;
        String summary;
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
            boolean isChecked = mBingRulesManager.isRoomNotificationsDisabled(mRoom.getRoomId());
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
                //Set<String> customTagList = mRoom.getAccountData().getKeys();

                if (null != mRoom.getAccountData().roomTag(RoomTag.ROOM_TAG_FAVOURITE)) {
                    value = resources.getString(R.string.room_settings_tag_pref_entry_value_favourite);
                    summary = resources.getString(R.string.room_settings_tag_pref_entry_favourite);
                } else if (null != mRoom.getAccountData().roomTag(RoomTag.ROOM_TAG_LOW_PRIORITY)) {
                    value = resources.getString(R.string.room_settings_tag_pref_entry_value_low_priority);
                    summary = resources.getString(R.string.room_settings_tag_pref_entry_low_priority);
                /* For further use in case of multiple tags support
                } else if(!mRoom.getAccountData().getKeys().isEmpty()) {
                    for(String tag : customTagList){
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

        if (null == getActivity()) {
            Log.d(LOG_TAG,"## onSharedPreferenceChanged(): no attached to an activity");
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

    /**
     * The room history readability has been updated.
     */
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

                if (0 == mRoom.getAliases().size()) {
                    displayAccessRoomWarning();
                }
            } else if(ACCESS_RULES_ANYONE_WITH_LINK_INCLUDING_GUEST.equals(newValue)) {
                // requires: {join_rule: "public"} and {guest_access: "can_join"}
                joinRuleToApply = !RoomState.JOIN_RULE_PUBLIC.equals(previousJoinRule)?RoomState.JOIN_RULE_PUBLIC:null;
                guestAccessRuleToApply = !RoomState.GUEST_ACCESS_CAN_JOIN.equals(previousGuestAccessRule)?RoomState.GUEST_ACCESS_CAN_JOIN:null;

                if (0 == mRoom.getAliases().size()) {
                    displayAccessRoomWarning();
                }
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
        boolean previousValue = mBingRulesManager.isRoomNotificationsDisabled(mRoom.getRoomId());

        // update only, if values are different
        if(isNotificationsMuted != previousValue) {
            displayLoadingView();
            mBingRulesManager.muteRoomNotifications(mRoom.getRoomId(), isNotificationsMuted, new BingRulesManager.onBingRuleUpdateListener() {
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
                    mSession.getMediasCache().uploadContent(resource.mContentStream, null, resource.mMimeType, null, new MXMediaUploadListener() {

                        @Override
                        public void onUploadError(String uploadId, int serverResponseCode, String serverErrorMessage) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Log.e(LOG_TAG, "Fail to upload the avatar");
                                    hideLoadingView(DO_NOT_UPDATE_UI);
                                }
                            });
                        }

                        @Override
                        public void onUploadComplete(final String uploadId, final String contentUri) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(LOG_TAG, "The avatar has been uploaded, update the room avatar");
                                    mRoom.updateAvatarUrl(contentUri, mUpdateCallback);
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

    //================================================================================
    // Banned members management
    //================================================================================

    /**
     * Refresh the banned users list.
     */
    private void refreshBannedMembersList() {
        ArrayList<RoomMember> bannedMembers = new ArrayList<>();
        Collection<RoomMember> members = mRoom.getMembers();

        if (null != members) {
            for (RoomMember member : members) {
                if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_BAN)) {
                    bannedMembers.add(member);
                }
            }
        }

        Collections.sort(bannedMembers, new Comparator<RoomMember>() {
            @Override
            public int compare(RoomMember m1, RoomMember m2) {
                return m1.getUserId().toLowerCase().compareTo(m2.getUserId().toLowerCase());
            }
        });

        PreferenceScreen preferenceScreen = getPreferenceScreen();

        preferenceScreen.removePreference(mBannedMembersSettingsCategoryDivider);
        preferenceScreen.removePreference(mBannedMembersSettingsCategory);
        mBannedMembersSettingsCategory.removeAll();

        if (bannedMembers.size() > 0) {
            preferenceScreen.addPreference(mBannedMembersSettingsCategoryDivider);
            preferenceScreen.addPreference(mBannedMembersSettingsCategory);

            for (RoomMember member : bannedMembers) {
                VectorCustomActionEditTextPreference preference = new VectorCustomActionEditTextPreference(getActivity());

                final String userId = member.getUserId();

                preference.setTitle(userId);
                preference.setKey(BANNED_PREFERENCE_KEY_BASE + userId);

                preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent startRoomInfoIntent = new Intent(getActivity(), VectorMemberDetailsActivity.class);
                        startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, userId);
                        startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                        startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                        getActivity().startActivity(startRoomInfoIntent);
                        return false;
                    }
                });

                mBannedMembersSettingsCategory.addPreference(preference);
            }
        }
    }

    //================================================================================
    // Aliases management
    //================================================================================

    private final ApiCallback mAliasUpdatesCallback =  new ApiCallback<Void>() {
        @Override
        public void onSuccess(Void info) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hideLoadingView(false);
                    refreshAddresses();
                }
            });
        }

        /**
         * Error management.
         * @param errorMessage the error message
         */
        private void onError(final String errorMessage) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
                    hideLoadingView(false);
                    refreshAddresses();
                }
            });
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
    };

    /**
     * Manage the long click on an address.
     * @param roomAlias the room alias.
     * @param anchorView the popup menu anchor view.
     */
    @SuppressLint("NewApi")
    private void onAddressLongClick(final String roomAlias, final View anchorView) {
        Context context = getActivity();
        final PopupMenu popup = (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) ? new PopupMenu(context, anchorView, Gravity.END) : new PopupMenu(context, anchorView);

        popup.getMenuInflater().inflate(R.menu.vector_room_settings_addresses, popup.getMenu());

        // force to display the icons
        try {
            Field[] fields = popup.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popup);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "onMessageClick : force to display the icons failed " + e.getLocalizedMessage());
        }

        Menu menu = popup.getMenu();
        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(context, R.attr.icon_tint_on_light_action_bar_color));

        String canonicalAlias = mRoom.getLiveState().alias;
        boolean canUpdateAliases = canUpdateAliases();

        menu.findItem(R.id.ic_action_vector_delete_alias).setVisible(canUpdateAliases);
        menu.findItem(R.id.ic_action_vector_set_as_main_address).setVisible(canUpdateAliases && !TextUtils.equals(roomAlias, canonicalAlias));
        menu.findItem(R.id.ic_action_vector_unset_main_address).setVisible(canUpdateAliases && TextUtils.equals(roomAlias, canonicalAlias));

        // display the menu
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (item.getItemId() == R.id.ic_action_vector_unset_main_address) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setMessage(R.string.room_settings_addresses_disable_main_address_prompt_msg);
                    builder.setTitle(R.string.room_settings_addresses_disable_main_address_prompt_title);

                    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            displayLoadingView();
                            mRoom.updateCanonicalAlias(null, mAliasUpdatesCallback);
                        }
                    });

                    builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // nothing
                        }
                    });

                    AlertDialog dialog = builder.create();
                    dialog.show();
                } else  if (item.getItemId() == R.id.ic_action_vector_set_as_main_address) {
                    displayLoadingView();
                    mRoom.updateCanonicalAlias(roomAlias, mAliasUpdatesCallback);
                } else if (item.getItemId() == R.id.ic_action_vector_delete_alias) {
                    displayLoadingView();
                    mRoom.removeAlias(roomAlias, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            // when there is only one alias, it becomes the main alias.
                            if (mRoom.getAliases().size() == 1) {
                                mRoom.updateCanonicalAlias(mRoom.getAliases().get(0), mAliasUpdatesCallback);
                            } else {
                                mAliasUpdatesCallback.onSuccess(info);
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            mAliasUpdatesCallback.onNetworkError(e);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            mAliasUpdatesCallback.onMatrixError(e);
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            mAliasUpdatesCallback.onUnexpectedError(e);
                        }
                    });
                } else if (item.getItemId() == R.id.ic_action_vector_room_url) {
                    VectorUtils.copyToClipboard(getActivity(), VectorUtils.getPermalink(roomAlias, null));
                } else {
                    VectorUtils.copyToClipboard(getActivity(), roomAlias);
                }

                return true;
            }
        });

        popup.show();
    }

    /**
     * Tells if the current user can updates the room aliases.
     * @return true if the user is allowed.
     */
    private boolean canUpdateAliases() {
        boolean canUpdateAliases = false;

        PowerLevels powerLevels =  mRoom.getLiveState().getPowerLevels();

        if (null != powerLevels) {
            int powerLevel = powerLevels.getUserPowerLevel(mSession.getMyUserId());
            canUpdateAliases = powerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_ALIASES);
        }

        return canUpdateAliases;
    }

    /**
     * Refresh the addresses section
     */
    private void refreshAddresses() {
        final String localSuffix = ":" + mSession.getHomeServerConfig().getHomeserverUri().getHost();
        final String canonicalAlias = mRoom.getLiveState().alias;
        final ArrayList<String> aliases = new ArrayList<>(mRoom.getAliases());

        // remove the displayed preferences
        mAddressesSettingsCategory.removeAll();

        if (0 == aliases.size()) {
            AddressPreference preference = new AddressPreference(getActivity());
            preference.setTitle(getString(R.string.room_settings_addresses_no_local_addresses));
            preference.setKey(NO_LOCAL_ADDRESS_PREFERENCE_KEY);
            mAddressesSettingsCategory.addPreference(preference);
        } else {
            ArrayList<String> localAliases  = new ArrayList<>();
            ArrayList<String> remoteAliases  = new ArrayList<>();

            for(String alias : aliases) {
                if (alias.endsWith(localSuffix)) {
                    localAliases.add(alias);
                } else {
                    remoteAliases.add(alias);
                }
            }

            // the local aliases are displayed first in the list
            aliases.clear();
            aliases.addAll(localAliases);
            aliases.addAll(remoteAliases);

            int index = 0;

            for (String alias : aliases) {
                AddressPreference preference = new AddressPreference(getActivity());
                preference.setTitle(alias);
                preference.setKey(ADDRESSES_PREFERENCE_KEY_BASE + index);
                preference.setMainIconVisible(TextUtils.equals(alias, canonicalAlias));

                final String fAlias = alias;
                final AddressPreference fAddressPreference = preference;

                preference.setOnPreferenceLongClickListener( new VectorCustomActionEditTextPreference.OnPreferenceLongClickListener() {
                    @Override
                    public boolean onPreferenceLongClick(Preference preference) {
                        onAddressLongClick(fAlias, fAddressPreference.getMainIconView());
                        return true;
                    }
                });

                mAddressesSettingsCategory.addPreference(preference);
                index++;
            }
        }

        if (canUpdateAliases()) {
            // display the "add addresses" entry
            EditTextPreference addAddressPreference = new EditTextPreference(getActivity());
            addAddressPreference.setTitle(R.string.room_settings_addresses_add_new_address);
            addAddressPreference.setDialogTitle(R.string.room_settings_addresses_add_new_address);
            addAddressPreference.setKey(ADD_ADDRESSES_PREFERENCE_KEY);
            addAddressPreference.setIcon(CommonActivityUtils.tintDrawable(getActivity(), ContextCompat.getDrawable(getActivity(), R .drawable.ic_add_black), R.attr.settings_icon_tint_color));

            addAddressPreference.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            final String newAddress = ((String) newValue).trim();

                            // ignore empty alias
                            if (!TextUtils.isEmpty(newAddress)) {
                                if (!MXSession.isRoomAlias(newAddress)) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                                    builder.setTitle(R.string.room_settings_addresses_invalid_format_dialog_title);
                                    builder.setMessage(getString(R.string.room_settings_addresses_invalid_format_dialog_body, newAddress));

                                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    });

                                    AlertDialog dialog = builder.create();
                                    dialog.show();
                                } else if (aliases.indexOf(newAddress) < 0) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            displayLoadingView();
                                            mRoom.addAlias(newAddress, new ApiCallback<Void>() {
                                                @Override
                                                public void onSuccess(Void info) {
                                                    // when there is only one alias, it becomes the main alias.
                                                    if (mRoom.getAliases().size() == 1) {
                                                        mRoom.updateCanonicalAlias(mRoom.getAliases().get(0), mAliasUpdatesCallback);
                                                    } else {
                                                        mAliasUpdatesCallback.onSuccess(info);
                                                    }
                                                }

                                                @Override
                                                public void onNetworkError(Exception e) {
                                                    mAliasUpdatesCallback.onNetworkError(e);
                                                }

                                                @Override
                                                public void onMatrixError(MatrixError e) {
                                                    mAliasUpdatesCallback.onMatrixError(e);
                                                }

                                                @Override
                                                public void onUnexpectedError(Exception e) {
                                                    mAliasUpdatesCallback.onUnexpectedError(e);
                                                }
                                            });
                                        }
                                    });
                                }
                            }
                            return false;
                        }
                    });

            mAddressesSettingsCategory.addPreference(addAddressPreference);
        }
    }


    /**
     * Refresh the addresses section
     */
    private void refreshEndToEnd() {
        // encrypt to unverified devices
        final CheckBoxPreference sendToUnverifiedDevicesPref = (CheckBoxPreference)findPreference(getString(R.string.room_settings_never_send_to_unverified_devices_title));

        // reported by GA
        if (null == sendToUnverifiedDevicesPref) {
            Log.e(LOG_TAG, "## refreshEndToEnd() : sendToUnverifiedDevicesPref is null");
            return;
        }

        // test if the crypto is
        if (null == mSession.getCrypto()) {
            mAdvandceSettingsCategory.removePreference(sendToUnverifiedDevicesPref);
        } else if (null != sendToUnverifiedDevicesPref) {
            if (mRoom.isEncrypted()) {
                sendToUnverifiedDevicesPref.setChecked(false);

                mSession.getCrypto().getGlobalBlacklistUnverifiedDevices(
                        new SimpleApiCallback<Boolean>() {
                            @Override
                            public void onSuccess(final Boolean sendToVerifiedDevicesInAnyRoom) {
                                mSession.getCrypto().isRoomBlacklistUnverifiedDevices(mRoom.getRoomId(), new SimpleApiCallback<Boolean>() {
                                    @Override
                                    public void onSuccess(final Boolean sendToVerifiedDevicesInRoom) {
                                        sendToUnverifiedDevicesPref.setChecked(sendToVerifiedDevicesInRoom || sendToVerifiedDevicesInAnyRoom);
                                        sendToUnverifiedDevicesPref.setEnabled(!sendToVerifiedDevicesInAnyRoom);
                                    }
                                });
                            }
                        }
                );

            }  else if (null != sendToUnverifiedDevicesPref) {
                mAdvandceSettingsCategory.removePreference(sendToUnverifiedDevicesPref);
            }

            sendToUnverifiedDevicesPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    mSession.getCrypto().isRoomBlacklistUnverifiedDevices(mRoom.getRoomId(), new SimpleApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean status) {
                            if (sendToUnverifiedDevicesPref.isChecked() != status) {
                                SimpleApiCallback<Void> callback = new SimpleApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                    }
                                };

                                if (sendToUnverifiedDevicesPref.isChecked()) {
                                    mSession.getCrypto().setRoomBlacklistUnverifiedDevices(mRoom.getRoomId(), callback);
                                } else {
                                    mSession.getCrypto().setRoomUnblacklistUnverifiedDevices(mRoom.getRoomId(), callback);
                                }
                            }
                        }
                    });

                    return true;
                }
            });
        }

        final String key = PREF_KEY_ENCRYPTION + mRoom.getRoomId();

        // remove the displayed preferences
        Preference e2ePref = mAdvandceSettingsCategory.findPreference(key);

        if (null != e2ePref) {
            mAdvandceSettingsCategory.removePreference(e2ePref);
        }

        // remove the preference because it might switch from a SwitchPreference to  VectorCustomActionEditTextPreference
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(key);
        editor.commit();

        if (mRoom.isEncrypted()) {
            VectorCustomActionEditTextPreference isEncryptedPreference = new VectorCustomActionEditTextPreference(getActivity());
            isEncryptedPreference.setTitle(R.string.room_settings_addresses_e2e_enabled);
            isEncryptedPreference.setKey(key);
            isEncryptedPreference.setIcon(getResources().getDrawable(R.drawable.e2e_verified));
            mAdvandceSettingsCategory.addPreference(isEncryptedPreference);
        } else {
            PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();
            int myPowerLevel = -1;
            int minimumPowerLevel = 0;

            if (null != powerLevels) {
                myPowerLevel = powerLevels.getUserPowerLevel(mSession.getMyUserId());
                minimumPowerLevel = powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_MESSAGE_ENCRYPTION);
            }

            // Test if the user has enough power levels to enable the crypto is in this room
            if (myPowerLevel < minimumPowerLevel) {
                VectorCustomActionEditTextPreference isEncryptedPreference = new VectorCustomActionEditTextPreference(getActivity());
                isEncryptedPreference.setTitle(R.string.room_settings_addresses_e2e_disabled);
                isEncryptedPreference.setKey(key);
                isEncryptedPreference.setIcon(CommonActivityUtils.tintDrawable(getActivity(), getResources().getDrawable(R.drawable.e2e_unencrypted), R.attr.settings_icon_tint_color));
                mAdvandceSettingsCategory.addPreference(isEncryptedPreference);
            } else if (mSession.isCryptoEnabled()) {
                final VectorSwitchPreference encryptSwitchPreference = new VectorSwitchPreference(getActivity());
                encryptSwitchPreference.setTitle(R.string.room_settings_addresses_e2e_encryption_warning);
                encryptSwitchPreference.setKey(key);
                encryptSwitchPreference.setIcon(CommonActivityUtils.tintDrawable(getActivity(), getResources().getDrawable(R.drawable.e2e_unencrypted), R.attr.settings_icon_tint_color));
                encryptSwitchPreference.setChecked(false);
                mAdvandceSettingsCategory.addPreference(encryptSwitchPreference);

                encryptSwitchPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValueAsVoid) {
                        boolean newValue = (boolean) newValueAsVoid;

                        if (newValue != mRoom.isEncrypted()) {
                            new AlertDialog.Builder(VectorApp.getCurrentActivity())
                                    .setTitle(R.string.room_settings_addresses_e2e_prompt_title)
                                    .setMessage(R.string.room_settings_addresses_e2e_prompt_message)
                                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();

                                            displayLoadingView();
                                            mRoom.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new ApiCallback<Void>() {

                                                private void onDone() {
                                                    hideLoadingView(false);
                                                    refreshEndToEnd();
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

                                        }
                                    })
                                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            encryptSwitchPreference.setChecked(false);
                                        }
                                    })
                                    .create()
                                    .show();
                        }
                        return true;
                    }
                });

            }
        }
    }
}
