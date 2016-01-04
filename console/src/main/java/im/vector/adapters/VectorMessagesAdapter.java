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
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.Event;
import im.vector.VectorApp;
import im.vector.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;

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
                    DateUtils.FORMAT_NO_YEAR |
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
    protected boolean manageSubView(int position, View convertView, View subView, int msgType) {
        Boolean isMergedView = super.manageSubView(position, convertView, subView, msgType);

        View view = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator);
        if (null != view) {
            View line = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator_line);

            if (null != line) {
                line.setBackgroundColor(Color.TRANSPARENT);
            }

            MessageRow row = getItem(position);
            Event msg = row.getEvent();
            String nextUserId = null;

            if ((position + 1) < this.getCount()) {
                MessageRow nextRow = getItem(position + 1);

                if (null != nextRow)  {
                    nextUserId = nextRow.getEvent().userId;
                }
            }

            view.setVisibility(((null != nextUserId) && (nextUserId.equals(msg.userId)) || ((position + 1) == this.getCount())) ? View.GONE : View.VISIBLE);
        }

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
}
