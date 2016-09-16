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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Pusher;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.ThreePid;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.ga.GAHelper;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.preference.UserAvatarPreference;
import im.vector.preference.VectorCustomActionEditTextPreference;
import im.vector.util.BugReporter;
import im.vector.util.ResourceUtils;
import im.vector.util.VectorUtils;

public class VectorSettingsPreferencesFragment extends PreferenceFragment {
    private static final String LOG_TAG = "VPreferenceFragment";

    // arguments indexes
    private static final String ARG_MATRIX_ID = "VectorSettingsPreferencesFragment.ARG_MATRIX_ID";

    private static final String EMAIL_PREFERENCE_KEY_BASE = "EMAIL_PREFERENCE_KEY_BASE";
    private static final String PUSHER_PREFERENCE_KEY_BASE = "PUSHER_PREFERENCE_KEY_BASE";
    private static final String IGNORED_USER_KEY_BASE = "IGNORED_USER_KEY_BASE";
    private static final String ADD_EMAIL_PREFERENCE_KEY = "ADD_EMAIL_PREFERENCE_KEY";
    private static final String APP_INFO_LINK_PREFERENCE_KEY = "application_info_link";

    private static final String DUMMY_RULE = "DUMMY_RULE";

    // members
    private MXSession mSession;
    private View mLoadingView;

    // rule Id <-> preference name
    private static HashMap<String, String> mPushesRuleByResourceId = null;

