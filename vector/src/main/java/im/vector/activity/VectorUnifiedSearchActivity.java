/*
 * Copyright 2016 OpenMarket Ltd
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.VectorUnifiedSearchFragmentPagerAdapter;
import im.vector.contacts.ContactsManager;
import im.vector.util.PermissionsToolsKt;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Displays a generic activity search method
 */
public class VectorUnifiedSearchActivity extends VectorBaseSearchActivity implements VectorBaseSearchActivity.IVectorSearchActivity {
    private static final String LOG_TAG = VectorUnifiedSearchActivity.class.getSimpleName();

    public static final String EXTRA_ROOM_ID = "VectorUnifiedSearchActivity.EXTRA_ROOM_ID";
    public static final String EXTRA_TAB_INDEX = "VectorUnifiedSearchActivity.EXTRA_TAB_INDEX";

    // activity life cycle management:
    // - Bundle keys
    private static final String KEY_STATE_CURRENT_TAB_INDEX = "CURRENT_SELECTED_TAB";
    private static final String KEY_STATE_SEARCH_PATTERN = "SEARCH_PATTERN";

    // item position when it is a search in no room
    public static final int SEARCH_ROOMS_TAB_POSITION = 0;
    public static final int SEARCH_MESSAGES_TAB_POSITION = 1;
    public static final int SEARCH_PEOPLE_TAB_POSITION = 2;
    public static final int SEARCH_FILES_TAB_POSITION = 3;

    // UI items
    private ImageView mBackgroundImageView;
    private TextView mNoResultsTxtView;
    private View mLoadOldestContentView;

    private String mRoomId;

    private VectorUnifiedSearchFragmentPagerAdapter mPagerAdapter;
    private ViewPager mViewPager;

    private int mPosition;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_vector_unified_search;
    }

    @Override
    public void initUiAndData() {
        super.initUiAndData();

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        // the session should be passed in parameter
        // but the current design does not describe how the multi accounts will be managed.
        MXSession session = Matrix.getInstance(this).getDefaultSession();
        if (session == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        // UI widgets binding & init fields
        mBackgroundImageView = findViewById(R.id.search_background_imageview);
        mNoResultsTxtView = findViewById(R.id.search_no_result_textview);
        setWaitingView(findViewById(R.id.search_in_progress_view));
        mLoadOldestContentView = findViewById(R.id.search_load_oldest_progress);

        if (null != getIntent()) {
            mRoomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        }

        mPagerAdapter = new VectorUnifiedSearchFragmentPagerAdapter(getSupportFragmentManager(), this, session, mRoomId);

        // Get the ViewPager and set it's PagerAdapter so that it can display items
        mViewPager = findViewById(R.id.search_view_pager);
        mViewPager.setAdapter(mPagerAdapter);

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                int permissions = mPagerAdapter.getPermissionsRequest(position);

                if (0 != permissions) {
                    // Check permission to access contacts
                    PermissionsToolsKt.checkPermissions(permissions, VectorUnifiedSearchActivity.this, PermissionsToolsKt.PERMISSION_REQUEST_CODE);
                }
                searchAccordingToSelectedTab();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        // Give the TabLayout the ViewPager
        TabLayout tabLayout = findViewById(R.id.search_filter_tabs);
        tabLayout.setupWithViewPager(mViewPager);

        // the tab i
        if ((null != getIntent()) && getIntent().hasExtra(EXTRA_TAB_INDEX)) {
            mPosition = getIntent().getIntExtra(EXTRA_TAB_INDEX, 0);
        } else {
            mPosition = isFirstCreation() ? 0 : getSavedInstanceState().getInt(KEY_STATE_CURRENT_TAB_INDEX, 0);
        }
        mViewPager.setCurrentItem(mPosition);

        // restore the searched pattern
        mPatternToSearchEditText.setText(isFirstCreation() ? null : getSavedInstanceState().getString(KEY_STATE_SEARCH_PATTERN, null));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * Trigger a new search to the selected fragment.
     */
    private void searchAccordingToSelectedTab() {
        final String pattern = mPatternToSearchEditText.getText().toString().trim();
        final int position = mViewPager.getCurrentItem();

        if (mPosition != position) {
            mPagerAdapter.cancelSearch(mPosition);
        }

        mPosition = position;

        // the background image view should only be displayed when there is no pattern,
        // the rooms searches has a result : the public rooms list.
        resetUi(TextUtils.isEmpty(pattern) && !mPagerAdapter.isSearchInRoomNameFragment(position)
                && !mPagerAdapter.isSearchInPeoplesFragment(position));

        boolean isRemoteSearching = mPagerAdapter.search(position, pattern, new MatrixMessageListFragment.OnSearchResultListener() {
            @Override
            public void onSearchSucceed(int nbrMessages) {
                onSearchEnd(position, nbrMessages);
            }

            @Override
            public void onSearchFailed() {
                onSearchEnd(position, 0);
            }
        });

        if (isRemoteSearching) {
            showWaitingView();
        }
    }

    @Override
    protected void onPatternUpdate(boolean isTypingUpdate) {
        final int position = mViewPager.getCurrentItem();

        // the messages searches are not done locally.
        // so, such searches can only be done if the user taps on the search button.
        if (isTypingUpdate && (mPagerAdapter.isSearchInMessagesFragment(position) || mPagerAdapter.isSearchInFilesFragment(position))) {
            return;
        }

        searchAccordingToSelectedTab();
    }

    /**
     * Reset the UI to its init state:
     * - "waiting while searching" screen disabled
     * - background image visible
     * - no results message disabled
     *
     * @param showBackgroundImage true to display it
     */
    private void resetUi(boolean showBackgroundImage) {
        // stop "wait while searching" screen
        hideWaitingView();

        // display the background
        if (null != mBackgroundImageView) {
            mBackgroundImageView.setVisibility(showBackgroundImage ? View.VISIBLE : View.GONE);
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
     *
     * @param tabIndex    the tab index
     * @param nbrMessages the number of found messages.
     */
    private void onSearchEnd(int tabIndex, int nbrMessages) {
        if (mViewPager.getCurrentItem() == tabIndex) {
            Log.d(LOG_TAG, "## onSearchEnd() nbrMsg=" + nbrMessages);
            // stop "wait while searching" screen
            hideWaitingView();

            // display the background view if there is no pending such
            mBackgroundImageView.setVisibility(!mPagerAdapter.isSearchInPeoplesFragment(tabIndex)
                    && (0 == nbrMessages) && TextUtils.isEmpty(mPatternToSearchEditText.getText().toString())
                    ? View.VISIBLE
                    : View.GONE);

            // display the "no result" text only if the researched text is not empty
            mNoResultsTxtView.setVisibility(((0 == nbrMessages)
                    && !TextUtils.isEmpty(mPatternToSearchEditText.getText().toString())) ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (0 == permissions.length) {
            Log.d(LOG_TAG, "## onRequestPermissionsResult(): cancelled " + requestCode);
        } else if (requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE) {
            if (PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                Log.d(LOG_TAG, "## onRequestPermissionsResult(): READ_CONTACTS permission granted");
                // trigger a contacts book refresh
                ContactsManager.getInstance().refreshLocalContactsSnapshot();

                searchAccordingToSelectedTab();
            } else {
                Log.d(LOG_TAG, "## onRequestPermissionsResult(): READ_CONTACTS permission not granted");
                Toast.makeText(this, R.string.missing_permissions_warning, Toast.LENGTH_SHORT).show();
            }
        }
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
        int currentIndex = mViewPager.getCurrentItem();
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
        searchAccordingToSelectedTab();
    }
}


