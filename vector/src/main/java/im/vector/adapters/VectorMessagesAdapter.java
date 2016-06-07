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

package im.vector.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.LoginFilter;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.JsonUtils;

import im.vector.VectorApp;
import im.vector.R;
import im.vector.util.VectorUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;

/**
 * An adapter which can display room information.
 */
public class VectorMessagesAdapter extends MessagesAdapter {

    public interface VectorMessagesAdapterActionsListener {
        /**
         * An action has been  triggered on an event.
         * @param event the event.
         * @param action an action ic_action_vector_XXX
         */
        void onEventAction(final Event event, final int action);
    }

    // an event is highlighted when the user taps on it
    private String mHighlightedEventId;

    protected VectorMessagesAdapterActionsListener mVectorMessagesAdapterEventsListener = null;
    protected Date mReferenceDate = new Date();
    protected ArrayList<Date> mMessagesDateList = new ArrayList<Date>();
    protected Handler mUiHandler;

    protected HashMap<String, String> mEventFormattedTsMap = new HashMap<String, String>();

    public VectorMessagesAdapter(MXSession session, Context context, int textResLayoutId, int imageResLayoutId,
                                 int noticeResLayoutId, int emoteRestLayoutId, int fileResLayoutId, int videoResLayoutId, MXMediasCache mediasCache) {

        super(session, context,
                textResLayoutId,
                imageResLayoutId,
                noticeResLayoutId,
                emoteRestLayoutId,
                fileResLayoutId,
                videoResLayoutId,
                mediasCache);

        // for dispatching data to add to the adapter we need to be on the main thread
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    public VectorMessagesAdapter(MXSession session, Context context, MXMediasCache mediasCache) {
        super(session, context,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_image_video,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_file,
                R.layout.adapter_item_vector_message_image_video,
                mediasCache);

        // for dispatching data to add to the adapter we need to be on the main thread
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * the parent fragment is paused.
     */
    public void onPause() {
        mEventFormattedTsMap.clear();
    }


    /**
     * Toogle the selection mode.
     * @param eventId the tapped eventID.
     */
    public void onEventTap(String eventId) {
        // the tap to select is only enabled when the adapter is not in search mode.
        if (!mIsSearchMode) {
            if (null == mHighlightedEventId) {
                mHighlightedEventId = eventId;
            } else {
                mHighlightedEventId = null;
            }
            notifyDataSetChanged();
        }
    }

    /**
     * Cancel the message selection mode
     */
    public void cancelSelectionMode() {
        if (null != mHighlightedEventId) {
            mHighlightedEventId = null;
            notifyDataSetChanged();
        }
    }

    /**
     * @return true if there is a selected item.
     */
    public boolean isInSelectionMode() {
        return null != mHighlightedEventId;
    }

    /**
     * Define the events listener
     * @param listener teh events listener
     */
    public void setVectorMessagesAdapterActionsListener(VectorMessagesAdapterActionsListener listener) {
        mVectorMessagesAdapterEventsListener = listener;
    }

    /**
     * Provides the formatted timestamp to display.
     * null means that the timestamp text must be hidden.
     * @param event the event.
     * @return  the formatted timestamp to display.
     */
    @Override
    protected String getFormattedTimestamp(Event event) {
        String res = mEventFormattedTsMap.get(event.eventId);

        if (null != res) {
            return res;
        }

        if (event.isValidOriginServerTs()) {
            res = AdapterUtils.tsToString(mContext, event.getOriginServerTs(), true);
        } else {
            res = " ";
        }

        mEventFormattedTsMap.put(event.eventId, res);

        return res;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);

        if (null != view) {
            view.setBackgroundColor(Color.TRANSPARENT);
        }

        return view;
    }

    @Override
    protected void setTypingVisibility(View avatarLayoutView, int status) {
    }

    @Override
    protected void refreshPresenceRing(ImageView presenceView, String userId) {
    }

    @Override
    protected void loadMemberAvatar(ImageView avatarView, RoomMember member, String userId, String url) {
        if (!mSession.isAlive()) {
            return;
        }

        if ((member != null) && (null == url)) {
            url = member.avatarUrl;
        }

        if (null != member) {
            VectorUtils.loadUserAvatar(mContext, mSession, avatarView, url, member.getUserId(), member.displayname);
        } else {
            VectorUtils.loadUserAvatar(mContext, mSession, avatarView, url, userId, null);
        }
    }

    @Override
    public void notifyDataSetChanged() {
        //  do not refresh the room when the application is in background
        // on large rooms, it drains a lot of battery
        if (!VectorApp.isAppInBackground()) {
            super.notifyDataSetChanged();
        }

        // the event with invalid timestamp must be pushed at the end of the history
        this.setNotifyOnChange(false);
        ArrayList<MessageRow> undeliverableEvents = null;

        for(int i = 0; i < getCount(); i++) {
            MessageRow row = getItem(i);

            if ((null != row.getEvent()) && !row.getEvent().isValidOriginServerTs()) {
                if (null == undeliverableEvents) {
                    undeliverableEvents = new ArrayList<MessageRow>();
                }
                undeliverableEvents.add(row);
                this.remove(row);
                i--;
            }
        }

        if (null != undeliverableEvents) {
            this.addAll(undeliverableEvents);
        }

        this.setNotifyOnChange(true);

        // build messages timestamps
        ArrayList<Date> dates = new ArrayList<Date>();

        Date latestDate = AdapterUtils.zeroTimeDate(new Date());

        for(int index = 0; index < this.getCount(); index++) {
            MessageRow row = getItem(index);
            Event event = row.getEvent();

            if (event.isValidOriginServerTs()) {
                latestDate = AdapterUtils.zeroTimeDate(new Date(event.getOriginServerTs()));
            }

            dates.add(latestDate);
        }

        synchronized (this) {
            mMessagesDateList = dates;
            mReferenceDate = new Date();
        }
    }

    /**
     * Converts a difference of days to a string.
     * @param date the date to dislay
     * @param nbrDays the number of days between the reference days
     * @return the date text
     */
    private String dateDiff(Date date, long nbrDays) {
        if (nbrDays == 0) {
            return mContext.getResources().getString(R.string.today);
        } else if (nbrDays == 1) {
            return mContext.getResources().getString(R.string.yesterday);
        } else if (nbrDays < 7) {
            return (new SimpleDateFormat("EEEE", AdapterUtils.getLocale(mContext))).format(date);
        } else  {
            int flags = DateUtils.FORMAT_SHOW_DATE |
                    DateUtils.FORMAT_SHOW_YEAR |
                    DateUtils.FORMAT_ABBREV_ALL |
                    DateUtils.FORMAT_SHOW_WEEKDAY;

            Formatter f = new Formatter(new StringBuilder(50), AdapterUtils.getLocale(mContext));
            return DateUtils.formatDateRange(mContext, f, date.getTime(), date.getTime(), flags).toString();
        }
    }

    protected String headerMessage(int position) {
        Date prevMessageDate = null;
        Date messageDate = null;

        synchronized (this) {
            if ((position > 0) && (position < mMessagesDateList.size())) {
                prevMessageDate = mMessagesDateList.get(position -1);
            }
            if (position < mMessagesDateList.size()) {
                messageDate = mMessagesDateList.get(position);
            }
        }

        // sanity check
        if (null == messageDate) {
            return null;
        }

        // same day or get the oldest message
        if ((null != prevMessageDate) && 0 == (prevMessageDate.getTime() - messageDate.getTime())) {
            return null;
        }

        return dateDiff(messageDate, (mReferenceDate.getTime() - messageDate.getTime()) / AdapterUtils.MS_IN_DAY);
    }

    @Override
    protected boolean isAvatarDisplayedOnRightSide(Event event) {
        return false;
    }

    @Override
    protected void refreshReceiverLayout(final LinearLayout receiversLayout, final boolean leftAlign, final String eventId, final RoomState roomState) {
        if (null != receiversLayout) {
            // replaced by displayReadReceipts
            receiversLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onTypingUsersUpdate() {
        // the typing users are now displayed in a dedicated area in the activity
    }

    /**
     * Display the read receipts within the dedicated vector layout.
     * Console application displays them on the message side.
     * Vector application displays them in a dedicated line under the message
     * @param avatarsListView the read receipts layout
     * @param eventId the event Id.
     * @param roomState the room state.
     */
    private void displayReadReceipts(final View avatarsListView, final String eventId, final RoomState roomState) {
        if (!mSession.isAlive()) {
            return;
        }

        IMXStore store = mSession.getDataHandler().getStore();

        // sanity check
        if (null == roomState) {
            avatarsListView.setVisibility(View.GONE);
            return;
        }

        // hide the read receipts until there is a way to retrieve them
        // without triggering a request per message
        if (mIsPreviewMode) {
            avatarsListView.setVisibility(View.GONE);
            return;
        }

        List<ReceiptData> receipts = store.getEventReceipts(roomState.roomId, eventId, true, true);

        // if there is no receipt to display
        // hide the dedicated layout
        if ((null == receipts) || (0 == receipts.size())) {
            avatarsListView.setVisibility(View.GONE);
            return;
        }

        avatarsListView.setVisibility(View.VISIBLE);

        ArrayList<View> imageViews = new ArrayList<View>();

        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_1).findViewById(org.matrix.androidsdk.R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_2).findViewById(org.matrix.androidsdk.R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_3).findViewById(org.matrix.androidsdk.R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_4).findViewById(org.matrix.androidsdk.R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_5).findViewById(org.matrix.androidsdk.R.id.avatar_img));

        TextView moreText = (TextView)avatarsListView.findViewById(R.id.message_more_than_expected);

        int index = 0;
        int bound = Math.min(receipts.size(), imageViews.size());

        for (; index < bound; index++) {
            final ReceiptData r = receipts.get(index);
            RoomMember member = roomState.getMember(r.userId);
            ImageView imageView = (ImageView) imageViews.get(index);

            imageView.setVisibility(View.VISIBLE);
            imageView.setTag(null);

            if (null != member) {
                VectorUtils.loadRoomMemberAvatar(mContext, mSession, imageView, member);
            } else {
                // should never happen
                VectorUtils.loadUserAvatar(mContext, mSession, imageView, null, r.userId, r.userId);
            }
        }

        moreText.setVisibility((receipts.size() <= imageViews.size()) ? View.GONE : View.VISIBLE);
        moreText.setText(receipts.size() - imageViews.size() + "+");

        for(; index < imageViews.size(); index++) {
            imageViews.get(index).setVisibility(View.INVISIBLE);
        }

        if (receipts.size() > 0) {
            avatarsListView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mMessagesAdapterEventsListener) {
                        mMessagesAdapterEventsListener.onMoreReadReceiptClick(eventId);
                    }
                }
            });
        } else {
            avatarsListView.setOnClickListener(null);
        }
    }

    /**
     * The user taps on the action icon.
     * @param event the selected event.
     * @param anchorView the popup anchor.
     */
    @SuppressLint("NewApi")
    private void onMessageClick(final Event event, final View anchorView) {
        final PopupMenu popup = (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) ? new PopupMenu(mContext, anchorView, Gravity.END) : new PopupMenu(mContext, anchorView);

        popup.getMenuInflater().inflate(R.menu.vector_room_message_settings, popup.getMenu());

        // force to display the icons
        try {
            Field[] fields = popup.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popup);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
        }

        boolean isSelfMessage = TextUtils.equals(event.getSender(), mSession.getMyUserId());

        Menu menu = popup.getMenu();

        // hide entries
        for(int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setVisible(false);
        }

        if (mSession.getHomeserverConfig().getHomeserverUri().getHost().equals("matrix.org")) {
            menu.findItem(R.id.ic_action_vector_permalink).setVisible(true);
        }

        // before enabling them
        // according to the event type.
        if (TextUtils.equals(event.type, Event.EVENT_TYPE_MESSAGE)) {
            Message message = JsonUtils.toMessage(event.getContentAsJsonObject());

            if (Message.MSGTYPE_TEXT.equals(message.msgtype)) {
                menu.findItem(R.id.ic_action_vector_copy).setVisible(true);
            }
        }

        if (event.canBeResent()) {
            menu.findItem(R.id.ic_action_vector_resend_message).setVisible(true);

            if (event.isUndeliverable()) {
                menu.findItem(R.id.ic_action_vector_delete_message).setVisible(true);
            }
        } else if (event.mSentState == Event.SentState.SENT) {
            menu.findItem(R.id.ic_action_vector_delete_message).setVisible(!mIsPreviewMode);

            if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
                Message message = JsonUtils.toMessage(event.getContentAsJsonObject());

                // share / forward the message
                menu.findItem(R.id.ic_action_vector_share).setVisible(true);
                menu.findItem(R.id.ic_action_vector_forward).setVisible(true);

                // save the media in the downloads directory
                if (Message.MSGTYPE_IMAGE.equals(message.msgtype) || Message.MSGTYPE_VIDEO.equals(message.msgtype) || Message.MSGTYPE_FILE.equals(message.msgtype)) {
                    menu.findItem(R.id.ic_action_vector_save).setVisible(true);
                }

                // offer to report a message content
                menu.findItem(R.id.ic_action_vector_report).setVisible(!mIsPreviewMode && !TextUtils.equals(event.sender, mSession.getMyUserId()));
            }

        }

        // display the menu
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                // warn the listener
                if (null != mVectorMessagesAdapterEventsListener) {
                    mVectorMessagesAdapterEventsListener.onEventAction(event, item.getItemId());
                }

                // disable the selection
                mHighlightedEventId = null;
                notifyDataSetChanged();

                return true;
            }
        });

        popup.show();
    }

    /**
     * Manage the select mode i.e highlight an item when the user tap on it
     * @param convertView teh cell view.
     * @param event the linked event
     */
    private void manageSelectionMode(final View convertView, final Event event) {
        final String eventId = event.eventId;

        boolean isInSelectionMode = (null != mHighlightedEventId);
        boolean isHighlighted = TextUtils.equals(eventId, mHighlightedEventId);

        // display the action icon when selected
        convertView.findViewById(R.id.messagesAdapter_action_image).setVisibility(isHighlighted ? View.VISIBLE : View.GONE);

        float alpha = (!isInSelectionMode || isHighlighted) ? 1.0f : 0.2f;

        // the message body is dimmed when not selected
        convertView.findViewById(R.id.messagesAdapter_body_view).setAlpha(alpha);
        convertView.findViewById(R.id.messagesAdapter_avatars_list).setAlpha(alpha);

        TextView tsTextView = (TextView)convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_timestamp);
        if (isInSelectionMode && isHighlighted) {
            tsTextView.setVisibility(View.VISIBLE);
        }

        convertView.findViewById(org.matrix.androidsdk.R.id.message_timestamp_layout_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.equals(eventId, mHighlightedEventId)) {
                    onMessageClick(event, convertView.findViewById(R.id.messagesAdapter_action_anchor));
                } else {
                    onEventTap(eventId);
                }
            }
        });

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!mIsSearchMode) {
                    onMessageClick(event, convertView.findViewById(R.id.messagesAdapter_action_anchor));
                    mHighlightedEventId = eventId;
                    notifyDataSetChanged();
                    return true;
                }

                return false;
            }
        });
    }

    @Override
    protected boolean mergeView(Event event, int position, boolean shouldBeMerged) {
        if (shouldBeMerged) {
            shouldBeMerged = null == headerMessage(position);
        }

        return shouldBeMerged;
    }

    @Override
    protected void addContentViewListeners(final View convertView, final View contentView, final int position) {
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mMessagesAdapterEventsListener) {
                    mMessagesAdapterEventsListener.onContentClick(position);
                }
            }
        });

        contentView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                MessageRow row = getItem(position);
                Event event = row.getEvent();

                if (!mIsSearchMode) {
                    onMessageClick(event, convertView.findViewById(R.id.messagesAdapter_action_anchor));
                    mHighlightedEventId = event.eventId;
                    notifyDataSetChanged();
                    return true;
                }

                return true;
            }
        });
    }


    @Override
    protected boolean manageSubView(int position, View convertView, View subView, int msgType) {
        MessageRow row = getItem(position);
        Event event = row.getEvent();

        // mother class implementation
        Boolean isMergedView = super.manageSubView(position, convertView, subView, msgType);

        // remove the message separator when it is not required
        View view = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator);
        if (null != view) {
            View line = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator_line);

            if (null != line) {
                line.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        // display the day separator
        View headerLayout = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_header);
        if (null != headerLayout) {
            String header = headerMessage(position);

            if (null != header) {
                TextView headerText = (TextView) convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_header_text);
                headerText.setText(header);
                headerLayout.setVisibility(View.VISIBLE);

                View topHeaderMargin = headerLayout.findViewById(R.id.messagesAdapter_message_header_top_margin);
                topHeaderMargin.setVisibility((0 == position) ? View.GONE : View.VISIBLE);
            } else {
                headerLayout.setVisibility(View.GONE);
            }
        }

        // the timestamp is hidden except for the latest message and when there is no search
        View rightTsTextLayout = convertView.findViewById(org.matrix.androidsdk.R.id.message_timestamp_layout_right);

        if (null != rightTsTextLayout) {
            TextView tsTextView = (TextView)rightTsTextLayout.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_timestamp);

            if (null != tsTextView) {
                tsTextView.setVisibility((((position + 1) == this.getCount()) || mIsSearchMode) ? View.VISIBLE : View.GONE);
            }
        }

        // On Vector application, the read receipts are displayed in a dedicated line under the message
        View avatarsListView = convertView.findViewById(R.id.messagesAdapter_avatars_list);

        if (null != avatarsListView) {
            displayReadReceipts(avatarsListView, event.eventId, row.getRoomState());
        }

        // selection mode
        manageSelectionMode(convertView, event);

        return isMergedView;
    }

    public int presenceOnlineColor() {
        return mContext.getResources().getColor(R.color.presence_online);
    }

    public int presenceOfflineColor() {
        return mContext.getResources().getColor(R.color.presence_offline);
    }

    public int presenceUnavailableColor() {
        return mContext.getResources().getColor(R.color.presence_unavailable);
    }

    public int highlightMessageColor(Context context) {
        return context.getResources().getColor(R.color.vector_fuchsia_color);
    }

    public int searchHighlightMessageColor(Context context) {
        return context.getResources().getColor(R.color.vector_green_color);
    }
}
