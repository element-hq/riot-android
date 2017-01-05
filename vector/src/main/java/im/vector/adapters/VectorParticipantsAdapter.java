/*
 * Copyright 2016 OpenMarket Ltd
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
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import org.matrix.androidsdk.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
import im.vector.util.VectorUtils;

/**
 * This class displays the users search results list.
 * The first list row can be customized.
 */
public class VectorParticipantsAdapter extends BaseExpandableListAdapter {

    private static final String LOG_TAG = "VectorAddPartsAdapt";

    // search events listener
    public interface OnParticipantsSearchListener {
        /**
         * The search is ended.
         *
         * @param count the number of matched user
         */
        void onSearchEnd(int count);
    }

    // defines the search method
    // contains the pattern
    public static final String SEARCH_METHOD_CONTAINS = "SEARCH_METHOD_CONTAINS";
    // starts with
    public static final String SEARCH_METHOD_STARTS_WITH = "SEARCH_METHOD_STARTS_WITH";

    // layout info
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;

    // account info
    private final MXSession mSession;
    private final String mRoomId;

    // used layouts
    private final int mCellLayoutResourceId;
    private final int mHeaderLayoutResourceId;

    // participants list
    private Collection<ParticipantAdapterItem> mUnusedParticipants = null;
    private ArrayList<String> mMemberUserIds = null;
    private ArrayList<String> mDisplayNamesList = null;
    private String mPattern = "";
    private String mSearchMethod = SEARCH_METHOD_CONTAINS;

    // display the private rooms participants
    private Comparator<ParticipantAdapterItem> mPrepopulationSortMethod;
    private Collection<ParticipantAdapterItem> mPrivateRoomsParticipants = null;

    private List<ParticipantAdapterItem> mItemsToHide = new ArrayList<>();

    // the participant sort method
    private Comparator<ParticipantAdapterItem> mSortMethod = ParticipantAdapterItem.alphaComparator;

    // define the first entry to set
    private ParticipantAdapterItem mFirstEntry;

    // the participants can be split in sections
    ArrayList<ArrayList<ParticipantAdapterItem>> mParticipantsListsList = new ArrayList<>();
    private int mFirstEntryPosition = -1;
    private int mContactBookSectionPosition = -1;
    private int mRoomContactsSectionPosition = -1;

    /**
     * Create a room member adapter.
     * If a room id is defined, the adapter is in edition mode : the user can add / remove dynamically members or leave the room.
     * If there is none, the room is in creation mode : the user can add/remove members to create a new room.
     *
     * @param context          the context.
     * @param cellLayoutResourceId the cell layout.
     * @param headerLayoutResourceId the header layout
     * @param session          the session.
     * @param roomId           the room id.
     */
    public VectorParticipantsAdapter(Context context, int cellLayoutResourceId, int headerLayoutResourceId, MXSession session, String roomId) {
        mContext = context;

        mLayoutInflater = LayoutInflater.from(context);
        mCellLayoutResourceId = cellLayoutResourceId;
        mHeaderLayoutResourceId = headerLayoutResourceId;

        mSession = session;
        mRoomId = roomId;
    }

    /**
     * Search a pattern in the known members list.
     *
     * @param pattern        the pattern to search
     * @param searchMethod   the search method
     * @param searchListener the search result listener
     */
    public void setSearchedPattern(String pattern, String searchMethod, final OnParticipantsSearchListener searchListener) {
        setSearchedPattern(pattern, searchMethod, null, searchListener);
    }

    /**
     * Set the pre-population sort method.
     * If a method is defined, it implies that the prepopulation is enabled
     * i.e. the private rooms members are displayed when there is no pattern to searcj.
     * @param sortMethod the sort method.
     */
    public void setPrepopulate(Comparator<ParticipantAdapterItem> sortMethod) {
        mPrepopulationSortMethod = sortMethod;
    }

    /**
     * Search a pattern in the known members list.
     * @param pattern the pattern to search
     * @param searchMethod the search method
     * @param firstEntry the entry to display in the results list.
     * @param searchListener the search result listener
     */
    public void setSearchedPattern(String pattern, String searchMethod, ParticipantAdapterItem firstEntry, OnParticipantsSearchListener searchListener) {
        if (null == pattern) {
            pattern = "";
        } else {
            pattern = pattern.toLowerCase();
        }

        if (!pattern.trim().equals(mPattern) || ((null != mPrepopulationSortMethod) && TextUtils.isEmpty(pattern))) {
            mPattern = pattern.trim().toLowerCase();
            refresh(searchMethod, firstEntry, searchListener);
        } else if (null != searchListener) {
            int displayedItemsCount = 0;

            for(ArrayList<ParticipantAdapterItem> list : mParticipantsListsList) {
                displayedItemsCount += list.size();
            }

            searchListener.onSearchEnd(displayedItemsCount);
        }
    }

