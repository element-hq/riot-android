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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.TabListener;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.groups.GroupsManager;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.util.Log;

import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.contacts.ContactsManager;
import im.vector.fragments.GroupDetailsBaseFragment;
import im.vector.fragments.GroupDetailsHomeFragment;
import im.vector.fragments.GroupDetailsPeopleFragment;
import im.vector.fragments.GroupDetailsRoomsFragment;
import im.vector.fragments.VectorRoomDetailsMembersFragment;
import im.vector.fragments.VectorRoomSettingsFragment;
import im.vector.fragments.VectorSearchRoomFilesListFragment;
import im.vector.util.ThemeUtils;

/**
 *
 */
public class VectorGroupDetailsActivity extends MXCActionBarActivity implements TabListener {
    private static final String LOG_TAG = VectorRoomDetailsActivity.class.getSimpleName();

    // the group ID
    public static final String EXTRA_GROUP_ID = "EXTRA_GROUP_ID";

    // open a dedicated tab at launch
    public static final String EXTRA_SELECTED_TAB_ID = "VectorGroupDetailsActivity.EXTRA_SELECTED_TAB_ID";

    // tab related items
    private static final String TAG_FRAGMENT_GROUP_HOME = "TAG_FRAGMENT_GROUP_HOME";
    private static final String TAG_FRAGMENT_GROUP_PEOPLE = "TAG_FRAGMENT_GROUP_PEOPLE";
    private static final String TAG_FRAGMENT_GROUP_ROOMS = "TAG_FRAGMENT_GROUP_ROOMS";
    private static final String KEY_FRAGMENT_TAG = "KEY_FRAGMENT_TAG";

    private String mCurrentFragmentTag;
    private ActionBar mActionBar;

    // private classes
    private MXSession mSession;
    private GroupsManager mGroupsManager;
    private Group mGroup;

    private View mLoadingView;

    // fragments
    private GroupDetailsHomeFragment mGroupDetailsHomeFragment;
    private GroupDetailsPeopleFragment mGroupDetailsPeopleFragment;
    private GroupDetailsRoomsFragment mGroupDetailsRoomsFragment;

    // activity life cycle management:
    // - Bundle keys
    private static final String KEY_STATE_CURRENT_TAB_INDEX = "CURRENT_SELECTED_TAB";

    // todo manage leave group

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

        mGroup = mGroupsManager.getGroup(intent.getStringExtra(EXTRA_GROUP_ID));

        if (null == mGroup) {
            Log.e(LOG_TAG, "unknown group");
            finish();
            return;
        }

        int selectedTab = intent.getIntExtra(EXTRA_SELECTED_TAB_ID, -1);

        setContentView(R.layout.activity_vector_group_details);

        // UI widgets binding & init fields
        mLoadingView = findViewById(R.id.group_loading_layout);

        // tab creation and restore tabs UI context
        mActionBar = getSupportActionBar();
        mActionBar.setDisplayShowHomeEnabled(true);
        mActionBar.setDisplayHomeAsUpEnabled(true);

        createNavigationTabs(savedInstanceState, selectedTab);
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
        Log.d(LOG_TAG, "## onSaveInstanceState(): ");

