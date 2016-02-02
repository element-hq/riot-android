/* 
 * Copyright 2014 OpenMarket Ltd
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
package im.vector.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;

import java.util.HashMap;

import im.vector.Matrix;
import im.vector.R;
import im.vector.pref.AvatarPreference;
import im.vector.util.VectorUtils;

public class VectorSettingsActivity extends MXCActionBarActivity {

    private static HashMap<String, String> mPushesRuleByResourceId = null;
    private MXSession mSession;
    public View mLoadingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_vector_settings);

        // get the loading view
        mLoadingView = findViewById(R.id.vector_settings_spinner_views);

        // TODO the matrix id must be passed in parameter
        mSession = Matrix.getInstance(VectorSettingsActivity.this).getDefaultSession();

        MyPreferenceFragment fragment = new MyPreferenceFragment();
        fragment.mSession = mSession;

        initPreferences();

        getFragmentManager().beginTransaction().replace(R.id.vector_settings_page, fragment).commit();
    }

    private void initPreferences() {
        if (null == mPushesRuleByResourceId) {
            mPushesRuleByResourceId = new HashMap<String, String>();

            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_enable_all_notif), BingRule.RULE_ID_DISABLE_ALL);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_messages_my_display_name), BingRule.RULE_ID_CONTAIN_DISPLAY_NAME);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_messages_my_user_name), BingRule.RULE_ID_CONTAIN_USER_NAME);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_messages_sent_to_me), BingRule.RULE_ID_ONE_TO_ONE_ROOM);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_invited_to_room), BingRule.RULE_ID_INVITE_ME);
            mPushesRuleByResourceId.put(getResources().getString(R.string.settings_call_invitations), BingRule.RULE_ID_CALL);
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(this.getResources().getString(R.string.settings_display_name), mSession.getMyUser().displayname);
        editor.putString(this.getResources().getString(R.string.settings_version), VectorUtils.getApplicationVersion(this));

        BingRuleSet mBingRuleSet = mSession.getDataHandler().pushRules();

        for(String resourceText : mPushesRuleByResourceId.keySet()) {
            String ruleId = mPushesRuleByResourceId.get(resourceText);

            BingRule rule = mBingRuleSet.findDefaultRule(ruleId);
            Boolean isEnabled = ((null != rule) && rule.isEnabled);

            // this rule is the opposite of the bingrules
            if (TextUtils.equals(resourceText, getResources().getString(R.string.settings_enable_all_notif))) {
                isEnabled = !isEnabled;
            }

            editor.putBoolean(resourceText, isEnabled);
        }

        editor.commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragment
    {
        public MXSession mSession;
        private View mLoadingView;

        private void displayLoadingView() {
            mLoadingView.setVisibility(View.VISIBLE);
        }

        private void hideLoadingView() {
            mLoadingView.setVisibility(View.GONE);
        }

        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.vector_settings_preferences);

            mLoadingView = ((VectorSettingsActivity)getActivity()).mLoadingView;

            PreferenceManager preferenceManager = getPreferenceManager();

            AvatarPreference avatarPreference = (AvatarPreference)preferenceManager.findPreference("matrixId");
            avatarPreference.setSession(mSession);
            avatarPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    /*
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI.getPath());
                    startActivityForResult(intent, 1);*/

                    return true;
                }
            });

            final EditTextPreference displaynamePref = (EditTextPreference)preferenceManager.findPreference(getActivity().getResources().getString(R.string.settings_display_name));
            displaynamePref.setSummary(mSession.getMyUser().displayname);
            displaynamePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final String value = (String)newValue;

                    if (!TextUtils.equals(mSession.getMyUser().displayname, value)) {
                        displayLoadingView();

                        mSession.getMyUser().updateDisplayName(value, new ApiCallback<Void>() {
                            @Override
                                    public void onSuccess(Void info) {
                                        displaynamePref.setSummary(value);
                                        hideLoadingView();
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
                    return true;
                }
            });
        }
    }
}