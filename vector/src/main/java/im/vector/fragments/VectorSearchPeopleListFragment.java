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

package im.vector.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;

import im.vector.Matrix;
import im.vector.R;

import im.vector.activity.VectorBaseSearchActivity;
import im.vector.activity.VectorMemberDetailsActivity;

import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorAddParticipantsAdapter;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;


public class VectorSearchPeopleListFragment extends Fragment {

    public static final String ARG_MATRIX_ID = "VectorSearchPeopleListFragment.ARG_MATRIX_ID";
    public static final String ARG_LAYOUT_ID = "VectorSearchPeopleListFragment.ARG_LAYOUT_ID";

    // the session
    private MXSession mSession;
    private ListView mPeopleListView;
    private VectorAddParticipantsAdapter mAdapter;

    // pending requests
    // a request might be called whereas the fragment is not initialized
    // wait the resume to perform the search
    private String mPendingPattern;
    private MatrixMessageListFragment.OnSearchResultListener mPendingSearchResultListener;

    // contacts manager listener
    // detect if a contact is a matrix user
    private ContactsManager.ContactsManagerListener mContactsListener = new ContactsManager.ContactsManagerListener() {
        @Override
        public void onRefresh() {
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onContactPresenceUpdate(final Contact contact, final String matrixId) {
            if (null != getActivity()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int firstIndex = mPeopleListView.getFirstVisiblePosition();
                        int lastIndex = mPeopleListView.getLastVisiblePosition();

                        for(int index = firstIndex; index <= lastIndex; index++) {
                            if (mAdapter.getItem(index).mContact == contact) {
                                mAdapter.getItem(index).mUserId = matrixId;
                                mAdapter.notifyDataSetChanged();
                                break;
                            }
                        }
                    }
                });
            }
        }
    };

    // refresh the presence asap
    private MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onPresenceUpdate(final Event event, final User user) {
            if (null != getActivity()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int firstIndex = mPeopleListView.getFirstVisiblePosition();
                        int lastIndex = mPeopleListView.getLastVisiblePosition();

                        for (int index = firstIndex; index <= lastIndex; index++) {
                            if (TextUtils.equals(user.user_id, mAdapter.getItem(index).mUserId)) {
                                mAdapter.notifyDataSetChanged();
                                break;
                            }
                        }
                    }
                });
            }
        }
    };


    /**
     * Static constructor
     * @param matrixId the matrix id
     * @return a VectorSearchPeopleListFragment instance
     */
    public static VectorSearchPeopleListFragment newInstance(String matrixId, int layoutResId) {
        VectorSearchPeopleListFragment f = new VectorSearchPeopleListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Bundle args = getArguments();


        String matrixId = args.getString(ARG_MATRIX_ID);
        mSession = Matrix.getInstance(getActivity()).getSession(matrixId);

        if (null == mSession) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mPeopleListView = (ListView)v.findViewById(R.id.search_people_list);
        mAdapter = new VectorAddParticipantsAdapter(getActivity(), R.layout.adapter_item_vector_add_participants, mSession, null);
        mPeopleListView.setAdapter(mAdapter);

        mPeopleListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ParticipantAdapterItem item = mAdapter.getItem(position);

                Intent startRoomInfoIntent = new Intent(getActivity(), VectorMemberDetailsActivity.class);
                startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, item.mUserId);
                startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                startActivity(startRoomInfoIntent);
            }
        });

        return v;
    }

    /**
     * Search a pattern in the room
     * @param pattern
     * @param onSearchResultListener
     */
    public void searchPattern(final String pattern, final MatrixMessageListFragment.OnSearchResultListener onSearchResultListener) {
        if (null == mPeopleListView) {
            mPendingPattern = pattern;
            mPendingSearchResultListener = onSearchResultListener;
            return;
        }

        if (TextUtils.isEmpty(pattern)) {
            mPeopleListView.setVisibility(View.GONE);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onSearchResultListener.onSearchSucceed(0);
                }
            });
        } else {
            mAdapter.setSearchedPattern(pattern, new VectorAddParticipantsAdapter.OnParticipantsSearchListener() {
                @Override
                public void onSearchEnd(final int count) {
                    mPeopleListView.post(new Runnable() {
                        @Override
                        public void run() {
                            mPeopleListView.setVisibility((count == 0) ? View.INVISIBLE : View.VISIBLE);
                            onSearchResultListener.onSearchSucceed(count);
                        }
                    });
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mAdapter.setSearchedPattern(null, null, null);

        mSession.getDataHandler().removeListener(mEventsListener);
        ContactsManager.removeListener(mContactsListener);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() instanceof VectorBaseSearchActivity.IVectorSearchActivity) {
            ((VectorBaseSearchActivity.IVectorSearchActivity)getActivity()).refreshSearch();
        } else {
            if (null != mPendingPattern) {
                searchPattern(mPendingPattern, mPendingSearchResultListener);
                mPendingPattern = null;
                mPendingSearchResultListener = null;
            }
        }

        mSession.getDataHandler().addListener(mEventsListener);
        ContactsManager.addListener(mContactsListener);
    }
}
