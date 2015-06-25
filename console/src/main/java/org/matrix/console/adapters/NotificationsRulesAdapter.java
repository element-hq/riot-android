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
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.ContentRule;
import org.matrix.console.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * An adapter which can display string
 */
public class NotificationsRulesAdapter extends ArrayAdapter<BingRule> {

    public static interface NotificationClickListener {
        public void onAddWordRule(String word, Boolean alwaysNotify, Boolean playSound, Boolean highlight);
        public void onAddRoomRule(Room room, Boolean alwaysNotify, Boolean playSound, Boolean highlight);
        public void onAddSenderRule(String sender, Boolean alwaysNotify, Boolean playSound, Boolean highlight);

        public void onToggleRule(BingRule rule);
        public void onRemoveRule(BingRule rule);
    }

    public static int PER_WORD_NOTIFICATION = 0;
    public static int PER_ROOM_NOTIFICATION = 1;
    public static int PER_SENDER_NOTIFICATION = 2;

    protected Context mContext;
    private LayoutInflater mLayoutInflater;
    private MXSession mSession;
    private int mExistingRuleLayoutResourceId;
    private int mNewRuleLayoutResourceId;
    private int mNotificationType;

    private ArrayList<Room> mRoomsList = null;
    private String mMyUserId = null;
    ArrayAdapter<String> spinnerArrayAdapter = null;

    NotificationClickListener mListener = null;

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

    public void setRooms(Collection<Room> roomsList, String userId) {
        mRoomsList = new ArrayList<Room>(roomsList);
        mMyUserId = userId;
    }

