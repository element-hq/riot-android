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
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v4.app.Fragment;
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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.TabListener;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.fragments.VectorMessagesSearchResultsListFragment;
import im.vector.fragments.VectorRoomsSearchResultsListFragment;

/**
 * Displays a generic activity search method
 */
public class VectorUnifiedSearchActivity extends MXCActionBarActivity implements TabListener {
    private static final String LOG_TAG = "VectorUniSrchActivity";

    private static final int SPEECH_REQUEST_CODE = 1234;

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
    ActionBar mActionBar;

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
    private VectorMessagesSearchResultsListFragment mSearchInMessagesFragment;
    private VectorRoomsSearchResultsListFragment mSearchInRoomNamesFragment;
    private VectorMessagesSearchResultsListFragment mSearchInFilesFragment;
    private VectorMessagesSearchResultsListFragment mSearchInPeopleFragment;
    private MXSession mSession;

    // UI items
    private ImageView mBackgroundImageView;
    private TextView mNoResultsTxtView;
    private View mLoadOldestContentView;
    private View mWaitWhileSearchInProgressView;
    private EditText mPatternToSearchEditText;

    // Menu items
    MenuItem mMicroMenuItem;
    MenuItem mClearEditTextMenuItem;

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
    protected void onCreate(Bundle savedInstanceState) {
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
        mActionBar = getSupportActionBar();

        // customize the action bar with a custom view to contain the search input text
        View actionBarView = customizeActionBar();

        // add the search logic based on the text search input listener
        mPatternToSearchEditText = (EditText) actionBarView.findViewById(R.id.room_action_bar_edit_text);
        actionBarView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mPatternToSearchEditText.requestFocus();
                InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                im.showSoftInput(mPatternToSearchEditText, 0);
            }
        }, 100);

        mPatternToSearchEditText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(android.text.Editable s) {
                VectorUnifiedSearchActivity.this.refreshMenuEntries();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mPatternToSearchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    // start the search according to the current selected tab
                    searchAccordingToTabHandler();
                    return true;
                }
                return false;
            }
        });

        // tab creation and restore tabs UI context
        createNavigationTabs(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "## onDestroy(): ");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "## onResume(): ");

        searchAccordingToTabHandler();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.vector_searches, menu);

        mMicroMenuItem = menu.findItem(R.id.ic_action_speak_to_search);
        mClearEditTextMenuItem = menu.findItem(R.id.ic_action_clear_search);

        refreshMenuEntries();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_speak_to_search) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Voice recognition Demo...");
            startActivityForResult(intent, SPEECH_REQUEST_CODE);

        } else if (id ==  R.id.ic_action_clear_search) {
            mPatternToSearchEditText.setText("");
            searchAccordingToTabHandler();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Handle the results from the voice recognition activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if ((requestCode == SPEECH_REQUEST_CODE) && (resultCode == RESULT_OK)) {
            final ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            // one matched items
            if (matches.size() == 1) {
                // use it
                mPatternToSearchEditText.setText(matches.get(0));
                searchAccordingToTabHandler();
            } else if (matches.size() > 1) {
                // if they are several matches, let the user chooses the right one.
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                String[] mes = matches.toArray(new String[matches.size()]);

                builder.setItems(mes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        mPatternToSearchEditText.setText(matches.get(item));
                        VectorUnifiedSearchActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                searchAccordingToTabHandler();
                            }
                        });
                    }
                });

                AlertDialog alert = builder.create();
                alert.show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * @return true of the device supports speech recognizer.
     */
    private boolean supportSpeechRecognizer() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);

        return (null != activities) && (activities.size() > 0);
    }

    /**
     * Refresh the menu entries
     */
    private void refreshMenuEntries() {
        boolean hasText = !TextUtils.isEmpty(mPatternToSearchEditText.getText());

        if (null != mMicroMenuItem) {
            mMicroMenuItem.setVisible(!hasText && supportSpeechRecognizer());
        }

        if (null != mClearEditTextMenuItem) {
            mClearEditTextMenuItem.setVisible(hasText);
        }
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
     * Update the tag of the current tab with its UI values
     */
    private void saveCurrentUiTabContext() {
        if(-1 != mCurrentTabIndex) {
            ActionBar.Tab currentTab = mActionBar.getTabAt(mCurrentTabIndex);
            TabListenerHolder tabTag = (TabListenerHolder) currentTab.getTag();
            tabTag.mBackgroundVisibility = mBackgroundImageView.getVisibility();
            tabTag.mNoResultsTxtViewVisibility = mNoResultsTxtView.getVisibility();
            tabTag.mSearchedPattern = mPatternToSearchEditText.getText().toString();
            currentTab.setTag(tabTag);
        }
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
                mSearchInRoomNamesFragment = VectorRoomsSearchResultsListFragment.newInstance(mSession.getMyUser().userId, R.layout.fragment_vector_recents_list);
                ft.replace(R.id.search_fragment_container, mSearchInRoomNamesFragment, tabListenerHolder.mFragmentTag);
                Log.d(LOG_TAG, "## onTabSelected() SearchInRoomNames frag added");
            } else {
                ft.attach(mSearchInRoomNamesFragment);
                Log.d(LOG_TAG, "## onTabSelected() SearchInRoomNames frag attach");
            }
            mCurrentTabIndex = mSearchInRoomNamesTabIndex;

        } else if (tabListenerHolder.mFragmentTag.equals(TAG_FRAGMENT_SEARCH_IN_MESSAGE)) {
            if (null == mSearchInMessagesFragment) {
                mSearchInMessagesFragment = VectorMessagesSearchResultsListFragment.newInstance(mSession.getMyUser().userId, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
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


