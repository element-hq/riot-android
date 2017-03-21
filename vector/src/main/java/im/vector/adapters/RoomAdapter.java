package im.vector.adapters;

import android.content.Context;
import android.graphics.Typeface;
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

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
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
        TextView vRoomFirstMessage;

        @BindView(R.id.room_update_date)
        TextView vRoomTimestamp;

        @BindView(R.id.room_unread_count)
        TextView vRoomUnreadCount;

        private RoomViewHolder(final View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        private void populateViews(final Room room) {
            final RoomSummary roomSummary = mSession.getDataHandler().getStore().getSummary(room.getRoomId());
            int unreadMsgCount = roomSummary.getUnreadEventsCount();

            final String roomName = VectorUtils.getRoomDisplayName(mContext, mSession, room);
            vRoomName.setText(roomName);
            vRoomName.setTypeface(null, (0 != unreadMsgCount) ? Typeface.BOLD : Typeface.NORMAL);
            VectorUtils.loadRoomAvatar(mContext, mSession, vRoomAvatar, room);

            vRoomUnreadCount.setText(unreadMsgCount);

        }
    }
}
