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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
public class VectorAddParticipantsAdapter extends ArrayAdapter<ParticipantAdapterItem> {

    private static final String LOG_TAG = "VectorAddPartsAdapt";

    // search events listener
    public interface OnParticipantsSearchListener {
        /**
         * The search is ended.
         * @param count the number of matched user
         */
        void onSearchEnd(int count);
    }

    //
    private Context mContext;
    private LayoutInflater mLayoutInflater;

    private MXSession mSession;
    private String mRoomId;
    private int mLayoutResourceId;

    private Collection<ParticipantAdapterItem> mUnusedParticipants = null;
    private ArrayList<String> mMemberUserIds = null;
    private ArrayList<String> mDisplayNamesList = null;
    private String mPattern = "";

    ParticipantAdapterItem mFirstEntry;

    /**
     * Create a room member adapter.
     * If a room id is defined, the adapter is in edition mode : the user can add / remove dynamically members or leave the room.
     * If there is none, the room is in creation mode : the user can add/remove members to create a new room.
     * @param context the context.
     * @param layoutResourceId the layout.
     * @param session the session.
     * @param roomId the room id.
     */
    public VectorAddParticipantsAdapter(Context context, int layoutResourceId, MXSession session, String roomId) {
        super(context, layoutResourceId);

        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mLayoutResourceId = layoutResourceId;
        mSession = session;

        mRoomId = roomId;
    }

    /**
     * Search a pattern in the known members list.
     * @param pattern the pattern to search
     */
    public void setSearchedPattern(String pattern, final OnParticipantsSearchListener searchListener) {
        setSearchedPattern(pattern, null, searchListener);
    }

    /**
     * Search a pattern in the known members list.
     * @param pattern the pattern to search
     * @param firstEntry the entry to display in the results list.
     */
    public void setSearchedPattern(String pattern, ParticipantAdapterItem firstEntry, OnParticipantsSearchListener searchListener) {
        if (null == pattern) {
            pattern = "";
        }

        if (!pattern.trim().equals(mPattern)) {
            mPattern = pattern.trim().toLowerCase();
            refresh(firstEntry, searchListener);
        } else if (null != searchListener) {
            searchListener.onSearchEnd(getCount());
        }
    }

    /**
     * Refresh the un-invited members
     */
    public void listOtherMembers() {
        // refresh only when performing a search
        if (TextUtils.isEmpty(mPattern)) {
            return;
        }

        IMXStore store = mSession.getDataHandler().getStore();

        // list the used members IDs
        mMemberUserIds = new ArrayList<String>();

        if ((null != mRoomId) && (null != store)) {
            Room fromRoom = store.getRoom(mRoomId);

            if (null != fromRoom) {
                Collection<RoomMember> members = fromRoom.getMembers();
                for (RoomMember member : members) {
                    if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN) || TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {
                        mMemberUserIds.add(member.getUserId());
                    }
                }
            }
        }

        HashMap<String, ParticipantAdapterItem> map = VectorUtils.listKnownParticipants(mContext, mSession);

        // add contact emails
        Collection<Contact> contacts = ContactsManager.getLocalContactsSnapshot(getContext());

        for(Contact contact : contacts) {
            for(String email : contact.mEmails) {
                if (!TextUtils.isEmpty(email)) {
                    Contact dummyContact = new Contact(email);
                    dummyContact.setDisplayName(email);
                    dummyContact.mEmails.add(email);

                    ParticipantAdapterItem participant = new ParticipantAdapterItem(dummyContact, getContext());
                    participant.mUserId = email;

                    // always use the member description over the contacts book one
                    // it avoid matching email to matrix id.
                    if (!map.containsKey(email)) {
                        map.put(email, participant);
                    }
                }
            }
        }

        // remove the known users
        for(String id : mMemberUserIds) {
            map.remove(id);
        }

        // retrieve the list
        mUnusedParticipants = map.values();

        // list the display names
        mDisplayNamesList = new ArrayList<>();

