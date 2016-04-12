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

package im.vector.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.JsonUtils;
import im.vector.VectorApp;
import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.adapters.VectorRoomMembersAdapter;

import java.util.Collection;
import java.util.HashMap;

/**
 * A dialog fragment showing a list of room members for a given room.
 */
public class RoomMembersDialogFragment extends DialogFragment {
    private static final String LOG_TAG = "RoomMembersDialogFragment";

    public static final String ARG_ROOM_ID = "RoomMembersDialogFragment.ARG_ROOM_ID";

    public static RoomMembersDialogFragment newInstance(MXSession session, String roomId) {
        RoomMembersDialogFragment f= new RoomMembersDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        f.setArguments(args);
        f.setSession(session);
        return f;
    }

    private ListView mListView;
    private VectorRoomMembersAdapter mAdapter;
    private String mRoomId;
    private MXSession mSession;

    private Handler uiThreadHandler;

    private IMXEventListener mEventsListenener = new MXEventListener() {
        @Override
        public void onPresenceUpdate(Event event, final User user) {
            // Someone's presence has changed, reprocess the whole list
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.saveUser(user);
                    mAdapter.sortMembers();
                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                        RoomMember member = JsonUtils.toRoomMember(event.content);
                        User user = mSession.getDataHandler().getStore().getUser(member.getUserId());

                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        boolean displayLeftMembers = preferences.getBoolean(getString(R.string.settings_key_display_left_members), false);

                        if (member.hasLeft() && !displayLeftMembers) {
                            mAdapter.deleteUser(user);
                            mAdapter.remove(member);
                            mAdapter.notifyDataSetChanged();
                        } else {
                            // the user can be a new one
                            boolean mustResort = mAdapter.saveUser(user);
                            mAdapter.updateMember(event.stateKey, JsonUtils.toRoomMember(event.content));

                            if (mustResort) {
                                mAdapter.sortMembers();
                                mAdapter.notifyDataSetChanged();
                            }
                        }
                    } else if (Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(event.type)) {
                        mAdapter.setPowerLevels(JsonUtils.toPowerLevels(event.content));
                    }
                }
            });
        }
    };

    public void setSession(MXSession session) {
        mSession = session;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRoomId = getArguments().getString(ARG_ROOM_ID);
        uiThreadHandler = new Handler();

        if (mSession == null) {
            throw new RuntimeException("No MXSession.");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mSession.getDataHandler().getRoom(mRoomId).removeEventListener(mEventsListenener);

    }

    @Override
    public void onResume() {
        super.onResume();
        mSession.getDataHandler().getRoom(mRoomId).addEventListener(mEventsListenener);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        d.setTitle(getString(R.string.members_list));
        return d;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_dialog_member_list, container, false);
        mListView = ((ListView)v.findViewById(R.id.listView_members));

        final Room room = mSession.getDataHandler().getRoom(mRoomId);

        HashMap<String, String> membershipStrings = new HashMap<String, String>();
        membershipStrings.put(RoomMember.MEMBERSHIP_INVITE, getActivity().getString(R.string.membership_invite));
        membershipStrings.put(RoomMember.MEMBERSHIP_JOIN, getActivity().getString(R.string.membership_join));
        membershipStrings.put(RoomMember.MEMBERSHIP_LEAVE, getActivity().getString(R.string.membership_leave));
        membershipStrings.put(RoomMember.MEMBERSHIP_BAN, getActivity().getString(R.string.membership_ban));

        mAdapter = new VectorRoomMembersAdapter(getActivity(), mSession.getHomeserverConfig(), R.layout.adapter_item_room_members, room.getLiveState(), getMXMediasCache(), membershipStrings);

        // apply the sort settings
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mAdapter.sortByLastActivePresence(preferences.getBoolean(getString(R.string.settings_key_sort_by_last_seen), true));
        mAdapter.displayMembership(preferences.getBoolean(getString(R.string.settings_key_display_left_members), false));

        boolean displayLeftMembers = preferences.getBoolean(getString(R.string.settings_key_display_left_members), false);

        Collection<RoomMember> members = room.getMembers();
        if (members != null) {
            IMXStore store = mSession.getDataHandler().getStore();

            for (RoomMember m : members) {
                // by default the
                if ((!m.hasLeft()) || displayLeftMembers) {
                    mAdapter.add(m);
                    mAdapter.saveUser(store.getUser(m.getUserId()));
                }
            }
            mAdapter.sortMembers();
        }

        mAdapter.setPowerLevels(room.getLiveState().getPowerLevels());
        mListView.setAdapter(mAdapter);

        // display the number of members in this room
        // don't update it dynamically
        // assume that the number of members will not be updated
        this.getDialog().setTitle(getString(R.string.members_list) + " (" + mAdapter.getCount() + ")");

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get the member and display the possible actions for them
                final RoomMember roomMember = mAdapter.getItem(position);
                final Activity activity = VectorApp.getCurrentActivity();

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent startRoomInfoIntent = new Intent(activity, VectorMemberDetailsActivity.class);
                        startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_ROOM_ID, mRoomId);
                        startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, roomMember.getUserId());
                        startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                        startActivity(startRoomInfoIntent);
                    }
                });

                // dismiss the member list
                RoomMembersDialogFragment.this.dismiss();
            }
        });

        return v;
    }
}
