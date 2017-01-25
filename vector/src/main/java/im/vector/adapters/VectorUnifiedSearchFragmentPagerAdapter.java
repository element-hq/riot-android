/*
 * Copyright 2017 OpenMarket Ltd
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

package im.vector.adapters;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.text.TextUtils;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;

import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.fragments.VectorSearchMessagesListFragment;
import im.vector.fragments.VectorSearchPeopleListFragment;
import im.vector.fragments.VectorSearchRoomsFilesListFragment;
import im.vector.fragments.VectorSearchRoomsListFragment;

import java.util.ArrayList;

/**
 * Unified search pager adapter
 */
public class VectorUnifiedSearchFragmentPagerAdapter extends FragmentPagerAdapter {

    private final Context mContext;
    private final MXSession mSession;
    private final String mRoomId;

    private final Fragment[] mFragments;
    private final Long[] mFragmentIds;
    private final ArrayList<Integer> mTabTitles;

    /**
     * Constructor
     * @param fm the fragment manager
     * @param context the context
     * @param session the session
     * @param roomId the room id
     */
    public VectorUnifiedSearchFragmentPagerAdapter(FragmentManager fm, Context context, MXSession session, String roomId) {
        super(fm);
        mContext = context;
        mSession = session;
        mRoomId = roomId;

        mTabTitles = new ArrayList<>();

        if (TextUtils.isEmpty(roomId)) {
            mTabTitles.add(R.string.tab_title_search_rooms);
        }

        mTabTitles.add(R.string.tab_title_search_messages);

        if (TextUtils.isEmpty(roomId)) {
            mTabTitles.add(R.string.tab_title_search_people);
        }

        mTabTitles.add(R.string.tab_title_search_files);

        mFragments = new Fragment[mTabTitles.size()];
        mFragmentIds = new Long[mTabTitles.size()];
    }

    @Override
    public int getCount() {
        if (null != mTabTitles) {
            return mTabTitles.size();
        }

        return 0;
    }

    @Override
    public Fragment getItem(int position) {
        Fragment fragment = mFragments[position];

        if (null == fragment) {
            int titleId = mTabTitles.get(position);

            switch (titleId) {
                case R.string.tab_title_search_rooms: {
                    fragment = VectorSearchRoomsListFragment.newInstance(mSession.getMyUserId(), R.layout.fragment_vector_recents_list);
                    break;
                }
                case R.string.tab_title_search_messages: {
                    fragment = VectorSearchMessagesListFragment.newInstance(mSession.getMyUserId(), mRoomId, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
                    break;
                }
                case R.string.tab_title_search_people: {
                    fragment = VectorSearchPeopleListFragment.newInstance(mSession.getMyUserId(), R.layout.fragment_vector_search_people_list);
                    break;
                }
                case R.string.tab_title_search_files: {
                    fragment = VectorSearchRoomsFilesListFragment.newInstance(mSession.getMyUserId(), mRoomId, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
                    break;
                }
            }

            // should never fails
            if (null == fragment) {
                return null;
            }

            mFragments[position] = fragment;
        }

        return fragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (null != mTabTitles) {
            return mContext.getResources().getString(mTabTitles.get(position));
        }

        return "??";
    }

    @Override
    public long getItemId(int position) {
        // sanity checks
        if (null == mFragmentIds) {
            return position;
        }

        // fix the screen rotation issues
        // The fragments are not properly restored after a screen rotation.
        // Ensure that the fragments are recreated.
        Long id = mFragmentIds[position];

        if (null == id) {
            // the identifier must be unique.
            // we cannot use the fragment
            id = System.currentTimeMillis();
            mFragmentIds[position] = id;
        }

        return id;
    }

    /**
     * Cancel any pending search
     */
    public void cancelSearch(int position) {
        Fragment fragment = mFragments[position];

        if (null == fragment) {
            return;
        }

        int titleId = mTabTitles.get(position);

        if (titleId ==  R.string.tab_title_search_messages) {
            ((VectorSearchMessagesListFragment)fragment).cancelCatchingRequests();
        } else if (titleId ==  R.string.tab_title_search_files) {
            ((VectorSearchRoomsFilesListFragment)fragment).cancelCatchingRequests();
        }
    }

    /**
     * Triggers a search in the currently displayed fragments
     * @param position the fragment position
     * @param pattern the pattern to search
     * @param listener the search listener
     * @return true if a remote search is triggered
     */
    public boolean search(int position, String pattern,  MatrixMessageListFragment.OnSearchResultListener listener) {
        // sanity checks
        if (null == mFragments) {
            listener.onSearchSucceed(0);
            return false;
        }

        Fragment fragment = mFragments[position];

        // sanity checks
        if (null == fragment) {
            listener.onSearchSucceed(0);
            return false;
        }

        boolean res = false;
        int titleId = mTabTitles.get(position);

        switch (titleId) {
            case R.string.tab_title_search_rooms: {
                res = PublicRoomsManager.isRequestInProgress();
                ((VectorSearchRoomsListFragment)fragment).searchPattern(pattern, listener);
                break;
            }
            case R.string.tab_title_search_messages: {
                res = !TextUtils.isEmpty(pattern);
                ((VectorSearchMessagesListFragment)fragment).searchPattern(pattern, listener);
               break;
            }
            case R.string.tab_title_search_people: {
                res = ((VectorSearchPeopleListFragment)fragment).isReady();
                ((VectorSearchPeopleListFragment)fragment).searchPattern(pattern, listener);
                break;
            }
            case R.string.tab_title_search_files: {
                res = !TextUtils.isEmpty(pattern);
                ((VectorSearchRoomsFilesListFragment)fragment).searchPattern(pattern, listener);
                break;
            }
        }

        return res;
    }

    /**
     * Provide the permission request for a dedicated position
     * @param position the position
     * @return the required permission or 0 if none are required
     */
    public int getPermissionsRequest(int position) {
        if (null != mTabTitles) {
            int titleId = mTabTitles.get(position);

            if (titleId == R.string.tab_title_search_people) {
                return CommonActivityUtils.REQUEST_CODE_PERMISSION_MEMBERS_SEARCH;
            }
        }

        return 0;
    }

    /**
     * Tells if the current fragment at the provided position is the room search one.
     * @param position the position
     * @return true if it is the expected one.
     */
    public boolean isSearchInRoomNameFragment(int position) {
        return (null != mTabTitles) && (R.string.tab_title_search_rooms == mTabTitles.get(position));
    }

    /**
     * Tells if the current fragment at the provided position is the messages search one.
     * @param position the position
     * @return true if it is the expected one.
     */
    public boolean isSearchInMessagesFragment(int position) {
        return (null != mTabTitles) && (R.string.tab_title_search_messages == mTabTitles.get(position));
    }

    /**
     * Tells if the current fragment at the provided position is the files search one.
     * @param position the position
     * @return true if it is the expected one.
     */
    public boolean isSearchInFilesFragment(int position) {
        return (null != mTabTitles) && (R.string.tab_title_search_files == mTabTitles.get(position));
    }
}
