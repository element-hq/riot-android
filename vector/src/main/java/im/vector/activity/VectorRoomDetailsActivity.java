/*
 * Copyright 2014 OpenMarket Ltd
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

package im.vector.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.tabs.TabLayout;

import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.listeners.MXEventListener;

import java.util.List;

import butterknife.BindView;
import im.vector.Matrix;
import im.vector.R;
import im.vector.contacts.ContactsManager;
import im.vector.fragments.VectorRoomDetailsMembersFragment;
import im.vector.fragments.VectorRoomSettingsFragment;
import im.vector.fragments.VectorSearchRoomFilesListFragment;
import im.vector.util.PermissionsToolsKt;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * This class implements the room details screen, using a tab UI pattern.
 * Each tab is filled in with its corresponding fragment.
 * There are 2 tabs:
 * - People tab: the members of the room
 * - Settings tab: the settings of the room
 */
public class VectorRoomDetailsActivity extends MXCActionBarActivity implements TabLayout.OnTabSelectedListener {
    private static final String LOG_TAG = VectorRoomDetailsActivity.class.getSimpleName();

    // exclude the room ID
    public static final String EXTRA_ROOM_ID = "VectorRoomDetailsActivity.EXTRA_ROOM_ID";
    // open a dedicated tab at launch
    public static final String EXTRA_SELECTED_TAB_ID = "VectorRoomDetailsActivity.EXTRA_SELECTED_TAB_ID";

    // tab related items
    private static final String TAG_FRAGMENT_PEOPLE_ROOM_DETAILS = "im.vector.activity.TAG_FRAGMENT_PEOPLE_ROOM_DETAILS";
    private static final String TAG_FRAGMENT_FILES_DETAILS = "im.vector.activity.TAG_FRAGMENT_FILES_DETAILS";
    private static final String TAG_FRAGMENT_SETTINGS_ROOM_DETAIL = "im.vector.activity.TAG_FRAGMENT_SETTINGS_ROOM_DETAIL";

    // a tab can be selected at launch (with EXTRA_SELECTED_TAB_ID)
    // so the tab index must be fixed.
    public static final int PEOPLE_TAB_INDEX = 0;
    public static final int FILE_TAB_INDEX = 1;
    public static final int SETTINGS_TAB_INDEX = 2;

    private int mCurrentTabIndex = -1;
    private VectorRoomDetailsMembersFragment mRoomDetailsMembersFragment;
    private VectorSearchRoomFilesListFragment mSearchFilesFragment;
    private VectorRoomSettingsFragment mRoomSettingsFragment;

    @BindView(R.id.tabLayout)
    TabLayout mTabLayout;

    // activity life cycle management:
    // - Bundle keys
    private static final String KEY_STATE_CURRENT_TAB_INDEX = "CURRENT_SELECTED_TAB";

    private String mRoomId;
    private String mMatrixId;

    // request the contacts permission
    private boolean mIsContactsPermissionChecked;

