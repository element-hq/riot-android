/*
 * Copyright 2015 OpenMarket Ltd
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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.TabListener;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;

import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.fragments.VectorSearchRoomsListFragment;
import im.vector.fragments.VectorSearchMessagesListFragment;

/**
 * Displays a generic activity search method
 */
public class VectorUnifiedSearchActivity extends VectorBaseSearchActivity implements TabListener {
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
    private static final String KEY_STATE_IS_BACKGROUND_ROOMS_TAB = "IS_BACKGROUND_ROOMS_TAB";
    private static final String KEY_STATE_IS_BACKGROUND_MESSAGES_TAB = "IS_BACKGROUND_MESSAGES_TAB";
    private static final String KEY_STATE_IS_BACKGROUND_PEOPLE_TAB = "IS_BACKGROUND_PEOPLE_TAB";
    private static final String KEY_STATE_IS_BACKGROUND_FILES_TAB = "IS_BACKGROUND_FILES_TAB";
    private static final String KEY_STATE_SEARCH_PATTERN_MESSAGES_TAB = "SEARCH_PATTERN_MESSAGES";
    private static final String KEY_STATE_SEARCH_PATTERN_ROOMS_TAB = "SEARCH_PATTERN_ROOMS";
    private static final String KEY_STATE_SEARCH_PATTERN_PEOPLE_TAB = "SEARCH_PATTERN_PEOPLE";
    private static final String KEY_STATE_SEARCH_PATTERN_FILES_TAB = "SEARCH_PATTERN_FILES";

    // search fragments
    private VectorSearchMessagesListFragment mSearchInMessagesFragment;
    private VectorSearchRoomsListFragment mSearchInRoomNamesFragment;
    // TODO implement dedicated fragments
    //private VectorMessagesSearchResultsListFragment mSearchInFilesFragment;
    //private VectorMessagesSearchResultsListFragment mSearchInPeopleFragment;
    private MXSession mSession;

    // UI items
    private ImageView mBackgroundImageView;
    private TextView mNoResultsTxtView;
    private View mLoadOldestContentView;
    private View mWaitWhileSearchInProgressView;

    /**
     * Save the custom date for each tab
     */
    private static class TabListenerHolder {
        public final String mFragmentTag;
        public int mBackgroundVisibility;
        public int mNoResultsTxtViewVisibility;
        public String mSearchedPattern;

        public TabListenerHolder(String aTabTag, int aBackgroundVisibility, int aNoResultsVisibility, String aSearchPattern) {
            mFragmentTag = aTabTag;
            mNoResultsTxtViewVisibility = aNoResultsVisibility;
            mBackgroundVisibility = aBackgroundVisibility;
            mSearchedPattern = aSearchPattern;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_unified_search);

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

    /**
     * Called by the fragments when they are resumed.
     * It is used to refresh the search while playing with the tab.
     */
    public void onSearchFragmentResume() {
        searchAccordingToTabHandler();
    }

    /**
     * Update the tag of the tab with its the UI values
     * @param aTabToUpdate the tab to be updated
     */
    private void saveUiTabContext(ActionBar.Tab aTabToUpdate) {
        TabListenerHolder tabTag = (TabListenerHolder) aTabToUpdate.getTag();
        tabTag.mBackgroundVisibility = mBackgroundImageView.getVisibility();
        tabTag.mNoResultsTxtViewVisibility = mNoResultsTxtView.getVisibility();
        tabTag.mSearchedPattern = mPatternToSearchEditText.getText().toString();
        aTabToUpdate.setTag(tabTag);
    }

    /**
     * Restore the UI context associated with the tab
     * @param aTabToRestore the tab to be restored
     */
    private void restoreUiTabContext(ActionBar.Tab aTabToRestore) {
        TabListenerHolder tabTag = (TabListenerHolder) aTabToRestore.getTag();
        mBackgroundImageView.setVisibility(tabTag.mBackgroundVisibility);
        mNoResultsTxtView.setVisibility(tabTag.mNoResultsTxtViewVisibility);
        mPatternToSearchEditText.setText(tabTag.mSearchedPattern);
    }

