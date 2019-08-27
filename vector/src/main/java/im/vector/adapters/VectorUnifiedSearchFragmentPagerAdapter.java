/*
 * Copyright 2017 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

package im.vector.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.ViewGroup;

import androidx.collection.SparseArrayCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;

import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.fragments.VectorSearchMessagesListFragment;
import im.vector.fragments.VectorSearchPeopleListFragment;
import im.vector.fragments.VectorSearchRoomsFilesListFragment;
import im.vector.fragments.VectorSearchRoomsListFragment;
import im.vector.util.PermissionsToolsKt;

/**
 * Unified search pager adapter
 */
public class VectorUnifiedSearchFragmentPagerAdapter extends FragmentPagerAdapter {

    private final Context mContext;
    private final MXSession mSession;
    private final String mRoomId;

    // position + (title res id , fragment)
    private final SparseArrayCompat<Pair<Integer, Fragment>> mFragmentsData;

    /**
     * Constructor
     *
     * @param fm      the fragment manager
     * @param context the context
     * @param session the session
     * @param roomId  the room id
     */
    public VectorUnifiedSearchFragmentPagerAdapter(FragmentManager fm, Context context, MXSession session, String roomId) {
        super(fm);
        mContext = context;
        mSession = session;
        mRoomId = roomId;

        mFragmentsData = new SparseArrayCompat<>();

        final boolean searchInRoom = !TextUtils.isEmpty(roomId);

        int pos = 0;
        if (!searchInRoom) {
            mFragmentsData.put(pos, new Pair<Integer, Fragment>(R.string.tab_title_search_rooms, null));
            pos++;
        }

        mFragmentsData.put(pos, new Pair<Integer, Fragment>(R.string.tab_title_search_messages, null));
        pos++;

        if (!searchInRoom) {
            mFragmentsData.put(pos, new Pair<Integer, Fragment>(R.string.tab_title_search_people, null));
            pos++;
        }

        mFragmentsData.put(pos, new Pair<Integer, Fragment>(R.string.tab_title_search_files, null));
    }

    @Override
    public int getCount() {
        return mFragmentsData.size();
    }

