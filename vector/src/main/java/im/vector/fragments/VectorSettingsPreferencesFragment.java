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
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.support.design.widget.TextInputEditText;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Pusher;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.DeviceInfo;
import org.matrix.androidsdk.rest.model.DevicesListResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ThirdPartyIdentifier;
import org.matrix.androidsdk.rest.model.ThreePid;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.CountryPickerActivity;
import im.vector.activity.PhoneNumberAdditionActivity;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.contacts.ContactsManager;
import im.vector.ga.GAHelper;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.preference.ProgressBarPreference;
import im.vector.preference.UserAvatarPreference;
import im.vector.preference.VectorCustomActionEditTextPreference;
import im.vector.util.PhoneNumberUtils;
import im.vector.util.ResourceUtils;
import im.vector.util.SharedDataItem;
import im.vector.util.VectorUtils;

public class VectorSettingsPreferencesFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String LOG_TAG = "VPreferenceFragment";

    // arguments indexes
    private static final String ARG_MATRIX_ID = "VectorSettingsPreferencesFragment.ARG_MATRIX_ID";

    private static final String EMAIL_PREFERENCE_KEY_BASE = "EMAIL_PREFERENCE_KEY_BASE";
    private static final String PHONE_NUMBER_PREFERENCE_KEY_BASE = "PHONE_NUMBER_PREFERENCE_KEY_BASE";
    private static final String PUSHER_PREFERENCE_KEY_BASE = "PUSHER_PREFERENCE_KEY_BASE";
    private static final String DEVICES_PREFERENCE_KEY_BASE = "DEVICES_PREFERENCE_KEY_BASE";
    private static final String IGNORED_USER_KEY_BASE = "IGNORED_USER_KEY_BASE";
    private static final String ADD_EMAIL_PREFERENCE_KEY = "ADD_EMAIL_PREFERENCE_KEY";
    private static final String ADD_PHONE_NUMBER_PREFERENCE_KEY = "ADD_PHONE_NUMBER_PREFERENCE_KEY";
    private static final String APP_INFO_LINK_PREFERENCE_KEY = "application_info_link";

    private static final String DUMMY_RULE = "DUMMY_RULE";
    private static final String LABEL_UNAVAILABLE_DATA = "none";

    private static final int REQUEST_E2E_FILE_REQUEST_CODE = 123;
    private static final int REQUEST_NEW_PHONE_NUMBER = 456;
    private static final int REQUEST_PHONEBOOK_COUNTRY = 789;

    // rule Id <-> preference name
    private static HashMap<String, String> mPushesRuleByResourceId = null;
    // members
    private MXSession mSession;
    // disable some updates if there is
    private final IMXNetworkEventListener mNetworkListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            refreshDisplay();
        }
    };
    // events listener
    private final MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onBingRulesUpdate() {
            refreshPreferences();
            refreshDisplay();
        }

        @Override
        public void onAccountInfoUpdate(MyUser myUser) {
            // refresh the settings value
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance().getApplicationContext());
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(getString(R.string.settings_display_name), myUser.displayname);
            editor.commit();

            refreshDisplay();
        }
    };
    private View mLoadingView;
    // cryptography
    private DeviceInfo mMyDeviceInfo;
    private PreferenceCategory mCryptographyCategory;
    private PreferenceCategory mCryptographyCategoryDivider;
    // displayed emails
    private PreferenceCategory mUserSettingsCategory;
    private List<String> mDisplayedEmails = new ArrayList<>();
    private List<String> mDisplayedPhoneNumber = new ArrayList<>();
    // Local contacts
    private PreferenceCategory mContactSettingsCategory;
    private VectorCustomActionEditTextPreference mContactPhonebookCountryPreference;
    // displayed pushers
    private PreferenceCategory mPushersSettingsDivider;
    private PreferenceCategory mPushersSettingsCategory;
    private List<Pusher> mDisplayedPushers = new ArrayList<>();
    // devices: device IDs and device names
    private PreferenceCategory mDevicesListSettingsCategory;
    private PreferenceCategory mDevicesListSettingsCategoryDivider;
    private List<DeviceInfo> mDevicesNameList = new ArrayList<>();
    // used to avoid requesting to enter the password for each deletion
    private String mAccountPassword;
    // displayed the ignored users list
    private PreferenceCategory mIgnoredUserSettingsCategoryDivider;
    private PreferenceCategory mIgnoredUserSettingsCategory;
    // background sync category
    private PreferenceCategory mBackgroundSyncCategory;
    private CheckBoxPreference mStartBackgroundSyncOnBoot;
    private EditTextPreference mSyncRequestTimeoutPreference;
    private EditTextPreference mSyncRequestDelayPreference;
    private PreferenceCategory mLabsCategory;

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

        final Context appContext = getActivity().getApplicationContext();

        // retrieve the arguments
        Bundle args = getArguments();
        String matrixId = args.getString(ARG_MATRIX_ID);
        mSession = Matrix.getInstance(appContext).getSession(matrixId);

        // sanity checks
        if (null == mSession) {
            getActivity().finish();
            return;
        }

        // define the layout
        addPreferencesFromResource(R.xml.vector_settings_preferences);

        if (null == mPushesRuleByResourceId) {
            mPushesRuleByResourceId = new HashMap<>();

            mPushesRuleByResourceId.put(getString(R.string.settings_enable_all_notif), BingRule.RULE_ID_DISABLE_ALL);
            mPushesRuleByResourceId.put(getString(R.string.settings_enable_this_device), DUMMY_RULE);
            mPushesRuleByResourceId.put(getString(R.string.settings_turn_screen_on), DUMMY_RULE);
            mPushesRuleByResourceId.put(getString(R.string.settings_containing_my_name), BingRule.RULE_ID_CONTAIN_DISPLAY_NAME);
            mPushesRuleByResourceId.put(getString(R.string.settings_messages_in_one_to_one), BingRule.RULE_ID_ONE_TO_ONE_ROOM);
            mPushesRuleByResourceId.put(getString(R.string.settings_messages_in_group_chat), BingRule.RULE_ID_ALL_OTHER_MESSAGES_ROOMS);
            mPushesRuleByResourceId.put(getString(R.string.settings_invited_to_room), BingRule.RULE_ID_INVITE_ME);
            mPushesRuleByResourceId.put(getString(R.string.settings_call_invitations), BingRule.RULE_ID_CALL);
            mPushesRuleByResourceId.put(getString(R.string.settings_messages_sent_by_bot), BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS);
        }

        UserAvatarPreference avatarPreference = (UserAvatarPreference) findPreference("matrixId");
        avatarPreference.setSession(mSession);
        avatarPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                onUpdateAvatarClick();
                return false;
            }
        });

        EditTextPreference passwordPreference = (EditTextPreference) findPreference(getString(R.string.settings_change_password));
        passwordPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                onPasswordUpdateClick();
                return false;
            }
        });

        // application version
        VectorCustomActionEditTextPreference versionTextPreference = (VectorCustomActionEditTextPreference) findPreference(getString(R.string.settings_version));
        if (null != versionTextPreference) {
            versionTextPreference.setSummary(VectorUtils.getApplicationVersion(appContext));

            versionTextPreference.setOnPreferenceLongClickListener(new VectorCustomActionEditTextPreference.OnPreferenceLongClickListener() {
                @Override
                public boolean onPreferenceLongClick(Preference preference) {
                    VectorUtils.copyToClipboard(appContext ,VectorUtils.getApplicationVersion(appContext));
                    return true;
                }
            });
        }

        // olm version
        EditTextPreference olmTextPreference = (EditTextPreference) findPreference(getString(R.string.settings_olm_version));
        if (null != olmTextPreference) {
            olmTextPreference.setSummary(Matrix.getInstance(appContext).getDefaultSession().getCryptoVersion(appContext, false));
        }

        // user account
        EditTextPreference accountIdTextPreference = (EditTextPreference) findPreference(getString(R.string.settings_logged_in));
        if (null != accountIdTextPreference) {
            accountIdTextPreference.setSummary(mSession.getMyUserId());
        }

        // home server
        EditTextPreference homeServerTextPreference = (EditTextPreference) findPreference(getString(R.string.settings_home_server));
        if (null != homeServerTextPreference) {
            homeServerTextPreference.setSummary(mSession.getHomeserverConfig().getHomeserverUri().toString());
        }

        // identity server
        EditTextPreference identityServerTextPreference = (EditTextPreference) findPreference(getString(R.string.settings_identity_server));
        if (null != identityServerTextPreference) {
            identityServerTextPreference.setSummary(mSession.getHomeserverConfig().getIdentityServerUri().toString());
        }

        // terms & conditions
        EditTextPreference termConditionsPreference = (EditTextPreference) findPreference(getString(R.string.settings_app_term_conditions));

        if (null != termConditionsPreference) {
            termConditionsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    VectorUtils.displayAppTac();
                    return false;
                }
            });
        }

        // privacy policy
        EditTextPreference privacyPreference = (EditTextPreference) findPreference(getString(R.string.settings_privacy_policy));

        if (null != termConditionsPreference) {
            privacyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    VectorUtils.displayAppPrivacyPolicy();
                    return false;
                }
            });
        }

        // third party notice
        EditTextPreference thirdPartyNotices = (EditTextPreference) findPreference(getString(R.string.settings_third_party_notices));

        if (null != termConditionsPreference) {
            thirdPartyNotices.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    VectorUtils.displayThirdPartyLicenses();
                    return false;
                }
            });
        }

        // copyright
        EditTextPreference copyrightNotices = (EditTextPreference) findPreference(getString(R.string.settings_copyright));

        if (null != termConditionsPreference) {
            copyrightNotices.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    VectorUtils.displayAppCopyright();
                    return false;
                }
            });
        }

        // clear cache
        EditTextPreference clearCachePreference = (EditTextPreference) findPreference(getString(R.string.settings_clear_cache));

        if (null != clearCachePreference) {
            clearCachePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    displayLoadingView();
                    Matrix.getInstance(appContext).reloadSessions(appContext);
                    return false;
                }
            });
        }

        final EditTextPreference displaynamePref = (EditTextPreference) findPreference(appContext.getString(R.string.settings_display_name));
        displaynamePref.setSummary(mSession.getMyUser().displayname);
        displaynamePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                onDisplayNameClick((null == newValue) ? null : ((String) newValue).trim());
                return false;
            }
        });

        // push rules
        for (String resourceText : mPushesRuleByResourceId.keySet()) {
            final CheckBoxPreference switchPreference = (CheckBoxPreference) findPreference(resourceText);

            if (null != switchPreference) {
                switchPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValueAsVoid) {
                        // on some old android APIs,
                        // the callback is called even if there is no user interaction
                        // so the value will be checked to ensure there is really no update.
                        onPushRuleClick(preference.getKey(), (boolean) newValueAsVoid);
                        return true;
                    }
                });
            }
        }

        final CheckBoxPreference useBackgroundSyncPref = (CheckBoxPreference) findPreference(appContext.getString(R.string.settings_enable_background_sync));

        if (null != useBackgroundSyncPref) {
            final GcmRegistrationManager gcmMgr = Matrix.getInstance(appContext).getSharedGCMRegistrationManager();

            useBackgroundSyncPref.setChecked(gcmMgr.isBackgroundSyncAllowed());

            useBackgroundSyncPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object aNewValue) {
                    boolean newValue = (boolean) aNewValue;

                    if (newValue != gcmMgr.isBackgroundSyncAllowed()) {
                        gcmMgr.setBackgroundSyncAllowed(newValue);
                    }

                    return true;
                }
            });
        }

        final CheckBoxPreference useGaPref = (CheckBoxPreference) findPreference(appContext.getString(R.string.ga_use_settings));

        if (!GAHelper.isGAUseUpdatable()) {
            PreferenceCategory otherCategory = (PreferenceCategory) findPreference(getString(R.string.settings_other));
            otherCategory.removePreference(useGaPref);
        } else {
            useGaPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (null != getActivity()) {
                        Boolean useGA = GAHelper.useGA(getActivity());
                        boolean newGa = (boolean) newValue;

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
                    }

                    return true;
                }
            });
        }

        mUserSettingsCategory = (PreferenceCategory) findPreference(getString(R.string.settings_user_settings));
        mContactSettingsCategory = (PreferenceCategory) findPreference(getString(R.string.settings_contact));
        mPushersSettingsCategory = (PreferenceCategory) findPreference(getString(R.string.settings_notifications_targets));
        mPushersSettingsDivider = (PreferenceCategory) findPreference("notifications_targets_divider");
        mIgnoredUserSettingsCategory = (PreferenceCategory) findPreference(getString(R.string.settings_ignored_users));
        mIgnoredUserSettingsCategoryDivider = (PreferenceCategory) findPreference("ignore_users_divider");
        mDevicesListSettingsCategory = (PreferenceCategory) findPreference(getString(R.string.settings_devices_list));
        mDevicesListSettingsCategoryDivider = (PreferenceCategory) findPreference(getString(R.string.devices_divider));
        mCryptographyCategory = (PreferenceCategory) findPreference(getString(R.string.settings_cryptography));
        mCryptographyCategoryDivider = (PreferenceCategory) findPreference(getString(R.string.cryptography_divider));
        mLabsCategory = (PreferenceCategory) findPreference("labs");

        // preference to start the App info screen, to facilitate App permissions access
        Preference applicationInfoLInkPref = findPreference(APP_INFO_LINK_PREFERENCE_KEY);
        applicationInfoLInkPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (null != getActivity()) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Uri uri = Uri.fromParts("package", appContext.getPackageName(), null);
                    intent.setData(uri);
                    getActivity().getApplicationContext().startActivity(intent);
                }

                return true;
            }
        });

        // Contacts
        setContactsPreferences();

        // Home display
        setHomeDisplayPreferences();

        // background sync management
        mBackgroundSyncCategory = (PreferenceCategory) findPreference(getString(R.string.settings_background_sync));
        mStartBackgroundSyncOnBoot = (CheckBoxPreference) findPreference(getString(R.string.settings_start_background_sync_on_boot));
        mSyncRequestTimeoutPreference = (EditTextPreference) findPreference(getString(R.string.settings_set_sync_timeout));
        mSyncRequestDelayPreference = (EditTextPreference) findPreference(getString(R.string.settings_set_sync_delay));

        final CheckBoxPreference useCryptoPref = (CheckBoxPreference) findPreference(appContext.getString(R.string.room_settings_labs_end_to_end));
        final Preference cryptoIsEnabledPref = findPreference(appContext.getString(R.string.room_settings_labs_end_to_end_is_active));

        cryptoIsEnabledPref.setEnabled(false);

        if (!mSession.isCryptoEnabled()) {
            useCryptoPref.setChecked(false);
            mLabsCategory.removePreference(cryptoIsEnabledPref);
        } else {
            mLabsCategory.removePreference(useCryptoPref);
        }

        useCryptoPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValueAsVoid) {
                if (TextUtils.isEmpty(mSession.getCredentials().deviceId)) {
                    new AlertDialog.Builder(VectorApp.getCurrentActivity())
                            .setMessage(R.string.room_settings_labs_end_to_end_warnings)
                            .setPositiveButton(R.string.logout, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    CommonActivityUtils.logout(getActivity(), true);

                                }
                            })
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    useCryptoPref.setChecked(false);
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    dialog.dismiss();
                                    useCryptoPref.setChecked(false);
                                }
                            })
                            .create()
                            .show();
                } else {
                    boolean newValue = (boolean) newValueAsVoid;

                    if (mSession.isCryptoEnabled() != newValue) {
                        displayLoadingView();

                        mSession.enableCrypto(newValue, new ApiCallback<Void>() {
                            private void refresh() {
                                if (null != getActivity()) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            hideLoadingView();
                                            useCryptoPref.setChecked(mSession.isCryptoEnabled());

                                            if (mSession.isCryptoEnabled()) {
                                                mLabsCategory.removePreference(useCryptoPref);
                                                mLabsCategory.addPreference(cryptoIsEnabledPref);
                                            }
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onSuccess(Void info) {
                                useCryptoPref.setEnabled(false);
                                refresh();
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                useCryptoPref.setChecked(false);
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                useCryptoPref.setChecked(false);
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                useCryptoPref.setChecked(false);
                            }
                        });
                    }
                }

                return true;
            }
        });

        addButtons();
        refreshPushersList();
        refreshEmailsList();
        refreshPhoneNumbersList();
        refreshIgnoredUsersList();
        refreshDevicesList();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        if (null != view) {
            View listView = view.findViewById(android.R.id.list);

            if (null != listView) {
                listView.setPadding(0, 0, 0, 0);
            }
        }

        return view;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // if the user toggles the contacts book permission
        if (TextUtils.equals(key, ContactsManager.CONTACTS_BOOK_ACCESS_KEY)) {
            // reset the current snapshot
            ContactsManager.getInstance().clearSnapshot();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        final Context context = getActivity().getApplicationContext();

        if (mSession.isAlive()) {
            mSession.getDataHandler().removeListener(mEventsListener);
            Matrix.getInstance(context).removeNetworkEventListener(mNetworkListener);
        }

        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mSession.isAlive()) {
            final Context context = getActivity().getApplicationContext();

            mSession.getDataHandler().addListener(mEventsListener);

            Matrix.getInstance(context).addNetworkEventListener(mNetworkListener);

            mSession.getMyUser().refreshThirdPartyIdentifiers(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    // ensure that the activity still exists
                    if (null != getActivity()) {
                        // and the result is called in the right thread
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                refreshEmailsList();
                                refreshPhoneNumbersList();
                            }
                        });
                    }
                }
            });

            Matrix.getInstance(context).getSharedGCMRegistrationManager().refreshPushersList(Matrix.getInstance(context).getSessions(), new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    refreshPushersList();
                }
            });

            PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this);

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
     *
     * @param refresh true to refresh the display
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
        Context appContext = getActivity().getApplicationContext();

        PreferenceManager preferenceManager = getPreferenceManager();

        // refresh the avatar
        UserAvatarPreference avatarPreference = (UserAvatarPreference) preferenceManager.findPreference("matrixId");
        avatarPreference.refreshAvatar();
        avatarPreference.setEnabled(isConnected);

        // refresh the display name
        final EditTextPreference displaynamePref = (EditTextPreference) preferenceManager.findPreference(appContext.getString(R.string.settings_display_name));
        displaynamePref.setSummary(mSession.getMyUser().displayname);
        displaynamePref.setText(mSession.getMyUser().displayname);
        displaynamePref.setEnabled(isConnected);

        // change password
        final EditTextPreference changePasswordPref = (EditTextPreference) preferenceManager.findPreference(appContext.getString(R.string.settings_change_password));
        changePasswordPref.setEnabled(isConnected);

        // update the push rules
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext);

        BingRuleSet rules = mSession.getDataHandler().pushRules();

        GcmRegistrationManager gcmMgr = Matrix.getInstance(appContext).getSharedGCMRegistrationManager();

        for (String resourceText : mPushesRuleByResourceId.keySet()) {
            CheckBoxPreference switchPreference = (CheckBoxPreference) preferenceManager.findPreference(resourceText);

            if (null != switchPreference) {
                if (resourceText.equals(getString(R.string.settings_enable_this_device))) {
                    switchPreference.setChecked(gcmMgr.areDeviceNotificationsAllowed());
                } else if (resourceText.equals(getString(R.string.settings_turn_screen_on))) {
                    switchPreference.setChecked(gcmMgr.isScreenTurnedOn());
                } else {
                    switchPreference.setEnabled((null != rules) && isConnected);
                    switchPreference.setChecked(preferences.getBoolean(resourceText, false));
                }
            }
        }
    }

    private void addButtons() {
        // display the "add email" entry
        EditTextPreference addEmailPreference = new EditTextPreference(getActivity());
        addEmailPreference.setTitle(R.string.settings_add_email_address);
        addEmailPreference.setDialogTitle(R.string.settings_add_email_address);
        addEmailPreference.setKey(ADD_EMAIL_PREFERENCE_KEY);
        addEmailPreference.setIcon(R.drawable.ic_add_black);
        addEmailPreference.setOrder(100);
        addEmailPreference.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        addEmailPreference.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        final String email = (null == newValue) ? null : ((String) newValue).trim();

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

        // display the "add phone number" entry
        Preference addPhoneNumberPreference = new Preference(getActivity());
        addPhoneNumberPreference.setKey(ADD_PHONE_NUMBER_PREFERENCE_KEY);
        addPhoneNumberPreference.setIcon(R.drawable.ic_add_black);
        addPhoneNumberPreference.setTitle(R.string.settings_add_phone_number);
        addPhoneNumberPreference.setOrder(200);

        addPhoneNumberPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = PhoneNumberAdditionActivity.getIntent(getActivity(), mSession.getCredentials().userId);
                startActivityForResult(intent, REQUEST_NEW_PHONE_NUMBER);
                return true;
            }
        });

        mUserSettingsCategory.addPreference(addPhoneNumberPreference);
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

                final EditText oldPasswordText = (EditText) view.findViewById(R.id.change_password_old_pwd_text);
                final EditText newPasswordText = (EditText) view.findViewById(R.id.change_password_new_pwd_text);
                final EditText confirmNewPasswordText = (EditText) view.findViewById(R.id.change_password_confirm_new_pwd_text);

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
                                                    getString(textId),
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

                final Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
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

        if (fResourceText.equals(getString(R.string.settings_turn_screen_on))) {
            if (gcmMgr.isScreenTurnedOn() != newValue) {
                gcmMgr.setScreenTurnedOn(newValue);
            }
            return;
        }

        if (fResourceText.equals(getString(R.string.settings_enable_this_device))) {
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
                    editor.putString(getString(R.string.settings_display_name), value);
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
                if (CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO, getActivity())) {
                    Intent intent = new Intent(getActivity(), VectorMediasPickerActivity.class);
                    intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true);
                    startActivityForResult(intent, VectorUtils.TAKE_IMAGE);
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_E2E_FILE_REQUEST_CODE:
                    importKeys(data);
                    break;
                case REQUEST_NEW_PHONE_NUMBER:
                    refreshPhoneNumbersList();
                    break;
                case REQUEST_PHONEBOOK_COUNTRY:
                    onPhonebookCountryUpdate(data);
                    break;
                case VectorUtils.TAKE_IMAGE:
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
                    break;
            }
        }
    }

    /**
     * Refresh the known information about the account
     */
    private void refreshPreferences() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(getString(R.string.settings_display_name), mSession.getMyUser().displayname);
        editor.putString(getString(R.string.settings_version), VectorUtils.getApplicationVersion(getActivity()));

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
                            Log.e(LOG_TAG, "## refreshPreferences failed " + e.getMessage());
                        }
                    }
                }

                editor.putBoolean(resourceText, isEnabled);
            }
        }

        editor.commit();
    }

    /**
     * Display a dialog which asks confirmation for the deletion of a 3pid
     *
     * @param pid the 3pid to delete
     * @param preferenceSummary the displayed 3pid
     */
    private void displayDelete3PIDConfirmationDialog(final ThirdPartyIdentifier pid, final CharSequence preferenceSummary) {
        final String mediumFriendlyName = ThreePid.getMediumFriendlyName(pid.medium, getActivity()).toLowerCase();
        final String dialogMessage = getString(R.string.settings_delete_threepid_confirmation, mediumFriendlyName, preferenceSummary);

        new AlertDialog.Builder(VectorApp.getCurrentActivity())
                .setTitle(R.string.dialog_title_confirmation)
                .setMessage(dialogMessage)
                .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        displayLoadingView();

                        mSession.getMyUser().delete3Pid(pid, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                switch (pid.medium) {
                                    case ThreePid.MEDIUM_EMAIL:
                                        refreshEmailsList();
                                        break;
                                    case ThreePid.MEDIUM_MSISDN:
                                        refreshPhoneNumbersList();
                                        break;
                                }
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
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
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
        preferenceScreen.removePreference(mIgnoredUserSettingsCategoryDivider);
        mIgnoredUserSettingsCategory.removeAll();

        if (ignoredUsersList.size() > 0) {
            preferenceScreen.addPreference(mIgnoredUserSettingsCategoryDivider);
            preferenceScreen.addPreference(mIgnoredUserSettingsCategory);

            for (final String userId : ignoredUsersList) {
                VectorCustomActionEditTextPreference preference = new VectorCustomActionEditTextPreference(getActivity());

                preference.setTitle(userId);
                preference.setKey(IGNORED_USER_KEY_BASE + userId);

                preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        new AlertDialog.Builder(VectorApp.getCurrentActivity())
                                .setMessage(getString(R.string.settings_unignore_user, userId))
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
        final GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(getActivity()).getSharedGCMRegistrationManager();
        final List<Pusher> pushersList = new ArrayList<>(gcmRegistrationManager.mPushersList);

        if (pushersList.isEmpty()) {
            getPreferenceScreen().removePreference(mPushersSettingsCategory);
            getPreferenceScreen().removePreference(mPushersSettingsDivider);
            return;
        }

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

            for (final Pusher pusher : mDisplayedPushers) {
                if (null != pusher.lang) {
                    boolean isThisDeviceTarget = TextUtils.equals(gcmRegistrationManager.getGCMRegistrationToken(), pusher.pushkey);

                    VectorCustomActionEditTextPreference preference = new VectorCustomActionEditTextPreference(getActivity(), isThisDeviceTarget ? Typeface.BOLD : Typeface.NORMAL);
                    preference.setTitle(pusher.deviceDisplayName);
                    preference.setSummary(pusher.appDisplayName);
                    preference.setKey(PUSHER_PREFERENCE_KEY_BASE + index);
                    index++;
                    mPushersSettingsCategory.addPreference(preference);

                    // the user cannot remove the self device target
                    if (!isThisDeviceTarget) {
                        preference.setOnPreferenceLongClickListener(new VectorCustomActionEditTextPreference.OnPreferenceLongClickListener() {
                            @Override
                            public boolean onPreferenceLongClick(Preference preference) {
                                final String dialogMessage = getString(R.string.settings_delete_notification_targets_confirmation);
                                new AlertDialog.Builder(VectorApp.getCurrentActivity())
                                        .setTitle(R.string.dialog_title_confirmation)
                                        .setMessage(dialogMessage)
                                        .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();

                                                displayLoadingView();
                                                gcmRegistrationManager.unregister(mSession, pusher, new ApiCallback<Void>() {
                                                    @Override
                                                    public void onSuccess(Void info) {
                                                        refreshPushersList();
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
        final List<ThirdPartyIdentifier> currentEmail3PID = new ArrayList<>(mSession.getMyUser().getlinkedEmails());

        List<String> newEmailsList = new ArrayList<>();
        for (ThirdPartyIdentifier identifier : currentEmail3PID) {
            newEmailsList.add(identifier.address);
        }

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

            // add new emails list
            mDisplayedEmails = newEmailsList;

            int index = 0;
            final Preference addEmailBtn = mUserSettingsCategory.findPreference(ADD_EMAIL_PREFERENCE_KEY);

            // reported by GA
            if (null == addEmailBtn) {
                return;
            }
            
            int order = addEmailBtn.getOrder();

            for (final ThirdPartyIdentifier email3PID : currentEmail3PID) {
                VectorCustomActionEditTextPreference preference = new VectorCustomActionEditTextPreference(getActivity());

                preference.setTitle(getString(R.string.settings_email_address));
                preference.setSummary(email3PID.address);
                preference.setKey(EMAIL_PREFERENCE_KEY_BASE + index);
                preference.setOrder(order);

                preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        displayDelete3PIDConfirmationDialog(email3PID, preference.getSummary());
                        return true;
                    }
                });

                preference.setOnPreferenceLongClickListener(new VectorCustomActionEditTextPreference.OnPreferenceLongClickListener() {
                    @Override
                    public boolean onPreferenceLongClick(Preference preference) {
                        VectorUtils.copyToClipboard(getActivity(), email3PID.address);
                        return true;
                    }
                });

                mUserSettingsCategory.addPreference(preference);

                index++;
                order++;
            }

            addEmailBtn.setOrder(order);
        }
    }

    /**
     * A request has been processed.
     * Display a toast if there is a an error message
     *
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
     *
     * @param email the email to add.
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

        mSession.getMyUser().requestEmailValidationToken(pid, new ApiCallback<Void>() {
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
                if (TextUtils.equals(MatrixError.THREEPID_IN_USE, e.errcode)) {
                    onCommonDone(getString(R.string.account_email_already_used_error));
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

    /**
     * Show an email validation dialog to warn the user tho valid his email link.
     *
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
    // Phone number management
    //==============================================================================================================

    /**
     * Refresh phone number list
     */
    private void refreshPhoneNumbersList() {
        final List<ThirdPartyIdentifier> currentPhoneNumber3PID = new ArrayList<>(mSession.getMyUser().getlinkedPhoneNumbers());

        List<String> phoneNumberList = new ArrayList<>();
        for (ThirdPartyIdentifier identifier : currentPhoneNumber3PID) {
            phoneNumberList.add(identifier.address);
        }

        // check first if there is an update
        boolean isNewList = true;
        if ((null != mDisplayedPhoneNumber) && (phoneNumberList.size() == mDisplayedPhoneNumber.size())) {
            isNewList = !mDisplayedPhoneNumber.containsAll(phoneNumberList);
        }

        if (isNewList) {
            // remove the displayed one
            for (int index = 0; ; index++) {
                Preference preference = mUserSettingsCategory.findPreference(PHONE_NUMBER_PREFERENCE_KEY_BASE + index);

                if (null != preference) {
                    mUserSettingsCategory.removePreference(preference);
                } else {
                    break;
                }
            }

            // add new phone number list
            mDisplayedPhoneNumber = phoneNumberList;

            int index = 0;
            final Preference addPhoneBtn = mUserSettingsCategory.findPreference(ADD_PHONE_NUMBER_PREFERENCE_KEY);
            int order = addPhoneBtn.getOrder();

            for (final ThirdPartyIdentifier phoneNumber3PID : currentPhoneNumber3PID) {
                VectorCustomActionEditTextPreference preference = new VectorCustomActionEditTextPreference(getActivity());

                preference.setTitle(getString(R.string.settings_phone_number));
                String phoneNumberFormatted = phoneNumber3PID.address;
                try {
                    // Attempt to format phone number
                    final Phonenumber.PhoneNumber phoneNumber = PhoneNumberUtil.getInstance().parse("+" + phoneNumberFormatted, null);
                    phoneNumberFormatted = PhoneNumberUtil.getInstance().format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
                } catch (NumberParseException e) {
                    // Do nothing, we will display raw version
                }
                preference.setSummary(phoneNumberFormatted);
                preference.setKey(PHONE_NUMBER_PREFERENCE_KEY_BASE + index);
                preference.setOrder(order);

                preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        displayDelete3PIDConfirmationDialog(phoneNumber3PID, preference.getSummary());
                        return true;
                    }
                });

                preference.setOnPreferenceLongClickListener(new VectorCustomActionEditTextPreference.OnPreferenceLongClickListener() {
                    @Override
                    public boolean onPreferenceLongClick(Preference preference) {
                        VectorUtils.copyToClipboard(getActivity(), phoneNumber3PID.address);
                        return true;
                    }
                });

                index++;
                order++;
                mUserSettingsCategory.addPreference(preference);
            }

            addPhoneBtn.setOrder(order);
        }

    }

    //==============================================================================================================
    // contacts management
    //==============================================================================================================

    private void setContactsPreferences() {
        // Permission
        if (Build.VERSION.SDK_INT >= 23) {
            // on Android >= 23, use the system one
            mContactSettingsCategory.removePreference(findPreference(ContactsManager.CONTACTS_BOOK_ACCESS_KEY));
        }
        // Phonebook country
        mContactPhonebookCountryPreference = (VectorCustomActionEditTextPreference) findPreference(getString(R.string.settings_contacts_phonebook_country));
        mContactPhonebookCountryPreference.setSummary(PhoneNumberUtils.getHumanCountryCode(PhoneNumberUtils.getCountryCode(getActivity())));

        mContactPhonebookCountryPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = CountryPickerActivity.getIntent(getActivity(), true);
                startActivityForResult(intent, REQUEST_PHONEBOOK_COUNTRY);
                return true;
            }
        });
    }

    private void onPhonebookCountryUpdate(final Intent data) {
        if (data != null && data.hasExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_NAME)
                && data.hasExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE)) {
            final String countryCode = data.getStringExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE);
            if (!TextUtils.equals(countryCode, PhoneNumberUtils.getCountryCode(getActivity()))) {
                PhoneNumberUtils.setCountryCode(getActivity(), countryCode);
                mContactPhonebookCountryPreference.setSummary(data.getStringExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_NAME));
            }
        }
    }

    //==============================================================================================================
    // Home display management
    //==============================================================================================================

    /**
     * Set the home display preferences
     */
    private void setHomeDisplayPreferences() {
        // Missed notifications
        final String pinMissedNotificationsKey = getString(R.string.settings_pin_missed_notifications);
        final CheckBoxPreference pinMissedNotifications = (CheckBoxPreference) findPreference(pinMissedNotificationsKey);
        if (null != pinMissedNotifications) {
            pinMissedNotifications.setChecked(PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getBoolean(pinMissedNotificationsKey, false));
            pinMissedNotifications.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object aIsChecked) {
                    boolean isChecked = (boolean) aIsChecked;

                    PreferenceManager.getDefaultSharedPreferences(getActivity())
                            .edit()
                            .putBoolean(pinMissedNotificationsKey, isChecked)
                            .apply();
                    return true;
                }
            });
        }

        // Unread messages
        final String pinUnreadMessagesKey = getString(R.string.settings_pin_unread_messages);
        final CheckBoxPreference pinUnreadMessages = (CheckBoxPreference) findPreference(pinUnreadMessagesKey);
        if (null != pinUnreadMessages) {
            pinUnreadMessages.setChecked(PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getBoolean(pinUnreadMessagesKey, false));
            pinUnreadMessages.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object aIsChecked) {
                    boolean isChecked = (boolean) aIsChecked;

                    PreferenceManager.getDefaultSharedPreferences(getActivity())
                            .edit()
                            .putBoolean(pinUnreadMessagesKey, isChecked)
                            .apply();
                    return true;
                }
            });
        }
    }

    //==============================================================================================================
    // background sync management
    //==============================================================================================================

    /**
     * Convert a delay in seconds to string
     *
     * @param seconds the delay in seconds
     * @return the text
     */
    private String secondsToText(int seconds) {
        if (seconds > 1) {
            return seconds + " " + getString(R.string.settings_seconds);
        } else {
            return seconds + " " + getString(R.string.settings_second);
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

        final boolean startOnBoot = gcmmgr.isStartBackgroundSyncOnBoot();
        final int timeout = gcmmgr.getBackgroundSyncTimeOut() / 1000;
        final int delay = gcmmgr.getBackgroundSyncDelay() / 1000;

        // update the settings
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(getString(R.string.settings_start_background_sync_on_boot), startOnBoot);
        editor.putString(getString(R.string.settings_set_sync_timeout), timeout + "");
        editor.putString(getString(R.string.settings_set_sync_delay), delay + "");
        editor.commit();

        if (null != mStartBackgroundSyncOnBoot) {
            mStartBackgroundSyncOnBoot.setChecked(startOnBoot);

            mStartBackgroundSyncOnBoot.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean newStartOnBoot = (boolean) newValue;

                    if (newStartOnBoot != startOnBoot) {
                        gcmmgr.setStartBackgroundSyncOnBoot(newStartOnBoot);

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

        if (null != mSyncRequestTimeoutPreference) {
            mSyncRequestTimeoutPreference.setSummary(secondsToText(timeout));
            mSyncRequestTimeoutPreference.setText(timeout + "");

            mSyncRequestTimeoutPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int newTimeOut = timeout;

                    try {
                        newTimeOut = Integer.parseInt((String) newValue);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refreshBackgroundSyncPrefs : parseInt failed " + e.getMessage());
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
            mSyncRequestDelayPreference.setText(delay + "" );

            mSyncRequestDelayPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {

                    int newDelay = delay;

                    try {
                        newDelay = Integer.parseInt((String) newValue);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refreshBackgroundSyncPrefs : parseInt failed " + e.getMessage());
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

        // these settings are dedicated when a client does not support GCM
        if (gcmmgr.hasRegistrationToken()) {
            mBackgroundSyncCategory.removePreference(mStartBackgroundSyncOnBoot);
            mBackgroundSyncCategory.removePreference(mSyncRequestTimeoutPreference);
            mBackgroundSyncCategory.removePreference(mSyncRequestDelayPreference);
        }
    }

    //==============================================================================================================
    // Cryptography
    //==============================================================================================================

    private void removeCryptographyPreference() {
        PreferenceScreen preferenceScreen;
        if (null != (preferenceScreen = getPreferenceScreen())) {
            preferenceScreen.removePreference(mCryptographyCategory);
            preferenceScreen.removePreference(mCryptographyCategoryDivider);
        }
    }

    /**
     * Build the cryptography preference section.
     *
     * @param aMyDeviceInfo the device info
     */
    private void refreshCryptographyPreference(final DeviceInfo aMyDeviceInfo) {
        final String userId = mSession.getMyUserId();
        final String deviceId = mSession.getCredentials().deviceId;
        VectorCustomActionEditTextPreference cryptoInfoTextPreference;

        // device name
        if ((null != aMyDeviceInfo) && !TextUtils.isEmpty(aMyDeviceInfo.display_name)) {
            cryptoInfoTextPreference = (VectorCustomActionEditTextPreference) findPreference(getString(R.string.encryption_information_device_name));
            if (null != cryptoInfoTextPreference) {
                cryptoInfoTextPreference.setSummary(aMyDeviceInfo.display_name);

                cryptoInfoTextPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        displayDeviceRenameDialog(aMyDeviceInfo);
                        return true;
                    }
                });

                cryptoInfoTextPreference.setOnPreferenceLongClickListener(new VectorCustomActionEditTextPreference.OnPreferenceLongClickListener() {
                    @Override
                    public boolean onPreferenceLongClick(Preference preference) {
                        VectorUtils.copyToClipboard(getActivity(), aMyDeviceInfo.display_name);
                        return true;
                    }
                });
            }
        }

        // crypto section: device ID
        if (!TextUtils.isEmpty(deviceId)) {
            cryptoInfoTextPreference = (VectorCustomActionEditTextPreference) findPreference(getString(R.string.encryption_information_device_id));
            if (null != cryptoInfoTextPreference) {
                cryptoInfoTextPreference.setSummary(deviceId);

                cryptoInfoTextPreference.setOnPreferenceLongClickListener(new VectorCustomActionEditTextPreference.OnPreferenceLongClickListener() {
                    @Override
                    public boolean onPreferenceLongClick(Preference preference) {
                        VectorUtils.copyToClipboard(getActivity(), deviceId);
                        return true;
                    }
                });
            }

            VectorCustomActionEditTextPreference exportPref = (VectorCustomActionEditTextPreference) findPreference(getString(R.string.encryption_export_e2e_room_keys));

            exportPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    exportKeys();
                    return true;
                }
            });

            VectorCustomActionEditTextPreference importPref = (VectorCustomActionEditTextPreference) findPreference(getString(R.string.encryption_import_e2e_room_keys));

            importPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    importKeys();
                    return true;
                }
            });
        }

        // crypto section: device key (fingerprint)
        if (!TextUtils.isEmpty(deviceId) && !TextUtils.isEmpty(userId)) {
            mSession.getCrypto().getDeviceInfo(userId, deviceId, new SimpleApiCallback<MXDeviceInfo>() {
                @Override
                public void onSuccess(final MXDeviceInfo deviceInfo) {
                    if ((null != deviceInfo) && !TextUtils.isEmpty(deviceInfo.fingerprint()) && (null != getActivity())) {
                        VectorCustomActionEditTextPreference cryptoInfoTextPreference = (VectorCustomActionEditTextPreference) findPreference(getString(R.string.encryption_information_device_key));

                        if (null != cryptoInfoTextPreference) {
                            cryptoInfoTextPreference.setSummary(deviceInfo.fingerprint());

                            cryptoInfoTextPreference.setOnPreferenceLongClickListener(new VectorCustomActionEditTextPreference.OnPreferenceLongClickListener() {
                                @Override
                                public boolean onPreferenceLongClick(Preference preference) {
                                    VectorUtils.copyToClipboard(getActivity(), deviceInfo.fingerprint());
                                    return true;
                                }
                            });
                        }
                    }
                }
            });
        }

        // encrypt to unverified devices
        final CheckBoxPreference sendToUnverifiedDevicesPref = (CheckBoxPreference)findPreference(getString(R.string.encryption_never_send_to_unverified_devices_title));

        if (null != sendToUnverifiedDevicesPref) {
            sendToUnverifiedDevicesPref.setChecked(false);

            mSession.getCrypto().getGlobalBlacklistUnverifiedDevices(new SimpleApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean status) {
                    sendToUnverifiedDevicesPref.setChecked(status);
                }
            });

            sendToUnverifiedDevicesPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    mSession.getCrypto().getGlobalBlacklistUnverifiedDevices(new SimpleApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean status) {
                            if (sendToUnverifiedDevicesPref.isChecked() != status) {
                                mSession.getCrypto().setGlobalBlacklistUnverifiedDevices(sendToUnverifiedDevicesPref.isChecked(), new SimpleApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {

                                    }
                                });
                            }
                        }
                    });

                    return true;
                }
            });
        }
    }

    //==============================================================================================================
    // devices list
    //==============================================================================================================

    private void removeDevicesPreference() {
        PreferenceScreen preferenceScreen;
        if (null != (preferenceScreen = getPreferenceScreen())) {
            preferenceScreen.removePreference(mDevicesListSettingsCategory);
            preferenceScreen.removePreference(mDevicesListSettingsCategoryDivider);
        }
    }

    /**
     * Force the refresh of the devices list.<br>
     * The devices list is the list of the devices where the user as looged in.
     * It can be any mobile device, as any browser.
     */
    private void refreshDevicesList() {
        if ((null != mSession) && (mSession.isCryptoEnabled()) && (!TextUtils.isEmpty(mSession.getCredentials().deviceId))) {
            // display a spinner while loading the devices list
            if (0 == mDevicesListSettingsCategory.getPreferenceCount()) {
                ProgressBarPreference preference = new ProgressBarPreference(getActivity());
                mDevicesListSettingsCategory.addPreference(preference);
            }

            mSession.getDevicesList(new ApiCallback<DevicesListResponse>() {
                @Override
                public void onSuccess(DevicesListResponse info) {
                    if (0 == info.devices.size()) {
                        removeDevicesPreference();
                    } else {
                        buildDevicesSettings(info.devices);
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    removeDevicesPreference();
                    onCommonDone(e.getMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    removeDevicesPreference();
                    onCommonDone(e.getMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    removeDevicesPreference();
                    onCommonDone(e.getMessage());
                }
            });
        } else {
            removeDevicesPreference();
            removeCryptographyPreference();
        }
    }

    /**
     * Build the devices portion of the settings.<br>
     * Each row correspond to a device ID and its corresponding device name. Clicking on the row
     * display a dialog containing: the device ID, the device name and the "last seen" information.
     *
     * @param aDeviceInfoList the list of the devices
     */
    private void buildDevicesSettings(List<DeviceInfo> aDeviceInfoList) {
        VectorCustomActionEditTextPreference preference;
        int typeFaceHighlight;
        boolean isNewList = true;
        String myDeviceId = mSession.getCredentials().deviceId;

        if ((null != mDevicesNameList) && (aDeviceInfoList.size() == mDevicesNameList.size())) {
            isNewList = !mDevicesNameList.containsAll(aDeviceInfoList);
        }

        if (isNewList) {
            int prefIndex = 0;
            mDevicesNameList = aDeviceInfoList;

            // sort before display: most recent first
            DeviceInfo.sortByLastSeen(mDevicesNameList);

            // start from scratch: remove the displayed ones
            mDevicesListSettingsCategory.removeAll();

            for (DeviceInfo deviceInfo : mDevicesNameList) {
                // set bold to distinguish current device ID
                if ((null != myDeviceId) && myDeviceId.equals(deviceInfo.device_id)) {
                    mMyDeviceInfo = deviceInfo;
                    typeFaceHighlight = Typeface.BOLD;
                } else {
                    typeFaceHighlight = Typeface.NORMAL;
                }

                // add the edit text preference
                preference = new VectorCustomActionEditTextPreference(getActivity(), typeFaceHighlight);

                if ((null == deviceInfo.device_id) && (null == deviceInfo.display_name)) {
                    continue;
                } else {
                    if (null != deviceInfo.device_id) {
                        preference.setTitle(deviceInfo.device_id);
                    }

                    // display name parameter can be null (new JSON API)
                    if (null != deviceInfo.display_name) {
                        preference.setSummary(deviceInfo.display_name);
                    }
                }

                preference.setKey(DEVICES_PREFERENCE_KEY_BASE + prefIndex);
                prefIndex++;

                // onClick handler: display device details dialog
                final DeviceInfo fDeviceInfo = deviceInfo;
                preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        displayDeviceDetailsDialog(fDeviceInfo);
                        return true;
                    }
                });

                mDevicesListSettingsCategory.addPreference(preference);
            }

            refreshCryptographyPreference(mMyDeviceInfo);
        }
    }

    /**
     * Display a dialog containing the device ID, the device name and the "last seen" information.<>
     * This dialog allow to delete the corresponding device (see {@link #displayDeviceDeletionDialog(DeviceInfo)})
     *
     * @param aDeviceInfo the device information
     */
    private void displayDeviceDetailsDialog(DeviceInfo aDeviceInfo) {
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View layout = inflater.inflate(R.layout.devices_details_settings, null);

        if (null != aDeviceInfo) {
            //device ID
            TextView textView = (TextView) layout.findViewById(R.id.device_id);
            textView.setText(aDeviceInfo.device_id);

            // device name
            textView = (TextView) layout.findViewById(R.id.device_name);
            String displayName = (TextUtils.isEmpty(aDeviceInfo.display_name)) ? LABEL_UNAVAILABLE_DATA : aDeviceInfo.display_name;
            textView.setText(displayName);

            // last seen info
            textView = (TextView) layout.findViewById(R.id.device_last_seen);
            if (!TextUtils.isEmpty(aDeviceInfo.last_seen_ip)) {
                String lastSeenIp = aDeviceInfo.last_seen_ip;
                String lastSeenTime = LABEL_UNAVAILABLE_DATA;

                if (null != getActivity()) {
                    SimpleDateFormat dateFormatTime = new SimpleDateFormat(getString(R.string.devices_details_time_format));
                    String time = dateFormatTime.format(new Date(aDeviceInfo.last_seen_ts));

                    DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());
                    lastSeenTime = dateFormat.format(new Date(aDeviceInfo.last_seen_ts)) + ", " + time;
                }
                String lastSeenInfo = this.getString(R.string.devices_details_last_seen_format, lastSeenIp, lastSeenTime);
                textView.setText(lastSeenInfo);
            } else {
                // hide last time seen section
                layout.findViewById(R.id.device_last_seen_title).setVisibility(View.GONE);
                textView.setVisibility(View.GONE);
            }

            // title & icon
            builder.setTitle(R.string.devices_details_dialog_title);
            builder.setIcon(android.R.drawable.ic_dialog_info);
            builder.setView(layout);

            final DeviceInfo fDeviceInfo = aDeviceInfo;

            builder.setPositiveButton(R.string.rename, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    displayDeviceRenameDialog(fDeviceInfo);
                }
            });

            // disable the deletion for our own device
            if (!TextUtils.equals(mSession.getCrypto().getMyDevice().deviceId, fDeviceInfo.device_id)) {
                builder.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        displayDeviceDeletionDialog(fDeviceInfo);
                    }
                });
            }

            builder.setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                        dialog.cancel();
                        return true;
                    }
                    return false;
                }
            });

            builder.create().show();
        } else {
            Log.e(LOG_TAG, "## displayDeviceDetailsDialog(): sanity check failure");
            if (null != getActivity())
                CommonActivityUtils.displayToast(getActivity().getApplicationContext(), "DeviceDetailsDialog cannot be displayed.\nBad input parameters.");
        }
    }

    /**
     * Display an alert dialog to rename a device
     *
     * @param aDeviceInfoToRename device info
     */
    private void displayDeviceRenameDialog(final DeviceInfo aDeviceInfoToRename) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.devices_details_device_name);

        final EditText input = new EditText(getActivity());
        input.setText(aDeviceInfoToRename.display_name);
        builder.setView(input);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                displayLoadingView();

                mSession.setDeviceName(aDeviceInfoToRename.device_id, input.getText().toString(), new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        // search which preference is updated
                        int count = mDevicesListSettingsCategory.getPreferenceCount();

                        for (int i = 0; i < count; i++) {
                            VectorCustomActionEditTextPreference pref = (VectorCustomActionEditTextPreference) mDevicesListSettingsCategory.getPreference(i);

                            if (TextUtils.equals(aDeviceInfoToRename.device_id, pref.getTitle())) {
                                pref.setSummary(input.getText());
                            }
                        }

                        // detect if the updated device is the current account one
                        Preference pref = findPreference(getString(R.string.encryption_information_device_id));
                        if (TextUtils.equals(pref.getSummary(), aDeviceInfoToRename.device_id)) {
                            (findPreference(getString(R.string.encryption_information_device_name))).setSummary(input.getText());
                        }

                        hideLoadingView();
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
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    /**
     * Try to delete a device.
     * @param deviceId the device id
     */
    private void deleteDevice(final String deviceId) {
        displayLoadingView();
        mSession.deleteDevice(deviceId, mAccountPassword, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                hideLoadingView();
                refreshDevicesList(); // force settings update
            }

            private void onError(String message) {
                mAccountPassword = null;
                onCommonDone(message);
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
    /**
     * Display a delete confirmation dialog to remove a device.<br>
     * The user is invited to enter his password to confirm the deletion.
     *
     * @param aDeviceInfoToDelete device info
     */
    private void displayDeviceDeletionDialog(final DeviceInfo aDeviceInfoToDelete) {
        if ((null != aDeviceInfoToDelete) && (null != aDeviceInfoToDelete.device_id)) {
            if (!TextUtils.isEmpty(mAccountPassword)) {
                deleteDevice(aDeviceInfoToDelete.device_id);
            } else {
                android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(getActivity());
                LayoutInflater inflater = getActivity().getLayoutInflater();
                View layout = inflater.inflate(R.layout.devices_settings_delete, null);


                final EditText passwordEditText = (EditText) layout.findViewById(R.id.delete_password);
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setTitle(R.string.devices_delete_dialog_title);
                builder.setView(layout);

                builder.setPositiveButton(R.string.devices_delete_submit_button_label, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (null != mSession) {
                            if (TextUtils.isEmpty(passwordEditText.toString())) {
                                CommonActivityUtils.displayToast(VectorSettingsPreferencesFragment.this.getActivity().getApplicationContext(), "Password missing..");
                                return;
                            }
                            mAccountPassword = passwordEditText.getText().toString();
                            deleteDevice(aDeviceInfoToDelete.device_id);
                        }
                    }
                });

                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        hideLoadingView();
                    }
                });

                builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                            dialog.cancel();
                            hideLoadingView();
                            return true;
                        }
                        return false;
                    }
                });

                builder.create().show();
            }
        } else {
            Log.e(LOG_TAG, "## displayDeviceDeletionDialog(): sanity check failure");
        }
    }

    /**
     * Manage the e2e keys export.
     */
    private void exportKeys() {
        View dialogLayout = getActivity().getLayoutInflater().inflate(R.layout.dialog_export_e2e_keys, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
        dialog.setTitle(R.string.encryption_export_room_keys);
        dialog.setView(dialogLayout);

        final TextInputEditText passPhrase1EditText = (TextInputEditText) dialogLayout.findViewById(R.id.dialog_e2e_keys_passphrase_edit_text);
        final TextInputEditText passPhrase2EditText = (TextInputEditText) dialogLayout.findViewById(R.id.dialog_e2e_keys_confirm_passphrase_edit_text);
        final Button exportButton = (Button) dialogLayout.findViewById(R.id.dialog_e2e_keys_export_button);
        final TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                exportButton.setEnabled(!TextUtils.isEmpty(passPhrase1EditText.getText()) && TextUtils.equals(passPhrase1EditText.getText(), passPhrase2EditText.getText()));
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };

        passPhrase1EditText.addTextChangedListener(textWatcher);
        passPhrase2EditText.addTextChangedListener(textWatcher);

        exportButton.setEnabled(false);

        final AlertDialog exportDialog = dialog.show();

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayLoadingView();

                CommonActivityUtils.exportKeys(mSession, passPhrase1EditText.getText().toString(), new ApiCallback<String>() {
                    private void onDone(String message) {
                        hideLoadingView();
                        Toast.makeText(VectorApp.getInstance().getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onSuccess(String filename) {
                        onDone(filename);
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onDone(e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        onDone(e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onDone(e.getLocalizedMessage());
                    }
                });

                exportDialog.dismiss();
            }
        });
    }

    /**
     * Manage the e2e keys import.
     */
    @SuppressLint("NewApi")
    private void importKeys() {
        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        }
        fileIntent.setType("*/*");
        startActivityForResult(fileIntent, REQUEST_E2E_FILE_REQUEST_CODE);
    }

    /**
     * Manage the e2e keys import.
     *
     * @param intent the intent result
     */
    private void importKeys(Intent intent) {
        // sanity check
        if (null == intent) {
            return;
        }

        ArrayList<SharedDataItem> sharedDataItems = new ArrayList<>(SharedDataItem.listSharedDataItems(intent));

        if (sharedDataItems.size() > 0) {
            final SharedDataItem sharedDataItem = sharedDataItems.get(0);
            View dialogLayout = getActivity().getLayoutInflater().inflate(R.layout.dialog_import_e2e_keys, null);
            AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
            dialog.setTitle(R.string.encryption_import_room_keys);
            dialog.setView(dialogLayout);

            final TextInputEditText passPhraseEditText = (TextInputEditText) dialogLayout.findViewById(R.id.dialog_e2e_keys_passphrase_edit_text);
            final Button importButton = (Button) dialogLayout.findViewById(R.id.dialog_e2e_keys_import_button);

            passPhraseEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    importButton.setEnabled(!TextUtils.isEmpty(passPhraseEditText.getText()));
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

            importButton.setEnabled(false);

            final AlertDialog importDialog = dialog.show();
            final Context appContext = getActivity().getApplicationContext();

            importButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String password = passPhraseEditText.getText().toString();
                    final ResourceUtils.Resource resource = ResourceUtils.openResource(appContext, sharedDataItem.getUri(), sharedDataItem.getMimeType(appContext));

                    byte[] data;

                    try {
                        data = new byte[resource.mContentStream.available()];
                        resource.mContentStream.read(data);
                        resource.mContentStream.close();
                    } catch (Exception e) {
                        try {
                            resource.mContentStream.close();
                        } catch (Exception e2) {
                            Log.e(LOG_TAG, "## importKeys() : " + e2.getMessage());
                        }
                        Toast.makeText(appContext, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    displayLoadingView();

                    mSession.getCrypto().importRoomKeys(data, password, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            hideLoadingView();
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            Toast.makeText(appContext, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                            hideLoadingView();
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            Toast.makeText(appContext, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                            hideLoadingView();
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            Toast.makeText(appContext, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                            hideLoadingView();
                        }
                    });

                    importDialog.dismiss();
                }
            });
        }
    }
}
