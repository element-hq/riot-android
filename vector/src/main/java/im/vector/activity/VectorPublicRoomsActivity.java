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

import android.content.Intent;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.PublicRoom;
import im.vector.R;
import im.vector.fragments.VectorPublicRoomsListFragment;

import java.util.ArrayList;

/**
 * Displays a list of public rooms
 */
public class VectorPublicRoomsActivity extends MXCActionBarActivity {

    private static final String TAG_FRAGMENT_PUBLIC_ROOMS_LIST = "VectorPublicRoomsActivity.TAG_FRAGMENT_PUBLIC_ROOMS_LIST";

    public static final String EXTRA_PUBLIC_ROOMS_LIST_ID = "VectorPublicRoomsActivity.EXTRA_PUBLIC_ROOMS_LIST_ID";

    private VectorPublicRoomsListFragment mVectorPublicRoomsListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_public_rooms);

        Intent intent = getIntent();

        MXSession session = getSession(intent);
        ArrayList<PublicRoom> publicrooms =(ArrayList<PublicRoom>)intent.getSerializableExtra(EXTRA_PUBLIC_ROOMS_LIST_ID) ;

        FragmentManager fm = getSupportFragmentManager();
        mVectorPublicRoomsListFragment = (VectorPublicRoomsListFragment) fm.findFragmentByTag(TAG_FRAGMENT_PUBLIC_ROOMS_LIST);

        if (null == mVectorPublicRoomsListFragment) {
            mVectorPublicRoomsListFragment = VectorPublicRoomsListFragment.newInstance(session.getMyUserId(), R.layout.fragment_vector_public_rooms_list, publicrooms);
            fm.beginTransaction().add(R.id.layout_public__rooms_list, mVectorPublicRoomsListFragment, TAG_FRAGMENT_PUBLIC_ROOMS_LIST).commit();
        }
    }
}


