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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.LinearLayoutManager;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.group.GroupRoom;
import org.matrix.androidsdk.rest.model.group.GroupUser;
import org.matrix.androidsdk.util.Log;

import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorGroupDetailsActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.HomeGroupRoomAdapter;
import im.vector.adapters.HomeGroupUserAdapter;
import im.vector.adapters.HomeRoomAdapter;
import im.vector.util.PreferencesManager;
import im.vector.util.RoomUtils;
import im.vector.view.HomeSectionView;

public class VectorGroupDetailsHomeFragment extends Fragment {
    private static final String LOG_TAG = VectorGroupDetailsHomeFragment.class.getSimpleName();

    @BindView(R.id.nested_scrollview)
    NestedScrollView mNestedScrollView;

    @BindView(R.id.featured_rooms_section)
    HomeSectionView mFeaturedRoomsSection;

    @BindView(R.id.featured_users_section)
    HomeSectionView mFeaturedUsersSection;

    private List<HomeSectionView> mHomeSectionViews = new ArrayList<>();

    private MXSession mSession;

    private VectorGroupDetailsActivity mActivity;

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_details_home, container, false);
    }

    @Override
    @CallSuper
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ButterKnife.bind(this, view);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSession = Matrix.getInstance(getContext()).getDefaultSession();
        mActivity = (VectorGroupDetailsActivity) getActivity();

        //mPrimaryColor = ContextCompat.getColor(getActivity(), R.color.tab_home);
        //mSecondaryColor = ContextCompat.getColor(getActivity(), R.color.tab_home_secondary);

        initViews();

        //mActivity.showWaitingView();
    }

    @Override
    public void onResume() {
        super.onResume();
        initData();

        for (HomeSectionView homeSectionView : mHomeSectionViews) {
            homeSectionView.scrollToPosition(0);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        // Rooms list
        mFeaturedRoomsSection.setTitle(R.string.bottom_action_rooms);
        mFeaturedRoomsSection.setPlaceholders(getString(R.string.no_room_placeholder), getString(R.string.no_result_placeholder));
        mFeaturedRoomsSection.setupGroupRoomRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_contact_view, true, new HomeGroupRoomAdapter.OnSelectGroupRoomListener() {
                    @Override
                    public void onSelectGroupRoom(GroupRoom groupRoom, int position) {
                        Room room = mSession.getDataHandler().getStore().getRoom(groupRoom.roomId);

                        // Launch corresponding room activity
                        HashMap<String, Object> params = new HashMap<>();
                        params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                        params.put(VectorRoomActivity.EXTRA_ROOM_ID, groupRoom.roomId);
                        params.put(VectorRoomActivity.EXTRA_IS_UNREAD_PREVIEW_MODE, (null == room));
                        CommonActivityUtils.goToRoomPage(getActivity(), mSession, params);
                    }
                });
        mHomeSectionViews.add(mFeaturedRoomsSection);

        // group users list
        mFeaturedUsersSection.setTitle(R.string.featured_users);
        mFeaturedUsersSection.setHideIfEmpty(true);
        mFeaturedUsersSection.setPlaceholders("should never happen", getString(R.string.no_result_placeholder));
        mFeaturedUsersSection.setupGroupUserRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_contact_view, true, new HomeGroupUserAdapter.OnSelectGroupUserListener() {
                    @Override
                    public void onSelectGroupUser(GroupUser groupUser, int position) {
                        Intent userIntent = new Intent(getActivity(), VectorMemberDetailsActivity.class);
                        userIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, groupUser.userId);

                        if (!TextUtils.isEmpty(groupUser.avatarUrl)) {
                            userIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_AVATAR_URL, groupUser.avatarUrl);
                        }

                        if (!TextUtils.isEmpty(groupUser.displayname)) {
                            userIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_DISPLAY_NAME, groupUser.displayname);
                        }

                        userIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                        startActivity(userIntent);
                    }
                });
        mHomeSectionViews.add(mFeaturedUsersSection);


        // Add listeners to hide the floating button when needed
        final GestureDetectorCompat gestureDetector = new GestureDetectorCompat(mActivity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent event1, MotionEvent event2,
                                   float velocityX, float velocityY) {
                /*if (mActivity.getFloatingActionButton() != null
                        && mNestedScrollView.getBottom() > mActivity.getFloatingActionButton().getTop()) {
                    mActivity.hideFloatingActionButton(getTag());
                }*/
                return true;
            }
        });

        mNestedScrollView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (null != mNestedScrollView) {
                    gestureDetector.onTouchEvent(event);
                    return mNestedScrollView.onTouchEvent(event);
                } else {
                    return false;
                }
            }
        });
        mNestedScrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                //mActivity.hideFloatingActionButton(getTag());
            }
        });
    }

    /*
     * *********************************************************************************************
     * Data management
     * *********************************************************************************************
     */

    /**
     * Init the rooms data
     */
    private void initData() {
        if ((null == mSession) || (null == mSession.getDataHandler())) {
            Log.e(LOG_TAG, "## initData() : null session");
        }

        mFeaturedRoomsSection.setGroupRooms(mActivity.getGroup().getGroupRooms().getRoomsList());
        mFeaturedUsersSection.setGroupUsers(mActivity.getGroup().getGroupUsers().getUsers());
    }
}
