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
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
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
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;

import java.util.HashMap;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.preference.UserAvatarPreference;
import im.vector.util.ResourceUtils;
import im.vector.util.VectorUtils;

public class VectorSettingsPreferencesFragment extends PreferenceFragment {
    // arguments indexes
    private static final String ARG_MATRIX_ID = "VectorSettingsPreferencesFragment.ARG_MATRIX_ID";

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

        // push rules
        for(String resourceText : mPushesRuleByResourceId.keySet()) {
            final SwitchPreference switchPreference = (SwitchPreference)preferenceManager.findPreference(resourceText);

            if (null != switchPreference) {
                final String fResourceText = resourceText;

                switchPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        onPushRuleClick(fResourceText);
                        return false;
                    }
                });
            }
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

        refreshDisplay();
    }


    @Override
    public void onPause() {
        super.onPause();

        if (mSession.isActive()) {
            mSession.getDataHandler().removeListener(mEventsListener);
        }

        Matrix.getInstance(getActivity()).removeNetworkEventListener(mNetworkListener);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mSession.isActive()) {
            mSession.getDataHandler().addListener(mEventsListener);
        }

        Matrix.getInstance(getActivity()).addNetworkEventListener(mNetworkListener);

        //
        refreshDisplay();
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
    private void hideLoadingView(Boolean refresh) {
        mLoadingView.setVisibility(View.GONE);

        if (refresh) {
            refreshDisplay();
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
    private void onPushRuleClick(final String fResourceText) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final String ruleId = mPushesRuleByResourceId.get(fResourceText);
                BingRule rule = mSession.getDataHandler().pushRules().findDefaultRule(ruleId);

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
        });
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
            if (null != data) {
                Uri thumbnailUri = null;

                if (null != data) {
                    ClipData clipData = null;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        clipData = data.getClipData();
                    }

                    // multiple data
                    if (null != clipData) {
                        if (clipData.getItemCount() > 0) {
                            thumbnailUri = clipData.getItemAt(0).getUri();
                        }
                    } else if (null != data.getData()) {
                        thumbnailUri = data.getData();
                    }
                }

                Bitmap thumbnail = null;

                if (null != thumbnailUri) {
                    thumbnail = VectorUtils.getBitmapFromuri(getActivity(), thumbnailUri);
                }

                String thumbnailURL = mSession.getMediasCache().saveBitmap(thumbnail, null);

                if (null != thumbnailURL) {
                    displayLoadingView();

                    ResourceUtils.Resource resource = ResourceUtils.openResource(getActivity(), Uri.parse(thumbnailURL));

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
    }
}
