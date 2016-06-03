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

package im.vector.activity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.support.v7.app.ActionBar.TabListener;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;

import im.vector.Matrix;
import im.vector.R;
import im.vector.fragments.VectorSearchPeopleListFragment;
import im.vector.fragments.VectorSearchRoomsFilesListFragment;
import im.vector.fragments.VectorSearchRoomsListFragment;
import im.vector.fragments.VectorSearchMessagesListFragment;


/**
 * Displays a generic activity search method
 */
public class VectorUnifiedSearchActivity extends VectorBaseSearchActivity implements TabListener, VectorBaseSearchActivity.IVectorSearchActivity  {
    private static final String LOG_TAG = "VectorUniSrchActivity";

    public static final CharSequence NOT_IMPLEMENTED = "Not yet implemented";

    // tab related items
    private static final String TAG_FRAGMENT_SEARCH_IN_MESSAGE = "im.vector.activity.TAG_FRAGMENT_SEARCH_IN_MESSAGE";
    private static final String TAG_FRAGMENT_SEARCH_IN_ROOM_NAMES = "im.vector.activity.TAG_FRAGMENT_SEARCH_IN_ROOM_NAMES";
    private static final String TAG_FRAGMENT_SEARCH_PEOPLE = "im.vector.activity.TAG_FRAGMENT_SEARCH_PEOPLE";
    private static final String TAG_FRAGMENT_SEARCH_IN_FILES = "im.vector.activity.TAG_FRAGMENT_SEARCH_IN_FILES";
    private int mSearchInRoomNamesTabIndex = -1;
    private int mSearchInMessagesTabIndex = -1;
    private int mSearchInPeopleTabIndex = -1;
    private int mSearchInFilesTabIndex = -1;
    private int mCurrentTabIndex = -1;

    // activity life cycle management:
    // - Bundle keys
    private static final String KEY_STATE_CURRENT_TAB_INDEX = "CURRENT_SELECTED_TAB";
    private static final String KEY_STATE_SEARCH_PATTERN = "SEARCH_PATTERN";

    // search fragments
    private VectorSearchMessagesListFragment mSearchInMessagesFragment;
    private VectorSearchRoomsListFragment mSearchInRoomNamesFragment;
    private VectorSearchRoomsFilesListFragment mSearchInFilesFragment;
    private VectorSearchPeopleListFragment mSearchInPeopleFragment;
    private MXSession mSession;