    private void searchAccordingToTabHandler() {
        int currentIndex = mActionBar.getSelectedNavigationIndex();

        resetUi();

        String pattern = mPatternToSearchEditText.getText().toString();

        if((currentIndex == mSearchInRoomNamesTabIndex) && (null != mSearchInRoomNamesFragment)) {
            if (mSearchInRoomNamesFragment.isAdded()) {

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
        }
        else if((currentIndex == mSearchInMessagesTabIndex) && (null != mSearchInMessagesFragment)) {
            if (mSearchInMessagesFragment.isAdded())  {

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
            } else {
                mWaitWhileSearchInProgressView.setVisibility(View.GONE);
            }
        }
        else {
            onSearchEnd(currentIndex, 0);
            Toast.makeText(VectorUnifiedSearchActivity.this, NOT_IMPLEMENTED, Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Add a custom action bar with a view
     * @return the action bar inflated view
     */
    private View customizeActionBar() {
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_HOME_AS_UP);

        // add a custom action bar view containing an EditText to input the search text
        ActionBar.LayoutParams layout = new ActionBar.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT);
        View actionBarLayout =  getLayoutInflater().inflate(R.layout.vector_search_action_bar, null);
        mActionBar.setCustomView(actionBarLayout, layout);

        return actionBarLayout;
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
        int isBackroundDisplayed;
        String searchPattern;

        // Set the tabs navigation mode
        mActionBar.setNavigationMode(android.support.v7.app.ActionBar.NAVIGATION_MODE_TABS);

        // ROOMS names search tab creation
        android.support.v7.app.ActionBar.Tab tabToBeadded = mActionBar.newTab();
        String tabTitle = getResources().getString(R.string.tab_title_search_rooms);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        isBackroundDisplayed = (null != aSavedInstanceState)?aSavedInstanceState.getInt(KEY_STATE_IS_BACKGROUND_ROOMS_TAB, View.VISIBLE):View.VISIBLE;
        searchPattern = (null != aSavedInstanceState)?aSavedInstanceState.getString(KEY_STATE_SEARCH_PATTERN_ROOMS_TAB,""):"";
        tabToBeadded.setTag(new TabListenerHolder(TAG_FRAGMENT_SEARCH_IN_ROOM_NAMES, isBackroundDisplayed, View.GONE, searchPattern));
        mActionBar.addTab(tabToBeadded);
        mSearchInRoomNamesTabIndex = tabIndex++;

        // MESSAGES search tab creation
        tabToBeadded = mActionBar.newTab();
        tabTitle = getResources().getString(R.string.tab_title_search_messages);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        isBackroundDisplayed = (null != aSavedInstanceState)?aSavedInstanceState.getInt(KEY_STATE_IS_BACKGROUND_MESSAGES_TAB, View.VISIBLE):View.VISIBLE;
        searchPattern = (null != aSavedInstanceState)?aSavedInstanceState.getString(KEY_STATE_SEARCH_PATTERN_MESSAGES_TAB,""):"";
        tabToBeadded.setTag(new TabListenerHolder(TAG_FRAGMENT_SEARCH_IN_MESSAGE, isBackroundDisplayed, View.GONE, searchPattern));
        mActionBar.addTab(tabToBeadded);
        mSearchInMessagesTabIndex = tabIndex++;

        // PEOPLE search tab creation
        tabToBeadded = mActionBar.newTab();
        tabTitle = getResources().getString(R.string.tab_title_search_people);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        isBackroundDisplayed = (null != aSavedInstanceState)?aSavedInstanceState.getInt(KEY_STATE_IS_BACKGROUND_PEOPLE_TAB, View.VISIBLE):View.VISIBLE;
        searchPattern = (null != aSavedInstanceState)?aSavedInstanceState.getString(KEY_STATE_SEARCH_PATTERN_PEOPLE_TAB,""):"";
        tabToBeadded.setTag(new TabListenerHolder(TAG_FRAGMENT_SEARCH_PEOPLE, isBackroundDisplayed, View.GONE, searchPattern));
        mActionBar.addTab(tabToBeadded);
        mSearchInPeopleTabIndex = tabIndex++;

        // FILES search tab creation
        tabToBeadded = mActionBar.newTab();
        tabTitle = getResources().getString(R.string.tab_title_search_files);
        tabToBeadded.setText(tabTitle);
        tabToBeadded.setTabListener(this);
        isBackroundDisplayed = (null != aSavedInstanceState)?aSavedInstanceState.getInt(KEY_STATE_IS_BACKGROUND_FILES_TAB, View.VISIBLE):View.VISIBLE;
        searchPattern = (null != aSavedInstanceState)?aSavedInstanceState.getString(KEY_STATE_SEARCH_PATTERN_FILES_TAB,""):"";
        tabToBeadded.setTag(new TabListenerHolder(TAG_FRAGMENT_SEARCH_IN_FILES, isBackroundDisplayed, View.GONE, searchPattern));
        mActionBar.addTab(tabToBeadded);
        mSearchInFilesTabIndex = tabIndex++;

        // set the default tab to be displayed
        tabIndexToRestore = (null != aSavedInstanceState)?aSavedInstanceState.getInt(KEY_STATE_CURRENT_TAB_INDEX, 0) : 0;
        if(-1 == tabIndexToRestore) {
            // default value: display the search in rooms tab
            tabIndexToRestore = mSearchInRoomNamesTabIndex;
        }
        mCurrentTabIndex = tabIndexToRestore;

        // set the tab to display
        mActionBar.setSelectedNavigationItem(tabIndexToRestore);
    }

    @Override
    public void onTabSelected(android.support.v7.app.ActionBar.Tab tab, android.support.v4.app.FragmentTransaction ft) {
        TabListenerHolder tabListenerHolder = (TabListenerHolder) tab.getTag();
        Log.d(LOG_TAG, "## onTabSelected() FragTag=" + tabListenerHolder.mFragmentTag);

        // clear any displayed windows
        resetUi();

        // inter tab selection life cycle: restore tab UI
        restoreUiTabContext(tab);

        if (tabListenerHolder.mFragmentTag.equals(TAG_FRAGMENT_SEARCH_IN_ROOM_NAMES)) {
            if (null == mSearchInRoomNamesFragment) {
                mSearchInRoomNamesFragment = VectorSearchRoomsListFragment.newInstance(mSession.getMyUser().userId, R.layout.fragment_vector_recents_list);
                ft.replace(R.id.search_fragment_container, mSearchInRoomNamesFragment, tabListenerHolder.mFragmentTag);
                Log.d(LOG_TAG, "## onTabSelected() SearchInRoomNames frag added");
            } else {
                ft.attach(mSearchInRoomNamesFragment);
                Log.d(LOG_TAG, "## onTabSelected() SearchInRoomNames frag attach");
            }
            mCurrentTabIndex = mSearchInRoomNamesTabIndex;

        } else if (tabListenerHolder.mFragmentTag.equals(TAG_FRAGMENT_SEARCH_IN_MESSAGE)) {
            if (null == mSearchInMessagesFragment) {
                mSearchInMessagesFragment = VectorSearchMessagesListFragment.newInstance(mSession.getMyUser().userId, null, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
                ft.replace(R.id.search_fragment_container, mSearchInMessagesFragment, tabListenerHolder.mFragmentTag);
                Log.d(LOG_TAG, "## onTabSelected() SearchInMessages frag added");
            } else {
                ft.attach(mSearchInMessagesFragment);
                Log.d(LOG_TAG, "## onTabSelected() SearchInMessages frag added");
            }
            mCurrentTabIndex = mSearchInMessagesTabIndex;
        } else if (tabListenerHolder.mFragmentTag.equals(TAG_FRAGMENT_SEARCH_PEOPLE)) {
            mCurrentTabIndex = mSearchInPeopleTabIndex;
        } else if (tabListenerHolder.mFragmentTag.equals(TAG_FRAGMENT_SEARCH_IN_FILES)) {
            mCurrentTabIndex = mSearchInFilesTabIndex;
        }

        if (-1 != mCurrentTabIndex) {
            searchAccordingToTabHandler();
        }
    }

    @Override
    public void onTabUnselected(android.support.v7.app.ActionBar.Tab tab, android.support.v4.app.FragmentTransaction ft) {
        TabListenerHolder tabListenerHolder = (TabListenerHolder) tab.getTag();
        Log.d(LOG_TAG, "## onTabUnselected() FragTag=" + tabListenerHolder.mFragmentTag);

        // save tab UI context before leaving the tab...
        saveUiTabContext(tab);

        if(tabListenerHolder.mFragmentTag.equals(TAG_FRAGMENT_SEARCH_IN_MESSAGE)) {
            if(null != mSearchInMessagesFragment) {
                ft.detach(mSearchInMessagesFragment);
            }
        }
        else if(tabListenerHolder.mFragmentTag.equals(TAG_FRAGMENT_SEARCH_IN_ROOM_NAMES) ){
            if(null != mSearchInRoomNamesFragment) {
                ft.detach(mSearchInRoomNamesFragment);
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

        // save background visibility for each tab (could be optimized)
        int visibility = ((TabListenerHolder)mActionBar.getTabAt(mSearchInRoomNamesTabIndex).getTag()).mBackgroundVisibility;
        outState.putInt(KEY_STATE_IS_BACKGROUND_ROOMS_TAB, visibility);
        visibility = ((TabListenerHolder)mActionBar.getTabAt(mSearchInMessagesTabIndex).getTag()).mBackgroundVisibility;
        outState.putInt(KEY_STATE_IS_BACKGROUND_MESSAGES_TAB, visibility);
        visibility = ((TabListenerHolder)mActionBar.getTabAt(mSearchInPeopleTabIndex).getTag()).mBackgroundVisibility;
        outState.putInt(KEY_STATE_IS_BACKGROUND_PEOPLE_TAB, visibility);
        visibility = ((TabListenerHolder)mActionBar.getTabAt(mSearchInFilesTabIndex).getTag()).mBackgroundVisibility;
        outState.putInt(KEY_STATE_IS_BACKGROUND_FILES_TAB, visibility);

        // save search pattern for each tab (could be optimized)
        String searchedPattern = ((TabListenerHolder)mActionBar.getTabAt(mSearchInRoomNamesTabIndex).getTag()).mSearchedPattern;
        outState.putString(KEY_STATE_SEARCH_PATTERN_ROOMS_TAB, searchedPattern);
        searchedPattern = ((TabListenerHolder)mActionBar.getTabAt(mSearchInMessagesTabIndex).getTag()).mSearchedPattern;
        outState.putString(KEY_STATE_SEARCH_PATTERN_MESSAGES_TAB, searchedPattern);
        searchedPattern = ((TabListenerHolder)mActionBar.getTabAt(mSearchInPeopleTabIndex).getTag()).mSearchedPattern;
        outState.putString(KEY_STATE_SEARCH_PATTERN_PEOPLE_TAB, searchedPattern);
        searchedPattern = ((TabListenerHolder)mActionBar.getTabAt(mSearchInFilesTabIndex).getTag()).mSearchedPattern;
        outState.putString(KEY_STATE_SEARCH_PATTERN_FILES_TAB, searchedPattern);
    }
}


