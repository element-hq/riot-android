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
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.group.GroupRoom;
import org.matrix.androidsdk.rest.model.group.GroupUser;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.VectorGroupDetailsActivity;
import im.vector.adapters.GroupDetailsPeopleAdapter;
import im.vector.adapters.GroupDetailsRoomsAdapter;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SimpleDividerItemDecoration;

public class VectorGroupDetailsRoomsFragment extends Fragment {
    // internal constants values
    private static final String LOG_TAG = VectorGroupDetailsRoomsFragment.class.getSimpleName();

    @BindView(R.id.recyclerview)
    RecyclerView mRecycler;

    @BindView(R.id.search_view)
    SearchView mSearchView;

    private GroupDetailsRoomsAdapter mAdapter;

    private MXSession mSession;
    private VectorGroupDetailsActivity mActivity;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_details_rooms, container, false);
    }

    @Override
    @CallSuper
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ButterKnife.bind(this, view);

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mAdapter.getFilter().filter(newText, new Filter.FilterListener() {
                    @Override
                    public void onFilterComplete(int count) {
                        mAdapter.notifyDataSetChanged();
                    }
                });
                return true;
            }
        });
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSession = Matrix.getInstance(getContext()).getDefaultSession();
        mActivity = (VectorGroupDetailsActivity) getActivity();

        initViews();

        mAdapter.onFilterDone(mSearchView.getQuery().toString());
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    /**
     * Prepare views
     */
    private void initViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        mRecycler.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mRecycler.addItemDecoration(new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, margin));
        mRecycler.addItemDecoration(new EmptyViewItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, 40, 16, 14));
        mAdapter = new GroupDetailsRoomsAdapter(getActivity(), new GroupDetailsRoomsAdapter.OnSelectRoomListener() {
            @Override
            public void onSelectItem(GroupRoom groupRoom, int position) {
                int a = 0;
                a++;

            }
        });
        mRecycler.setAdapter(mAdapter);
        mAdapter.setGroupRooms(mActivity.getGroup().getGroupRooms().getRoomsList());
    }
}