        for(ParticipantAdapterItem item : mUnusedParticipants) {
            if (!TextUtils.isEmpty(item.mDisplayName)) {
                mDisplayNamesList.add(item.mDisplayName);
            }
        }
    }

    /**
     * refresh the display
     */
    public void refresh() {
        refresh(null, null);
    }

    /**
     * Refresh the display.
     * @param theFirstEntry the first entry in the result.
     */
    public void refresh(final ParticipantAdapterItem theFirstEntry, final OnParticipantsSearchListener searchListener) {
        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "refresh : the session is not anymore active");
            return;
        }

        this.setNotifyOnChange(false);
        this.clear();
        ArrayList<ParticipantAdapterItem> nextMembersList = new ArrayList<ParticipantAdapterItem>();

        if (!TextUtils.isEmpty(mPattern)) {
            // the list members are refreshed in background to avoid UI locks
            if (null == mUnusedParticipants) {
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        listOtherMembers();

                        Handler handler = new Handler(Looper.getMainLooper());

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                refresh(theFirstEntry, searchListener);
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

            // check if each member matches the pattern
            for(ParticipantAdapterItem item: mUnusedParticipants) {
                if (item.matchWithPattern(pattern)) {
                    // for contact with emails, check if they are some matched matrix Id
                    if (null != item.mContact) {
                        // the email <-> matrix Ids matching is done asynchronously
                        if (item.mContact.hasMatridIds(mContext)) {
                            Log.d(LOG_TAG, "the contact " + item.mContact.getDisplayName() + " contains matrix ID");
                            item.mUserId = item.mContact.getFirstMatrixId().mMatrixId;
                        }
                    }

                    nextMembersList.add(item);
                }
            }

            ParticipantAdapterItem firstEntry = theFirstEntry;

            // detect if the user ID is defined in the known members list
            if ((null != mMemberUserIds) && (null != firstEntry)) {
                if (mMemberUserIds.indexOf(theFirstEntry.mUserId) >= 0) {
                    firstEntry = null;
                }
            }

            Collections.sort(nextMembersList, ParticipantAdapterItem.alphaComparator);

            if (null != firstEntry) {
                nextMembersList.add(0, firstEntry);

                // avoid multiple definitions
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

        this.setNotifyOnChange(true);
        this.addAll(nextMembersList);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        final ParticipantAdapterItem participant = getItem(position);

        // retrieve the ui items
        final ImageView thumbView = (ImageView) convertView.findViewById(R.id.filtered_list_avatar);
        final TextView nameTextView = (TextView) convertView.findViewById(R.id.filtered_list_name);
        final TextView statusTextView = (TextView) convertView.findViewById(R.id.filtered_list_status);
        final ImageView matrixUserBadge =  (ImageView) convertView.findViewById(R.id.filtered_list_matrix_user);

        // set the avatar
        if (null != participant.mAvatarBitmap) {
            thumbView.setImageBitmap(participant.mAvatarBitmap);
        } else {
            if ((null != mFirstEntry) && (position == 0)) {
                thumbView.setImageBitmap(VectorUtils.getAvatar(thumbView.getContext(), VectorUtils.getAvatarcolor(null), "@@", true));
            } else if ((null != participant.mUserId) && (android.util.Patterns.EMAIL_ADDRESS.matcher(participant.mUserId).matches())) {
                thumbView.setImageBitmap(VectorUtils.getAvatar(thumbView.getContext(), VectorUtils.getAvatarcolor(participant.mUserId), "@@", true));
            } else {
                if (TextUtils.isEmpty(participant.mUserId)) {
                    VectorUtils.loadUserAvatar(mContext, mSession, thumbView, participant.mAvatarUrl, participant.mDisplayName, participant.mDisplayName);
                } else {

                    // try to provide a better display for a participant when the user is known.
                    if (TextUtils.equals(participant.mUserId, participant.mDisplayName) || TextUtils.isEmpty(participant.mAvatarUrl)) {
                        IMXStore store = mSession.getDataHandler().getStore();

                        if (null != store) {
                            User user = store.getUser(participant.mUserId);

                            if (null != user) {
                                if (TextUtils.equals(participant.mUserId, participant.mDisplayName) && !TextUtils.isEmpty(user.displayname)) {
                                    participant.mDisplayName = user.displayname;
                                }

                                if (null == participant.mAvatarUrl) {
                                    participant.mAvatarUrl = user.avatar_url;
                                }
                            }
                        }
                    }

                    VectorUtils.loadUserAvatar(mContext, mSession, thumbView, participant.mAvatarUrl, participant.mUserId, participant.mDisplayName);
                }
            }
        }

        // set the display name
        String displayname = participant.mDisplayName;

        // detect if the username is used by several users
        int pos = mDisplayNamesList.indexOf(displayname);

        if (pos >= 0) {
            if (pos == mDisplayNamesList.lastIndexOf(displayname)) {
                pos = -1;
            }
        }

        if ((pos >= 0) && !TextUtils.isEmpty(participant.mUserId)) {
            displayname += " (" + participant.mUserId + ")";
        }

        nameTextView.setText(displayname);

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
                    VectorAddParticipantsAdapter.this.notifyDataSetChanged();
                }
            });
        }

        // the contact defines a matrix user but there is no way to get more information (presence, avatar)
        if ((participant.mContact != null) && (participant.mUserId != null) && !TextUtils.equals(participant.mUserId, participant.mDisplayName)) {
            statusTextView.setText(participant.mUserId);
            matrixUserBadge.setVisibility(View.VISIBLE);
        }
        else {
            statusTextView.setText(status);
            matrixUserBadge.setVisibility(View.GONE);
        }

        View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("", nameTextView.getText());
                clipboard.setPrimaryClip(clip);

                Toast.makeText(mContext, mContext.getResources().getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();

                return true;
            }
        };

        // the cellLayout setOnLongClickListener might be trapped by the scroll management
        // so add it to some UI items.
        nameTextView.setOnLongClickListener(onLongClickListener);
        thumbView.setOnLongClickListener(onLongClickListener);

        // the checkbox is not managed here
        final CheckBox checkBox = (CheckBox)convertView.findViewById(R.id.filtered_list_checkbox);
        checkBox.setVisibility(View.GONE);

        return convertView;
    }
}