    /**
     * Set the sort method.
     * @param comparator the sort method
     */
    public void setSortMethod(Comparator<ParticipantAdapterItem> comparator) {
        if (null == comparator) {
            mSortMethod = ParticipantAdapterItem.alphaComparator;
        } else {
            mSortMethod = comparator;
        }
    }

    /**
     * Refresh the un-invited members
     */
    private void listOtherMembers() {
        IMXStore store = mSession.getDataHandler().getStore();

        // list the used members IDs
        mMemberUserIds = new ArrayList<>();

        if ((null != mRoomId) && (null != store)) {
            Room fromRoom = store.getRoom(mRoomId);

            if (null != fromRoom) {
                Collection<RoomMember> members = fromRoom.getLiveState().getDisplayableMembers();
                for (RoomMember member : members) {
                    if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN) || TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {
                        mMemberUserIds.add(member.getUserId());
                    }
                }
            }
        }

        // remove the participant to hide
        for(ParticipantAdapterItem item : mItemsToHide) {
            mMemberUserIds.add(item.mUserId);
        }

        HashMap<String, ParticipantAdapterItem> privateRoomMembersMap  = new HashMap<>();
        if (null != store) {
            Collection<Room> rooms = store.getRooms();

            for(Room room : rooms) {
                if (!room.getLiveState().isPublic() && !room.isConferenceUserRoom()) {
                    Collection<RoomMember> members = room.getMembers();

                    for(RoomMember member : members) {
                        String userId = member.getUserId();

                        if (!privateRoomMembersMap.containsKey(userId) && !MXCallsManager.isConferenceUserId(userId)) {
                            // display only the join / invite members
                            if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN) || TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {
                                privateRoomMembersMap.put(member.getUserId(), new ParticipantAdapterItem(member));
                            }
                        }
                    }
                }
            }
        }

        HashMap<String, ParticipantAdapterItem> knownUsersMap = VectorUtils.listKnownParticipants(mSession);

        // add contact emails
        Collection<Contact> contacts = ContactsManager.getLocalContactsSnapshot(mContext);

        for(Contact contact : contacts) {
            for(String email : contact.getEmails()) {
                if (!TextUtils.isEmpty(email)) {
                    Contact dummyContact = new Contact(email);
                    dummyContact.setDisplayName(contact.getDisplayName());
                    dummyContact.addEmailAdress(email);

                    ParticipantAdapterItem participant = new ParticipantAdapterItem(dummyContact, mContext);
                    participant.mUserId = email;

                    // always use the member description over the contacts book one
                    // it avoid matching email to matrix id.
                    if (!knownUsersMap.containsKey(email)) {
                        knownUsersMap.put(email, participant);
                    }
                }
            }
        }

        // remove the known users
        for(String id : mMemberUserIds) {
            knownUsersMap.remove(id);
            privateRoomMembersMap.remove(id);
        }

        // retrieve the list
        mUnusedParticipants = knownUsersMap.values();

        if (null != mPrepopulationSortMethod) {
            ArrayList<ParticipantAdapterItem> list = new ArrayList<>(privateRoomMembersMap.values());
            Collections.sort(list, mPrepopulationSortMethod);
            mPrivateRoomsParticipants = list;
        }

        // list the display names
        mDisplayNamesList = new ArrayList<>();

        for(ParticipantAdapterItem item : mUnusedParticipants) {
            if (!TextUtils.isEmpty(item.mDisplayName)) {
                mDisplayNamesList.add(item.mDisplayName.toLowerCase());
            }
        }
    }

    /**
     * @return true if the known members list has been initialized.
     */
    public boolean isKnownMembersInitialized() {
        return null != mDisplayNamesList;
    }

    /**
     * Tells an item fullfill the search method.
     * @param item the item to test
     * @param searchMethod the search method
     * @param pattern the pattern
     * @return true if match the search method
     */
    private static boolean match(ParticipantAdapterItem item, String searchMethod, String pattern) {
        if (TextUtils.equals(searchMethod, SEARCH_METHOD_CONTAINS)) {
            return item.contains(pattern);
        } else {
            return item.startsWith(pattern);
        }
    }

    /**
     * Test if the contact is used by an entry of this adapter.
     * Refresh if required.
     * @param contact the updated contact
     * @param matrixId the linked matrix Id
     * @param firstVisiblePosition the first visible row
     * @param lastVisiblePosition the last visible row
     */
    public void onContactUpdate(Contact contact, String matrixId, int firstVisiblePosition, int lastVisiblePosition) {
        if (null != contact) {
            int pos = -1;

            if (mContactBookSectionPosition >= 0) {
                List<ParticipantAdapterItem> list = mParticipantsListsList.get(mContactBookSectionPosition);

                // detect of the contact is used in the adapter
                for (int index = 0; index < list.size(); index++) {
                    ParticipantAdapterItem item = list.get(index);

                    if (item.mContact == contact) {
                        pos = index;
                        item.mUserId = matrixId;
                        break;
                    }
                }
            }

            // the item has been found
            if (pos >= 0) {
                // refresh if a duplicated entry has been removed
                // or if the contact is displayed
                if (checkDuplicatedMatrixIds() ||
                        ((pos >= firstVisiblePosition) && (pos <= lastVisiblePosition))) {
                    notifyDataSetChanged();
                }
            }
        }
    }

    /**
     * Check if some entries use the same matrix ids.
     * If some use the same, prefer the room member one.
     * @return true if some duplicated entries have been removed.
     */
    private boolean checkDuplicatedMatrixIds() {
        boolean gotDuplicated = false;

        // if several entries have the same matrix id.
        // keep the dedicated contact book entry over the room participants
        ArrayList<String> matrixUserIds = new ArrayList<>();

        if (mContactBookSectionPosition >= 0) {
            List<ParticipantAdapterItem> list = mParticipantsListsList.get(mContactBookSectionPosition);

            for (int i = 0; i < list.size(); i++) {
                String userId = list.get(i).mUserId;

                if (!TextUtils.isEmpty(userId)) {
                    matrixUserIds.add(userId);
                }
            }
        }

        if ((matrixUserIds.size() > 0) && (mRoomContactsSectionPosition >= 0))  {
            ArrayList<ParticipantAdapterItem> itemsToRemove = new ArrayList<>();

            List<ParticipantAdapterItem> list = mParticipantsListsList.get(mRoomContactsSectionPosition);

            for (int i = 0; i < list.size(); i++) {
                ParticipantAdapterItem item = list.get(i);

                // if the entry is duplicated and comes from a contact
                if (matrixUserIds.contains(item.mUserId)) {
                    // remove it from the known users list
                    mUnusedParticipants.remove(item);
                    itemsToRemove.add(item);
                }
            }

            if (itemsToRemove.size() > 0) {
                list.removeAll(itemsToRemove);
                gotDuplicated = true;

                if (list.size() == 0) {
                    mParticipantsListsList.remove(list);
                    mRoomContactsSectionPosition = -1;
                    // assume that the participants are displayed after the contacts
                }
            }
        }


        return gotDuplicated;
    }

    /**
     * Defines a set of participant items to hide.
     * @param itemsToHide the set to hide
     */
    public void setHiddenParticipantItems(List<ParticipantAdapterItem> itemsToHide) {
        if (null != itemsToHide) {
            mItemsToHide = itemsToHide;
        }
    }

    /**
     * Refresh the display.
     * @param searchMethod the search method
     * @param theFirstEntry the first entry in the result.
     * @param searchListener the search result listener
     */
    private void refresh(final String searchMethod, final ParticipantAdapterItem theFirstEntry, final OnParticipantsSearchListener searchListener) {
        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "refresh : the session is not anymore active");
            return;
        }
        mSearchMethod = searchMethod;

        ArrayList<ParticipantAdapterItem> nextMembersList = new ArrayList<>();

        if (!TextUtils.isEmpty(mPattern) || (null != mPrepopulationSortMethod)) {
            // the list members are refreshed in background to avoid UI locks
            if (null == mUnusedParticipants) {
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        listOtherMembers();

                        Handler handler = new Handler(Looper.getMainLooper());

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                refresh(searchMethod, theFirstEntry, searchListener);
                            }
                        });
                    }
                });

                t.setPriority(Thread.MIN_PRIORITY);
                t.start();

                return;
            }

            // remove trailing spaces.
            String pattern = mPattern.trim().toLowerCase();

            HashMap<String, ParticipantAdapterItem> mContactItems = new HashMap<>();
            ArrayList<String> memberUserIds = new ArrayList<>();

            Collection<ParticipantAdapterItem> list = TextUtils.isEmpty(pattern) ? mPrivateRoomsParticipants : mUnusedParticipants;

            if (!TextUtils.isEmpty(pattern)) {
                // check if each member matches the pattern
                for (ParticipantAdapterItem item : list) {
                    if (match(item, searchMethod, pattern)) {
                        // for contact with emails, check if they are some matched matrix Id
                        if (null != item.mContact) {
                            // the email <-> matrix Ids matching is done asynchronously
                            if (item.mContact.hasMatridIds(mContext)) {
                                item.mUserId = item.mContact.getFirstMatrixId().mMatrixId;

                                // avoid duplicated entries between contact and member definitions
                                // always prefer a member definition
                                if (!memberUserIds.contains(item.mUserId) && !mContactItems.containsKey(item.mUserId)) {
                                    nextMembersList.add(item);
                                    mContactItems.put(item.mUserId, item);
                                }
                            } else {
                                nextMembersList.add(item);
                            }
                        } else {
                            // the user id was already by a contact
                            // prefer the member items over the contact ones.
                            if (mContactItems.containsKey(item.mUserId)) {
                                nextMembersList.remove(mContactItems.get(item.mUserId));
                                mContactItems.remove(item.mUserId);
                            }

                            nextMembersList.add(item);
                            memberUserIds.add(item.mUserId);
                        }
                    }
                }
            } else {
                nextMembersList.addAll(list);
            }

            ParticipantAdapterItem firstEntry = theFirstEntry;

            // detect if the user ID is defined in the known members list
            if ((null != mMemberUserIds) && (null != firstEntry)) {
                if (mMemberUserIds.indexOf(theFirstEntry.mUserId) >= 0) {
                    firstEntry = null;
                }
            }

            if (list == mUnusedParticipants) {
                Collections.sort(nextMembersList, mSortMethod);
            }

            if (null != firstEntry) {
                nextMembersList.add(0, firstEntry);

                // avoid multiple definitions of the written email
                for(int pos = 1; pos < nextMembersList.size(); pos++) {
                    ParticipantAdapterItem item = nextMembersList.get(pos);

                    if (TextUtils.equals(item.mUserId, firstEntry.mUserId)) {
                        nextMembersList.remove(pos);
                        break;
                    }
                }

                mFirstEntry = firstEntry;
            } else {
                mFirstEntry = null;
            }

            if (null != searchListener) {
                searchListener.onSearchEnd(nextMembersList.size());
            }
        }

        // split the participants in sections
        ArrayList<ParticipantAdapterItem> firstEntryList = new ArrayList<>();
        ArrayList<ParticipantAdapterItem> contactBookList = new ArrayList<>();
        ArrayList<ParticipantAdapterItem> roomContactsList = new ArrayList<>();

        for(ParticipantAdapterItem item : nextMembersList) {
            if (item == mFirstEntry) {
                firstEntryList.add(mFirstEntry);
            } else if (null != item.mContact) {
                contactBookList.add(item);
            } else {
                roomContactsList.add(item);
            }
        }

        mFirstEntryPosition = -1;
        mContactBookSectionPosition = -1;
        mRoomContactsSectionPosition = -1;

        int posCount = 0;

        mParticipantsListsList.clear();

        if (firstEntryList.size() > 0) {
            mParticipantsListsList.add(firstEntryList);
            mFirstEntryPosition = posCount;
            posCount++;
        }

        if (contactBookList.size() > 0) {
            mParticipantsListsList.add(contactBookList);
            mContactBookSectionPosition = posCount;
            posCount++;
        }

        if (roomContactsList.size() > 0) {
            mParticipantsListsList.add(roomContactsList);
            mRoomContactsSectionPosition = posCount;
            posCount++;
        }

        notifyDataSetChanged();
    }

    @Override
    public void onGroupCollapsed(int groupPosition) {
        super.onGroupCollapsed(groupPosition);
        // Do something here ?
    }

    @Override
    public void onGroupExpanded(int groupPosition) {
        super.onGroupExpanded(groupPosition);
        // Do something here ?
    }


    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }


    @Override
    public int getGroupCount() {
        return mParticipantsListsList.size();
    }

    private String getGroupTitle(int position) {
        if (position == mContactBookSectionPosition) {
            return mContext.getString(R.string.contact_book);
        } else if (position == mRoomContactsSectionPosition) {
            return mContext.getString(R.string.room_contact);
        } else {
            return "??";
        }
    }

    @Override
    public Object getGroup(int groupPosition) {
        return getGroupTitle(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return getGroupTitle(groupPosition).hashCode();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        if (groupPosition >= mParticipantsListsList.size()) {
            return 0;
        }

        return mParticipantsListsList.get(groupPosition).size();
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return null;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return 0L;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (null == convertView) {
            convertView = this.mLayoutInflater.inflate(this.mHeaderLayoutResourceId, null);
        }

        TextView sectionNameTxtView = (TextView)convertView.findViewById(org.matrix.androidsdk.R.id.heading);

        if (null != sectionNameTxtView) {
            sectionNameTxtView.setText(getGroupTitle(groupPosition));
        }

        ImageView imageView = (ImageView) convertView.findViewById(org.matrix.androidsdk.R.id.heading_image);

        if (!isExpanded) {
            imageView.setImageResource(R.drawable.ic_material_expand_less_black);
        } else {
            imageView.setImageResource(R.drawable.ic_material_expand_more_black);
        }

        View subLayout = convertView.findViewById(R.id.people_header_sub_layout);
        subLayout.setVisibility((groupPosition == mFirstEntryPosition) ? View.GONE : View.VISIBLE);

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mCellLayoutResourceId, parent, false);
        }

        // bound checks
        if (groupPosition >= mParticipantsListsList.size()) {
            return convertView;
        }

        List<ParticipantAdapterItem> list = mParticipantsListsList.get(groupPosition);

        if (childPosition >= list.size()) {
            return convertView;
        }

        final ParticipantAdapterItem participant = list.get(childPosition);

        // retrieve the ui items
        final ImageView thumbView = (ImageView) convertView.findViewById(R.id.filtered_list_avatar);
        final TextView nameTextView = (TextView) convertView.findViewById(R.id.filtered_list_name);
        final TextView statusTextView = (TextView) convertView.findViewById(R.id.filtered_list_status);
        final ImageView matrixUserBadge =  (ImageView) convertView.findViewById(R.id.filtered_list_matrix_user);

        // display the avatar
        participant.displayAvatar(mSession, thumbView);

        nameTextView.setText(participant.getUniqueDisplayName(mDisplayNamesList));

        // set the presence
        String status = "";

        User user = null;
        MXSession matchedSession = null;
        // retrieve the linked user
        ArrayList<MXSession> sessions = Matrix.getMXSessions(mContext);

        for(MXSession session : sessions) {
            if (null == user) {
                matchedSession = session;
                user = session.getDataHandler().getUser(participant.mUserId);
            }
        }

        if (null != user) {
            status = VectorUtils.getUserOnlineStatus(mContext, matchedSession, participant.mUserId, new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    Comparator<ParticipantAdapterItem> sortMethod = TextUtils.isEmpty(mPattern) ? mPrepopulationSortMethod : mSortMethod;

                    if (ParticipantAdapterItem.alphaComparator == sortMethod) {
                        VectorParticipantsAdapter.this.notifyDataSetChanged();
                    } else {
                        VectorParticipantsAdapter.this.refresh(mSearchMethod, mFirstEntry, null);
                    }
                }
            });
        }

        // the contact defines a matrix user but there is no way to get more information (presence, avatar)
        if (participant.mContact != null) {
            statusTextView.setText(participant.mUserId);
            boolean isMatrixUserId = !android.util.Patterns.EMAIL_ADDRESS.matcher(participant.mUserId).matches();
            matrixUserBadge.setVisibility(isMatrixUserId ? View.VISIBLE : View.GONE);
        }
        else {
            statusTextView.setText(status);
            matrixUserBadge.setVisibility(View.GONE);
        }

        // the checkbox is not managed here
        final CheckBox checkBox = (CheckBox)convertView.findViewById(R.id.filtered_list_checkbox);
        checkBox.setVisibility(View.GONE);

        return convertView;
    }
}
