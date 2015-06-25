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
package org.matrix.console.activity;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.rest.model.bingrules.ContentRule;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.console.Matrix;
import org.matrix.console.R;
import org.matrix.console.adapters.NotificationsRulesAdapter;

import java.util.HashMap;

public class NotificationSettingsActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "NotificationSettings";
    public static final String EXTRA_MATRIX_ID = "org.matrix.console.NotificationSettingsActivity.EXTRA_MATRIX_ID";

    private Button mDisableAllButton = null;
    private TextView mDisableAllTextView = null;

    private NotificationsRulesAdapter mPerWordAdapter;
    private NotificationsRulesAdapter mPerRoomAdapter;
    private NotificationsRulesAdapter mPerSenderAdapter;

    private ListView mPerWordList = null;
    private ListView mPerRoomList = null;
    private ListView mPerSenderList = null;

    private ImageView mContainUserNameImageView = null;
    private ImageView mContainMyDisplayNameImageView = null;
    private ImageView mJustSendToMeImageView = null;
    private ImageView mInviteToNewRoomImageView = null;
    private ImageView mPeopleJoinLeaveImageView = null;
    private ImageView mReceiveACallImageView = null;
    private ImageView mSuppressFromBotsImageView = null;

    private ImageView mNotifyAllOthersImageView = null;

    private LinearLayout mEnableLayout = null;
    private LinearLayout mAllSettingsLayout = null;

    private MXSession mxSession = null;
    private BingRulesManager mBingRulesManager = null;
    private BingRuleSet mBingRuleSet = null;

    private HashMap<String, ImageView> mRuleImageViewByRuleId = new HashMap<String, ImageView>();
    private HashMap<ImageView, String> mRuleIdByImageView = new HashMap<ImageView, String>();

    MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onBingRulesUpdate() {
            fullRefresh();
        }
    };

    NotificationsRulesAdapter.NotificationClickListener mOnRulesClicklistener = new NotificationsRulesAdapter.NotificationClickListener() {
        public void onAddWordRule(String word, Boolean alwaysNotify, Boolean playSound, Boolean highlight) {
            allowUserUpdate(false);
            mBingRulesManager.addRule(new ContentRule(BingRule.KIND_CONTENT, word, alwaysNotify, highlight, playSound), mOnBingRuleUpdateListener);
        }

        public void onAddRoomRule(Room room, Boolean alwaysNotify, Boolean playSound, Boolean highlight) {
            allowUserUpdate(false);
            mBingRulesManager.addRule(new BingRule(BingRule.KIND_ROOM, room.getRoomId(), alwaysNotify, highlight, playSound), mOnBingRuleUpdateListener);
        }

        public void onAddSenderRule(String sender, Boolean alwaysNotify, Boolean playSound, Boolean highlight){
            allowUserUpdate(false);
            mBingRulesManager.addRule(new BingRule(BingRule.KIND_SENDER, sender, alwaysNotify, highlight, playSound), mOnBingRuleUpdateListener);
        }

        public void onToggleRule(BingRule rule) {
            allowUserUpdate(false);
            mBingRulesManager.toggleRule(rule, mOnBingRuleUpdateListener);
        }

        public void onRemoveRule(BingRule rule) {
            allowUserUpdate(false);
            mBingRulesManager.deleteRule(rule, mOnBingRuleUpdateListener);

        }
    };

    BingRulesManager.onBingRuleUpdateListener mOnBingRuleUpdateListener = new BingRulesManager.onBingRuleUpdateListener() {
        public void onBingRuleUpdateSuccess() {
            allowUserUpdate(true);
            refreshUI();
        }

        public void onBingRuleUpdateFailure(String errorMessage) {
            allowUserUpdate(true);
            Toast.makeText(NotificationSettingsActivity.this, errorMessage, Toast.LENGTH_LONG).show();
        }
    };

    View.OnClickListener mOnImageClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String ruleId = mRuleIdByImageView.get(v);

            if (null != ruleId) {
                allowUserUpdate(false);
                mBingRulesManager.toggleRule(mBingRuleSet.findDefaultRule(ruleId), mOnBingRuleUpdateListener);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_MATRIX_ID)) {
            Log.e(LOG_TAG, "No matrix ID");
            finish();
            return;
        }

        mxSession = Matrix.getInstance(this).getSession(intent.getStringExtra(EXTRA_MATRIX_ID));

        if (null == mxSession) {
            Log.e(LOG_TAG, "No Valid session");
            finish();
            return;
        }

        mBingRulesManager = mxSession.getDataHandler().getBingRulesManager();

        mDisableAllButton = (Button)findViewById(R.id.notif_settings_disable_all_button);
        mDisableAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allowUserUpdate(false);
                mBingRulesManager.toggleRule(mBingRuleSet.findDefaultRule(BingRule.RULE_ID_DISABLE_ALL), mOnBingRuleUpdateListener);
            }
        });

        mDisableAllTextView = (TextView)findViewById(R.id.notification_settings_disable_text);

        mPerWordList = (ListView)findViewById(R.id.listView_perWord);
        mPerRoomList = (ListView)findViewById(R.id.listView_perRoom);
        mPerSenderList = (ListView)findViewById(R.id.listView_perSender);

        mPerWordAdapter = new NotificationsRulesAdapter(this, mxSession, NotificationsRulesAdapter.PER_WORD_NOTIFICATION, R.layout.adapter_notifications_existing_item, R.layout.adapter_notifications_new_item);
        mPerRoomAdapter = new NotificationsRulesAdapter(this, mxSession, NotificationsRulesAdapter.PER_ROOM_NOTIFICATION, R.layout.adapter_notifications_existing_item, R.layout.adapter_notifications_new_item);
        mPerSenderAdapter = new NotificationsRulesAdapter(this, mxSession, NotificationsRulesAdapter.PER_SENDER_NOTIFICATION, R.layout.adapter_notifications_existing_item, R.layout.adapter_notifications_new_item);

        mPerWordList.setAdapter(mPerWordAdapter);
        mPerRoomList.setAdapter(mPerRoomAdapter);
        mPerSenderList.setAdapter(mPerSenderAdapter);

        mContainUserNameImageView = (ImageView)findViewById(R.id.contain_my_user_name_imageview);
        mContainMyDisplayNameImageView = (ImageView)findViewById(R.id.contain_my_display_name_imageview);
        mJustSendToMeImageView = (ImageView)findViewById(R.id.just_sent_to_me_imageview);
        mInviteToNewRoomImageView = (ImageView)findViewById(R.id.invite_to_new_room_imageview);
        mPeopleJoinLeaveImageView = (ImageView)findViewById(R.id.people_leave_join_room_imageview);
        mReceiveACallImageView = (ImageView)findViewById(R.id.receive_a_call_imageview);
        mSuppressFromBotsImageView = (ImageView)findViewById(R.id.suppress_from_bots_imageview);

        mNotifyAllOthersImageView = (ImageView)findViewById(R.id.notify_all_others_imageview);

        mAllSettingsLayout = (LinearLayout)findViewById(R.id.settings_items_layout);
        mEnableLayout = (LinearLayout)findViewById(R.id.notif_settings_disable_all_layout);

        // define imageView <-> rule ID map
        mRuleImageViewByRuleId.put(BingRule.RULE_ID_CONTAIN_USER_NAME, mContainUserNameImageView);
        mRuleImageViewByRuleId.put(BingRule.RULE_ID_CONTAIN_DISPLAY_NAME, mContainMyDisplayNameImageView);
        mRuleImageViewByRuleId.put(BingRule.RULE_ID_ONE_TO_ONE_ROOM, mJustSendToMeImageView);
        mRuleImageViewByRuleId.put(BingRule.RULE_ID_INVITE_ME, mInviteToNewRoomImageView);
        mRuleImageViewByRuleId.put(BingRule.RULE_ID_PEOPLE_JOIN_LEAVE, mPeopleJoinLeaveImageView);
        mRuleImageViewByRuleId.put(BingRule.RULE_ID_CALL, mReceiveACallImageView);
        mRuleImageViewByRuleId.put(BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS, mSuppressFromBotsImageView);
        mRuleImageViewByRuleId.put(BingRule.RULE_ID_ALL_OTHER_MESSAGES_ROOMS, mNotifyAllOthersImageView);

        for(String key : mRuleImageViewByRuleId.keySet()) {
            ImageView imageView = mRuleImageViewByRuleId.get(key);

            imageView.setOnClickListener(mOnImageClickListener);
            mRuleIdByImageView.put(imageView, key);
        }

        fullRefresh();
    }

    private void allowUserUpdate(Boolean status) {
        mEnableLayout.setEnabled(status);
        mAllSettingsLayout.setEnabled(status);

        mEnableLayout.setAlpha(status ? 1.0f : 0.5f);
        mAllSettingsLayout.setAlpha(status ? 1.0f : 0.5f);
    }

    private void updateImageView(ImageView imageView, boolean enabled) {
        imageView.setImageBitmap(BitmapFactory.decodeResource(this.getResources(), enabled ? R.drawable.notification_pause : R.drawable.notification_play));
    }

    /**
     * Refresh the listview height because the listviews are displayed in a scrollview.
     * @param listView the listview.
     */
    private void refreshListViewHeight(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        int totalHeight = 0;
        for (int i = 0; i < listAdapter.getCount(); i++) {
            View listItem = listAdapter.getView(i, null, listView);
            listItem.measure(0, 0);
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }


    private void fullRefresh() {
        mBingRuleSet = mxSession.getDataHandler().pushRules();
        refreshUI();
    }

    private void refreshUI() {
        mPerWordAdapter.clear();
        mPerRoomAdapter.clear();
        mPerSenderAdapter.clear();

        if (null != mBingRuleSet) {
            BingRule disableAll = mBingRuleSet.findDefaultRule(BingRule.RULE_ID_DISABLE_ALL);

            if ((null != disableAll) && disableAll.isEnabled) {
                mDisableAllButton.setText(getString(R.string.notification_settings_enable_notifications));
                mDisableAllTextView.setVisibility(View.VISIBLE);
                mAllSettingsLayout.setVisibility(View.INVISIBLE);
                mEnableLayout.setBackgroundColor(Color.RED);

            } else {
                mDisableAllButton.setText(getString(R.string.notification_settings_disable_all));
                mDisableAllTextView.setVisibility(View.INVISIBLE);
                mAllSettingsLayout.setVisibility(View.VISIBLE);
                mEnableLayout.setBackgroundColor(Color.TRANSPARENT);
            }

            // per word
            if (null != mBingRuleSet.content) {
                mPerWordAdapter.addAll(mBingRuleSet.getContentRules());
            }
            // dummy bing rule to add a new one
            mPerWordAdapter.addAll(new BingRule(false));
            mPerWordAdapter.setListener(mOnRulesClicklistener);

            // per room
            if (null != mBingRuleSet.content) {
                mPerRoomAdapter.addAll(mBingRuleSet.getRoomRules());
                mPerRoomAdapter.setRooms(mxSession.getDataHandler().getStore().getRooms(), mxSession.getMyUser().userId);
            }
            // dummy bing rule to add a new one
            mPerRoomAdapter.addAll(new BingRule(false));
            mPerRoomAdapter.setListener(mOnRulesClicklistener);

            // per sender
            if (null != mBingRuleSet.content) {
                mPerSenderAdapter.addAll(mBingRuleSet.getSenderRules());
            }
            // dummy bing rule to add a new one
            mPerSenderAdapter.addAll(new BingRule(false));
            mPerSenderAdapter.setListener(mOnRulesClicklistener);

            for(String ruleId : mRuleImageViewByRuleId.keySet()) {
                BingRule rule = mBingRuleSet.findDefaultRule(ruleId);
                updateImageView(mRuleImageViewByRuleId.get(ruleId), (null == rule) || (rule.isEnabled));
            }
        }

        mPerWordAdapter.notifyDataSetChanged();
        refreshListViewHeight(mPerWordList);

        mPerRoomAdapter.notifyDataSetChanged();
        refreshListViewHeight(mPerRoomList);

        mPerSenderAdapter.notifyDataSetChanged();
        refreshListViewHeight(mPerSenderList);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mxSession.getDataHandler().removeListener(mEventsListener);
    }
    @Override
    protected void onResume() {
        super.onResume();
        mxSession.getDataHandler().addListener(mEventsListener);
    }
}