        // save current tab
        if (null != mActionBar) {
            int currentIndex = mActionBar.getSelectedNavigationIndex();
            outState.putInt(KEY_STATE_CURRENT_TAB_INDEX, currentIndex);
        }
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
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (null != mGroup) {
            mGroupsManager.refreshGroupData(mGroup.getGroupId(), new ApiCallback<Void>() {
                private void onDone() {
                    if (null != mCurrentFragmentTag) {
                        GroupDetailsBaseFragment fragment = (GroupDetailsBaseFragment) getSupportFragmentManager().findFragmentByTag(mCurrentFragmentTag);

                        if (null != fragment) {
                            fragment.refreshViews();
                        }
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

    /**
     * Update the tag of the tab with its the UI values
     *
     * @param aTabToUpdate the tab to be updated
     */
    private void saveUiTabContext(ActionBar.Tab aTabToUpdate) {
        Bundle tabTag = (Bundle) aTabToUpdate.getTag();
        aTabToUpdate.setTag(tabTag);
    }

    /**
     * Reset the UI to its init state:
     * - "waiting while searching" screen disabled
     * - background image visible
     * - no results message disabled
     */
    private void resetUi() {
        // stop "wait while searching" screen
        if (null != mLoadingView) {
            mLoadingView.setVisibility(View.GONE);
        }
    }

    /**
     * Initialise the navigation tabs.
     *
     * @param aSavedInstanceState the saved instance
     * @param defaultSelectedTab  the default selected tab
     */
    private void createNavigationTabs(Bundle aSavedInstanceState, int defaultSelectedTab) {
        int tabIndexToRestore;

        // Set the tabs navigation mode
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        ActionBar.Tab tabToBeAdded = mActionBar.newTab();
        String tabTitle = getResources().getString(R.string.group_details_home);
        tabToBeAdded.setText(tabTitle);
        tabToBeAdded.setTabListener(this);
        Bundle tabBundle = new Bundle();
        tabBundle.putString(KEY_FRAGMENT_TAG, TAG_FRAGMENT_GROUP_HOME);
        tabToBeAdded.setTag(tabBundle);
        mActionBar.addTab(tabToBeAdded);

        tabToBeAdded = mActionBar.newTab();
        tabTitle = getResources().getString(R.string.group_details_people);
        tabToBeAdded.setText(tabTitle);
        tabToBeAdded.setTabListener(this);
        tabBundle = new Bundle();
        tabBundle.putString(KEY_FRAGMENT_TAG, TAG_FRAGMENT_GROUP_PEOPLE);
        tabToBeAdded.setTag(tabBundle);
        mActionBar.addTab(tabToBeAdded);

        tabToBeAdded = mActionBar.newTab();
        tabTitle = getResources().getString(R.string.group_details_rooms);
        tabToBeAdded.setText(tabTitle);
        tabToBeAdded.setTabListener(this);
        tabBundle = new Bundle();
        tabBundle.putString(KEY_FRAGMENT_TAG, TAG_FRAGMENT_GROUP_ROOMS);
        tabToBeAdded.setTag(tabBundle);
        mActionBar.addTab(tabToBeAdded);

        // set the default tab to be displayed
        tabIndexToRestore = (null != aSavedInstanceState) ? aSavedInstanceState.getInt(KEY_STATE_CURRENT_TAB_INDEX, -1) : -1;

        if (-1 == tabIndexToRestore) {
            tabIndexToRestore = defaultSelectedTab;
        }

        if (-1 == tabIndexToRestore) {
            // default value: display the search in rooms tab
            tabIndexToRestore = 0;
        }

        mActionBar.setStackedBackgroundDrawable(new ColorDrawable(ThemeUtils.getColor(this, R.attr.tab_bar_background_color)));

        // set the tab to display & set current tab index
        mActionBar.setSelectedNavigationItem(tabIndexToRestore);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        Bundle tabHolder = (Bundle) tab.getTag();
        String fragmentTag = tabHolder.getString(KEY_FRAGMENT_TAG, "");
        Log.d(LOG_TAG, "## onTabSelected() FragTag=" + fragmentTag);

        resetUi();

        if (fragmentTag.equals(TAG_FRAGMENT_GROUP_HOME)) {
            mGroupDetailsHomeFragment = (GroupDetailsHomeFragment) getSupportFragmentManager().findFragmentByTag(TAG_FRAGMENT_GROUP_HOME);

            if (null == mGroupDetailsHomeFragment) {
                mGroupDetailsHomeFragment = new GroupDetailsHomeFragment();
                ft.replace(R.id.group_details_fragment_container, mGroupDetailsHomeFragment, TAG_FRAGMENT_GROUP_HOME);
                Log.d(LOG_TAG, "## onTabSelected() home frag replace");
            } else {
                ft.attach(mGroupDetailsHomeFragment);
                Log.d(LOG_TAG, "## onTabSelected() home frag attach");
            }
            mCurrentFragmentTag = TAG_FRAGMENT_GROUP_HOME;
        } else if (fragmentTag.equals(TAG_FRAGMENT_GROUP_PEOPLE)) {
            mGroupDetailsPeopleFragment = (GroupDetailsPeopleFragment) getSupportFragmentManager().findFragmentByTag(TAG_FRAGMENT_GROUP_PEOPLE);

            if (null == mGroupDetailsPeopleFragment) {
                mGroupDetailsPeopleFragment = new GroupDetailsPeopleFragment();
                ft.replace(R.id.group_details_fragment_container, mGroupDetailsPeopleFragment, TAG_FRAGMENT_GROUP_PEOPLE);
                Log.d(LOG_TAG, "## onTabSelected() people frag replace");
            } else {
                ft.attach(mGroupDetailsPeopleFragment);
                Log.d(LOG_TAG, "## onTabSelected() people frag attach");
            }
            mCurrentFragmentTag = TAG_FRAGMENT_GROUP_PEOPLE;
        } else if (fragmentTag.equals(TAG_FRAGMENT_GROUP_ROOMS)) {
            mGroupDetailsRoomsFragment = (GroupDetailsRoomsFragment) getSupportFragmentManager().findFragmentByTag(TAG_FRAGMENT_GROUP_ROOMS);

            if (null == mGroupDetailsRoomsFragment) {
                mGroupDetailsRoomsFragment = new GroupDetailsRoomsFragment();
                ft.replace(R.id.group_details_fragment_container, mGroupDetailsRoomsFragment, TAG_FRAGMENT_GROUP_ROOMS);
                Log.d(LOG_TAG, "## onTabSelected() rooms frag replace");
            } else {
                ft.attach(mGroupDetailsRoomsFragment);
                Log.d(LOG_TAG, "## onTabSelected() rooms frag attach");
            }
            mCurrentFragmentTag = TAG_FRAGMENT_GROUP_ROOMS;
        }

        mActionBar.setStackedBackgroundDrawable(new ColorDrawable(ThemeUtils.getColor(this, R.attr.tab_bar_background_color)));

        // reset the activity title
        // some fragments update it (VectorRoomDetailsMembersFragment for example)
        if (null != getSupportActionBar()) {
            getSupportActionBar().setTitle(this.getResources().getString(R.string.title_activity_group_details));
        }
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
        Bundle tabHolder = (Bundle) tab.getTag();
        String fragmentTag = tabHolder.getString(KEY_FRAGMENT_TAG, "");
        Log.d(LOG_TAG, "## onTabUnselected() FragTag=" + fragmentTag);

        // save tab UI context before leaving the tab...
        saveUiTabContext(tab);

        if (fragmentTag.equals(TAG_FRAGMENT_GROUP_HOME)) {
            if (null != mGroupDetailsHomeFragment) {
                ft.detach(mGroupDetailsHomeFragment);
            }
        } else if (fragmentTag.equals(TAG_FRAGMENT_GROUP_PEOPLE)) {
            if (null != mGroupDetailsPeopleFragment) {
                ft.detach(mGroupDetailsPeopleFragment);
            }
        } else if (fragmentTag.equals(TAG_FRAGMENT_GROUP_ROOMS)) {
            if (null != mGroupDetailsRoomsFragment) {
                ft.detach(mGroupDetailsRoomsFragment);
            }
        }
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
    }
}
