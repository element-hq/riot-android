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
import android.graphics.Typeface;
import org.matrix.androidsdk.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import im.vector.R;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorUtils;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.util.EventDisplay;

/**
 * An adapter which display the rooms list
 */
public class VectorRoomsSelectionAdapter extends ArrayAdapter<RoomSummary> {
    private static final String LOG_TAG = "VectRoomsSelectAdapt";

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;
    private MXSession mSession;

    /**
     * Constructor of a public rooms adapter.
     * @param context the context
     * @param layoutResourceId the layout
     */
    public VectorRoomsSelectionAdapter(Context context, int layoutResourceId, MXSession session) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        mSession = session;
    }

    /**
     * Provides the formatted timestamp to display.
     * null means that the timestamp text must be hidden.
     * @param event the event.
     * @return  the formatted timestamp to display.
     */
    private String getFormattedTimestamp(Event event) {
        String text =  AdapterUtils.tsToString(mContext, event.getOriginServerTs(), false);

        // don't display the today before the time
        String today = mContext.getString(R.string.today) + " ";
        if (text.startsWith(today)) {
            text = text.substring(today.length());
        }

        return text;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "getView : the session is not anymore valid");
            return convertView;
        }

        RoomSummary roomSummary = getItem(position);
        String roomName = roomSummary.getRoomName();

        // retrieve the UI items
        ImageView avatarImageView = (ImageView)convertView.findViewById(R.id.room_avatar_image_view);
        TextView roomNameTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
        TextView roomMessageTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomMessage);

        TextView timestampTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
        View separatorView = convertView.findViewById(R.id.recents_separator);

        // display the room avatar
        avatarImageView.setBackgroundColor(mContext.getResources().getColor(android.R.color.transparent));
        Room childRoom = mSession.getDataHandler().getRoom(roomSummary.getRoomId());

        if (null != childRoom) {
            VectorUtils.loadRoomAvatar(mContext, mSession, avatarImageView, childRoom);
        }

        if (roomSummary.getLatestReceivedEvent() != null) {
            EventDisplay eventDisplay = new EventDisplay(mContext, roomSummary.getLatestReceivedEvent(), roomSummary.getLatestRoomState());
            eventDisplay.setPrependMessagesWithAuthor(true);
            roomMessageTxtView.setText(eventDisplay.getTextualDisplay(ThemeUtils.getColor(mContext, R.attr.vector_text_gray_color)));

            timestampTxtView.setText(getFormattedTimestamp(roomSummary.getLatestReceivedEvent()));
            timestampTxtView.setTextColor(ThemeUtils.getColor(mContext, R.attr.vector_0_54_black_color));
            timestampTxtView.setTypeface(null, Typeface.NORMAL);
            timestampTxtView.setVisibility(View.VISIBLE);
        } else {
            roomMessageTxtView.setText("");
            timestampTxtView.setVisibility(View.GONE);
        }

        // display the room name
        roomNameTxtView.setText(roomName);

        // separator
        separatorView.setVisibility(View.VISIBLE);

        convertView.findViewById(R.id.bing_indicator_unread_message).setVisibility(View.INVISIBLE);
        convertView.findViewById(R.id.recents_groups_separator_line).setVisibility(View.GONE);
        convertView.findViewById(R.id.roomSummaryAdapter_action).setVisibility(View.GONE);
        convertView.findViewById(R.id.roomSummaryAdapter_action_image).setVisibility(View.GONE);

        convertView.findViewById(R.id.recents_groups_invitation_group).setVisibility(View.GONE);

        return convertView;
    }
}