    private final MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLeaveRoom(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // pop to the home activity
                    Intent intent = new Intent(VectorRoomDetailsActivity.this, VectorHomeActivity.class);
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
            });
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_vector_room_details;
    }

    @Override
    public void initUiAndData() {
        configureToolbar();

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

        if ((null == mSession) || !mSession.isAlive()) {
            finish();
            return;
        }

        mRoomId = intent.getStringExtra(EXTRA_ROOM_ID);
        mRoom = mSession.getDataHandler().getRoom(mRoomId);
        int selectedTab = intent.getIntExtra(EXTRA_SELECTED_TAB_ID, -1);

        // UI widgets binding & init fields
        setWaitingView(findViewById(R.id.settings_loading_layout));

        // tab creation and restore tabs UI context
        createNavigationTabs(selectedTab);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        List<Fragment> allFragments = getSupportFragmentManager().getFragments();

        // dispatch the result to each fragments
        for (Fragment fragment : allFragments) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(LOG_TAG, "## onSaveInstanceState(): ");

        // save current tab
        if (null != mTabLayout) {
            int currentIndex = mTabLayout.getSelectedTabPosition();
            outState.putInt(KEY_STATE_CURRENT_TAB_INDEX, currentIndex);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Transmit to Fragment
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (0 == permissions.length) {
            Log.d(LOG_TAG, "## onRequestPermissionsResult(): cancelled " + requestCode);
        } else if (requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE) {
            if (Manifest.permission.READ_CONTACTS.equals(permissions[0])) {
                if (PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                    Log.d(LOG_TAG, "## onRequestPermissionsResult(): READ_CONTACTS permission granted");
                } else {
                    Log.w(LOG_TAG, "## onRequestPermissionsResult(): READ_CONTACTS permission not granted");
                    Toast.makeText(this, R.string.missing_permissions_warning, Toast.LENGTH_SHORT).show();
                }

                ContactsManager.getInstance().refreshLocalContactsSnapshot();
            }
        }
    }

    /**
     * Back key management
     */
    public void onBackPressed() {
        boolean isTrapped = false;

        if (PEOPLE_TAB_INDEX == mCurrentTabIndex) {
            isTrapped = mRoomDetailsMembersFragment.onBackPressed();
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

        mTabLayout.removeOnTabSelectedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSession.isAlive()) {
            // check if the room has been left from another client
            if ((!mRoom.isJoined() && !mRoom.isInvited())
                    || !mSession.getDataHandler().doesRoomExist(mRoom.getRoomId())) {
                // pop to the home activity
                Intent intent = new Intent(VectorRoomDetailsActivity.this, VectorHomeActivity.class);
                intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return;
            }

            // listen for room leave event
            mRoom.addEventListener(mEventListener);

            // start the file search if the selected tab is the file one
            startFileSearch();
        }

        mTabLayout.addOnTabSelectedListener(this);
    }

    /**
     * Reset the UI to its init state:
     * - "waiting while searching" screen disabled
     * - background image visible
     * - no results message disabled
     */
    private void resetUi() {
        // stop "wait while searching" screen
        hideWaitingView();
    }

    // =============================================================================================
    // Tabs logic implementation
    private void createNavigationTabs(int defaultSelectedTab) {
        int tabIndexToRestore;

        // set the default tab to be displayed
        tabIndexToRestore = isFirstCreation() ? -1 : getSavedInstanceState().getInt(KEY_STATE_CURRENT_TAB_INDEX, -1);

        if (-1 == tabIndexToRestore) {
            tabIndexToRestore = defaultSelectedTab;
        }

        if (-1 == tabIndexToRestore) {
            // default value: display the search in rooms tab
            tabIndexToRestore = PEOPLE_TAB_INDEX;
        }

        // set the tab to display & set current tab index
        TabLayout.Tab tab = mTabLayout.getTabAt(defaultSelectedTab);

        if (tab != null) {
            tab.select();
        }

        // Ensure Fragment is always loaded
        onTabSelected(tabIndexToRestore);
    }

    /**
     * Called when a tab enters the selected state.
     *
     * @param tab The tab that was selected
     */
    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        onTabSelected(tab.getPosition());
    }

    private void onTabSelected(int tabIndex) {
        resetUi();

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        if (tabIndex == PEOPLE_TAB_INDEX) {
            mRoomDetailsMembersFragment = (VectorRoomDetailsMembersFragment) getSupportFragmentManager().findFragmentByTag(TAG_FRAGMENT_PEOPLE_ROOM_DETAILS);
            if (null == mRoomDetailsMembersFragment) {
                mRoomDetailsMembersFragment = VectorRoomDetailsMembersFragment.newInstance();
                ft.replace(R.id.room_details_fragment_container, mRoomDetailsMembersFragment, TAG_FRAGMENT_PEOPLE_ROOM_DETAILS);
                Log.d(LOG_TAG, "## onTabSelected() people frag replace");
            } else {
                ft.attach(mRoomDetailsMembersFragment);
                Log.d(LOG_TAG, "## onTabSelected() people frag attach");
            }
            mCurrentTabIndex = PEOPLE_TAB_INDEX;

            if (!mIsContactsPermissionChecked) {
                mIsContactsPermissionChecked = true;
                PermissionsToolsKt.checkPermissions(PermissionsToolsKt.PERMISSIONS_FOR_MEMBER_DETAILS, this, PermissionsToolsKt.PERMISSION_REQUEST_CODE);
            }
        } else if (tabIndex == SETTINGS_TAB_INDEX) {
            mRoomSettingsFragment = (VectorRoomSettingsFragment) getSupportFragmentManager().findFragmentByTag(TAG_FRAGMENT_SETTINGS_ROOM_DETAIL);
            if (null == mRoomSettingsFragment) {
                mRoomSettingsFragment = VectorRoomSettingsFragment.newInstance(mMatrixId, mRoomId);
                ft.replace(R.id.room_details_fragment_container, mRoomSettingsFragment, TAG_FRAGMENT_SETTINGS_ROOM_DETAIL);
                Log.d(LOG_TAG, "## onTabSelected() settings frag replace");
            } else {
                ft.attach(mRoomSettingsFragment);
                Log.d(LOG_TAG, "## onTabSelected() settings frag attach");
            }

            mCurrentTabIndex = SETTINGS_TAB_INDEX;
        } else if (tabIndex == FILE_TAB_INDEX) {
            mSearchFilesFragment = (VectorSearchRoomFilesListFragment) getSupportFragmentManager().findFragmentByTag(TAG_FRAGMENT_FILES_DETAILS);
            if (null == mSearchFilesFragment) {
                mSearchFilesFragment = VectorSearchRoomFilesListFragment.newInstance(mSession.getCredentials().userId,
                        mRoomId, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
                ft.replace(R.id.room_details_fragment_container, mSearchFilesFragment, TAG_FRAGMENT_FILES_DETAILS);
                Log.d(LOG_TAG, "## onTabSelected() file frag replace");
            } else {
                ft.attach(mSearchFilesFragment);
                Log.d(LOG_TAG, "## onTabSelected() file frag attach");
            }

            mCurrentTabIndex = FILE_TAB_INDEX;
            startFileSearch();
        } else {
            Toast.makeText(this, "Not yet implemented", Toast.LENGTH_SHORT).show();
            mCurrentTabIndex = SETTINGS_TAB_INDEX;
            Log.w(LOG_TAG, "## onTabSelected() unknown tab selected!!");
        }

        ft.commit();

        // reset the activity title
        // some fragments update it (VectorRoomDetailsMembersFragment for example)
        if (null != getSupportActionBar()) {
            getSupportActionBar().setTitle(R.string.room_details_title);
        }
    }

    /**
     * Start a file search
     */
    private void startFileSearch() {
        if (mCurrentTabIndex == FILE_TAB_INDEX) {
            showWaitingView();
            mSearchFilesFragment.startFilesSearch(new MatrixMessageListFragment.OnSearchResultListener() {
                @Override
                public void onSearchSucceed(int nbrMessages) {
                    onSearchEnd(FILE_TAB_INDEX, nbrMessages);
                }

                @Override
                public void onSearchFailed() {
                    onSearchEnd(FILE_TAB_INDEX, 0);
                }
            });
        }
    }

    /**
     * The search is done.
     *
     * @param tabIndex    the tab index
     * @param nbrMessages the number of found messages.
     */
    private void onSearchEnd(int tabIndex, int nbrMessages) {
        if (mCurrentTabIndex == tabIndex) {
            Log.d(LOG_TAG, "## onSearchEnd() nbrMsg=" + nbrMessages);
            // stop "wait while searching" screen
            hideWaitingView();
        }
    }

    /**
     * Called when a tab exits the selected state.
     *
     * @param tab The tab that was unselected
     */
    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        if (tab.getPosition() == FILE_TAB_INDEX
                && mSearchFilesFragment != null) {
            mSearchFilesFragment.cancelCatchingRequests();
        }
    }

    /**
     * Called when a tab that is already selected is chosen again by the user. Some applications
     * may use this action to return to the top level of a category.
     *
     * @param tab The tab that was reselected.
     */
    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        // Nothing to do
    }
}
