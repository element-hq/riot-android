/*
 * Copyright 2017 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
import android.view.ViewGroup;

import im.vector.R;
import im.vector.fragments.GroupDetailsBaseFragment;
import im.vector.fragments.GroupDetailsHomeFragment;
import im.vector.fragments.GroupDetailsPeopleFragment;
import im.vector.fragments.GroupDetailsRoomsFragment;

/**
 * Groups pager adapter
 */
public class GroupDetailsFragmentPagerAdapter extends FragmentPagerAdapter {
    private static final String LOG_TAG = GroupDetailsFragmentPagerAdapter.class.getSimpleName();

    private static final int HOME_FRAGMENT_INDEX = 0;
    private static final int PEOPLE_FRAGMENT_INDEX = 1;
    private static final int ROOMS_FRAGMENT_INDEX = 2;
    private static final int FRAGMENTS_COUNT = 3;

    private final Context mContext;

    private GroupDetailsHomeFragment mHomeFragment;
    private GroupDetailsPeopleFragment mPeopleFragment;
    private GroupDetailsRoomsFragment mRoomsFragment;

    public GroupDetailsFragmentPagerAdapter(FragmentManager fm, Context context) {
        super(fm);
        mContext = context;
    }

    @Override
    public int getCount() {
        return FRAGMENTS_COUNT;
    }

    @Override
    public Fragment getItem(int position) {
        Fragment fragment;

        switch (position) {
            case HOME_FRAGMENT_INDEX: {
                fragment = mHomeFragment;

                if (null == fragment) {
                    fragment = mHomeFragment = new GroupDetailsHomeFragment();
                }
                break;
            }
            case PEOPLE_FRAGMENT_INDEX: {
                fragment = mPeopleFragment;

                if (null == fragment) {
                    fragment = mPeopleFragment = new GroupDetailsPeopleFragment();
                }
                break;
            }
            case ROOMS_FRAGMENT_INDEX: {
                fragment = mRoomsFragment;

                if (null == fragment) {
                    fragment = mRoomsFragment = new GroupDetailsRoomsFragment();
                }
                break;
            }
            default:
                fragment = null;
        }

        return fragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case HOME_FRAGMENT_INDEX: {
                return mContext.getString(R.string.group_details_home);
            }
            case PEOPLE_FRAGMENT_INDEX: {
                return mContext.getString(R.string.group_details_people);
            }
            case ROOMS_FRAGMENT_INDEX: {
                return mContext.getString(R.string.group_details_rooms);
            }
        }

        return super.getPageTitle(position);
    }

    /**
     * @return the home fragment
     */
    public GroupDetailsBaseFragment getHomeFragment() {
        return mHomeFragment;
    }

    /**
     * @return the people fragment
     */
    public GroupDetailsBaseFragment getPeopleFragment() {
        return mPeopleFragment;
    }

    /**
     * @return the rooms fragment
     */
    public GroupDetailsBaseFragment getRoomsFragment() {
        return mRoomsFragment;
    }
}
