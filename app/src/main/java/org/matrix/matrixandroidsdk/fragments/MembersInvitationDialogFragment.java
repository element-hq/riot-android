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

package org.matrix.matrixandroidsdk.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.MembersInvitationAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

/**
 * A dialog fragment showing a list of room members for a given room.
 */
public class MembersInvitationDialogFragment extends DialogFragment {
    private static final String LOG_TAG = "MembersInvitationDialogFragment";

    public static final String ARG_ROOM_ID = "org.matrix.matrixandroidsdk.fragments.MembersInvitationDialogFragment.ARG_ROOM_ID";

    private ListView mListView;
    private MembersInvitationAdapter mAdapter;
    private MXSession mSession;
    private String mRoomId;

    public static MembersInvitationDialogFragment newInstance(MXSession session, String roomId) {
        MembersInvitationDialogFragment f = new MembersInvitationDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        f.setArguments(args);
        f.setSession(session);
        return f;
    }

    public void setSession(MXSession session) {
        mSession = session;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getActivity().getApplicationContext();
        mRoomId = getArguments().getString(ARG_ROOM_ID);
    }

    /**
     * Return the used medias cache.
     * This method can be overridden to use another medias cache
     * @return the used medias cache
     */
    public MXMediasCache getMXMediasCache() {
        return Matrix.getInstance(getActivity()).getMediasCache();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_dialog_invitation_member_list, null);
        builder.setView(view);
        initView(view);
        builder.setTitle(getString(R.string.title_activity_invite_user));

        builder.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {

                        ArrayList<RoomMember> members = mAdapter.getSelectedMembers();
                        ArrayList<String> userIDs = new ArrayList<String>();

                        for(RoomMember member : members) {
                            userIDs.add(member.getUserId());
                        }

                        Room room = mSession.getDataHandler().getRoom(mRoomId);
                        room.invite(userIDs, new SimpleApiCallback<Void>(getActivity()) {
                            @Override
                            public void onSuccess(Void info) {
                            }
                        });
                    }
                });


        builder.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {

                    }
                });

        return builder.create();
    }

    // Comparator to order members alphabetically
    private Comparator<RoomMember> alphaComparator = new Comparator<RoomMember>() {
        @Override
        public int compare(RoomMember member1, RoomMember member2) {
            String lhs = member1.displayname;

            if (null == lhs) {
                lhs = member1.getUserId();
            }

            String rhs = member2.displayname;

            if (null == rhs) {
                rhs = member2.getUserId();
            }

            if (lhs == null) {
                return -1;
            }
            else if (rhs == null) {
                return 1;
            }
            if (lhs.startsWith("@")) {
                lhs = lhs.substring(1);
            }
            if (rhs.startsWith("@")) {
                rhs = rhs.substring(1);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
        }
    };

    /**
     * Init the dialog view.
     * @param v the dialog view.
     */
    void initView(View v) {
        mListView = ((ListView)v.findViewById(R.id.listView_members));

        IMXStore store = mSession.getDataHandler().getStore();

        // remove the current room members
        ArrayList<String> idsToIgnore = new ArrayList<String>();
        Room room = store.getRoom(mRoomId);

        for(RoomMember member : room.getMembers()) {
            idsToIgnore.add(member.getUserId());
        }

        // get the members list
        ArrayList<RoomMember> members_OneToOne = new ArrayList<RoomMember>();
        ArrayList<String> ids_OneToOne = new ArrayList<String>();

        ArrayList<RoomMember> members_MaxTenMembers = new ArrayList<RoomMember>();
        ArrayList<String> ids_MaxTenMembers = new ArrayList<String>();

        ArrayList<RoomMember> members_BigRooms = new ArrayList<RoomMember>();
        ArrayList<String> ids_BigRooms = new ArrayList<String>();

        Collection<RoomSummary> summaries = store.getSummaries();

        for(RoomSummary summary : summaries) {
            // not the current summary
            if (!summary.getRoomId().equals(mRoomId)) {
                Collection<RoomMember> members = room.getMembers();

                for (RoomMember member : members) {
                    String userID = member.getUserId();

                    // accepted User ID or still active users
                    if ((idsToIgnore.indexOf(userID) < 0) && (RoomMember.MEMBERSHIP_JOIN.equals(member.membership))) {
                        int posOneToOne = ids_OneToOne.indexOf(userID);
                        int posTenMembers = ids_MaxTenMembers.indexOf(userID);
                        int posBigRooms = ids_BigRooms.indexOf(userID);

                        if (members.size() <= 2) {
                            if (posBigRooms >= 0) {
                                members_BigRooms.remove(posBigRooms);
                                ids_BigRooms.remove(posBigRooms);
                            }

                            if (posTenMembers >= 0) {
                                members_MaxTenMembers.remove(posTenMembers);
                                ids_MaxTenMembers.remove(posTenMembers);
                            }

                            if (posOneToOne < 0) {
                                members_OneToOne.add(member);
                                ids_OneToOne.add(member.getUserId());
                            }
                        } else if (members.size() <= 10) {
                            if (posBigRooms >= 0) {
                                members_BigRooms.remove(posBigRooms);
                                ids_BigRooms.remove(posBigRooms);
                            }

                            if ((posTenMembers < 0) && (posOneToOne < 0)) {
                                members_MaxTenMembers.add(member);
                                ids_MaxTenMembers.add(member.getUserId());
                            }
                        } else {
                            if ((posBigRooms < 0) && (posTenMembers < 0) && (posOneToOne < 0)) {
                                members_BigRooms.add(member);
                                ids_BigRooms.add(member.getUserId());
                            }
                        }
                    }
                }
            }
        }

        mAdapter = new MembersInvitationAdapter(getActivity(), R.layout.adapter_item_members_invitation, getMXMediasCache());

        Collections.sort(members_OneToOne, alphaComparator);
        Collections.sort(members_MaxTenMembers, alphaComparator);
        Collections.sort(members_BigRooms, alphaComparator);

        mAdapter.addAll(members_OneToOne);
        mAdapter.addAll(members_MaxTenMembers);
        mAdapter.addAll(members_BigRooms);

        ArrayList<Integer> bounds = new ArrayList<Integer>();
        ArrayList<String> sectionTitles = new ArrayList<String>();

        int index = 0;

        if (members_OneToOne.size() > 0) {
            bounds.add(index);
            sectionTitles.add(getActivity().getResources().getString(R.string.members_one_to_one));
            index += members_OneToOne.size();
        }

        if (members_MaxTenMembers.size() > 0) {
            bounds.add(index);
            sectionTitles.add(getActivity().getResources().getString(R.string.members_small_room_members));
            index += members_MaxTenMembers.size();
        }

        if (members_BigRooms.size() > 0) {
            bounds.add(index);
            sectionTitles.add(getActivity().getResources().getString(R.string.members_large_room_members));
            index += members_BigRooms.size();
        }

        mAdapter.setSectionTiles(bounds, sectionTitles);

        mListView.setAdapter(mAdapter);
    }
}
