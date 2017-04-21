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
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;

import butterknife.BindColor;
import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.util.RoomUtils;
import im.vector.util.VectorUtils;

public class RoomViewHolder extends RecyclerView.ViewHolder {
    private static final String LOG_TAG = PeopleAdapter.class.getSimpleName();

    @BindView(R.id.room_avatar)
    ImageView vRoomAvatar;

    @BindView(R.id.room_name)
    TextView vRoomName;

    @BindView(R.id.room_message)
    TextView vRoomLastMessage;

    @BindView(R.id.room_update_date)
    @Nullable
    TextView vRoomTimestamp;

    @BindView(R.id.indicator_unread_message)
    @Nullable
    View vRoomUnreadIndicator;

    @BindView(R.id.room_unread_count)
    TextView vRoomUnreadCount;

    @BindView(R.id.room_avatar_direct_chat_icon)
    View vRoomDirectChatIcon;

    @BindView(R.id.room_avatar_encrypted_icon)
    View vRoomEncryptedIcon;

    @BindView(R.id.room_more_action_click_area)
    @Nullable
    View vRoomMoreActionClickArea;

    @BindView(R.id.room_more_action_anchor)
    @Nullable
    View vRoomMoreActionAnchor;

    @BindColor(R.color.vector_fuchsia_color)
    int mFuchsiaColor;
    @BindColor(R.color.vector_green_color)
    int mGreenColor;
    @BindColor(R.color.vector_silver_color)
    int mSilverColor;

    // the session
    private MXSession mSession;

    // the context
    private Context mContext;

    // the more actions listener
    AbsAdapter.MoreRoomActionListener mMoreActionListener;

    public RoomViewHolder(final Context context, final MXSession session, final View itemView, final AbsAdapter.MoreRoomActionListener moreRoomActionListener) {
        super(itemView);
        ButterKnife.bind(this, itemView);

        mContext = context;
        mSession = session;

        mMoreActionListener = moreRoomActionListener;
    }

    /**
     * Refresh the holder layout
     * @param room the room
     * @param isDirectChat true when the room is a direct chat one
     * @param isInvitation true when the room is an invitation one
     */
    public void populateViews(final Room room, final boolean isDirectChat, final boolean isInvitation) {
        // sanity check
        if (null == room) {
            Log.e(LOG_TAG, "## populateViews() : null room");
            return;
        }

        final RoomSummary roomSummary = mSession.getDataHandler().getStore().getSummary(room.getRoomId());
        final Room childRoom =  mSession.getDataHandler().getStore().getRoom(roomSummary.getRoomId());

        int unreadMsgCount = roomSummary.getUnreadEventsCount();
        int highlightCount = 0;
        int notificationCount = 0;

        if (null != childRoom) {
            highlightCount = childRoom.getHighlightCount();
            notificationCount = childRoom.getNotificationCount();
        }

        int bingUnreadColor;
        if (isInvitation || (0 != highlightCount) || roomSummary.isHighlighted()) {
            bingUnreadColor = mFuchsiaColor;
        } else if (0 != room.getNotificationCount()) {
            bingUnreadColor = mGreenColor;
        } else if (0 != unreadMsgCount) {
            bingUnreadColor = mSilverColor;
        } else {
            bingUnreadColor = Color.TRANSPARENT;
        }

        if (isInvitation || (notificationCount > 0)) {
            vRoomUnreadCount.setText(isInvitation ? "!" : String.valueOf(notificationCount));
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

        vRoomDirectChatIcon.setVisibility(isDirectChat ? View.VISIBLE : View.INVISIBLE);
        vRoomEncryptedIcon.setVisibility(room.isEncrypted() ? View.VISIBLE : View.INVISIBLE);


        if (vRoomUnreadIndicator != null) {
            // set bing view background colour
            vRoomUnreadIndicator.setBackgroundColor(bingUnreadColor);
            vRoomUnreadIndicator.setVisibility(roomSummary.isInvited() ? View.INVISIBLE : View.VISIBLE);
        }

        if (vRoomTimestamp != null) {
            vRoomTimestamp.setText(RoomUtils.getRoomTimestamp(mContext, roomSummary.getLatestReceivedEvent()));
        }

        if (vRoomMoreActionClickArea != null && vRoomMoreActionAnchor != null) {
            vRoomMoreActionClickArea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mMoreActionListener) {
                        mMoreActionListener.onMoreActionClick(vRoomMoreActionAnchor, room);
                    }
                }
            });
        }
    }
}
