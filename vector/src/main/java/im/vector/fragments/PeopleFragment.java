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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.OnCheckedChanged;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.AbsListAdapter;
import im.vector.adapters.ContactAdapter;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.RoomAdapter;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
import im.vector.contacts.PIDsRetriever;
import im.vector.util.RoomUtils;
import im.vector.util.VectorUtils;
import im.vector.view.SimpleDividerItemDecoration;

public class PeopleFragment extends AbsHomeFragment implements ContactsManager.ContactsManagerListener {

    private static final String LOG_TAG = PeopleFragment.class.getSimpleName();

    @BindView(R.id.direct_chats_header)
    TextView mDirectChatsHeader;

    @BindView(R.id.direct_chats_recyclerview)
    RecyclerView mDirectChatsRecyclerView;

    @BindView(R.id.direct_chats_placeholder)
    View mDirectChatsPlaceholder;

    @BindView(R.id.local_contacts_header)
    TextView mLocalContactsHeader;

    @BindView(R.id.matrix_only_filter_checkbox)
    CheckBox mMatrixUserOnlyCheckbox;

    @BindView(R.id.local_contact_recyclerview)
    RecyclerView mLocalContactsRecyclerView;

    @BindView(R.id.local_contact_placeholder)
    View mLocalContactsPlaceholder;

    @BindView(R.id.known_contacts_header)
    TextView mKnownContactsHeader;

    @BindView(R.id.known_contact_recyclerview)
    RecyclerView mKnownContactsRecyclerView;

    @BindView(R.id.known_contact_placeholder)
    TextView mKnownContactsPlaceholder;

    @BindString(R.string.local_address_book_header)
    String mLocalContactsHeaderText;
    @BindString(R.string.known_contacts_header)
    String mKnownContactsHeaderText;

    private RoomAdapter mDirectChatAdapter;
    private ContactAdapter mLocalContactAdapter;
    private ContactAdapter mKnownContactAdapter;

    private List<Room> mDirectChats = new ArrayList<>();
    private List<ParticipantAdapterItem> mLocalContacts = new ArrayList<>();
    private List<ParticipantAdapterItem> mKnownContacts = new ArrayList<>();

    // way to detect that the contacts list has been updated
    private int mContactsSnapshotSession = -1;
    private MXEventListener mEventsListener;

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
        mEventsListener = new MXEventListener() {
            @Override
            public void onPresenceUpdate(final Event event, final User user) {
                mLocalContactAdapter.updateItemWithUser(user);
                mKnownContactAdapter.updateItemWithUser(user);
            }
        };

        prepareViews();

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_MEMBERS_SEARCH, getActivity(), this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mSession.getDataHandler().addListener(mEventsListener);
        ContactsManager.getInstance().addListener(this);
        // Direct chats
        initDirectChatsData();
        initDirectChatsViews();