    // UI items
    private ImageView mBackgroundImageView;
    private TextView mNoResultsTxtView;
    private View mLoadOldestContentView;
    private View mWaitWhileSearchInProgressView;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_unified_search);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        // the session should be passed in parameter
        // but the current design does not describe how the multi accounts will be managed.
        mSession = Matrix.getInstance(this).getDefaultSession();
        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        // UI widgets binding & init fields
        mBackgroundImageView = (ImageView)findViewById(R.id.search_background_imageview);
        mNoResultsTxtView = (TextView)findViewById(R.id.search_no_result_textview);
        mWaitWhileSearchInProgressView = findViewById(R.id.search_in_progress_view);
        mLoadOldestContentView = findViewById(R.id.search_load_oldest_progress);

        // tab creation and restore tabs UI context
        createNavigationTabs(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    // inherited from VectorBaseSearchActivity
    protected void onPatternUpdate() {
        searchAccordingToTabHandler();
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

        // display the background
        if (null != mBackgroundImageView) {
            mBackgroundImageView.setVisibility(View.VISIBLE);
        }

        if (null != mNoResultsTxtView) {
            mNoResultsTxtView.setVisibility(View.GONE);
        }

        if (null != mLoadOldestContentView) {
            mLoadOldestContentView.setVisibility(View.GONE);
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

            // display the background only if there is no result
            if (0 == nbrMessages) {
                mBackgroundImageView.setVisibility(View.VISIBLE);
            } else {
                mBackgroundImageView.setVisibility(View.GONE);
            }

            // display the "no result" text only if the researched text is not empty
            mNoResultsTxtView.setVisibility(((0 == nbrMessages) && !TextUtils.isEmpty(mPatternToSearchEditText.getText().toString())) ? View.VISIBLE : View.GONE);
        }
    }

    private void searchAccordingToTabHandler() {
        int currentIndex = mActionBar.getSelectedNavigationIndex();

        resetUi();

        String pattern = mPatternToSearchEditText.getText().toString();

        if((currentIndex == mSearchInRoomNamesTabIndex) && (null != mSearchInRoomNamesFragment)) {
            // display the "wait while searching" screen (progress bar)
            mWaitWhileSearchInProgressView.setVisibility(View.VISIBLE);

            mSearchInRoomNamesFragment.searchPattern(pattern, new MatrixMessageListFragment.OnSearchResultListener() {
                @Override
                public void onSearchSucceed(int nbrMessages) {
                    onSearchEnd(mSearchInRoomNamesTabIndex, nbrMessages);
                }

                @Override
                public void onSearchFailed() {
                    onSearchEnd(mSearchInRoomNamesTabIndex, 0);
                }
            });
        }
        else if ((currentIndex == mSearchInMessagesTabIndex) && (null != mSearchInMessagesFragment)) {
            // display the "wait while searching" screen (progress bar)
            mWaitWhileSearchInProgressView.setVisibility(View.VISIBLE);

            mSearchInMessagesFragment.searchPattern(pattern, new MatrixMessageListFragment.OnSearchResultListener() {
                @Override
                public void onSearchSucceed(int nbrMessages) {
                    onSearchEnd(mSearchInMessagesTabIndex, nbrMessages);
                }

                @Override
                public void onSearchFailed() {
                    onSearchEnd(mSearchInMessagesTabIndex, 0);
                }
            });
        }
        else if ((currentIndex == mSearchInFilesTabIndex) && (null != mSearchInFilesFragment)) {
            // display the "wait while searching" screen (progress bar)
            mWaitWhileSearchInProgressView.setVisibility(View.VISIBLE);

            mSearchInFilesFragment.searchPattern(pattern, new MatrixMessageListFragment.OnSearchResultListener() {
                @Override
                public void onSearchSucceed(int nbrMessages) {
                    onSearchEnd(mSearchInFilesTabIndex, nbrMessages);
                }

                @Override
                public void onSearchFailed() {
                    onSearchEnd(mSearchInFilesTabIndex, 0);
                }
            });
        }
        else if ((currentIndex == mSearchInPeopleTabIndex) && (null != mSearchInPeopleFragment)) {
            // display the "wait while searching" screen (progress bar)
            mWaitWhileSearchInProgressView.setVisibility(View.VISIBLE);

            mSearchInPeopleFragment.searchPattern(pattern, new MatrixMessageListFragment.OnSearchResultListener() {
                @Override
                public void onSearchSucceed(int nbrMessages) {
                    onSearchEnd(mSearchInPeopleTabIndex, nbrMessages);
                }

                @Override
                public void onSearchFailed() {
                    onSearchEnd(mSearchInPeopleTabIndex, 0);
                }
            });
        }
        else {
            onSearchEnd(currentIndex, 0);
            Toast.makeText(VectorUnifiedSearchActivity.this, NOT_IMPLEMENTED, Toast.LENGTH_SHORT).show();
        }
    }

    //==============================================================================================================
    // Tabs logic implementation
    //==============================================================================================================

    /**
     * Create the search fragment instances from the saved instance;
     * @param aSavedInstanceState the saved instance.
     */
    private void createNavigationTabs(Bundle aSavedInstanceState) {
        int tabIndex = 0;
        int tabIndexToRestore;

        // Set the tabs navigation mode
        mActionBar.setNavigationMode(android.support.v7.app.ActionBar.NAVIGATION_MODE_TABS);

        // ROOMS names search tab creation
        android.support.v7.app.ActionBar.Tab tabToBeadded = mActionBar.newTab();
        String tabTitle = getResources().getString(R.string.tab_title_search_rooms);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        tabToBeadded.setTag(TAG_FRAGMENT_SEARCH_IN_ROOM_NAMES);
        mActionBar.addTab(tabToBeadded);
        mSearchInRoomNamesTabIndex = tabIndex++;

        // MESSAGES search tab creation
        tabToBeadded = mActionBar.newTab();
        tabTitle = getResources().getString(R.string.tab_title_search_messages);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        tabToBeadded.setTag(TAG_FRAGMENT_SEARCH_IN_MESSAGE);
        mActionBar.addTab(tabToBeadded);
        mSearchInMessagesTabIndex = tabIndex++;

        // PEOPLE search tab creation
        tabToBeadded = mActionBar.newTab();
        tabTitle = getResources().getString(R.string.tab_title_search_people);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        tabToBeadded.setTag(TAG_FRAGMENT_SEARCH_PEOPLE);
        mActionBar.addTab(tabToBeadded);
        mSearchInPeopleTabIndex = tabIndex++;

        // FILES search tab creation
        tabToBeadded = mActionBar.newTab();
        tabTitle = getResources().getString(R.string.tab_title_search_files);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        tabToBeadded.setTag(TAG_FRAGMENT_SEARCH_IN_FILES);
        mActionBar.addTab(tabToBeadded);
        mSearchInFilesTabIndex = tabIndex++;

        // set the default tab to be displayed
        tabIndexToRestore = (null != aSavedInstanceState)? aSavedInstanceState.getInt(KEY_STATE_CURRENT_TAB_INDEX, 0) : 0;
        if(-1 == tabIndexToRestore) {
            // default value: display the search in rooms tab
            tabIndexToRestore = mSearchInRoomNamesTabIndex;
        }
        mCurrentTabIndex = tabIndexToRestore;
        // set the tab to display
        mActionBar.setSelectedNavigationItem(tabIndexToRestore);

        // restore the searched pattern
        mPatternToSearchEditText.setText((null != aSavedInstanceState) ? aSavedInstanceState.getString(KEY_STATE_SEARCH_PATTERN, null) : null);
    }

    @Override
    public void onTabSelected(android.support.v7.app.ActionBar.Tab tab, android.support.v4.app.FragmentTransaction ft) {
        Log.d(LOG_TAG, "## onTabSelected() FragTag=" + tab.getTag());

        // clear any displayed windows
        resetUi();

        // attach / replace a fragment by tag
        String tabTag = (String)tab.getTag();
        Fragment fragment = null;
        boolean replace = false;

        // search a room by name
        if (TextUtils.equals(tabTag, TAG_FRAGMENT_SEARCH_IN_ROOM_NAMES)) {
            if (null == mSearchInRoomNamesFragment) {
                replace = true;
                mSearchInRoomNamesFragment = VectorSearchRoomsListFragment.newInstance(mSession.getMyUserId(), R.layout.fragment_vector_recents_list);
            }
            fragment = mSearchInRoomNamesFragment;
            mCurrentTabIndex = mSearchInRoomNamesTabIndex;

        }
        // search a message by its body
        else if (TextUtils.equals((String)tab.getTag(), TAG_FRAGMENT_SEARCH_IN_MESSAGE)) {
            if (null == mSearchInMessagesFragment) {
                replace = true;
                mSearchInMessagesFragment = VectorSearchMessagesListFragment.newInstance(mSession.getMyUserId(), null, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            }
            fragment = mSearchInMessagesFragment;
            mCurrentTabIndex = mSearchInMessagesTabIndex;
        }
        // search a file by name
        else if (TextUtils.equals((String)tab.getTag(), TAG_FRAGMENT_SEARCH_IN_FILES)) {
            if (null == mSearchInFilesFragment) {
                replace = true;
                mSearchInFilesFragment = VectorSearchRoomsFilesListFragment.newInstance(mSession.getMyUserId(), null, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            }
            fragment = mSearchInFilesFragment;
            mCurrentTabIndex = mSearchInFilesTabIndex;
        }
        // search an user by name
        else if (TextUtils.equals((String)tab.getTag(), TAG_FRAGMENT_SEARCH_PEOPLE)) {
            if (null == mSearchInPeopleFragment) {
                replace = true;
                mSearchInPeopleFragment = VectorSearchPeopleListFragment.newInstance(mSession.getMyUserId(), R.layout.fragment_vector_search_people_list);
            }
            fragment = mSearchInPeopleFragment;
            mCurrentTabIndex = mSearchInPeopleTabIndex;
        }

        if (replace) {
            ft.replace(R.id.search_fragment_container, fragment, tabTag);
        } else {
            ft.attach(fragment);
        }

        /*if (-1 != mCurrentTabIndex) {
            searchAccordingToTabHandler();
        }*/

        resetUi();
    }

    @Override
    public void onTabUnselected(android.support.v7.app.ActionBar.Tab tab, android.support.v4.app.FragmentTransaction ft) {
        String tabTag = (String) tab.getTag();
        Log.d(LOG_TAG, "## onTabUnselected() FragTag=" + tabTag);

        if (TextUtils.equals(tabTag, TAG_FRAGMENT_SEARCH_IN_MESSAGE)) {
            if (null != mSearchInMessagesFragment) {
                mSearchInMessagesFragment.cancelCatchingRequests();
                ft.detach(mSearchInMessagesFragment);
            }
        }
        else if (TextUtils.equals(tabTag, TAG_FRAGMENT_SEARCH_IN_ROOM_NAMES)) {
            if (null != mSearchInRoomNamesFragment) {
                ft.detach(mSearchInRoomNamesFragment);
            }
        }
        else if (TextUtils.equals(tabTag, TAG_FRAGMENT_SEARCH_IN_FILES)) {
            if (null != mSearchInFilesFragment) {
                mSearchInFilesFragment.cancelCatchingRequests();
                ft.detach(mSearchInFilesFragment);
            }
        }
        else if (TextUtils.equals(tabTag, TAG_FRAGMENT_SEARCH_PEOPLE)) {
            if (null != mSearchInPeopleFragment) {
                ft.detach(mSearchInPeopleFragment);
            }
        }
    }

    @Override
    public void onTabReselected(android.support.v7.app.ActionBar.Tab tab, FragmentTransaction ft) {
    }

    //==============================================================================================================
    // Life cycle Activity methods
    //==============================================================================================================

    @SuppressLint("LongLogTag")
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(LOG_TAG, "## onSaveInstanceState(): ");

        // save current tab
        int currentIndex = mActionBar.getSelectedNavigationIndex();
        outState.putInt(KEY_STATE_CURRENT_TAB_INDEX, currentIndex);

        String searchPattern = mPatternToSearchEditText.getText().toString();

        if (!TextUtils.isEmpty(searchPattern)) {
            outState.putString(KEY_STATE_SEARCH_PATTERN, searchPattern);
        }
    }

    //==============================================================================================================
    // VectorBaseSearchActivity.IVectorSearchActivity
    //==============================================================================================================

    public void refreshSearch() {
        searchAccordingToTabHandler();
    }
}


