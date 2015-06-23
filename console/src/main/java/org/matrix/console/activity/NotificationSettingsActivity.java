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

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.console.Matrix;
import org.matrix.console.R;
import org.matrix.console.adapters.NotificationsRulesAdapter;

import java.util.HashMap;

public class NotificationSettingsActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "NotificationSettingsActivity";
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


    private HashMap<String, ImageView> mRuleImageByRuleId = new HashMap<String, ImageView>();

    MXEventListener mListener = new MXEventListener() {
        @Override
        public void onBingRulesUpdate() {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        // TODO manage multi sessions
        mxSession = Matrix.getInstance(this).getDefaultSession();

        mDisableAllButton = (Button)findViewById(R.id.notif_settings_disable_all_button);
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
        mRuleImageByRuleId.put(BingRule.RULE_ID_CONTAIN_USER_NAME, mContainUserNameImageView);
        mRuleImageByRuleId.put(BingRule.RULE_ID_CONTAIN_DISPLAY_NAME, mContainMyDisplayNameImageView);
        mRuleImageByRuleId.put(BingRule.RULE_ID_ONE_TO_ONE_ROOM, mJustSendToMeImageView);
        mRuleImageByRuleId.put(BingRule.RULE_ID_INVITE_ME, mInviteToNewRoomImageView);
        mRuleImageByRuleId.put(BingRule.RULE_ID_PEOPLE_JOIN_LEAVE, mPeopleJoinLeaveImageView);
        mRuleImageByRuleId.put(BingRule.RULE_ID_CALL, mReceiveACallImageView);
        mRuleImageByRuleId.put(BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS, mSuppressFromBotsImageView);
        mRuleImageByRuleId.put(BingRule.RULE_ID_ALL_OTHER_MESSAGES_ROOMS, mNotifyAllOthersImageView);

        refresh();
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

    private void refresh() {
        mPerWordAdapter.clear();
        mPerRoomAdapter.clear();
        mPerSenderAdapter.clear();

        BingRuleSet bingRuleSet = mxSession.getDataHandler().pushRules();

        if (null != bingRuleSet) {

            BingRule disableAll = bingRuleSet.findRule(BingRule.RULE_ID_DISABLE_ALL);

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
            if (null != bingRuleSet.content) {
                mPerWordAdapter.addAll(bingRuleSet.getContent());
            }
            // dummy bing rule to add a new one
            mPerWordAdapter.addAll(new BingRule(false));

            // per room
            if (null != bingRuleSet.content) {
                mPerRoomAdapter.addAll(bingRuleSet.getRoom());
            }
            // dummy bing rule to add a new one
            mPerRoomAdapter.addAll(new BingRule(false));

            // per sender
            if (null != bingRuleSet.content) {
                mPerSenderAdapter.addAll(bingRuleSet.getSender());
            }
            // dummy bing rule to add a new one
            mPerSenderAdapter.addAll(new BingRule(false));

            for(String ruleId : mRuleImageByRuleId.keySet()) {
                BingRule rule = bingRuleSet.findRule(ruleId);
                updateImageView(mRuleImageByRuleId.get(ruleId),(null == rule) || (rule.isEnabled));
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
        mxSession.getDataHandler().removeListener(mListener);
    }
    @Override
    protected void onResume() {
        super.onResume();
        mxSession.getDataHandler().addListener(mListener);
    }
}
