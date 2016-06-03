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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;

import im.vector.R;
import im.vector.fragments.VectorSearchMessagesListFragment;

/**
 * Displays a room messages search
 */
public class VectorRoomMessagesSearchActivity extends VectorBaseSearchActivity {
    private static final String LOG_TAG = "VectorRoomMsgSearchAct";

    public static final String EXTRA_ROOM_ID = "VectorRoomMessagesSearchActivity.EXTRA_ROOM_ID";

    private static final String TAG_FRAGMENT_SEARCH_IN_MESSAGE = "im.vector.activity.TAG_FRAGMENT_SEARCH_IN_MESSAGE";

    // search fragment
    private VectorSearchMessagesListFragment mSearchInMessagesFragment;

    private MXSession mSession;

    // UI items
    private ImageView mBackgroundImageView;
    private TextView mNoResultsTxtView;
    private View mLoadOldestContentView;
    private View mWaitWhileSearchInProgressView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_room_messages_search);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        Intent intent = getIntent();

        mSession = getSession(intent);
        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        // UI widgets binding & init fields
        mBackgroundImageView = (ImageView) findViewById(R.id.search_background_imageview);
        mNoResultsTxtView = (TextView) findViewById(R.id.search_no_result_textview);
        mWaitWhileSearchInProgressView = findViewById(R.id.search_in_progress_view);
        mLoadOldestContentView = findViewById(R.id.search_load_oldest_progress);

        FragmentManager fm = getSupportFragmentManager();
        mSearchInMessagesFragment = (VectorSearchMessagesListFragment) fm.findFragmentByTag(TAG_FRAGMENT_SEARCH_IN_MESSAGE);

        if (mSearchInMessagesFragment == null) {
            mSearchInMessagesFragment = VectorSearchMessagesListFragment.newInstance(mSession.getMyUserId(), intent.getStringExtra(EXTRA_ROOM_ID), org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            fm.beginTransaction().add(R.id.search_fragment_container, mSearchInMessagesFragment, TAG_FRAGMENT_SEARCH_IN_MESSAGE).commit();
        }
        resetUi();
    }


    // inherited from VectorBaseSearchActivity
    protected void onPatternUpdate() {
        onSearchFragmentResume();
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
     * Called by the fragments when they are resumed.
     * It is used to refresh the search while playing with the tab.
     */
    public void onSearchFragmentResume() {
        resetUi();

        String pattern = mPatternToSearchEditText.getText().toString();
        if (mSearchInMessagesFragment.isAdded())  {
            // display the "wait while searching" screen (progress bar)
            mWaitWhileSearchInProgressView.setVisibility(View.VISIBLE);

            mSearchInMessagesFragment.searchPattern(pattern, new MatrixMessageListFragment.OnSearchResultListener() {
                @Override
                public void onSearchSucceed(int nbrMessages) {
                    onSearchEnd(nbrMessages);
                }

                @Override
                public void onSearchFailed() {
                    onSearchEnd(0);
                }
            });
        } else {
            mWaitWhileSearchInProgressView.setVisibility(View.GONE);
        }
    }

    /**
     * The search is done.
     * @param nbrMessages the number of found messages.
     */
    private void onSearchEnd(int nbrMessages) {
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


