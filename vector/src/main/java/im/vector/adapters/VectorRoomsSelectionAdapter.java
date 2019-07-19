/*
 * Copyright 2015 OpenMarket Ltd
 * Copyright 2018 New Vector Ltd
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.EventDisplay;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.Event;

import im.vector.R;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.RiotEventDisplay;
import im.vector.util.VectorUtils;

/**
 * An adapter which display the rooms list
 */
public class VectorRoomsSelectionAdapter extends ArrayAdapter<RoomSummary> {
    private static final String LOG_TAG = VectorRoomsSelectionAdapter.class.getSimpleName();

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final int mLayoutResourceId;
    private final MXSession mSession;

    /**
     * Constructor of a public rooms adapter.
     *
     * @param context          the context
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
     *
     * @param event the event.
     * @return the formatted timestamp to display.
     */
    private String getFormattedTimestamp(Event event) {
        String text = AdapterUtils.tsToString(mContext, event.getOriginServerTs(), false);

        // don't display the today before the time
        String today = mContext.getString(R.string.today) + " ";
        if (text.startsWith(today)) {
            text = text.substring(today.length());
        }

        return text;
    }

    // TODO Recycling is not managed well here
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

        // retrieve the UI items
        ImageView avatarImageView = convertView.findViewById(R.id.adapter_item_recent_room_avatar);
        TextView roomNameTxtView = convertView.findViewById(R.id.roomSummaryAdapter_roomName);
        TextView roomMessageTxtView = convertView.findViewById(R.id.roomSummaryAdapter_roomMessage);

        TextView timestampTxtView = convertView.findViewById(R.id.roomSummaryAdapter_ts);
        View separatorView = convertView.findViewById(R.id.recents_separator);

        // display the room avatar
        Room childRoom = mSession.getDataHandler().getRoom(roomSummary.getRoomId());

        if (null != childRoom) {
            VectorUtils.loadRoomAvatar(mContext, mSession, avatarImageView, childRoom);
        }

        if (roomSummary.getLatestReceivedEvent() != null) {
            EventDisplay eventDisplay = new RiotEventDisplay(mContext);
            eventDisplay.setPrependMessagesWithAuthor(true);
            roomMessageTxtView.setText(eventDisplay.getTextualDisplay(ThemeUtils.INSTANCE.getColor(mContext, android.R.attr.textColorTertiary),
                    roomSummary.getLatestReceivedEvent(),
                    roomSummary.getLatestRoomState()));

            timestampTxtView.setText(getFormattedTimestamp(roomSummary.getLatestReceivedEvent()));
            timestampTxtView.setTextColor(ThemeUtils.INSTANCE.getColor(mContext, android.R.attr.textColorSecondary));
            timestampTxtView.setTypeface(null, Typeface.NORMAL);
            timestampTxtView.setVisibility(View.VISIBLE);
        } else {
            roomMessageTxtView.setText("");
            timestampTxtView.setVisibility(View.GONE);
        }

        Room room = mSession.getDataHandler().getRoom(roomSummary.getRoomId());
        if (room != null) {
            // display the room name
            String roomName = room.getRoomDisplayName(mContext);
            roomNameTxtView.setText(roomName);
        } else {
            roomNameTxtView.setText(null);
        }

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
