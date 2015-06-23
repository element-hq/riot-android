/*
 * Copyright 2015 OpenMarket Ltd
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

package org.matrix.console.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.ContentRule;
import org.matrix.console.R;

import java.util.List;

/**
 * An adapter which can display string
 */
public class NotificationsRulesAdapter extends ArrayAdapter<BingRule> {

    public static int PER_WORD_NOTIFICATION = 0;
    public static int PER_ROOM_NOTIFICATION = 1;
    public static int PER_SENDER_NOTIFICATION = 2;

    protected Context mContext;
    private LayoutInflater mLayoutInflater;
    private MXSession mSession;
    private int mExistingRuleLayoutResourceId;
    private int mNewRuleLayoutResourceId;
    private int mNotificationType;

    /**
     * Construct an adapter which will display a list of image size
     * @param context Activity context
     * @param notificationType the notification type
     * @param existingdRuleLayoutResourceId The resource ID of the layout for each item.
     * @param newRuleLayoutResourceId The resource ID of the layout for each item.
     *
     */
    public NotificationsRulesAdapter(Context context, MXSession session, int notificationType, int existingdRuleLayoutResourceId, int newRuleLayoutResourceId) {
        super(context, existingdRuleLayoutResourceId);

        mContext = context;
        mSession = session;
        mLayoutInflater = LayoutInflater.from(mContext);
        mNotificationType = notificationType;

        mExistingRuleLayoutResourceId = existingdRuleLayoutResourceId;
        mNewRuleLayoutResourceId = newRuleLayoutResourceId;
    }

    /**
     * Build the rule settings.
     * @param rule the bingrule
     * @return the rules settings string
     */
    private String buildRuleSettings(BingRule rule) {
        String settings = "";

        if (rule.shouldNotify()) {
            settings += mContext.getString(R.string.notification_settings_always_notify);
        } else {
            settings += mContext.getString(R.string.notification_settings_never_notify);
        }

        if (rule.shouldPlaySound()) {
            settings += ", " + mContext.getString(R.string.notification_settings_custom_sound);
        }

        if (rule.shouldHighlight()) {
            settings += ", " + mContext.getString(R.string.notification_settings_highlight);
        }

        return settings;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if ((position+1) == this.getCount()) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(mNewRuleLayoutResourceId, parent, false);
            }

        } else {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(mExistingRuleLayoutResourceId, parent, false);
            }

            BingRule bingRule = getItem(position);

            // play/pause button
            ImageView playPauseImageView = (ImageView) convertView.findViewById(R.id.play_pause_imageview);
            playPauseImageView.setImageBitmap(BitmapFactory.decodeResource(mContext.getResources(), bingRule.isEnabled ? R.drawable.notification_pause : R.drawable.notification_play));

            // pattern text
            TextView notificationPattern = (TextView)convertView.findViewById(R.id.notification_pattern);

            if (mNotificationType == PER_WORD_NOTIFICATION) {
                notificationPattern.setText(((ContentRule)bingRule).pattern);
            } else if (mNotificationType == PER_ROOM_NOTIFICATION) {
                Room room = mSession.getDataHandler().getRoom(bingRule.ruleId);
                String displayName = bingRule.ruleId;

                if (null != room) {
                    displayName = room.getName(mSession.getMyUser().userId);
                }

                notificationPattern.setText(mContext.getText(R.string.notification_settings_room) + displayName);
            } else if (mNotificationType == PER_SENDER_NOTIFICATION) {
                notificationPattern.setText(bingRule.ruleId);
            }

            // rules text
            TextView notificationSettings = (TextView)convertView.findViewById(R.id.notification_settings);
            notificationSettings.setText(buildRuleSettings(bingRule));
        }

        return convertView;
    }
}
