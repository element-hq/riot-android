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

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.ContentManager;

import im.vector.VectorApp;
import im.vector.R;
import im.vector.util.VectorUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Formatter;
import java.util.List;

/**
 * An adapter which can display room information.
 */
public class VectorMessagesAdapter extends MessagesAdapter {

    private Date mReferenceDate = new Date();
    private ArrayList<Date> mMessagesDateList = new ArrayList<Date>();
    private Handler mUiHandler;

    public VectorMessagesAdapter(MXSession session, Context context, MXMediasCache mediasCache) {
        super(session, context,
                R.layout.adapter_item_vector_message_text,
                R.layout.adapter_item_vector_message_image,
                R.layout.adapter_item_vector_message_notice,
                R.layout.adapter_item_vector_message_emote,
                R.layout.adapter_item_vector_message_file,
                R.layout.adapter_item_vector_message_video,
                mediasCache);

        // for dispatching data to add to the adapter we need to be on the main thread
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Provides the formatted timestamp to display.
     * null means that the timestamp text must be hidden.
     * @param event the event.
     * @return  the formatted timestamp to display.
     */
    @Override
    protected String getFormattedTimestamp(Event event) {
        return AdapterUtils.tsToString(mContext, event.getOriginServerTs(), true);
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
        if ((member != null) && (null == url)) {
            url = member.avatarUrl;
        }

        // define a default avatar
        if (null != member) {
            VectorUtils.setMemberAvatar(avatarView, member.getUserId(), member.displayname);
        } else {
            VectorUtils.setMemberAvatar(avatarView, userId, null);
        }

        if (!TextUtils.isEmpty(url)) {
            int size = getContext().getResources().getDimensionPixelSize(org.matrix.androidsdk.R.dimen.chat_avatar_size);
            mMediasCache.loadAvatarThumbnail(mSession.getHomeserverConfig(), avatarView, url, size);
        }
    }

    @Override
    public void notifyDataSetChanged() {
        //  do not refresh the room when the application is in background
        // on large rooms, it drains a lot of battery
        if (!VectorApp.isAppInBackground()) {
            super.notifyDataSetChanged();
        }

        // build messages timestamps
        ArrayList<Date> dates = new ArrayList<Date>();

        for(int index = 0; index < this.getCount(); index++) {
            MessageRow row = getItem(index);
            Event msg = row.getEvent();
            dates.add( AdapterUtils.zeroTimeDate(new Date(msg.getOriginServerTs())));
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

    private String headerMessage(int position) {
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
        // replaced by displayReadReceipts
        receiversLayout.setVisibility(View.GONE);
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
        IMXStore store = mSession.getDataHandler().getStore();
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
                VectorUtils.setMemberAvatar(imageView, member.getUserId(), member.displayname);
            } else {
                // shoud never happen
                imageView.setImageResource(org.matrix.androidsdk.R.drawable.ic_contact_picture_holo_light);
            }

            if ((null != member) && (null != member.avatarUrl)) {
                loadSmallAvatar(imageView, member.avatarUrl);
            }

            // FIXME expected behaviour when the avatar is tapped.
        }

        // FIXME expected behaviour when this text is tapped.
        moreText.setVisibility((receipts.size() <= imageViews.size()) ? View.GONE : View.VISIBLE);
        moreText.setText(receipts.size() - imageViews.size() + "+");

        for(; index < imageViews.size(); index++) {
            imageViews.get(index).setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected boolean manageSubView(int position, View convertView, View subView, int msgType) {
        MessageRow row = getItem(position);
        Event msg = row.getEvent();

        // mother class call
        Boolean isMergedView = super.manageSubView(position, convertView, subView, msgType);

        // remove the message separator when it is not required
        View view = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator);
        if (null != view) {
            View line = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator_line);

            if (null != line) {
                line.setBackgroundColor(Color.TRANSPARENT);
            }

            String nextUserId = null;

            if ((position + 1) < this.getCount()) {
                MessageRow nextRow = getItem(position + 1);

                if (null != nextRow)  {
                    nextUserId = nextRow.getEvent().userId;
                }
            }

            view.setVisibility(((null != nextUserId) && (nextUserId.equals(msg.userId)) || ((position + 1) == this.getCount())) ? View.GONE : View.VISIBLE);
        }

        // display the day separator
        View headerLayout = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_header);
        if (null != headerLayout) {
            String header = headerMessage(position);

            if (null != header) {
                TextView headerText = (TextView) convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_header_text);
                headerText.setText(header.toUpperCase());
                headerLayout.setVisibility(View.VISIBLE);
            } else {
                headerLayout.setVisibility(View.GONE);
            }
        }

        // the timestamp is hidden except for the latest message and when there is no search
        View rightTsTextLayout = convertView.findViewById(org.matrix.androidsdk.R.id.message_timestamp_layout_right);

        if (null != rightTsTextLayout) {
            TextView tsTextView = (TextView)rightTsTextLayout.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_timestamp);

            if (null != tsTextView) {
                tsTextView.setVisibility((((position + 1) == this.getCount()) || mIsSearchMode) ? View.VISIBLE : View.INVISIBLE);
            }
        }

        // On Vector application, the read receipts are displayed in a dedicated line under the message
        View avatarsListView = convertView.findViewById(R.id.messagesAdapter_avatars_list);

        if (null != avatarsListView) {
            displayReadReceipts(avatarsListView, msg.eventId, row.getRoomState());
        }

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
        return context.getResources().getColor(R.color.vector_green_color);
    }
}