    public void setListener(NotificationClickListener listener) {
        mListener = listener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if ((position+1) == this.getCount()) {
            if ((convertView == null) || (null == convertView.findViewById(R.id.notification_room_spinner))) {
                convertView = mLayoutInflater.inflate(mNewRuleLayoutResourceId, parent, false);
            }

            final Spinner roomsSpinner = (Spinner)convertView.findViewById(R.id.notification_room_spinner);
            final EditText newText =  (EditText)convertView.findViewById(R.id.notification_new_pattern);
            final Button addButton = (Button)convertView.findViewById(R.id.notification_add_rule);
            final CheckBox alwaysNotifyCheckBox = (CheckBox)convertView.findViewById(R.id.always_notify_check);
            final CheckBox withSoundCheckBox = (CheckBox)convertView.findViewById(R.id.with_sound_check);
            final CheckBox withHighlightCheckBox = (CheckBox)convertView.findViewById(R.id.with_highlight_check);

            if (mNotificationType == PER_ROOM_NOTIFICATION) {
                roomsSpinner.setVisibility(View.VISIBLE);
                newText.setVisibility(View.GONE);

                if (null == spinnerArrayAdapter) {
                    spinnerArrayAdapter = new ArrayAdapter<String>(mContext, R.layout.adapter_notifications_room_item);
                }

                spinnerArrayAdapter.clear();

                if (null != mRoomsList) {
                    ArrayList<Room> roomsList = new ArrayList<Room>();
                    ArrayList<String> existingRoomIds = new ArrayList<String>();

                    if (this.getCount() > 1) {
                        // check if duplicated
                        for(int index = 0; index < (getCount()-1); index ++) {
                            BingRule bingrule = NotificationsRulesAdapter.this.getItem(index);
                            existingRoomIds.add(bingrule.ruleId);
                        }
                    }

                    // list the rooms
                    // does not list the notified ones
                    ArrayList<String> namesList = new ArrayList<String>();
                    for (Room room : mRoomsList) {
                        if (existingRoomIds.indexOf(room.getRoomId()) < 0) {
                            String name = room.getName(mMyUserId);

                            if (TextUtils.isEmpty(name)) {
                                name = room.getRoomId();
                            }

                            namesList.add(name);
                            roomsList.add(room);
                        }
                    }

                    if (roomsList.size() != mRoomsList.size()) {
                        mRoomsList = roomsList;
                    }

                    spinnerArrayAdapter.addAll(namesList);
                }

                roomsSpinner.setAdapter(spinnerArrayAdapter);
                spinnerArrayAdapter.notifyDataSetChanged();

            } else {
                addButton.setEnabled(false);
                addButton.setAlpha(0.5f);

                newText.setText("");
                newText.setVisibility(View.VISIBLE);
                roomsSpinner.setVisibility(View.GONE);

                newText.setHint((mNotificationType == PER_WORD_NOTIFICATION) ? mContext.getString(R.string.notification_settings_word_to_match) : mContext.getString(R.string.notification_settings_sender_hint));

                newText.addTextChangedListener(new TextWatcher() {
                    public void afterTextChanged(android.text.Editable s) {
                        String text = newText.getText().toString();

                        Boolean enable = true;

                        if (TextUtils.isEmpty(text)) {
                            enable = false;
                        } else if (NotificationsRulesAdapter.this.getCount() > 1) {
                            // check if duplicated
                            for (int index = 0; index < (NotificationsRulesAdapter.this.getCount() - 1); index++) {
                                BingRule bingrule = NotificationsRulesAdapter.this.getItem(index);

                                if (bingrule.ruleId.equals(text)) {
                                    enable = false;
                                    break;
                                }
                            }
                        }

                        addButton.setEnabled(enable);
                        addButton.setAlpha(enable ? 1.0f : 0.5f);
                    }

                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }
                });
            }

            alwaysNotifyCheckBox.setChecked(true);
            withSoundCheckBox.setChecked(false);
            withHighlightCheckBox.setChecked(false);

            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mListener) {
                        Boolean alwaysNotify = alwaysNotifyCheckBox.isChecked();
                        Boolean withSound = withSoundCheckBox.isChecked();
                        Boolean highlight = withHighlightCheckBox.isChecked();

                        try {
                            String text = newText.getText().toString();

                            if (!TextUtils.isEmpty(text) && (mNotificationType == PER_WORD_NOTIFICATION)) {
                                mListener.onAddWordRule(text, alwaysNotify, withSound, highlight);
                            } else if (mNotificationType == PER_ROOM_NOTIFICATION) {
                                mListener.onAddRoomRule(mRoomsList.get(roomsSpinner.getSelectedItemPosition()), alwaysNotify, withSound, highlight);
                            } else if (!TextUtils.isEmpty(text) && (mNotificationType == PER_SENDER_NOTIFICATION)) {
                                mListener.onAddSenderRule(newText.getText().toString(), alwaysNotify, withSound, highlight);
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            });
        } else {
            if ((convertView == null) || (null == convertView.findViewById(R.id.play_pause_imageview))) {
                convertView = mLayoutInflater.inflate(mExistingRuleLayoutResourceId, parent, false);
            }
            // pattern text
            TextView notificationPattern = (TextView)convertView.findViewById(R.id.notification_pattern);
            notificationPattern.setVisibility(View.VISIBLE);

            final BingRule bingRule = getItem(position);

            // play/pause button
            ImageView playPauseImageView = (ImageView) convertView.findViewById(R.id.play_pause_imageview);
            ImageView deleteImageView = (ImageView) convertView.findViewById(R.id.delete_imageview);

            playPauseImageView.setImageBitmap(BitmapFactory.decodeResource(mContext.getResources(), bingRule.isEnabled ? R.drawable.notification_pause : R.drawable.notification_play));

            if (mNotificationType == PER_WORD_NOTIFICATION) {
                notificationPattern.setText(((ContentRule) bingRule).pattern);
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

            playPauseImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mListener) {
                        mListener.onToggleRule(bingRule);
                    }
                }
            });

            deleteImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mListener) {
                        mListener.onRemoveRule(bingRule);
                    }
                }
            });

            // rules text
            TextView notificationSettings = (TextView)convertView.findViewById(R.id.notification_settings);
            notificationSettings.setText(buildRuleSettings(bingRule));
        }

        return convertView;
    }
}