    @Override
    public Fragment getItem(int position) {
        Pair<Integer, Fragment> pair = mFragmentsData.get(position);
        int titleId = pair == null ? -1 : pair.first;
        Fragment fragment = pair == null ? null : pair.second;

        if (fragment == null) {
            switch (titleId) {
                case R.string.tab_title_search_rooms: {
                    fragment = VectorSearchRoomsListFragment.newInstance(mSession.getMyUserId(),
                            R.layout.fragment_vector_recents_list);
                    break;
                }
                case R.string.tab_title_search_messages: {
                    fragment = VectorSearchMessagesListFragment.newInstance(mSession.getMyUserId(),
                            mRoomId, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
                    break;
                }
                case R.string.tab_title_search_people: {
                    fragment = VectorSearchPeopleListFragment.newInstance(mSession.getMyUserId(),
                            R.layout.fragment_vector_search_people_list);
                    break;
                }
                case R.string.tab_title_search_files: {
                    fragment = VectorSearchRoomsFilesListFragment.newInstance(mSession.getMyUserId(),
                            mRoomId, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
                    break;
                }
            }

            // should never fails
            if (null == fragment) {
                return null;
            }
        }

        return fragment;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Fragment createdFragment = (Fragment) super.instantiateItem(container, position);
        Pair<Integer, Fragment> pair = mFragmentsData.get(position);
        if (pair != null) {
            mFragmentsData.put(position, new Pair<>(pair.first, createdFragment));
        }
        return createdFragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (null != mFragmentsData && mFragmentsData.get(position) != null) {
            return mContext.getString(mFragmentsData.get(position).first);
        }

        return super.getPageTitle(position);
    }

    /**
     * Cancel any pending search
     */
    public void cancelSearch(int position) {
        Pair<Integer, Fragment> pair = mFragmentsData.get(position);
        int titleId = pair == null ? -1 : pair.first;
        Fragment fragment = pair == null ? null : pair.second;
        if (null == fragment) {
            return;
        }

        if (titleId == R.string.tab_title_search_messages) {
            ((VectorSearchMessagesListFragment) fragment).cancelCatchingRequests();
        } else if (titleId == R.string.tab_title_search_files) {
            ((VectorSearchRoomsFilesListFragment) fragment).cancelCatchingRequests();
        }
    }

    /**
     * Triggers a search in the currently displayed fragments
     *
     * @param position the fragment position
     * @param pattern  the pattern to search
     * @param listener the search listener
     * @return true if a remote search is triggered
     */
    public boolean search(int position, String pattern, MatrixMessageListFragment.OnSearchResultListener listener) {
        // sanity checks
        if (null == mFragmentsData) {
            listener.onSearchSucceed(0);
            return false;
        }

        Pair<Integer, Fragment> pair = mFragmentsData.get(position);
        int titleId = pair == null ? -1 : pair.first;
        Fragment fragment = pair == null ? null : pair.second;

        // sanity checks
        if (null == fragment) {
            listener.onSearchSucceed(0);
            return false;
        }

        boolean res = false;

        switch (titleId) {
            case R.string.tab_title_search_rooms: {
                res = PublicRoomsManager.getInstance().isRequestInProgress();
                ((VectorSearchRoomsListFragment) fragment).searchPattern(pattern, listener);
                break;
            }
            case R.string.tab_title_search_messages: {
                res = !TextUtils.isEmpty(pattern);
                ((VectorSearchMessagesListFragment) fragment).searchPattern(pattern, listener);
                break;
            }
            case R.string.tab_title_search_people: {
                res = ((VectorSearchPeopleListFragment) fragment).isReady();
                ((VectorSearchPeopleListFragment) fragment).searchPattern(pattern, listener);
                break;
            }
            case R.string.tab_title_search_files: {
                res = !TextUtils.isEmpty(pattern);
                ((VectorSearchRoomsFilesListFragment) fragment).searchPattern(pattern, listener);
                break;
            }
        }

        return res;
    }

    /**
     * Provide the permission request for a dedicated position
     *
     * @param position the position
     * @return the required permission or 0 if none are required
     */
    public int getPermissionsRequest(int position) {
        if (null != mFragmentsData) {
            Pair<Integer, Fragment> pair = mFragmentsData.get(position);
            int titleId = pair == null ? -1 : pair.first;

            if (titleId == R.string.tab_title_search_people) {
                return PermissionsToolsKt.PERMISSIONS_FOR_MEMBERS_SEARCH;
            }
        }

        return 0;
    }

    /**
     * Tells if the current fragment at the provided position is the room search one.
     *
     * @param position the position
     * @return true if it is the expected one.
     */
    public boolean isSearchInRoomNameFragment(int position) {
        Pair<Integer, Fragment> pair = mFragmentsData != null ? mFragmentsData.get(position) : null;
        return pair != null && (R.string.tab_title_search_rooms == pair.first);
    }

    /**
     * Tells if the current fragment at the provided position is the messages search one.
     *
     * @param position the position
     * @return true if it is the expected one.
     */
    public boolean isSearchInMessagesFragment(int position) {
        Pair<Integer, Fragment> pair = mFragmentsData != null ? mFragmentsData.get(position) : null;
        return pair != null && (R.string.tab_title_search_messages == pair.first);
    }

    /**
     * Tells if the current fragment at the provided position is the files search one.
     *
     * @param position the position
     * @return true if it is the expected one.
     */
    public boolean isSearchInFilesFragment(int position) {
        Pair<Integer, Fragment> pair = mFragmentsData != null ? mFragmentsData.get(position) : null;
        return pair != null && (R.string.tab_title_search_files == pair.first);
    }

    /**
     * Tells if the current fragment at the provided position is the people search one.
     *
     * @param position the position
     * @return true if it is the expected one.
     */
    public boolean isSearchInPeoplesFragment(int position) {
        Pair<Integer, Fragment> pair = mFragmentsData != null ? mFragmentsData.get(position) : null;
        return pair != null && (R.string.tab_title_search_people == pair.first);
    }

}
