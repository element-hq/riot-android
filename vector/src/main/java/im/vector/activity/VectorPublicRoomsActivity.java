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

import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.FragmentManager;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;

import im.vector.R;
import im.vector.fragments.VectorPublicRoomsListFragment;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Displays a list of public rooms
 */
public class VectorPublicRoomsActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = VectorPublicRoomsActivity.class.getSimpleName();

    public static final String EXTRA_SEARCHED_PATTERN = "VectorPublicRoomsActivity.EXTRA_SEARCHED_PATTERN";
    private static final String TAG_FRAGMENT_PUBLIC_ROOMS_LIST = "VectorPublicRoomsActivity.TAG_FRAGMENT_PUBLIC_ROOMS_LIST";


    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_vector_public_rooms;
    }

    @Override
    public int getTitleRes() {
        return R.string.directory_title;
    }

    @Override
    public void initUiAndData() {
        configureToolbar();

        if (CommonActivityUtils.shouldRestartApp(this)) {
            CommonActivityUtils.restartApp(this);
            Log.d(LOG_TAG, "onCreate : restart the application");
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        Intent intent = getIntent();

        MXSession session = getSession(intent);

        FragmentManager fm = getSupportFragmentManager();
        VectorPublicRoomsListFragment vectorPublicRoomsListFragment = (VectorPublicRoomsListFragment) fm.findFragmentByTag(TAG_FRAGMENT_PUBLIC_ROOMS_LIST);

        if (null == vectorPublicRoomsListFragment) {
            String pattern = null;

            if (intent.hasExtra(EXTRA_SEARCHED_PATTERN)) {
                pattern = intent.getStringExtra(EXTRA_SEARCHED_PATTERN);
            }

            vectorPublicRoomsListFragment = VectorPublicRoomsListFragment
                    .newInstance(session.getMyUserId(), R.layout.fragment_vector_public_rooms_list, pattern);
            fm.beginTransaction().add(R.id.layout_public__rooms_list, vectorPublicRoomsListFragment, TAG_FRAGMENT_PUBLIC_ROOMS_LIST).commit();
        }
    }
}