        // Local address book
        initContactsData();
        initContactsViews();
    }


    @Override
    public void onPause() {
        super.onPause();
        mSession.getDataHandler().removeListener(mEventsListener);
        ContactsManager.getInstance().removeListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_MEMBERS_SEARCH) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ContactsManager.getInstance().refreshLocalContactsSnapshot();
            } else {
                initContactsData();
                initContactsViews();
            }
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
        mDirectChatAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Toast.makeText(getActivity(), "onFilterComplete " + pattern, Toast.LENGTH_SHORT).show();
                updateDirectChatsDisplay(count);
                listener.onFilterDone(count);
            }
        });
        mLocalContactAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                updateLocalContactsDisplay(count);
                listener.onFilterDone(count);
            }
        });
        mKnownContactAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                updateKnownContactsDisplay(false, count);
                listener.onFilterDone(count);
            }
        });
    }

    @Override
    protected void onResetFilter() {
        mDirectChatAdapter.getFilter().filter("");
        mKnownContactAdapter.getFilter().filter("");
        mLocalContactAdapter.getFilter().filter("");
        updateDirectChatsDisplay(mDirectChatAdapter.getItemCount());
        updateKnownContactsDisplay(true, mKnownContactAdapter.getItemCount());
        updateLocalContactsDisplay(mLocalContactAdapter.getItemCount());
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    /**
     * Prepare views
     */
    private void prepareViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        SimpleDividerItemDecoration dividerItemDecoration =
                new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.HORIZONTAL, margin);

        // Direct chats
        mDirectChatsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mDirectChatsRecyclerView.addItemDecoration(dividerItemDecoration);
        mDirectChatsRecyclerView.setHasFixedSize(true);
        mDirectChatsRecyclerView.setNestedScrollingEnabled(false);
        mDirectChatAdapter = new RoomAdapter(getActivity(), new AbsListAdapter.OnSelectItemListener<Room>() {
            @Override
            public void onSelectItem(Room room, int position) {
                onRoomSelected(room, position);
            }
        });
        mDirectChatsRecyclerView.setAdapter(mDirectChatAdapter);

        // Local address book
        mLocalContactsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mLocalContactsRecyclerView.addItemDecoration(dividerItemDecoration);
        mLocalContactsRecyclerView.setHasFixedSize(true);
        mLocalContactsRecyclerView.setNestedScrollingEnabled(false);
        mLocalContactAdapter = new ContactAdapter(getActivity(), ParticipantAdapterItem.alphaComparator, new AbsListAdapter.OnSelectItemListener<ParticipantAdapterItem>() {
            @Override
            public void onSelectItem(ParticipantAdapterItem item, int position) {
                onContactSelected(item);
            }
        });

        // Known contacts
        mKnownContactsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mKnownContactsRecyclerView.addItemDecoration(dividerItemDecoration);
        mKnownContactsRecyclerView.setHasFixedSize(true);
        mKnownContactsRecyclerView.setNestedScrollingEnabled(false);
        mKnownContactAdapter = new ContactAdapter(getActivity(), ParticipantAdapterItem.getComparator(mSession), new AbsListAdapter.OnSelectItemListener<ParticipantAdapterItem>() {
            @Override
            public void onSelectItem(ParticipantAdapterItem item, int position) {
                onContactSelected(item);
            }
        });
    }

    /**
     * Update the display of direct chats views depending on content
     *
     * @param count
     */
    private void updateDirectChatsDisplay(final int count) {
        if (count > 0) {
            mDirectChatsRecyclerView.setVisibility(View.VISIBLE);
            mDirectChatsPlaceholder.setVisibility(View.GONE);
        } else {
            mDirectChatsPlaceholder.setVisibility(View.VISIBLE);
            mDirectChatsRecyclerView.setVisibility(View.GONE);
        }
    }

    /**
     * Update the display of local contacts views depending on content
     *
     * @param count
     */
    private void updateLocalContactsDisplay(final int count) {
        if (count > 0) {
            mLocalContactsHeader.setText(mLocalContactsHeaderText.concat(" (" + count + ")"));
            mLocalContactsRecyclerView.setVisibility(View.VISIBLE);
            mLocalContactsPlaceholder.setVisibility(View.GONE);
        } else {
            mLocalContactsHeader.setText(mLocalContactsHeaderText);
            mLocalContactsPlaceholder.setVisibility(View.VISIBLE);
            mLocalContactsRecyclerView.setVisibility(View.GONE);
        }
    }

    /**
     * Update the display of known contacts views depending on content
     *
     * @param count
     */
    private void updateKnownContactsDisplay(final boolean firstTime, final int count) {
        if (count > 0 ) {
            mKnownContactsHeader.setText(mKnownContactsHeaderText.concat(" (" + count + ")"));
            mKnownContactsRecyclerView.setVisibility(View.VISIBLE);
            mKnownContactsPlaceholder.setVisibility(View.GONE);
        } else {
            mKnownContactsHeader.setText(mKnownContactsHeaderText);
            if (firstTime) {
                mKnownContactsPlaceholder.setText(R.string.people_search_too_many_contacts);
            } else {
                mKnownContactsPlaceholder.setText(R.string.no_result_placeholder);
            }
            mKnownContactsPlaceholder.setVisibility(View.VISIBLE);
            mKnownContactsRecyclerView.setVisibility(View.GONE);
        }
    }

    /*
     * *********************************************************************************************
     * Data management
     * *********************************************************************************************
     */

    /**
     * Fill the direct chats adapter with data
     */
    private void initDirectChatsData() {
        final List<String> directChatIds = mSession.getDirectChatRoomIdsList();
        if (directChatIds != null && !directChatIds.isEmpty()) {
            mDirectChats = new ArrayList<>();

            for (String roomId : directChatIds) {
                mDirectChats.add(mSession.getDataHandler().getRoom(roomId));
            }

            Collections.sort(mDirectChats, RoomUtils.getRoomsDateComparator(mSession));
        }
    }


    /**
     * Fill the local address book and known contacts adapters with data
     */
    private void initContactsData() {
        ContactsManager.getInstance().retrievePids();

        if (mContactsSnapshotSession == -1 || mContactsSnapshotSession != ContactsManager.getInstance().getLocalContactsSnapshotSession()) {
            // First time on the screen or contact data outdated
            mLocalContacts.clear();
            mKnownContacts.clear();

            List<ParticipantAdapterItem> participants = new ArrayList<>();
            participants.addAll(VectorUtils.listKnownParticipants(mSession).values());
            participants.addAll(getContacts());

            // Build lists
            for (ParticipantAdapterItem item : participants) {
                if (item.mContact != null) {
                    if (!mMatrixUserOnlyCheckbox.isChecked() || !item.mContact.getMatrixIdMediums().isEmpty()) {
                        mLocalContacts.add(item);
                    }
                } else {
                    mKnownContacts.add(item);
                }
            }
        }
    }

    /**
     * Restore adapters/filter data after activity has been recreated
     *
     * @param savedInstanceState
     */
    private void restoreData(Bundle savedInstanceState) {
        // TODO Restore adapter items + filter
    }

    /*
     * *********************************************************************************************
     * User action management
     * *********************************************************************************************
     */

    @OnCheckedChanged(R.id.matrix_only_filter_checkbox)
    public void showMatrixUsersOnly(final boolean checked) {
        mLocalContactAdapter.setItems(checked ? getMatrixUsers() : mLocalContacts,
                new Filter.FilterListener() {
                    @Override
                    public void onFilterComplete(int count) {
                        updateLocalContactsDisplay(count);
                    }
                });
    }

    /**
     * Handle the click on a direct chat
     *
     * @param room
     * @param adapterPosition
     */
    private void onRoomSelected(final Room room, final int adapterPosition) {
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

    /**
     * Handle the click on a local or known contact
     *
     * @param item
     */
    private void onContactSelected(final ParticipantAdapterItem item) {
        if (item.mIsValid) {
            Intent startRoomInfoIntent = new Intent(getActivity(), VectorMemberDetailsActivity.class);
            startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, item.mUserId);
            startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
            startActivity(startRoomInfoIntent);
        }
    }

    /*
     * *********************************************************************************************
     * Utils
     * *********************************************************************************************
     */

    /**
     * Retrieve the contacts
     *
     * @return
     */
    private List<ParticipantAdapterItem> getContacts() {
        List<ParticipantAdapterItem> participants = new ArrayList<>();

        Collection<Contact> contacts = ContactsManager.getInstance().getLocalContactsSnapshot();
        mContactsSnapshotSession = ContactsManager.getInstance().getLocalContactsSnapshotSession();

        if (null != contacts) {
            for (Contact contact : contacts) {
                for (String email : contact.getEmails()) {
                    if (!TextUtils.isEmpty(email) && !ParticipantAdapterItem.isBlackedListed(email)) {
                        Contact dummyContact = new Contact(email);
                        dummyContact.setDisplayName(contact.getDisplayName());
                        dummyContact.addEmailAdress(email);
                        dummyContact.setThumbnailUri(contact.getThumbnailUri());

                        ParticipantAdapterItem participant = new ParticipantAdapterItem(dummyContact);

                        Contact.MXID mxid = PIDsRetriever.getInstance().getMXID(email);

                        if (null != mxid) {
                            participant.mUserId = mxid.mMatrixId;
                        } else {
                            participant.mUserId = email;
                        }
                        participants.add(participant);
                    }
                }

                for (Contact.PhoneNumber pn : contact.getPhonenumbers()) {
                    Contact.MXID mxid = PIDsRetriever.getInstance().getMXID(pn.mMsisdnPhoneNumber);

                    if (null != mxid) {
                        Contact dummyContact = new Contact(pn.mMsisdnPhoneNumber);
                        dummyContact.setDisplayName(contact.getDisplayName());
                        dummyContact.addPhoneNumber(pn.mRawPhoneNumber, pn.mE164PhoneNumber);
                        dummyContact.setThumbnailUri(contact.getThumbnailUri());
                        ParticipantAdapterItem participant = new ParticipantAdapterItem(dummyContact);
                        participant.mUserId = mxid.mMatrixId;
                        participants.add(participant);
                    }
                }
            }
        }

        return participants;
    }

    private List<ParticipantAdapterItem> getMatrixUsers() {
        List<ParticipantAdapterItem> matrixUsers = new ArrayList<>();
        for (ParticipantAdapterItem item : mLocalContacts) {
            if (!item.mContact.getMatrixIdMediums().isEmpty()) {
                matrixUsers.add(item);
            }
        }
        return matrixUsers;
    }

    /**
     * Init direct chats view with data and update its display
     */
    private void initDirectChatsViews() {
        mDirectChatAdapter.setItems(mDirectChats, null);
        updateDirectChatsDisplay(mDirectChats.size());

    }

    /**
     * Init contacts views with data and update their display
     */
    private void initContactsViews() {
        mLocalContactAdapter.setItems(mLocalContacts, null);
        mLocalContactsRecyclerView.setAdapter(mLocalContactAdapter);
        updateLocalContactsDisplay(mLocalContacts.size());
        mKnownContactAdapter.setItems(mKnownContacts, null);
        mKnownContactsRecyclerView.setAdapter(mKnownContactAdapter);
        updateKnownContactsDisplay(mCurrentFilter == null, mKnownContacts.size());
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    @Override
    public void onRefresh() {
        initContactsData();
        initContactsViews();
    }

    @Override
    public void onPIDsUpdate() {
        final List<ParticipantAdapterItem> newContactList = getContacts();
        if (!mLocalContacts.containsAll(newContactList)) {
            mLocalContacts.clear();
            mLocalContacts.addAll(newContactList);
            mLocalContactAdapter.setItems(mLocalContacts, new Filter.FilterListener() {
                @Override
                public void onFilterComplete(int count) {
                    updateLocalContactsDisplay(count);
                }
            });
        }
    }

    @Override
    public void onContactPresenceUpdate(Contact contact, String matrixId) {

    }
}
