/*
 * Copyright 2017 Vector Creations Ltd
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
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.MXPatterns;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.store.IMXStore;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.RoomUtils;
import im.vector.util.VectorUtils;
import im.vector.util.ViewUtilKt;

public class RoomViewHolder extends RecyclerView.ViewHolder {
    private static final String LOG_TAG = RoomViewHolder.class.getSimpleName();

    @BindView(R.id.room_avatar)
    ImageView vRoomAvatar;

    @BindView(R.id.room_name)
    TextView vRoomName;

    @BindView(R.id.room_name_server)
    @Nullable
    TextView vRoomNameServer;

    @BindView(R.id.room_message)
    @Nullable
    TextView vRoomLastMessage;

    @BindView(R.id.room_update_date)
    @Nullable
    TextView vRoomTimestamp;

    @BindView(R.id.indicator_unread_message)
    @Nullable
    View vRoomUnreadIndicator;

    @BindView(R.id.room_unread_count)
    TextView vRoomUnreadCount;

    @BindView(R.id.direct_chat_indicator)
    @Nullable
    View mDirectChatIndicator;

    @BindView(R.id.room_avatar_encrypted_icon)
    View vRoomEncryptedIcon;

    @BindView(R.id.room_more_action_click_area)
    @Nullable
    View vRoomMoreActionClickArea;

    @BindView(R.id.room_more_action_anchor)
    @Nullable
    View vRoomMoreActionAnchor;


    public RoomViewHolder(final View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
    }

    /**
     * Refresh the holder layout
     *
     * @param room                   the room
     * @param isDirectChat           true when the room is a direct chat one
     * @param isInvitation           true when the room is an invitation one
     * @param moreRoomActionListener
     */
    public void populateViews(final Context context,
                              final MXSession session,
                              final Room room,
                              final boolean isDirectChat,
                              final boolean isInvitation,
                              final AbsAdapter.MoreRoomActionListener moreRoomActionListener) {
        // sanity check
        if (null == room) {
            Log.e(LOG_TAG, "## populateViews() : null room");
            return;
        }

        if (null == session) {
            Log.e(LOG_TAG, "## populateViews() : null session");
            return;
        }

        if (null == session.getDataHandler()) {
            Log.e(LOG_TAG, "## populateViews() : null dataHandler");
            return;
        }

        IMXStore store = session.getDataHandler().getStore(room.getRoomId());

        if (null == store) {
            Log.e(LOG_TAG, "## populateViews() : null Store");
            return;
        }

        final RoomSummary roomSummary = store.getSummary(room.getRoomId());

        if (null == roomSummary) {
            Log.e(LOG_TAG, "## populateViews() : null roomSummary");
            return;
        }

        int unreadMsgCount = roomSummary.getUnreadEventsCount();
        int highlightCount;
        int notificationCount;

        highlightCount = roomSummary.getHighlightCount();
        notificationCount = roomSummary.getNotificationCount();

        // fix a crash reported by GA
        if ((null != room.getDataHandler()) && room.getDataHandler().getBingRulesManager().isRoomMentionOnly(room.getRoomId())) {
            notificationCount = highlightCount;
        }

        int bingUnreadColor;

        if (isInvitation || (0 != highlightCount)) {
            bingUnreadColor = ContextCompat.getColor(context, R.color.vector_fuchsia_color);
        } else if (0 != notificationCount) {
            bingUnreadColor = ThemeUtils.INSTANCE.getColor(context, R.attr.vctr_notice_secondary);
        } else if (0 != unreadMsgCount) {
            bingUnreadColor = ThemeUtils.INSTANCE.getColor(context, R.attr.vctr_unread_room_indent_color);
        } else {
            bingUnreadColor = Color.TRANSPARENT;
        }

        if (isInvitation || (notificationCount > 0)) {
            vRoomUnreadCount.setText(isInvitation ? "!" : RoomUtils.formatUnreadMessagesCounter(notificationCount));
            vRoomUnreadCount.setTypeface(null, Typeface.BOLD);
            ViewUtilKt.setRoundBackground(vRoomUnreadCount, bingUnreadColor);
            vRoomUnreadCount.setVisibility(View.VISIBLE);
        } else {
            vRoomUnreadCount.setVisibility(View.GONE);
        }

        String roomName = room.getRoomDisplayName(context);
        if (vRoomNameServer != null) {
            // This view holder is for the home page, we have up to two lines to display the name
            if (MXPatterns.isRoomAlias(roomName)) {
                // Room alias, split to display the server name on second line
                final String[] roomAliasSplitted = roomName.split(":");
                final String firstLine = roomAliasSplitted[0] + ":";
                final String secondLine = roomAliasSplitted[1];
                vRoomName.setLines(1);
                vRoomName.setText(firstLine);
                vRoomNameServer.setText(secondLine);
                vRoomNameServer.setVisibility(View.VISIBLE);
                vRoomNameServer.setTypeface(null, (0 != unreadMsgCount) ? Typeface.BOLD : Typeface.NORMAL);
            } else {
                // Allow the name to take two lines
                vRoomName.setLines(2);
                vRoomNameServer.setVisibility(View.GONE);
                vRoomName.setText(roomName);
            }
        } else {
            vRoomName.setText(roomName);
        }
        vRoomName.setTypeface(null, (0 != unreadMsgCount) ? Typeface.BOLD : Typeface.NORMAL);

        VectorUtils.loadRoomAvatar(context, session, vRoomAvatar, room);

        // get last message to be displayed
        if (vRoomLastMessage != null) {
            CharSequence lastMsgToDisplay = RoomUtils.getRoomMessageToDisplay(context, session, roomSummary);
            vRoomLastMessage.setText(lastMsgToDisplay);
        }

        if (mDirectChatIndicator != null) {
            mDirectChatIndicator.setVisibility(isDirectChat ? View.VISIBLE : View.INVISIBLE);
        }
        vRoomEncryptedIcon.setVisibility(room.isEncrypted() ? View.VISIBLE : View.INVISIBLE);

        if (vRoomUnreadIndicator != null) {
            // set bing view background colour
            vRoomUnreadIndicator.setBackgroundColor(bingUnreadColor);
            vRoomUnreadIndicator.setVisibility(roomSummary.isInvited() ? View.INVISIBLE : View.VISIBLE);
        }

        if (vRoomTimestamp != null) {
            vRoomTimestamp.setText(RoomUtils.getRoomTimestamp(context, roomSummary.getLatestReceivedEvent()));
        }

        if (vRoomMoreActionClickArea != null && vRoomMoreActionAnchor != null) {
            vRoomMoreActionClickArea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != moreRoomActionListener) {
                        moreRoomActionListener.onMoreActionClick(vRoomMoreActionAnchor, room);
                    }
                }
            });
        }
    }
}
