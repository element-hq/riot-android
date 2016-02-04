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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.HashMap;

import im.vector.Matrix;
import im.vector.R;
import im.vector.fragments.VectorSettingsPreferencesFragment;
import im.vector.util.VectorUtils;

public class VectorSettingsActivity extends MXCActionBarActivity {

    // rule Id <-> preference name
    private static HashMap<String, String> mPushesRuleByResourceId = null;

    // session
    private MXSession mSession;

    // the UI items
    private VectorSettingsPreferencesFragment mFragment;

    MXEventListener mEventsListener = new MXEventListener() {
        private void refresh() {
            refreshPreferences();
            refreshPreferencesDisplay();
        }

        @Override
        public void onBingRulesUpdate() {
            refresh();
        }

        @Override
        public void onAccountInfoUpdate(MyUser myUser) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mSession = getSession(intent);

        if (null == mSession) {
            mSession = Matrix.getInstance(VectorSettingsActivity.this).getDefaultSession();
        }

        if (mSession == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_vector_settings);

        refreshPreferences();

        // display the fragment
        mFragment = VectorSettingsPreferencesFragment.newInstance(mSession.getMyUser().userId, mPushesRuleByResourceId);
        getFragmentManager().beginTransaction().replace(R.id.vector_settings_page, mFragment).commit();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mSession.isActive()) {
            mSession.getDataHandler().removeListener(mEventsListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSession.isActive()) {
            mSession.getDataHandler().addListener(mEventsListener);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // pass the result to the fragment
        mFragment.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Refresh the fragment display.
     */
    private void refreshPreferencesDisplay() {
        if (null != mFragment) {
            mFragment.refreshDisplay();
        }
    }

    /**
     * Refresh the known information about the account
     */
    private void refreshPreferences() {
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
}