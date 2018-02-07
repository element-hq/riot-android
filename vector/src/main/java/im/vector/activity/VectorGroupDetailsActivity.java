/*
 * Copyright 2014 OpenMarket Ltd
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

package im.vector.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.groups.GroupsManager;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.util.Log;

import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.GroupDetailsFragmentPagerAdapter;
import im.vector.fragments.GroupDetailsBaseFragment;
import im.vector.util.ThemeUtils;
import im.vector.view.RiotViewPager;

/**
 *
 */
public class VectorGroupDetailsActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = VectorRoomDetailsActivity.class.getSimpleName();

    // the group ID
    public static final String EXTRA_GROUP_ID = "EXTRA_GROUP_ID";
    public static final String EXTRA_TAB_INDEX = "VectorUnifiedSearchActivity.EXTRA_TAB_INDEX";

    // private classes
    private MXSession mSession;
    private GroupsManager mGroupsManager;
    private Group mGroup;

    // UI views
    private View mLoadingView;
    private ProgressBar mGroupSyncInProgress;

    private RiotViewPager mPager;
    private GroupDetailsFragmentPagerAdapter mPagerAdapter;

    private MXEventListener mGroupEventsListener = new MXEventListener() {
        private void refresh(String groupId) {
            if ((null != mGroup) && TextUtils.equals(mGroup.getGroupId(), groupId)) {
                refreshGroupInfo();
            }
        }

        @Override
        public void onLeaveGroup(String groupId) {
            if ((null != mRoom) && TextUtils.equals(groupId, mGroup.getGroupId())) {
                VectorGroupDetailsActivity.this.finish();
            }
        }

        @Override
        public void onNewGroupInvitation(String groupId) {
            refresh(groupId);
        }

        @Override
        public void onJoinGroup(String groupId) {
            refresh(groupId);
        }


        @Override
        public void onGroupProfileUpdate(String groupId) {
            if ((null != mGroup) && TextUtils.equals(mGroup.getGroupId(), groupId)) {
                if (null != mPagerAdapter.getHomeFragment()) {
                    mPagerAdapter.getHomeFragment().refreshViews();
                }
            }
        }

        @Override
        public void onGroupRoomsListUpdate(String groupId) {
            if ((null != mGroup) && TextUtils.equals(mGroup.getGroupId(), groupId)) {
                if (null != mPagerAdapter.getRoomsFragment()) {
                    mPagerAdapter.getRoomsFragment().refreshViews();
                }

                if (null != mPagerAdapter.getHomeFragment()) {
                    mPagerAdapter.getHomeFragment().refreshViews();
                }
            }
        }

        @Override
        public void onGroupUsersListUpdate(String groupId) {
            if ((null != mGroup) && TextUtils.equals(mGroup.getGroupId(), groupId)) {
                if (null != mPagerAdapter.getPeopleFragment()) {
                    mPagerAdapter.getPeopleFragment().refreshViews();
                }

                if (null != mPagerAdapter.getHomeFragment()) {
                    mPagerAdapter.getHomeFragment().refreshViews();
                }
            }
        }

        @Override
        public void onGroupInvitedUsersListUpdate(String groupId) {
            onGroupUsersListUpdate(groupId);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        Intent intent = getIntent();

        if (!intent.hasExtra(EXTRA_GROUP_ID)) {
            Log.e(LOG_TAG, "No group id");
            finish();
            return;
        }

        // get current session
        mSession = Matrix.getInstance(getApplicationContext()).getSession(intent.getStringExtra(EXTRA_MATRIX_ID));

        if ((null == mSession) || !mSession.isAlive()) {
            finish();
            return;
        }

        mGroupsManager = mSession.getGroupsManager();

        String groupId = intent.getStringExtra(EXTRA_GROUP_ID);

        if (!MXSession.isGroupId(groupId)) {
            Log.e(LOG_TAG, "invalid group id " + groupId);
            finish();
            return;
        }

        mGroup = mGroupsManager.getGroup(groupId);

        if (null == mGroup) {
            Log.d(LOG_TAG, "## onCreate() : displaying " + groupId + " in preview mode");
            mGroup = new Group(groupId);
        } else {
            Log.d(LOG_TAG, "## onCreate() : displaying " + groupId);
        }

        setContentView(R.layout.activity_vector_group_details);

        // UI widgets binding & init fields
        mLoadingView = findViewById(R.id.group_loading_layout);

        // tab creation and restore tabs UI context
        ActionBar actionBar = getSupportActionBar();

        if (null != actionBar) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mGroupSyncInProgress = findViewById(R.id.group_sync_in_progress);

        mPager = findViewById(R.id.groups_pager);
        mPagerAdapter = new GroupDetailsFragmentPagerAdapter(getSupportFragmentManager(), this);
        mPager.setAdapter(mPagerAdapter);

        TabLayout layout = findViewById(R.id.group_tabs);
        ThemeUtils.setTabLayoutTheme(this, layout);

        if (intent.hasExtra(EXTRA_TAB_INDEX)) {
            mPager.setCurrentItem(getIntent().getIntExtra(EXTRA_TAB_INDEX, 0));
        } else {
            mPager.setCurrentItem((null != savedInstanceState) ? savedInstanceState.getInt(EXTRA_TAB_INDEX, 0) : 0);
        }
        layout.setupWithViewPager(mPager);

        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // dismiss the keyboard when swiping
                final View view = getCurrentFocus();
                if (view != null) {
                    final InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }

            @Override
            public void onPageSelected(int position) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    /**
     * @return the used group
     */
    public Group getGroup() {
        return mGroup;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        List<android.support.v4.app.Fragment> allFragments = getSupportFragmentManager().getFragments();

        // dispatch the result to each fragments
        for (android.support.v4.app.Fragment fragment : allFragments) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_TAB_INDEX, mPager.getCurrentItem());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSession.getDataHandler().removeListener(mGroupEventsListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshGroupInfo();
        mSession.getDataHandler().addListener(mGroupEventsListener);
    }

    /**
     * SHow the waiting view
     */
    public void showWaitingView() {
        if (null != mLoadingView) {
            mLoadingView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide the waiting view
     */
    public void stopWaitingView() {
        if (null != mLoadingView) {
            mLoadingView.setVisibility(View.GONE);
        }
    }

    /**
     * Refresh the group information
     */
    private void refreshGroupInfo() {
        if (null != mGroup) {
            mGroupSyncInProgress.setVisibility(View.VISIBLE);
            mGroupsManager.refreshGroupData(mGroup, new ApiCallback<Void>() {
                private void onDone() {
                    if (null != mGroupSyncInProgress) {
                        mGroupSyncInProgress.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onSuccess(Void info) {
                    onDone();
                }

                @Override
                public void onNetworkError(Exception e) {
                    onDone();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onDone();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onDone();
                }
            });
        }
    }
}
