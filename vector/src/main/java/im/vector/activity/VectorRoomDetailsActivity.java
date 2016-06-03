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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.TabListener;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.listeners.MXEventListener;

import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.fragments.VectorRoomDetailsMembersFragment;
import im.vector.fragments.VectorRoomSettingsFragment;
import im.vector.fragments.VectorSearchRoomFilesListFragment;

/**
 * This class implements the room details screen, using a tab UI pattern.
 * Each tab is filled in with its corresponding fragment.
 * There are 2 tabs:
 * - People tab: the members of the room
 * - Settings tab: the settings of the room
 */
public class VectorRoomDetailsActivity extends MXCActionBarActivity implements TabListener {
    private static final String LOG_TAG = "VectorRoomDetailsAct";

    // exclude the room ID
    public static final String EXTRA_ROOM_ID = "VectorRoomDetailsActivity.EXTRA_ROOM_ID";

    // tab related items
    private static final String TAG_FRAGMENT_PEOPLE_ROOM_DETAILS = "im.vector.activity.TAG_FRAGMENT_PEOPLE_ROOM_DETAILS";
    private static final String TAG_FRAGMENT_FILES_DETAILS = "im.vector.activity.TAG_FRAGMENT_FILES_DETAILS";
    private static final String TAG_FRAGMENT_SETTINGS_ROOM_DETAIL = "im.vector.activity.TAG_FRAGMENT_SETTINGS_ROOM_DETAIL";
    private static final String KEY_FRAGMENT_TAG = "KEY_FRAGMENT_TAG";
    private int mPeopleTabIndex = -1;
    private int mFileTabIndex = -1;
    private int mSettingsTabIndex = -1;
    private int mCurrentTabIndex = -1;
    private ActionBar mActionBar;
    private VectorRoomDetailsMembersFragment mAddPeopleFragment;
    private VectorSearchRoomFilesListFragment mSearchFilesFragment;
    private VectorRoomSettingsFragment mRoomSettingsFragment;

    // activity life cycle management:
    // - Bundle keys
    private static final String KEY_STATE_CURRENT_TAB_INDEX = "CURRENT_SELECTED_TAB";

    // UI items
    private View mLoadOldestContentView;
    private View mWaitWhileSearchInProgressView;

    private String mRoomId;
    private String mMatrixId;

