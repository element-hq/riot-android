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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ThreePid;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.preference.UserAvatarPreference;
import im.vector.preference.VectorCustomActionEditTextPreference;
import im.vector.util.ResourceUtils;
import im.vector.util.VectorUtils;

public class VectorSettingsPreferencesFragment extends PreferenceFragment {
    // arguments indexes
    private static final String ARG_MATRIX_ID = "VectorSettingsPreferencesFragment.ARG_MATRIX_ID";

    private static final String EMAIL_PREREFENCE_KEY_BASE = "EMAIL_PREREFENCE_KEY_BASE";
    private static final String ADD_EMAIL_PREFERENCE_KEY = "ADD_EMAIL_PREFERENCE_KEY";

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

        // define the layout
        addPreferencesFromResource(R.xml.vector_settings_preferences);

        if (null == mPushesRuleByResourceId) {
            mPushesRuleByResourceId = new HashMap<String, String>();

            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_enable_all_notif), BingRule.RULE_ID_DISABLE_ALL);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_messages_my_display_name), BingRule.RULE_ID_CONTAIN_DISPLAY_NAME);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_messages_my_user_name), BingRule.RULE_ID_CONTAIN_USER_NAME);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_messages_sent_to_me), BingRule.RULE_ID_ONE_TO_ONE_ROOM);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_invited_to_room), BingRule.RULE_ID_INVITE_ME);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_call_invitations), BingRule.RULE_ID_CALL);
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

        // terms & conditions
        EditTextPreference termConditionsPreference = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_term_conditions));

        if (null != termConditionsPreference) {
            termConditionsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    VectorUtils.displayLicense(getActivity());
                    return false;
                }
            });
        }

        // terms & conditions
        EditTextPreference privacyPreference = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_privacy_policy));

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
                final String fResourceText = resourceText;

                switchPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValueAsVoid) {
                        // on some old android APIs,
                        // the callback is called even if there is no user interaction
                        // so the value will be checked to ensure there is really no update.
                        onPushRuleClick(fResourceText, (boolean)newValueAsVoid);
                        return true;
                    }
                });
            }
        }

        mUserSettingsCategory = (PreferenceCategory)getPreferenceManager().findPreference(getResources().getString(R.string.settings_user_settings));
        refreshPreferences();
        refreshEmailsList();
        refreshDisplay();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mSession.isActive()) {
            mSession.getDataHandler().removeListener(mEventsListener);

            Matrix.getInstance(getActivity()).removeNetworkEventListener(mNetworkListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mSession.isActive()) {
            mSession.getDataHandler().addListener(mEventsListener);

            Matrix.getInstance(getActivity()).addNetworkEventListener(mNetworkListener);

            mSession.getMyUser().refreshLinkedEmails(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    refreshEmailsList();
                }
            });

            // ensure that
            refreshPreferences();
            refreshDisplay();
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

        for(String resourceText : mPushesRuleByResourceId.keySet()) {
            SwitchPreference switchPreference = (SwitchPreference) preferenceManager.findPreference(resourceText);

            if (null != switchPreference) {
                switchPreference.setEnabled((null != rules) && isConnected);
                switchPreference.setChecked(preferences.getBoolean(resourceText, false));
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
                        InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);

                        String oldPwd = oldPasswordText.getText().toString().trim();
                        String newPwd = newPasswordText.getText().toString().trim();

                        displayLoadingView();

                        mSession.updatePassword(oldPwd, newPwd, new ApiCallback<Void>() {
                            private void onDone(String message) {
                                hideLoadingView();

                                Toast.makeText(getActivity(),
                                        message,
                                        Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onSuccess(Void info) {
                                onDone(getActivity().getResources().getString(R.string.settings_password_updated));
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                onDone(getActivity().getResources().getString(R.string.settings_fail_to_update_password));
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                onDone(getActivity().getResources().getString(R.string.settings_fail_to_update_password));
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                onDone(getActivity().getResources().getString(R.string.settings_fail_to_update_password));
                            }
                        });
                    }
                });

                // Setting Negative "NO" Button
                alertDialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
                    }
                });

                AlertDialog dialog = alertDialog.show();

                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
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
        final String ruleId = mPushesRuleByResourceId.get(fResourceText);
        BingRule rule = mSession.getDataHandler().pushRules().findDefaultRule(ruleId);

        // check if there is an update
        boolean curValue = ((null != rule) && rule.isEnabled);

        if (TextUtils.equals(ruleId, BingRule.RULE_ID_DISABLE_ALL)) {
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
                    hideLoadingView();

                    BingRule rule = mSession.getDataHandler().pushRules().findDefaultRule(ruleId);
                    Boolean isEnabled = ((null != rule) && rule.isEnabled);

                    if (TextUtils.equals(ruleId, BingRule.RULE_ID_DISABLE_ALL)) {
                        isEnabled = !isEnabled;
                    }

                    final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean(fResourceText, isEnabled);
                    editor.commit();
                    hideLoadingView(true);
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
    private void onDisplayNameClick(String value) {
        if (!TextUtils.equals(mSession.getMyUser().displayname, value)) {
            displayLoadingView();

            mSession.getMyUser().updateDisplayName(value, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    hideLoadingView(true);
                }

                @Override
                public void onNetworkError(Exception e) {
                    hideLoadingView();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    hideLoadingView();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    hideLoadingView();
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
                intent.putExtra(VectorMediasPickerActivity.EXTRA_SINGLE_IMAGE_MODE, "");
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

                ResourceUtils.Resource resource = ResourceUtils.openResource(getActivity(), thumbnailUri);

                mSession.getContentManager().uploadContent(resource.contentStream, null, resource.mimeType, null, new ContentManager.UploadCallback() {
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
                                if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                                    mSession.getMyUser().updateAvatarUrl(uploadResponse.contentUri, new ApiCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void info) {
                                            hideLoadingView(true);
                                        }

                                        @Override
                                        public void onNetworkError(Exception e) {
                                            hideLoadingView(false);
                                        }

                                        @Override
                                        public void onMatrixError(MatrixError e) {
                                            hideLoadingView(false);
                                        }

                                        @Override
                                        public void onUnexpectedError(Exception e) {
                                            hideLoadingView(false);
                                        }
                                    });
                                } else {
                                    hideLoadingView(false);
                                }
                            }
                        });
                    }
                });
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
                Boolean isEnabled = ((null != rule) && rule.isEnabled);

                if (TextUtils.equals(ruleId, BingRule.RULE_ID_DISABLE_ALL)) {
                    isEnabled = !isEnabled;
                }

                editor.putBoolean(resourceText, isEnabled);
            }
        }

        editor.commit();
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
            for(int index = 0; ; index++) {
                Preference preference = mUserSettingsCategory.findPreference(EMAIL_PREREFENCE_KEY_BASE + index);

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

            for(String email : mDisplayedEmails) {
                VectorCustomActionEditTextPreference preference = new VectorCustomActionEditTextPreference(getActivity());

                preference.setTitle(getResources().getString(R.string.settings_email_address));
                preference.setSummary(email);
                preference.setKey(EMAIL_PREREFENCE_KEY_BASE + index);
                index++;
                mUserSettingsCategory.addPreference(preference);
            }

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
     * The emila binding fails : display a dedicated error message.
     * @param errorMessage the error message
     */
    private void onEmailBindingError(String errorMessage) {
        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
        hideLoadingView();
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
                showEmailValidationDialog(pid);
            }

            @Override
            public void onNetworkError(Exception e) {
                onEmailBindingError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onEmailBindingError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onEmailBindingError(e.getLocalizedMessage());
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
                        hideLoadingView();
                        refreshEmailsList();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onEmailBindingError(e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (TextUtils.equals(e.errcode, MatrixError.THREEPID_AUTH_FAILED)) {
                            hideLoadingView();
                            Toast.makeText(getActivity(), getString(R.string.account_email_validation_error), Toast.LENGTH_SHORT).show();
                        } else {
                            onEmailBindingError(e.getLocalizedMessage());
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onEmailBindingError(e.getLocalizedMessage());
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
}
