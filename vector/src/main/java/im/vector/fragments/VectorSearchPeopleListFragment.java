/*
 * Copyright 2016 OpenMarket Ltd
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

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ExpandableListView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.MXPatterns;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;

import java.util.List;
import java.util.Map;

import butterknife.BindView;
import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.VectorBaseSearchActivity;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorParticipantsAdapter;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
import im.vector.util.VectorUtils;

public class VectorSearchPeopleListFragment extends VectorBaseFragment {

    private static final String ARG_MATRIX_ID = "VectorSearchPeopleListFragment.ARG_MATRIX_ID";
    private static final String ARG_LAYOUT_ID = "VectorSearchPeopleListFragment.ARG_LAYOUT_ID";

    // the session
    private MXSession mSession;
    @BindView(R.id.search_people_list)
    ExpandableListView mPeopleListView;
    private VectorParticipantsAdapter mAdapter;

    // contacts manager listener
    // detect if a contact is a matrix user
    private final ContactsManager.ContactsManagerListener mContactsListener = new ContactsManager.ContactsManagerListener() {
        @Override
        public void onRefresh() {
            if (null != getActivity()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (getActivity() instanceof VectorBaseSearchActivity.IVectorSearchActivity) {
                            ((VectorBaseSearchActivity.IVectorSearchActivity) getActivity()).refreshSearch();
                        }
                    }
                });
            }
        }

        @Override
        public void onContactPresenceUpdate(final Contact contact, final String matrixId) {
        }

        @Override
        public void onPIDsUpdate() {
            if (null != getActivity()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.onPIdsUpdate();
                    }
                });
            }
        }

        @Override
        public void onIdentityServerTermsNotSigned(String token) {
            Log.w("VectorSearchPeopleListFragment", "onIdentityServerTermsNotSigned()");
        }
    };

    // refresh the presence asap
    private final MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onPresenceUpdate(final Event event, final User user) {
            if (null != getActivity()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Map<Integer, List<Integer>> visibleChildViews = VectorUtils.getVisibleChildViews(mPeopleListView, mAdapter);

                        for (Integer groupPosition : visibleChildViews.keySet()) {
                            List<Integer> childPositions = visibleChildViews.get(groupPosition);

                            for (Integer childPosition : childPositions) {
                                Object item = mAdapter.getChild(groupPosition, childPosition);

                                if (item instanceof ParticipantAdapterItem) {
                                    ParticipantAdapterItem participantAdapterItem = (ParticipantAdapterItem) item;

                                    if (TextUtils.equals(user.user_id, participantAdapterItem.mUserId)) {
                                        mAdapter.notifyDataSetChanged();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                });
            }
        }
    };


    /**
     * Static constructor
     *
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
    public int getLayoutResId() {
        Bundle args = getArguments();

        return args.getInt(ARG_LAYOUT_ID);
    }

    @Override
    public void onViewCreated(@NotNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();

        String matrixId = args.getString(ARG_MATRIX_ID);
        mSession = Matrix.getInstance(getActivity()).getSession(matrixId);

        if ((null == mSession) || !mSession.isAlive()) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        // the chevron is managed in the header view
        mPeopleListView.setGroupIndicator(null);
        mAdapter = new VectorParticipantsAdapter(getActivity(),
                R.layout.adapter_item_vector_add_participants,
                R.layout.adapter_item_vector_people_header,
                mSession, null, false);
        mPeopleListView.setAdapter(mAdapter);

        mPeopleListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                Object child = mAdapter.getChild(groupPosition, childPosition);

                if (child instanceof ParticipantAdapterItem && ((ParticipantAdapterItem) child).mIsValid) {
                    ParticipantAdapterItem item = (ParticipantAdapterItem) child;

                    Intent startRoomInfoIntent = new Intent(getActivity(), VectorMemberDetailsActivity.class);
                    startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, item.mUserId);

                    if (!TextUtils.isEmpty(item.mAvatarUrl)) {
                        startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_AVATAR_URL, item.mAvatarUrl);
                    }

                    if (!TextUtils.isEmpty(item.mDisplayName)) {
                        startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_DISPLAY_NAME, item.mDisplayName);
                    }

                    startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                    startActivity(startRoomInfoIntent);
                }

                return true;
            }
        });
    }

    /**
     * @return true if the local search is ready to start.
     */
    public boolean isReady() {
        return ContactsManager.getInstance().didPopulateLocalContacts() && mAdapter.isKnownMembersInitialized();
    }

    /**
     * Search a pattern in the room
     *
     * @param pattern                the pattern to search
     * @param onSearchResultListener the result listener
     */
    public void searchPattern(final String pattern, final MatrixMessageListFragment.OnSearchResultListener onSearchResultListener) {
        if (null == mPeopleListView) {
            return;
        }

        // wait that the local contacts are populated
        if (!ContactsManager.getInstance().didPopulateLocalContacts()) {
            mAdapter.reset();
            return;
        }

        ParticipantAdapterItem firstEntry = null;
        if (!TextUtils.isEmpty(pattern)) {
            // test if the pattern is a valid email or matrix id
            boolean isValid = android.util.Patterns.EMAIL_ADDRESS.matcher(pattern).matches()
                    || MXPatterns.isUserId(pattern);
            firstEntry = new ParticipantAdapterItem(pattern, null, pattern, isValid);
        }

        mAdapter.setSearchedPattern(pattern, firstEntry, new VectorParticipantsAdapter.OnParticipantsSearchListener() {
            @Override
            public void onSearchEnd(final int count) {
                if (!isAdded()) {
                    return;
                }

                mPeopleListView.post(new Runnable() {
                    @Override
                    public void run() {
                        mPeopleListView.setVisibility((count == 0 && !TextUtils.isEmpty(pattern)) ? View.INVISIBLE : View.VISIBLE);
                        onSearchResultListener.onSearchSucceed(count);
                    }
                });
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        mSession.getDataHandler().removeListener(mEventsListener);
        ContactsManager.getInstance().removeListener(mContactsListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSession.getDataHandler().addListener(mEventsListener);
        ContactsManager.getInstance().addListener(mContactsListener);
    }
}