    private final MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLeaveRoom(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // pop to the home activity
                    Intent intent = new Intent(VectorRoomDetailsActivity.this, VectorHomeActivity.class);
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    VectorRoomDetailsActivity.this.startActivity(intent);
                }
            });
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

        Intent intent = getIntent();

        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            mMatrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        // get current session
        mSession = Matrix.getInstance(getApplicationContext()).getSession(mMatrixId);
        if (null == mSession) {
            finish();
            return;
        }

        mRoomId = intent.getStringExtra(EXTRA_ROOM_ID);
        mRoom = mSession.getDataHandler().getRoom(mRoomId);

        setContentView(R.layout.activity_vector_room_details);

        // UI widgets binding & init fields
        mWaitWhileSearchInProgressView = findViewById(R.id.settings_loading_layout);
        mLoadOldestContentView = findViewById(R.id.search_load_oldest_progress);

        // tab creation and restore tabs UI context
        mActionBar = getSupportActionBar();
        createNavigationTabs(savedInstanceState);
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

    /**
     * Back key management
     */
    public void onBackPressed() {
        boolean isTrapped = false;

        if (mPeopleTabIndex == mCurrentTabIndex) {
            isTrapped = mAddPeopleFragment.onBackPressed();
        }

        if (!isTrapped) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // listen for room leave event
        mRoom.removeEventListener(mEventListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSession.isAlive()) {
            // check if the room has been left from another client
            if ((null == mRoom.getMember(mSession.getMyUserId())) || !mSession.getDataHandler().doesRoomExist(mRoom.getRoomId())) {
                // pop to the home activity
                Intent intent = new Intent(VectorRoomDetailsActivity.this, VectorHomeActivity.class);
                intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                VectorRoomDetailsActivity.this.startActivity(intent);
                return;
            }

            // listen for room leave event
            mRoom.addEventListener(mEventListener);

            // start the file search if the selected tab is the file one
            startFileSearch();
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
     * Restore the UI context associated with the tab
     *
     * @param aTabToRestore the tab to be restored
     */
    private void restoreUiTabContext(ActionBar.Tab aTabToRestore) {
        // Bundle tabTag = (Bundle) aTabToRestore.getTag();
        // restore here context here
    }

    /**
     * Reset the UI to its init state:
     * - "waiting while searching" screen disabled
     * - background image visible
     * - no results message disabled
     */
    private void resetUi() {
        // stop "wait while searching" screen
        if (null != mWaitWhileSearchInProgressView) {
            mWaitWhileSearchInProgressView.setVisibility(View.GONE);
        }

        if (null != mLoadOldestContentView) {
            mLoadOldestContentView.setVisibility(View.GONE);
        }
    }

    // =============================================================================================
    // Tabs logic implementation
    private void createNavigationTabs(Bundle aSavedInstanceState) {
        int tabIndex = 0;
        int tabIndexToRestore;

        // Set the tabs navigation mode
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // People tab creation: display the members of the this room
        ActionBar.Tab tabToBeadded = mActionBar.newTab();
        String tabTitle = getResources().getString(R.string.room_details_people);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        Bundle tabBundle = new Bundle();
        tabBundle.putString(KEY_FRAGMENT_TAG, TAG_FRAGMENT_PEOPLE_ROOM_DETAILS);
        tabToBeadded.setTag(tabBundle);
        mPeopleTabIndex = tabIndex++; // set tab index
        mActionBar.addTab(tabToBeadded);

        // Files tab creation: display the file list in the room history
        tabToBeadded = mActionBar.newTab();
        tabTitle = getResources().getString(R.string.room_details_files);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        tabBundle = new Bundle();
        tabBundle.putString(KEY_FRAGMENT_TAG, TAG_FRAGMENT_FILES_DETAILS);
        tabToBeadded.setTag(tabBundle);
        mFileTabIndex = tabIndex++; // set tab index
        mActionBar.addTab(tabToBeadded);


        // Settings tab creation: the room settings (room photo, name, topic..)
        tabToBeadded = mActionBar.newTab();
        tabTitle = getResources().getString(R.string.room_details_settings);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        tabBundle = new Bundle();
        tabBundle.putString(KEY_FRAGMENT_TAG, TAG_FRAGMENT_SETTINGS_ROOM_DETAIL);
        tabToBeadded.setTag(tabBundle);
        mSettingsTabIndex = tabIndex; // set tab index
        mActionBar.addTab(tabToBeadded);

        // set the default tab to be displayed
        tabIndexToRestore = (null != aSavedInstanceState) ? aSavedInstanceState.getInt(KEY_STATE_CURRENT_TAB_INDEX, -1) : -1;
        if (-1 == tabIndexToRestore) {
            // default value: display the search in rooms tab
            tabIndexToRestore = mPeopleTabIndex;
        }

        // set the tab to display & set current tab index
        mActionBar.setSelectedNavigationItem(tabIndexToRestore);
        mCurrentTabIndex = tabIndexToRestore;
    }

    /**
     * Called when a tab enters the selected state.
     *
     * @param tab The tab that was selected
     * @param ft  A {@link FragmentTransaction} for queuing fragment operations to execute
     *            during a tab switch. The previous tab's unselect and this tab's select will be
     *            executed in a single transaction. This FragmentTransaction does not support
     */
    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        Bundle tabHolder = (Bundle) tab.getTag();
        String fragmentTag = tabHolder.getString(KEY_FRAGMENT_TAG, "");
        Log.d(LOG_TAG, "## onTabSelected() FragTag=" + fragmentTag);

        // inter tab selection life cycle: restore tab UI
        restoreUiTabContext(tab);
        resetUi();

        if (fragmentTag.equals(TAG_FRAGMENT_PEOPLE_ROOM_DETAILS)) {
            mAddPeopleFragment = (VectorRoomDetailsMembersFragment)getSupportFragmentManager().findFragmentByTag(TAG_FRAGMENT_PEOPLE_ROOM_DETAILS);
            if (null == mAddPeopleFragment) {
                mAddPeopleFragment = VectorRoomDetailsMembersFragment.newInstance();
                ft.replace(R.id.room_details_fragment_container, mAddPeopleFragment, TAG_FRAGMENT_PEOPLE_ROOM_DETAILS);
                Log.d(LOG_TAG, "## onTabSelected() people frag replace");
            } else {
                ft.attach(mAddPeopleFragment);
                Log.d(LOG_TAG, "## onTabSelected() people frag attach");
            }
            mCurrentTabIndex = mPeopleTabIndex;
        }
        else if (fragmentTag.equals(TAG_FRAGMENT_SETTINGS_ROOM_DETAIL)) {
            onTabSelectSettingsFragment();
            mCurrentTabIndex = mSettingsTabIndex;
        }
        else if (fragmentTag.equals(TAG_FRAGMENT_FILES_DETAILS)) {
            mSearchFilesFragment = (VectorSearchRoomFilesListFragment)getSupportFragmentManager().findFragmentByTag(TAG_FRAGMENT_FILES_DETAILS);
            if (null == mSearchFilesFragment) {
                mSearchFilesFragment = VectorSearchRoomFilesListFragment.newInstance(mSession.getCredentials().userId, mRoomId, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
                ft.replace(R.id.room_details_fragment_container, mSearchFilesFragment, TAG_FRAGMENT_FILES_DETAILS);
                Log.d(LOG_TAG, "## onTabSelected() file frag replace");
            } else {
                ft.attach(mSearchFilesFragment);
                Log.d(LOG_TAG, "## onTabSelected() file frag attach");
            }

            mCurrentTabIndex = mFileTabIndex;
            startFileSearch();
        }
        else {
            Toast.makeText(this, "Not yet implemented", Toast.LENGTH_SHORT).show();
            mCurrentTabIndex = mSettingsTabIndex;
            Log.w(LOG_TAG, "## onTabSelected() unknown tab selected!!");
        }

        // reset the activity title
        // some fragments update it (VectorRoomDetailsMembersFragment for example)
        if (null != getSupportActionBar()) {
            getSupportActionBar().setTitle(this.getResources().getString(R.string.room_details_title));
        }
    }

    private void startFileSearch() {
        if (mCurrentTabIndex == mFileTabIndex) {
            mWaitWhileSearchInProgressView.setVisibility(View.VISIBLE);
            mSearchFilesFragment.startFilesSearch(new MatrixMessageListFragment.OnSearchResultListener() {
                @Override
                public void onSearchSucceed(int nbrMessages) {
                    onSearchEnd(mFileTabIndex, nbrMessages);
                }

                @Override
                public void onSearchFailed() {
                    onSearchEnd(mFileTabIndex, 0);
                }
            });
        }
    }

    /**
     * The search is done.
     * @param tabIndex the tab index
     * @param nbrMessages the number of found messages.
     */
    private void onSearchEnd(int tabIndex, int nbrMessages) {
        if (mCurrentTabIndex == tabIndex) {
            Log.d(LOG_TAG, "## onSearchEnd() nbrMsg=" + nbrMessages);
            // stop "wait while searching" screen
            mWaitWhileSearchInProgressView.setVisibility(View.GONE);
        }
    }

    /**
     * Called when a tab exits the selected state.
     *
     * @param tab The tab that was unselected
     * @param ft  A {@link FragmentTransaction} for queuing fragment operations to execute
     *            during a tab switch. This tab's unselect and the newly selected tab's select
     *            will be executed in a single transaction. This FragmentTransaction does not
     */
    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
        Bundle tabHolder = (Bundle) tab.getTag();
        String fragmentTag = tabHolder.getString(KEY_FRAGMENT_TAG, "");
        Log.d(LOG_TAG, "## onTabUnselected() FragTag=" + fragmentTag);

        // save tab UI context before leaving the tab...
        saveUiTabContext(tab);

        if (fragmentTag.equals(TAG_FRAGMENT_PEOPLE_ROOM_DETAILS)) {
            if (null != mAddPeopleFragment) {
                ft.detach(mAddPeopleFragment);
            }
        }
        else if (fragmentTag.equals(TAG_FRAGMENT_SETTINGS_ROOM_DETAIL)) {
            onTabUnselectedSettingsFragment();
        }
        else if (fragmentTag.equals(TAG_FRAGMENT_FILES_DETAILS)) {
            if (null != mSearchFilesFragment) {
                mSearchFilesFragment.cancelCatchingRequests();
                ft.detach(mSearchFilesFragment);
            }
        }

        else {
            Log.w(LOG_TAG, "## onTabUnselected() unknown tab selected!!");
        }
    }

    /**
     * Called when a tab that is already selected is chosen again by the user.
     * Some applications may use this action to return to the top level of a category.
     *
     * @param tab The tab that was reselected.
     * @param ft  A {@link FragmentTransaction} for queuing fragment operations to execute
     *            once this method returns. This FragmentTransaction does not support
     */
    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {

    }

    /**
     * Specific method to add the fragment, to avoid using the FragmentTransaction
     * that requires a Fragment based on the support V4.
     */
    private void onTabSelectSettingsFragment(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (null == mRoomSettingsFragment) {
                    mRoomSettingsFragment = VectorRoomSettingsFragment.newInstance(mMatrixId, mRoomId);
                    getFragmentManager().beginTransaction().replace(R.id.room_details_fragment_container, mRoomSettingsFragment, TAG_FRAGMENT_SETTINGS_ROOM_DETAIL).commit();
                    Log.d(LOG_TAG, "## onTabSelectSettingsFragment() settings frag replace");
                } else {
                    getFragmentManager().beginTransaction().attach(mRoomSettingsFragment).commit();
                    Log.d(LOG_TAG, "## onTabSelectSettingsFragment() settings frag attach");
                }
            }
        });
    }

    /**
     * Specific method to add the fragment, to avoid using the FragmentTransaction
     * that requires a Fragment based on the support V4.
     */
    private void onTabUnselectedSettingsFragment(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (null != mRoomSettingsFragment)
                    getFragmentManager().beginTransaction().detach(mRoomSettingsFragment).commit();
            }
        });
    }
    // ==========================================================================================

}
