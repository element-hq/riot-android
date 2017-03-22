/*
 * Copyright 2017 Vector Creations Ltd
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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import butterknife.BindColor;
import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
import im.vector.util.RoomUtils;
import im.vector.util.VectorUtils;

public class RoomAdapter extends AbsListAdapter<Room, RoomAdapter.RoomViewHolder> {

    private final Context mContext;
    private final MXSession mSession;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public RoomAdapter(final Context context, final OnSelectItemListener<Room> listener) {
        super(R.layout.adapter_item_room_view, listener);
        mContext = context;
        mSession = Matrix.getInstance(context).getDefaultSession();
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected RoomViewHolder createViewHolder(View itemView) {
        return new RoomViewHolder(itemView);
    }

    @Override
    protected void populateViewHolder(RoomViewHolder viewHolder, Room item) {
        viewHolder.populateViews(item);
    }

    @Override
    protected List<Room> getFilterItems(List<Room> items, String pattern) {
        List<Room> filteredRoom = new ArrayList<>();
        for (final Room room : items) {

            final String roomName = VectorUtils.getRoomDisplayName(mContext, mSession, room);
            if (Pattern.compile(Pattern.quote(pattern), Pattern.CASE_INSENSITIVE)
                    .matcher(roomName)
                    .find()) {
                filteredRoom.add(room);
            }
        }
        return filteredRoom;
    }

    /*
     * *********************************************************************************************
     * View holder
     * *********************************************************************************************
     */

    class RoomViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.room_avatar)
        ImageView vRoomAvatar;

        @BindView(R.id.room_name)
        TextView vRoomName;

        @BindView(R.id.room_message)
        TextView vRoomLastMessage;

        @BindView(R.id.room_update_date)
        TextView vRoomTimestamp;

        @BindView(R.id.room_unread_count)
        TextView vRoomUnreadCount;

        @BindView(R.id.indicator_unread_message)
        View vRoomUnreadIndicator;

        @BindColor(R.color.vector_fuchsia_color) int mFuchsiaColor;
        @BindColor(R.color.vector_green_color) int mGreenColor;
        @BindColor(R.color.vector_silver_color) int mSilverColor;

        private RoomViewHolder(final View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        private void populateViews(final Room room) {
            final RoomSummary roomSummary = mSession.getDataHandler().getStore().getSummary(room.getRoomId());

            int unreadMsgCount = roomSummary.getUnreadEventsCount();
            int bingUnreadColor;
            if (0 != room.getHighlightCount() || roomSummary.isHighlighted()) {
                bingUnreadColor = mFuchsiaColor;
            } else if (0 != room.getNotificationCount()) {
                bingUnreadColor = mGreenColor;
            } else if (0 != unreadMsgCount) {
                bingUnreadColor = mSilverColor;
            } else {
                bingUnreadColor = Color.TRANSPARENT;
            }

            if (unreadMsgCount > 0) {
                vRoomUnreadCount.setText(String.valueOf(unreadMsgCount));
                vRoomUnreadCount.setTypeface(null, Typeface.BOLD);
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.RECTANGLE);
                shape.setCornerRadius(100);
                shape.setColor(bingUnreadColor);
                vRoomUnreadCount.setBackground(shape);
                vRoomUnreadCount.setVisibility(View.VISIBLE);
            } else {
                vRoomUnreadCount.setVisibility(View.GONE);
            }

            final String roomName = VectorUtils.getRoomDisplayName(mContext, mSession, room);
            vRoomName.setText(roomName);
            vRoomName.setTypeface(null, (0 != unreadMsgCount) ? Typeface.BOLD : Typeface.NORMAL);
            VectorUtils.loadRoomAvatar(mContext, mSession, vRoomAvatar, room);

            // get last message to be displayed
            CharSequence lastMsgToDisplay = RoomUtils.getRoomMessageToDisplay(mContext, mSession, roomSummary);
            vRoomLastMessage.setText(lastMsgToDisplay);

            // set bing view background colour
            vRoomUnreadIndicator.setBackgroundColor(bingUnreadColor);
            vRoomUnreadIndicator.setVisibility(roomSummary.isInvited() ? View.INVISIBLE : View.VISIBLE);

            vRoomTimestamp.setText(RoomUtils.getRoomTimestamp(mContext, roomSummary.getLatestReceivedEvent()));
        }
    }
}
