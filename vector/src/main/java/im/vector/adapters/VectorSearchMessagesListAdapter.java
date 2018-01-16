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
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.util.EventDisplay;

import im.vector.R;
import im.vector.util.RiotEventDisplay;
import im.vector.util.VectorUtils;

/**
 * An adapter which display a list of messages found after a search
 */
public class VectorSearchMessagesListAdapter extends VectorMessagesAdapter {

    // display the room name in the result view
    private final boolean mDisplayRoomName;
    private String mPattern;

    public VectorSearchMessagesListAdapter(MXSession session, Context context, boolean displayRoomName, MXMediasCache mediasCache) {
        super(session, context,
                R.layout.adapter_item_vector_search_message_text_emote_notice,
                R.layout.adapter_item_vector_search_message_image_video,
                R.layout.adapter_item_vector_search_message_text_emote_notice,
                R.layout.adapter_item_vector_search_message_room_member,
                R.layout.adapter_item_vector_search_message_text_emote_notice,
                R.layout.adapter_item_vector_search_message_file,
                R.layout.adapter_item_vector_search_message_image_video,
                -1,
                R.layout.adapter_item_vector_search_message_emoji,
                R.layout.adapter_item_vector_message_code,
                mediasCache);

        setNotifyOnChange(true);
        mDisplayRoomName = displayRoomName;
        mSearchHighlightMessageTextColor = ContextCompat.getColor(context, R.color.vector_green_color);
    }

    /**
     * Define the pattern to highlight into the message body.
     *
     * @param pattern the pattern to hilight
     */
    public void setTextToHighlight(String pattern) {
        mPattern = pattern;
    }

    @Override
    protected boolean mergeView(Event event, int position, boolean shouldBeMerged) {
        return false;
    }

    @Override
    protected boolean supportMessageRowMerge(MessageRow row) {
        return false;
    }

    @Override
    public View getView(int position, View convertView2, ViewGroup parent) {
        View convertView = super.getView(position, convertView2, parent);

        MessageRow row = getItem(position);
        Event event = row.getEvent();

        //  some items are always hidden
        convertView.findViewById(R.id.messagesAdapter_avatars_list).setVisibility(View.GONE);
        convertView.findViewById(R.id.messagesAdapter_message_separator).setVisibility(View.GONE);
        convertView.findViewById(R.id.messagesAdapter_action_image).setVisibility(View.GONE);
        convertView.findViewById(R.id.messagesAdapter_top_margin_when_no_room_name).setVisibility(mDisplayRoomName ? View.GONE : View.VISIBLE);
        convertView.findViewById(R.id.messagesAdapter_message_header).setVisibility(View.GONE);

        Room room = mSession.getDataHandler().getStore().getRoom(event.roomId);

        RoomState roomState = row.getRoomState();

        if (null == roomState) {
            roomState = room.getLiveState();
        }


        // refresh the avatar
        ImageView avatarView = convertView.findViewById(R.id.messagesAdapter_roundAvatar).findViewById(R.id.avatar_img);
        mHelper.loadMemberAvatar(avatarView, row);

        // display the sender
        TextView senderTextView = convertView.findViewById(R.id.messagesAdapter_sender);
        if (senderTextView != null) {
            senderTextView.setText(VectorMessagesAdapterHelper.getUserDisplayName(event.getSender(), roomState));
        }

        // display the body
        TextView bodyTextView = convertView.findViewById(R.id.messagesAdapter_body);
        // set the message text
        EventDisplay display = new RiotEventDisplay(mContext, event, (null != room) ? room.getLiveState() : null);
        CharSequence text = display.getTextualDisplay();

        if (null == text) {
            text = "";
        }

        try {
            highlightPattern(bodyTextView, new SpannableString(text), mPattern);
        } catch (Exception e) {
            // an exception might be triggered with HTML content
            // Indeed, the formatting can fail because of the single line display.
            // in this case, the formatting is ignored.
            bodyTextView.setText(text.toString());
        }

        // display timestamp
        TextView timeTextView = convertView.findViewById(R.id.messagesAdapter_timestamp);
        timeTextView.setText(AdapterUtils.tsToString(mContext, event.getOriginServerTs(), true));

        // display the room name
        View roomNameLayout = convertView.findViewById(R.id.messagesAdapter_message_room_name_layout);
        roomNameLayout.setVisibility(mDisplayRoomName ? View.VISIBLE : View.GONE);

        if (mDisplayRoomName) {
            TextView roomTextView = convertView.findViewById(R.id.messagesAdapter_message_room_name_textview);
            roomTextView.setText(VectorUtils.getRoomDisplayName(mContext, mSession, room));
        }

        // display the day
        View dayLayout = convertView.findViewById(R.id.messagesAdapter_search_message_day_separator);

        // day separator
        String headerMessage = headerMessage(position);

        if (!TextUtils.isEmpty(headerMessage)) {
            dayLayout.setVisibility(View.VISIBLE);

            TextView headerText = convertView.findViewById(R.id.messagesAdapter_message_header_text);
            headerText.setText(headerMessage);

            dayLayout.findViewById(R.id.messagesAdapter_message_header_top_margin).setVisibility(View.GONE);
            dayLayout.findViewById(R.id.messagesAdapter_message_header_bottom_margin).setVisibility(View.GONE);
        } else {
            dayLayout.setVisibility(View.GONE);
        }

        // message separator is only displayed when a message is not the last message in a day section
        convertView.findViewById(R.id.messagesAdapter_search_separator_line).setVisibility(!TextUtils.isEmpty(headerMessage(position + 1)) ? View.GONE : View.VISIBLE);

        final int fPosition = position;

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mVectorMessagesAdapterEventsListener) {
                    mVectorMessagesAdapterEventsListener.onContentClick(fPosition);
                }
            }
        });


        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (null != mVectorMessagesAdapterEventsListener) {
                    return mVectorMessagesAdapterEventsListener.onContentLongClick(fPosition);
                }

                return false;
            }
        });

        return convertView;
    }
}