    // disable some updates if there is
    private IMXNetworkEventListener mNetworkListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            refreshDisplay();
        }
    };

    // displayed emails
    private PreferenceCategory mUserSettingsCategory;
    private List<String> mDisplayedEmails = new ArrayList<String>();

    // displayed pushers
    private PreferenceCategory mPushersSettingsCategory;
    private List<Pusher> mDisplayedPushers = new ArrayList<Pusher>();

    // displayed the ignored users list
    private PreferenceCategory mIgnoredUserSettingsCategory;

    // background sync category
    private PreferenceCategory mBackgroundSyncCategory;
    private EditTextPreference mSyncRequestTimeoutPreference;
    private EditTextPreference mSyncRequestDelayPreference;

    // events listener
    private MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onBingRulesUpdate() {
            refreshPreferences();
            refreshDisplay();
        }

        @Override
        public void onAccountInfoUpdate(MyUser myUser) {
            // refresh the settings value
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(getResources().getString(R.string.settings_display_name), myUser.displayname);
            editor.commit();

            refreshDisplay();
        }
    };

    // static constructor
    public static VectorSettingsPreferencesFragment newInstance(String matrixId) {
        VectorSettingsPreferencesFragment f = new VectorSettingsPreferencesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MATRIX_ID, matrixId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // retrieve the arguments
        Bundle args = getArguments();
        String matrixId = args.getString(ARG_MATRIX_ID);
        mSession = Matrix.getInstance(getActivity()).getSession(matrixId);

        // sanity checks
        if (null == mSession) {
            if (null != getActivity()) {
                getActivity().finish();
            }
            return;
        }

        // define the layout
        addPreferencesFromResource(R.xml.vector_settings_preferences);

        if (null == mPushesRuleByResourceId) {
            mPushesRuleByResourceId = new HashMap<>();

            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_enable_all_notif), BingRule.RULE_ID_DISABLE_ALL);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_enable_this_device), DUMMY_RULE);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_turn_screen_on), DUMMY_RULE);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_containing_my_name), BingRule.RULE_ID_CONTAIN_DISPLAY_NAME);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_messages_in_one_to_one), BingRule.RULE_ID_ONE_TO_ONE_ROOM);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_messages_in_group_chat), BingRule.RULE_ID_ALL_OTHER_MESSAGES_ROOMS);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_invited_to_room), BingRule.RULE_ID_INVITE_ME);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_call_invitations), BingRule.RULE_ID_CALL);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_messages_sent_by_bot), BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS);
        }

        final PreferenceManager preferenceManager = getPreferenceManager();

        UserAvatarPreference avatarPreference = (UserAvatarPreference)preferenceManager.findPreference("matrixId");
        avatarPreference.setSession(mSession);
        avatarPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                onUpdateAvatarClick();
                return false;
            }
        });

        EditTextPreference passwordPreference = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_change_password));
        passwordPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                onPasswordUpdateClick();
                return false;
            }
        });

        // application version
        EditTextPreference versionTextPreference = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_version));
        if (null != versionTextPreference) {
            versionTextPreference.setSummary(VectorUtils.getApplicationVersion(getActivity()));
        }

        // user account
        EditTextPreference accountIdTextPreference = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_logged_in));
        if (null != accountIdTextPreference) {
            accountIdTextPreference.setSummary(mSession.getMyUserId());
        }

        // home server
        EditTextPreference homeServerTextPreference = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_home_server));
        if (null != homeServerTextPreference) {
            homeServerTextPreference.setSummary(mSession.getHomeserverConfig().getHomeserverUri().toString());
        }

        // identity server
        EditTextPreference identityServerTextPreference = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_identity_server));
        if (null != identityServerTextPreference) {
            identityServerTextPreference.setSummary(mSession.getHomeserverConfig().getIdentityServerUri().toString());
        }

        // terms & conditions
        EditTextPreference termConditionsPreference = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_term_conditions));

        if (null != termConditionsPreference) {
            termConditionsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    VectorUtils.displayLicenses(getActivity());
                    return false;
                }
            });
        }

        // terms & conditions
        EditTextPreference privacyPreference = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.room_sliding_privacy_policy));

        if (null != termConditionsPreference) {
            privacyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    VectorUtils.displayPrivacyPolicy(getActivity());
                    return false;
                }
            });
        }

        // clear cache
        EditTextPreference clearCachePreference = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_clear_cache));

        if (null != clearCachePreference) {
            clearCachePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Matrix.getInstance(getActivity()).reloadSessions(getActivity());
                    return false;
                }
            });
        }

        final EditTextPreference displaynamePref = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_display_name));
        displaynamePref.setSummary(mSession.getMyUser().displayname);
        displaynamePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                onDisplayNameClick((String) newValue);
                return false;
            }
        });

        // push rules
        for(String resourceText : mPushesRuleByResourceId.keySet()) {
            final SwitchPreference switchPreference = (SwitchPreference)preferenceManager.findPreference(resourceText);

            if (null != switchPreference) {
                switchPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValueAsVoid) {
                        // on some old android APIs,
                        // the callback is called even if there is no user interaction
                        // so the value will be checked to ensure there is really no update.
                        onPushRuleClick(preference.getKey(), (boolean)newValueAsVoid);
                        return true;
                    }
                });
            }
        }

        final SwitchPreference useBackgroundSyncPref = (SwitchPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_enable_background_sync));

        if (null != useBackgroundSyncPref) {
            final GcmRegistrationManager gcmMgr = Matrix.getInstance(getActivity()).getSharedGCMRegistrationManager();

            useBackgroundSyncPref.setChecked(gcmMgr.isBackgroundSyncAllowed());

            useBackgroundSyncPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object aNewValue) {
                    boolean newValue = (boolean)aNewValue;

                    if (newValue != gcmMgr.isBackgroundSyncAllowed()) {
                        gcmMgr.setBackgroundSyncAllowed(newValue);
                    }

                    return true;
                }
            });
        }
        
        final SwitchPreference useGaPref = (SwitchPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.ga_use_settings));
        useGaPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean useGA = GAHelper.useGA(getActivity());
                boolean newGa = (boolean)newValue;

                if ((null != useGA) && (useGA != newGa)) {
                    if (!newGa) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                        builder.setMessage(getString(R.string.ga_use_disable_alert_message)).setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // do something here
                            }
                        }).show();
                    }
                    GAHelper.setUseGA(getActivity(), newGa);
                }

                return true;
            }
        });

        mUserSettingsCategory = (PreferenceCategory)getPreferenceManager().findPreference(getResources().getString(R.string.settings_user_settings));
        mPushersSettingsCategory = (PreferenceCategory)getPreferenceManager().findPreference(getResources().getString(R.string.settings_notifications_targets));
        mIgnoredUserSettingsCategory = (PreferenceCategory)getPreferenceManager().findPreference(getResources().getString(R.string.settings_ignored_users));

        // preference to start the App info screen, to facilitate App permissions access
        Preference applicationInfoLInkPref = findPreference(APP_INFO_LINK_PREFERENCE_KEY);
        applicationInfoLInkPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
                intent.setData(uri);

                if(null != getActivity()) {
                    getActivity().getApplicationContext().startActivity(intent);
                }

                return true;
            }
        });

        // background sync management
        mBackgroundSyncCategory = (PreferenceCategory)getPreferenceManager().findPreference(getResources().getString(R.string.settings_background_sync));
        mSyncRequestTimeoutPreference = (EditTextPreference)getPreferenceManager().findPreference(getResources().getString(R.string.settings_set_sync_timeout));
        mSyncRequestDelayPreference = (EditTextPreference)getPreferenceManager().findPreference(getResources().getString(R.string.settings_set_sync_delay));

        refreshPushersList();
        refreshEmailsList();
        refreshIgnoredUsersList();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mSession.isAlive()) {
            mSession.getDataHandler().removeListener(mEventsListener);
            Matrix.getInstance(getActivity()).removeNetworkEventListener(mNetworkListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mSession.isAlive()) {
            mSession.getDataHandler().addListener(mEventsListener);

            Matrix.getInstance(getActivity()).addNetworkEventListener(mNetworkListener);

            mSession.getMyUser().refreshLinkedEmails(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    // ensure that the activity still exists
                    if (null != getActivity()) {
                        // and the result is called in the right thread
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                refreshEmailsList();
                            }
                        });
                    }
                }
            });

            Matrix.getInstance(getActivity()).getSharedGCMRegistrationManager().refreshPushersList(Matrix.getInstance(getActivity()).getSessions(), new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    refreshPushersList();
                }
            });

            // refresh anything else
            refreshPreferences();
            refreshDisplay();
            refreshBackgroundSyncPrefs();
        }
    }

    //==============================================================================================================
    // Display methods
    //==============================================================================================================

    /**
     * Display the loading view.
     */
    private void displayLoadingView() {
        // search the loading view from the upper view
        if (null == mLoadingView) {
            View parent = getView();

            while ((parent != null) && (mLoadingView == null)) {
                mLoadingView = parent.findViewById(R.id.vector_settings_spinner_views);
                parent = (View) parent.getParent();
            }
        }

        if (null != mLoadingView) {
            mLoadingView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide the loading view.
     */
    private void hideLoadingView() {
        if (null != mLoadingView) {
            mLoadingView.setVisibility(View.GONE);
        }
    }

    /**
     * Hide the loading view and refresh the preferences.
     * @param refresh
     */
    private void hideLoadingView(boolean refresh) {
        mLoadingView.setVisibility(View.GONE);

        if (refresh) {
            refreshDisplay();
        }
    }

    /**
     * Refresh the preferences.
     */
    private void refreshDisplay() {
        boolean isConnected = Matrix.getInstance(getActivity()).isConnected();
        PreferenceManager preferenceManager = getPreferenceManager();

        // refresh the avatar
        UserAvatarPreference avatarPreference = (UserAvatarPreference)preferenceManager.findPreference("matrixId");
        avatarPreference.refreshAvatar();
        avatarPreference.setEnabled(isConnected);

        // refresh the display name
        final EditTextPreference displaynamePref = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_display_name));
        displaynamePref.setSummary(mSession.getMyUser().displayname);
        displaynamePref.setText(mSession.getMyUser().displayname);
        displaynamePref.setEnabled(isConnected);

        // change password
        final EditTextPreference changePasswordPref = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_change_password));
        changePasswordPref.setEnabled(isConnected);

        // update the push rules
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        BingRuleSet rules = mSession.getDataHandler().pushRules();

        GcmRegistrationManager gcmMgr = Matrix.getInstance(getActivity()).getSharedGCMRegistrationManager();

        for(String resourceText : mPushesRuleByResourceId.keySet()) {
            SwitchPreference switchPreference = (SwitchPreference) preferenceManager.findPreference(resourceText);

            if (null != switchPreference) {
                if (resourceText.equals(getResources().getString(R.string.settings_enable_this_device))) {
                    switchPreference.setChecked(gcmMgr.areDeviceNotificationsAllowed());
                } else if (resourceText.equals(getResources().getString(R.string.settings_turn_screen_on))) {
                    switchPreference.setChecked(gcmMgr.isScreenTurnedOn());
                } else {
                    switchPreference.setEnabled((null != rules) && isConnected);
                    switchPreference.setChecked(preferences.getBoolean(resourceText, false));
                }
            }
        }
    }

    //==============================================================================================================
    // Update items  methods
    //==============================================================================================================

    /**
     * Update the password.
     */
    private void onPasswordUpdateClick() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                final View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_dialog_change_password, null);
                alertDialog.setView(view);
                alertDialog.setTitle(getString(R.string.settings_change_password));

                final EditText oldPasswordText = (EditText)view.findViewById(R.id.change_password_old_pwd_text);
                final EditText newPasswordText = (EditText)view.findViewById(R.id.change_password_new_pwd_text);
                final EditText confirmNewPasswordText = (EditText)view.findViewById(R.id.change_password_confirm_new_pwd_text);

                // Setting Positive "Yes" Button
                alertDialog.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (null != getActivity()) {
                            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
                        }

                        String oldPwd = oldPasswordText.getText().toString().trim();
                        String newPwd = newPasswordText.getText().toString().trim();

                        displayLoadingView();

                        mSession.updatePassword(oldPwd, newPwd, new ApiCallback<Void>() {
                            private void onDone(final int textId) {
                                // check the activity still exists
                                if (null != getActivity()) {
                                    // and the code is called in the right thread
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            hideLoadingView();
                                            Toast.makeText(getActivity(),
                                                    getActivity().getResources().getString(textId),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onSuccess(Void info) {
                                onDone(R.string.settings_password_updated);
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                onDone(R.string.settings_fail_to_update_password);
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                onDone(R.string.settings_fail_to_update_password);
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                onDone(R.string.settings_fail_to_update_password);
                            }
                        });
                    }
                });

                // Setting Negative "NO" Button
                alertDialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (null != getActivity()) {
                            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
                        }
                    }
                });

                AlertDialog dialog = alertDialog.show();

                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (null != getActivity()) {
                            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
                        }
                    }
                });

                final Button saveButton =  dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                saveButton.setEnabled(false);

                confirmNewPasswordText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String oldPwd = oldPasswordText.getText().toString().trim();
                        String newPwd = newPasswordText.getText().toString().trim();
                        String newConfirmPwd = confirmNewPasswordText.getText().toString().trim();

                        saveButton.setEnabled((oldPwd.length() > 0) && (newPwd.length() > 0) && TextUtils.equals(newPwd, newConfirmPwd));
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                });

            }
        });
    }

    /**
     * Update a push rule.
     */
    private void onPushRuleClick(final String fResourceText, final boolean newValue) {
        final GcmRegistrationManager gcmMgr = Matrix.getInstance(getActivity()).getSharedGCMRegistrationManager();

        Log.d(LOG_TAG, "onPushRuleClick " + fResourceText + " : set to " + newValue);

        if (fResourceText.equals(getResources().getString(R.string.settings_turn_screen_on))) {
            if (gcmMgr.isScreenTurnedOn() != newValue) {
                gcmMgr.setScreenTurnedOn(newValue);
            }
            return;
        }

        if (fResourceText.equals(getResources().getString(R.string.settings_enable_this_device))) {
            boolean isConnected = Matrix.getInstance(getActivity()).isConnected();
            final boolean isAllowed = gcmMgr.areDeviceNotificationsAllowed();

            // avoid useless update
            if (isAllowed == newValue) {
                return;
            }

            gcmMgr.setDeviceNotificationsAllowed(!isAllowed);

            // when using GCM
            // need to register on servers
            if (isConnected && gcmMgr.useGCM() && (gcmMgr.isServerRegistred() || gcmMgr.isServerUnRegistred())) {
                final GcmRegistrationManager.ThirdPartyRegistrationListener listener = new GcmRegistrationManager.ThirdPartyRegistrationListener() {

                    private void onDone() {
                        if (null != getActivity()) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    hideLoadingView(true);
                                    refreshPushersList();
                                }
                            });
                        }
                    }

                    @Override
                    public void onThirdPartyRegistered() {
                        onDone();
                    }

                    @Override
                    public void onThirdPartyRegistrationFailed() {
                        gcmMgr.setDeviceNotificationsAllowed(isAllowed);
                        onDone();
                    }

                    @Override
                    public void onThirdPartyUnregistered() {
                        onDone();
                    }

                    @Override
                    public void onThirdPartyUnregistrationFailed() {
                        gcmMgr.setDeviceNotificationsAllowed(isAllowed);
                        onDone();
                    }
                };

                displayLoadingView();
                if (gcmMgr.isServerRegistred()) {
                    gcmMgr.unregister(listener);
                } else {
                    gcmMgr.register(listener);
                }
            }

            return;
        }

        final String ruleId = mPushesRuleByResourceId.get(fResourceText);
        BingRule rule = mSession.getDataHandler().pushRules().findDefaultRule(ruleId);

        // check if there is an update
        boolean curValue = ((null != rule) && rule.isEnabled);

        if (TextUtils.equals(ruleId, BingRule.RULE_ID_DISABLE_ALL) || TextUtils.equals(ruleId, BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS)) {
            curValue = !curValue;
        }

        // on some old android APIs,
        // the callback is called even if there is no user interaction
        // so the value will be checked to ensure there is really no update.
        if (newValue == curValue) {
            return;
        }

        if (null != rule) {
            displayLoadingView();
            mSession.getDataHandler().getBingRulesManager().toggleRule(rule, new BingRulesManager.onBingRuleUpdateListener() {

                private void onDone() {
                    // check if the activity still exists
                    if (null != getActivity()) {
                        // ensure that the response is done in the right thread.
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                hideLoadingView();

                                BingRule rule = mSession.getDataHandler().pushRules().findDefaultRule(ruleId);
                                boolean isEnabled = ((null != rule) && rule.isEnabled);

                                if (TextUtils.equals(ruleId, BingRule.RULE_ID_DISABLE_ALL) || TextUtils.equals(ruleId, BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS)) {
                                    isEnabled = !isEnabled;
                                }

                                final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putBoolean(fResourceText, isEnabled);
                                editor.commit();
                                hideLoadingView(true);
                            }
                        });
                    }
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
        }
    }

    /**
     * Update the displayname.
     */
    private void onDisplayNameClick(final String value) {
        if (!TextUtils.equals(mSession.getMyUser().displayname, value)) {
            displayLoadingView();

            mSession.getMyUser().updateDisplayName(value, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    // refresh the settings value
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(getResources().getString(R.string.settings_display_name), value);
                    editor.commit();

                    onCommonDone(null);

                    refreshDisplay();
                }

                @Override
                public void onNetworkError(Exception e) {
                    onCommonDone(e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onCommonDone(e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onCommonDone(e.getLocalizedMessage());
                }
            });
        }
    }

    /**
     * Update the avatar.
     */
    private void onUpdateAvatarClick() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(getActivity(), VectorMediasPickerActivity.class);
                intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true);
                startActivityForResult(intent, VectorUtils.TAKE_IMAGE);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            Uri thumbnailUri = VectorUtils.getThumbnailUriFromIntent(getActivity(), data, mSession.getMediasCache());

            if (null != thumbnailUri) {
                displayLoadingView();

                ResourceUtils.Resource resource = ResourceUtils.openResource(getActivity(), thumbnailUri, null);

                if (null != resource) {
                    mSession.getMediasCache().uploadContent(resource.mContentStream, null, resource.mMimeType, null, new MXMediaUploadListener() {

                        @Override
                        public void onUploadError(String uploadId, int serverResponseCode, String serverErrorMessage) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    hideLoadingView(false);
                                }
                            });
                        }

                        @Override
                        public void onUploadComplete(final String uploadId, final String contentUri) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mSession.getMyUser().updateAvatarUrl(contentUri, new ApiCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void info) {
                                            onCommonDone(null);
                                            refreshDisplay();
                                        }

                                        @Override
                                        public void onNetworkError(Exception e) {
                                            onCommonDone(e.getLocalizedMessage());
                                        }

                                        @Override
                                        public void onMatrixError(MatrixError e) {
                                            onCommonDone(e.getLocalizedMessage());
                                        }

                                        @Override
                                        public void onUnexpectedError(Exception e) {
                                            onCommonDone(e.getLocalizedMessage());
                                        }
                                    });
                                }
                            });
                        }
                    });
                }
            }
        }

    }


    /**
     * Refresh the known information about the account
     */
    private void refreshPreferences() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(this.getResources().getString(R.string.settings_display_name), mSession.getMyUser().displayname);
        editor.putString(this.getResources().getString(R.string.settings_version), VectorUtils.getApplicationVersion(getActivity()));

        BingRuleSet mBingRuleSet = mSession.getDataHandler().pushRules();

        if (null != mBingRuleSet) {
            for (String resourceText : mPushesRuleByResourceId.keySet()) {
                String ruleId = mPushesRuleByResourceId.get(resourceText);

                BingRule rule = mBingRuleSet.findDefaultRule(ruleId);
                boolean isEnabled = ((null != rule) && rule.isEnabled);

                if (TextUtils.equals(ruleId, BingRule.RULE_ID_DISABLE_ALL) || TextUtils.equals(ruleId, BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS)) {
                    isEnabled = !isEnabled;
                }
                // check if the rule is only defined by don't notify
                else if (isEnabled) {
                    List<JsonElement> actions = rule.actions;

                    // no action -> noting will be done
                    if ((null == actions) || (0 == actions.size())) {
                        isEnabled = false;
                    } else if (1 == actions.size()) {
                        try {
                            isEnabled = !TextUtils.equals(actions.get(0).getAsString(), BingRule.ACTION_DONT_NOTIFY);
                        } catch (Exception e) {
                        }
                    }
                }

                editor.putBoolean(resourceText, isEnabled);
            }
        }

        editor.commit();
    }

    //==============================================================================================================
    // ignored users list management
    //==============================================================================================================

    /**
     * Refresh the ignored users list
     */
    private void refreshIgnoredUsersList() {
        List<String> ignoredUsersList = mSession.getDataHandler().getIgnoredUserIds();

        Collections.sort(ignoredUsersList, new Comparator<String>() {
            @Override
            public int compare(String u1, String u2) {
                return u1.toLowerCase().compareTo(u2.toLowerCase());
            }
        });

        PreferenceScreen preferenceScreen = getPreferenceScreen();

        preferenceScreen.removePreference(mIgnoredUserSettingsCategory);
        mIgnoredUserSettingsCategory.removeAll();

        if (ignoredUsersList.size() > 0) {
            preferenceScreen.addPreference(mIgnoredUserSettingsCategory);

            for (final String userId : ignoredUsersList) {
                VectorCustomActionEditTextPreference preference = new VectorCustomActionEditTextPreference(getActivity());

                preference.setTitle(userId);
                preference.setKey(IGNORED_USER_KEY_BASE + userId);

                preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        new AlertDialog.Builder(VectorApp.getCurrentActivity())
                                .setMessage(getActivity().getString(R.string.settings_unignore_user, userId))
                                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();

                                        displayLoadingView();

                                        ArrayList<String> idsList = new ArrayList<>();
                                        idsList.add(userId);

                                        mSession.unIgnoreUsers(idsList, new ApiCallback<Void>() {
                                            @Override
                                            public void onSuccess(Void info) {
                                                onCommonDone(null);
                                            }

                                            @Override
                                            public void onNetworkError(Exception e) {
                                                onCommonDone(e.getLocalizedMessage());
                                            }

                                            @Override
                                            public void onMatrixError(MatrixError e) {
                                                onCommonDone(e.getLocalizedMessage());
                                            }

                                            @Override
                                            public void onUnexpectedError(Exception e) {
                                                onCommonDone(e.getLocalizedMessage());
                                            }
                                        });
                                    }
                                })
                                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                })
                                .create()
                                .show();

                        return false;
                    }
                });

                mIgnoredUserSettingsCategory.addPreference(preference);
            }
        }
    }

    //==============================================================================================================
    // pushers list management
    //==============================================================================================================

    /**
     * Refresh the pushers list
     */
    private void refreshPushersList() {
        GcmRegistrationManager registrationManager = Matrix.getInstance(getActivity()).getSharedGCMRegistrationManager();
        List<Pusher> pushersList = new ArrayList<>(registrationManager.mPushersList);

        // check first if there is an update
        boolean isNewList = true;
        if ((null != mDisplayedPushers) && (pushersList.size() == mDisplayedPushers.size())) {
            isNewList = !mDisplayedPushers.containsAll(pushersList);
        }

        if (isNewList) {
            // remove the displayed one
            mPushersSettingsCategory.removeAll();

            // add new emails list
            mDisplayedPushers = pushersList;

            int index = 0;

            for (Pusher pusher : mDisplayedPushers) {
                VectorCustomActionEditTextPreference preference = new VectorCustomActionEditTextPreference(getActivity());

                // fix https://github.com/vector-im/vector-android/issues/192
                // It appears that the server sends some invalid pushers where the device and the app are
                // invalid. In all these cases the language is set to null.
                if(null != pusher.lang) {
                    preference.setTitle(pusher.deviceDisplayName);
                    preference.setSummary(pusher.appDisplayName);
                    preference.setKey(PUSHER_PREFERENCE_KEY_BASE + index);
                    index++;
                    mPushersSettingsCategory.addPreference(preference);
                }
            }
        }
    }

    //==============================================================================================================
    // Email management
    //==============================================================================================================

    /**
     * Refresh the emails list
     */
    private void refreshEmailsList() {
        List<String> newEmailsList = mSession.getMyUser().getlinkedEmails();

        // check first if there is an update
        boolean isNewList = true;
        if ((null != mDisplayedEmails) && (newEmailsList.size() == mDisplayedEmails.size())) {
            isNewList = !mDisplayedEmails.containsAll(newEmailsList);
        }

        if (isNewList) {
            // remove the displayed one
            for (int index = 0; ; index++) {
                Preference preference = mUserSettingsCategory.findPreference(EMAIL_PREFERENCE_KEY_BASE + index);

                if (null != preference) {
                    mUserSettingsCategory.removePreference(preference);
                } else {
                    break;
                }
            }

            // remove the add email
            Preference curAddEmailPreference = mUserSettingsCategory.findPreference(ADD_EMAIL_PREFERENCE_KEY);
            if (null != curAddEmailPreference) {
                mUserSettingsCategory.removePreference(curAddEmailPreference);
            }

            // add new emails list
            mDisplayedEmails = newEmailsList;

            int index = 0;

            for (String email : mDisplayedEmails) {
                VectorCustomActionEditTextPreference preference = new VectorCustomActionEditTextPreference(getActivity());

                preference.setTitle(getResources().getString(R.string.settings_email_address));
                preference.setSummary(email);
                preference.setKey(EMAIL_PREFERENCE_KEY_BASE + index);

                final String fEmailAddress = email;

                preference.setOnPreferenceLongClickListener(new VectorCustomActionEditTextPreference.OnPreferenceLongClickListener() {
                    @Override
                    public boolean onPreferenceLongClick(Preference preference) {
                        VectorUtils.copyToClipboard(getActivity(), fEmailAddress);
                        return true;
                    }
                });

                index++;
                mUserSettingsCategory.addPreference(preference);
            }
        }

        Preference curAddEmailPreference = mUserSettingsCategory.findPreference(ADD_EMAIL_PREFERENCE_KEY);

        if (null == curAddEmailPreference) {
            // display the "add email" entry
            EditTextPreference addEmailPreference = new EditTextPreference(getActivity());
            addEmailPreference.setTitle(R.string.settings_add_email_address);
            addEmailPreference.setDialogTitle(R.string.settings_add_email_address);
            addEmailPreference.setKey(ADD_EMAIL_PREFERENCE_KEY);
            addEmailPreference.setIcon(getResources().getDrawable(R.drawable.ic_material_add_circle));

            addEmailPreference.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            final String email = (String) newValue;

                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    addEmail(email);
                                }
                            });

                            return false;
                        }
                    });

            mUserSettingsCategory.addPreference(addEmailPreference);
        }
    }

    /**
     * A request has been processed.
     * Display a toast if there is a an error message
     * @param errorMessage the error message
     */
    private void onCommonDone(final String errorMessage) {
        if (null != getActivity()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!TextUtils.isEmpty(errorMessage)) {
                        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
                    }
                    hideLoadingView();
                }
            });
        }
    }

    /**
     * Attempt to add a new email to the account
     * @param email
     */
    private void addEmail(String email) {
        // check first if the email syntax is valid
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(getActivity(), getString(R.string.auth_invalid_email), Toast.LENGTH_SHORT).show();
            return;
        }

        // check first if the email syntax is valid
        if (mDisplayedEmails.indexOf(email) >= 0) {
            Toast.makeText(getActivity(), getString(R.string.auth_email_already_defined), Toast.LENGTH_SHORT).show();
            return;
        }

        final ThreePid pid = new ThreePid(email, ThreePid.MEDIUM_EMAIL);

        displayLoadingView();

        mSession.getMyUser().requestValidationToken(pid, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                if (null != getActivity()) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showEmailValidationDialog(pid);
                        }
                    });
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onCommonDone(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onCommonDone(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onCommonDone(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Show an email validation dialog to warn the user tho valid his email link.
     * @param pid the used pid.
     */
    private void showEmailValidationDialog(final ThreePid pid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.account_email_validation_title);
        builder.setMessage(R.string.account_email_validation_message);
        builder.setPositiveButton(R.string._continue, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mSession.getMyUser().add3Pid(pid, true, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        if (null != getActivity()) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    hideLoadingView();
                                    refreshEmailsList();
                                }
                            });
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onCommonDone(e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (TextUtils.equals(e.errcode, MatrixError.THREEPID_AUTH_FAILED)) {
                            if (null != getActivity()) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        hideLoadingView();
                                        Toast.makeText(getActivity(), getString(R.string.account_email_validation_error), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        } else {
                            onCommonDone(e.getLocalizedMessage());
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onCommonDone(e.getLocalizedMessage());
                    }
                });
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                hideLoadingView();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    //==============================================================================================================
    // background sync management
    //==============================================================================================================

    /**
     * Convert a delay in seconds to string
     * @param seconds the delay in seconds
     * @return the text
     */
    private String secondsToText(int seconds) {
        if (seconds > 1) {
            return seconds + " " + getActivity().getString(R.string.settings_seconds);
        } else {
            return seconds + " " + getActivity().getString(R.string.settings_second);
        }
    }

    /**
     * Refresh the background sync preference
     */
    private void refreshBackgroundSyncPrefs() {
        // sanity check
        if (null == getActivity()) {
            return;
        }

        final GcmRegistrationManager gcmmgr = Matrix.getInstance(getActivity()).getSharedGCMRegistrationManager();

        final int timeout = gcmmgr.getBackgroundSyncTimeOut() / 1000;
        final int delay = gcmmgr.getBackgroundSyncDelay() / 1000;

        // update the settings
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(this.getResources().getString(R.string.settings_set_sync_timeout), timeout + "");
        editor.putString(this.getResources().getString(R.string.settings_set_sync_delay), delay + "");
        editor.commit();

        if (null != mSyncRequestTimeoutPreference) {
            mSyncRequestTimeoutPreference.setSummary(secondsToText(timeout));

            mSyncRequestTimeoutPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int newTimeOut = timeout;

                    try {
                        newTimeOut = Integer.parseInt((String) newValue);
                    } catch(Exception e) {
                    }

                    if (newTimeOut != timeout) {
                        gcmmgr.setBackgroundSyncTimeOut(newTimeOut * 1000);

                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                refreshBackgroundSyncPrefs();
                            }
                        });
                    }

                    return false;
                }
            });

        }

        if (null != mSyncRequestDelayPreference) {
            mSyncRequestDelayPreference.setSummary(secondsToText(delay));

            mSyncRequestDelayPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {

                    int newDelay = delay;

                    try {
                        newDelay = Integer.parseInt((String) newValue);
                    } catch(Exception e) {
                    }

                    if (newDelay != delay) {
                        gcmmgr.setBackgroundSyncDelay(newDelay * 1000);

                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                refreshBackgroundSyncPrefs();
                            }
                        });
                    }

                    return false;
                }
            });
        }

        // theses both settings are dedicated when a client does not support GCM
        if (gcmmgr.hasRegistrationToken()) {
            mBackgroundSyncCategory.removePreference(mSyncRequestTimeoutPreference);
            mBackgroundSyncCategory.removePreference(mSyncRequestDelayPreference);
        }
    }
}
