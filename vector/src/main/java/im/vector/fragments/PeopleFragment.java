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

package im.vector.fragments;

import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Toast;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.AbsListAdapter;
import im.vector.adapters.RoomAdapter;
import im.vector.contacts.Contact;
import im.vector.util.RoomUtils;
import im.vector.view.SimpleDividerItemDecoration;

public class PeopleFragment extends AbsHomeFragment implements AbsListAdapter.OnSelectItemListener<Room> {

    @BindView(R.id.direct_chats_recyclerview)
    RecyclerView mDirectChatsRecyclerView;

    @BindView(R.id.local_contact_recyclerview)
    RecyclerView mLocalContactsRecyclerView;

    @BindView(R.id.known_contact_recyclerview)
    RecyclerView mKnownContactsRecyclerView;

    private RoomAdapter mDirectChatAdapter;

    private List<Room> mDirectChats;
    private List<Contact> mLocalContacts;
    private List<Contact> mKnownContacts;

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static PeopleFragment newInstance() {
        return new PeopleFragment();
    }

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_people, container, false);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initViews();

        if (savedInstanceState != null) {
            // Restore adapter items
        }
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected void onMarkAllAsRead() {

    }

    @Override
    protected void onFilter(final String pattern, final OnFilterListener listener) {
        //TODO adapter getFilter().filter(pattern, listener)
        //TODO call listener.onFilterDone(); when filter is completed for all adapters
        mDirectChatAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Toast.makeText(getActivity(), "onFilterComplete " + pattern, Toast.LENGTH_SHORT).show();

                //TODO move the line below so it's only called once when all adapters are done with filtering
                listener.onFilterDone(count);
            }
        });
    }

    @Override
    protected void onResetFilter() {

    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        // Direct chats
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mDirectChatsRecyclerView.setLayoutManager(layoutManager);
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        SimpleDividerItemDecoration dividerItemDecoration =
                new SimpleDividerItemDecoration(mDirectChatsRecyclerView.getContext(), DividerItemDecoration.HORIZONTAL, margin);
        mDirectChatsRecyclerView.addItemDecoration(dividerItemDecoration);
        mDirectChatAdapter = new RoomAdapter(getActivity(), this);
        mDirectChatsRecyclerView.setAdapter(mDirectChatAdapter);

        final List<String> directChatIds = mSession.getDirectChatRoomIdsList();
        mDirectChats = new ArrayList<>();

        for (String roomId : directChatIds) {
            mDirectChats.add(mSession.getDataHandler().getRoom(roomId));
        }

        Collections.sort(mDirectChats, RoomUtils.getRoomsDateComparator(mSession));
        mDirectChatAdapter.setItems(mDirectChats);

    }

    /*
     * *********************************************************************************************
     * User action management
     * *********************************************************************************************
     */

    @Override
    public void onSelectItem(Room room, int adapterPosition) {
        final String roomId;
        // cannot join a leaving room
        if (room == null || room.isLeaving()) {
            roomId = null;
        } else {
            roomId = room.getRoomId();
        }

        if (roomId != null) {
            final RoomSummary roomSummary = mSession.getDataHandler().getStore().getSummary(roomId);

            if (null != roomSummary) {
                room.sendReadReceipt(null);

                // Reset the highlight
                if (roomSummary.setHighlighted(false)) {
                    mSession.getDataHandler().getStore().flushSummary(roomSummary);
                }
            }

            // Update badge unread count in case device is offline
            CommonActivityUtils.specificUpdateBadgeUnreadCount(mSession, getContext());

            // Launch corresponding room activity
            HashMap<String, Object> params = new HashMap<>();
            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);

            CommonActivityUtils.goToRoomPage(getActivity(), mSession, params);
        }

        // Refresh the adapter item
        mDirectChatAdapter.notifyItemChanged(adapterPosition);
    }
}
