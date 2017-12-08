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

import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import im.vector.R;
import im.vector.adapters.GroupAdapter;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SimpleDividerItemDecoration;

public class GroupsFragment extends AbsHomeFragment {
    private static final String LOG_TAG = PeopleFragment.class.getSimpleName();

    @BindView(R.id.recyclerview)
    RecyclerView mRecycler;

    // groups management
    private GroupAdapter mAdapter;

    // rooms list
    private final List<Group> mJoinedGroups = new ArrayList<>();
    private final List<Group> mInvitedGroups = new ArrayList<>();

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static GroupsFragment newInstance() {
        return new GroupsFragment();
    }

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_groups, container, false);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPrimaryColor = ContextCompat.getColor(getActivity(), R.color.tab_groups);
        mSecondaryColor = ContextCompat.getColor(getActivity(), R.color.tab_groups_secondary);

        initViews();

        mAdapter.onFilterDone(mCurrentFilter);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.setInvitation(mActivity.getRoomInvitations());
        mRecycler.addOnScrollListener(mScrollListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        mRecycler.removeOnScrollListener(mScrollListener);
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected List<Room> getRooms() {
        return new ArrayList<>();
    }

    @Override
    protected void onFilter(String pattern, final OnFilterListener listener) {
        mAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onFilterComplete " + count);
                if (listener != null) {
                    listener.onFilterDone(count);
                }
            }
        });
    }

    @Override
    protected void onResetFilter() {
        mAdapter.getFilter().filter("", new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onResetFilter " + count);
            }
        });
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        mRecycler.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mRecycler.addItemDecoration(new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, margin));
        mRecycler.addItemDecoration(new EmptyViewItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, 40, 16, 14));

        mAdapter = new GroupAdapter(getActivity(), new GroupAdapter.OnGroupSelectItemListener() {
            @Override
            public void onSelectItem(Group room, int position) {
            }

        }, null, null);
        mRecycler.setAdapter(mAdapter);
    }
}
