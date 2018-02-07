/*
 * Copyright 2016 OpenMarket Ltd
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
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;

import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.group.GroupRoom;

import butterknife.BindView;
import im.vector.R;
import im.vector.adapters.GroupDetailsRoomsAdapter;
import im.vector.util.GroupUtils;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SimpleDividerItemDecoration;

public class GroupDetailsRoomsFragment extends GroupDetailsBaseFragment {
    @BindView(R.id.recyclerview)
    RecyclerView mRecycler;

    @BindView(R.id.search_view)
    SearchView mSearchView;

    private GroupDetailsRoomsAdapter mAdapter;
    private String mCurrentFilter;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_details_rooms, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshViews();
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mCurrentFilter = mSearchView.getQuery().toString();
        mAdapter.onFilterDone(mCurrentFilter);
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    /**
     * Prepare views
     */
    @Override
    protected void initViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        mRecycler.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mRecycler.addItemDecoration(new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, margin));
        mRecycler.addItemDecoration(new EmptyViewItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, 40, 16, 14));
        mAdapter = new GroupDetailsRoomsAdapter(getActivity(), new GroupDetailsRoomsAdapter.OnSelectRoomListener() {
            @Override
            public void onSelectItem(GroupRoom groupRoom, int position) {
                mActivity.showWaitingView();
                GroupUtils.openGroupRoom(mActivity, mSession, groupRoom, new SimpleApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        mActivity.stopWaitingView();
                    }
                });
            }
        });
        mRecycler.setAdapter(mAdapter);

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(final String newText) {
                if (!TextUtils.equals(mCurrentFilter, newText)) {
                    mAdapter.getFilter().filter(newText, new Filter.FilterListener() {
                        @Override
                        public void onFilterComplete(int count) {
                            mCurrentFilter = newText;
                        }
                    });
                }
                return true;
            }
        });
        mSearchView.setMaxWidth(Integer.MAX_VALUE);
        mSearchView.setQueryHint(getString(R.string.filter_group_rooms));
        mSearchView.setFocusable(false);
        mSearchView.setIconifiedByDefault(false);
        mSearchView.clearFocus();
    }

    @Override
    public void refreshViews() {
        mAdapter.setGroupRooms(mActivity.getGroup().getGroupRooms().getRoomsList());
    }
}
