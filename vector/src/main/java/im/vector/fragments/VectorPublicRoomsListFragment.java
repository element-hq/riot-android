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

import android.content.Intent;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.PublicRoom;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.VectorPublicRoomsAdapter;

import java.util.ArrayList;
import java.util.HashMap;

public class VectorPublicRoomsListFragment extends Fragment {
    private static final String LOG_TAG = "VectorPubRoomsListFrg";

    public static final String ARG_LAYOUT_ID = "VectorPublicRoomsListFragment.ARG_LAYOUT_ID";
    public static final String ARG_MATRIX_ID = "VectorPublicRoomsListFragment.ARG_MATRIX_ID";
    public static final String ARG_ROOMS_LIST_ID = "VectorPublicRoomsListFragment.ARG_ROOMS_LIST_ID";


    public static VectorPublicRoomsListFragment newInstance(String matrixId, int layoutResId, ArrayList<PublicRoom> publicRooms) {
        VectorPublicRoomsListFragment f = new VectorPublicRoomsListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);
        args.putSerializable(ARG_ROOMS_LIST_ID, publicRooms);
        f.setArguments(args);
        return f;
    }

    protected String mMatrixId;
    protected MXSession mSession;
    protected ListView mRecentsListView;
    protected ArrayList<PublicRoom> mPublicRooms;
    protected VectorPublicRoomsAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Bundle args = getArguments();

        mMatrixId = args.getString(ARG_MATRIX_ID);
        mSession = Matrix.getInstance(getActivity()).getSession(mMatrixId);

        if (null == mSession) {
            throw new RuntimeException("Must have valid default MXSession.");
        }
        mPublicRooms = (ArrayList<PublicRoom>)args.getSerializable(ARG_ROOMS_LIST_ID);

        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mRecentsListView = (ListView)v.findViewById(R.id.fragment_public_rooms_list);
        // create the adapter
        mAdapter = new VectorPublicRoomsAdapter(getActivity(), R.layout.adapter_item_vector_recent_room);
        mAdapter.addAll(mPublicRooms);
        mRecentsListView.setAdapter(mAdapter);

        // Set rooms click listener:
        // - reset the unread count
        // - start the corresponding room activity
        mRecentsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                PublicRoom publicRoom = mAdapter.getItem(position);

                // launch corresponding room activity
                if (null != publicRoom.roomId) {
                    HashMap<String, Object> params = new HashMap<String, Object>();
                    params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                    params.put(VectorRoomActivity.EXTRA_ROOM_ID, publicRoom.roomId);

                    if (!TextUtils.isEmpty(publicRoom.name)) {
                        params.put(VectorRoomActivity.EXTRA_DEFAULT_NAME, publicRoom.name);
                    }

                    if (!TextUtils.isEmpty(publicRoom.topic)) {
                        params.put(VectorRoomActivity.EXTRA_DEFAULT_TOPIC, publicRoom.topic);
                    }

                    CommonActivityUtils.goToRoomPage(getActivity(), mSession, params);
                }
            }
        });

        return v;
    }
}
