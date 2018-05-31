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

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.VectorReadReceiptsAdapter;

/**
 * A dialog fragment showing the read receipts
 */
public class VectorReadReceiptsDialogFragment extends DialogFragment {
    private static final String LOG_TAG = VectorPublicRoomsListFragment.class.getSimpleName();

    private static final String ARG_ROOM_ID = "VectorReadReceiptsDialogFragment.ARG_ROOM_ID";
    private static final String ARG_EVENT_ID = "VectorReadReceiptsDialogFragment.ARG_EVENT_ID";
    private static final String ARG_SESSION_ID = "VectorReadReceiptsDialogFragment.ARG_SESSION_ID";

    public static VectorReadReceiptsDialogFragment newInstance(String userId, String roomId, String eventId) {
        VectorReadReceiptsDialogFragment f = new VectorReadReceiptsDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, userId);
        args.putString(ARG_ROOM_ID, roomId);
        args.putString(ARG_EVENT_ID, eventId);
        f.setArguments(args);
        return f;
    }

    private String mRoomId;
    private String mEventId;
    private MXSession mSession;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSession = Matrix.getInstance(getContext()).getSession(getArguments().getString(ARG_SESSION_ID));
        mRoomId = getArguments().getString(ARG_ROOM_ID);
        mEventId = getArguments().getString(ARG_EVENT_ID);

        // sanity check
        if ((mSession == null) || TextUtils.isEmpty(mRoomId) || TextUtils.isEmpty(mEventId)) {
            Log.e(LOG_TAG, "## onCreate() : invalid parameters");
            dismiss();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        d.setTitle(getString(R.string.read_receipts_list));
        return d;
    }

    /**
     * Return the used medias cache.
     * This method can be overridden to use another medias cache
     *
     * @return the used medias cache
     */
    private MXMediasCache getMXMediasCache() {
        return Matrix.getInstance(getActivity()).getMediasCache();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_dialog_member_list, container, false);
        ListView listView = v.findViewById(R.id.listView_members);

        final Room room = mSession.getDataHandler().getRoom(mRoomId);
        VectorReadReceiptsAdapter adapter = new VectorReadReceiptsAdapter(getActivity(), R.layout.adapter_item_read_receipt, mSession, room, getMXMediasCache());

        adapter.addAll(new ArrayList<>(mSession.getDataHandler().getStore().getEventReceipts(mRoomId, mEventId, true, true)));
        listView.setAdapter(adapter);

        return v;
    }
}
