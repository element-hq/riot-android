/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.VectorReadReceiptsAdapter;

/**
 * A dialog fragment showing the read receipts for an event
 */
public class VectorReadReceiptsDialogFragment extends DialogFragment {
    private static final String LOG_TAG = VectorPublicRoomsListFragment.class.getSimpleName();

    private static final String ARG_ROOM_ID = "VectorReadReceiptsDialogFragment.ARG_ROOM_ID";
    private static final String ARG_EVENT_ID = "VectorReadReceiptsDialogFragment.ARG_EVENT_ID";
    private static final String ARG_SESSION_ID = "VectorReadReceiptsDialogFragment.ARG_SESSION_ID";

    private VectorReadReceiptsAdapter mAdapter;

    public static VectorReadReceiptsDialogFragment newInstance(String userId, String roomId, String eventId) {
        VectorReadReceiptsDialogFragment f = new VectorReadReceiptsDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, userId);
        args.putString(ARG_ROOM_ID, roomId);
        args.putString(ARG_EVENT_ID, eventId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MXSession mxSession = Matrix.getInstance(getContext()).getSession(getArguments().getString(ARG_SESSION_ID));

        String roomId = getArguments().getString(ARG_ROOM_ID);
        String eventId = getArguments().getString(ARG_EVENT_ID);

        // sanity check
        if ((mxSession == null) || TextUtils.isEmpty(roomId) || TextUtils.isEmpty(eventId)) {
            Log.e(LOG_TAG, "## onCreate() : invalid parameters");
            dismiss();
            return;
        }

        Room room = mxSession.getDataHandler().getRoom(roomId);

        mAdapter = new VectorReadReceiptsAdapter(getActivity(), R.layout.adapter_item_read_receipt, mxSession, room);
        mAdapter.addAll(new ArrayList<>(mxSession.getDataHandler().getStore().getEventReceipts(roomId, eventId, true, true)));

        // Ensure all the members are loaded (ignore error)
        room.getMembersAsync(new SimpleApiCallback<List<RoomMember>>() {
            @Override
            public void onSuccess(List<RoomMember> info) {
                if (isAdded()) {
                    mAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        // FIXME The title is not displayed
        d.setTitle(R.string.read_receipts_list);
        return d;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_dialog_member_list, container, false);
        ListView listView = v.findViewById(R.id.listView_members);

        listView.setAdapter(mAdapter);

        return v;
    }
}
