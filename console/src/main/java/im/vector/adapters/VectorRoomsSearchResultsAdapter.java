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
import android.text.Html;
import android.text.Spanned;
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
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.EventUtils;

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
public class VectorRoomsSearchResultsAdapter extends VectorMessagesAdapter {

    public VectorRoomsSearchResultsAdapter(MXSession session, Context context, MXMediasCache mediasCache) {
        super(session, context, mediasCache);
        setNotifyOnChange(true);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.adapter_item_vector_searches, parent, false);
        }

        MessageRow row = getItem(position);
        Event event = row.getEvent();

        // retrieve the textViews
        TextView timeTextView = (TextView) convertView.findViewById(R.id.searches_time);
        TextView roomTextView = (TextView) convertView.findViewById(R.id.searches_room_name);
        TextView messageTextView = (TextView) convertView.findViewById(R.id.searches_message);

        // set the message text
        EventDisplay display = new EventDisplay(mContext, event, null);
        CharSequence text = display.getTextualDisplay(true);

        try {
            messageTextView.setText(text);
        } catch (Exception e) {
            // an exception might be triggered with HTML content
            // Indeed, the formatting can fail because of the single line display.
            // in this case, the formatting is ignored.
            messageTextView.setText(text.toString());
        }

        Room room = mSession.getDataHandler().getStore().getRoom(event.roomId);
        roomTextView.setText(VectorUtils.getRoomDisplayname(mContext, mSession, room));

        timeTextView.setText(AdapterUtils.tsToString(mContext, event.getOriginServerTs(), true));

        // day separator
        View headerView = convertView.findViewById(R.id.searches_message_header);
        String headerMessage = headerMessage(position);

        if (!TextUtils.isEmpty(headerMessage)) {
            headerView.setVisibility(View.VISIBLE);

            TextView headerText = (TextView)convertView.findViewById(R.id.messagesAdapter_message_header_text);
            headerText.setText(headerMessage);
        } else {
            headerView.setVisibility(View.GONE);
        }

        final int fPosition = position;

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (null != mMessagesAdapterEventsListener) {
                    return mMessagesAdapterEventsListener.onContentLongClick(fPosition);
                }

                return false;
            }
        });

        return convertView;
    }
}
