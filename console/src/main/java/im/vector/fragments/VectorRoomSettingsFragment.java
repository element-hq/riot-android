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

package im.vector.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import im.vector.R;
import im.vector.activity.VectorRoomDetailsActivity;


public class VectorRoomSettingsFragment extends Fragment {
    private static final String LOG_TAG = "VectorRoomSettingsFragment";

    private MXSession mSession;
    private Room mRoom;

    // top view
    private View mViewHierarchy;

    public void setSession(MXSession session) {
        mSession = session;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onResume() {
        super.onResume();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mViewHierarchy = inflater.inflate(R.layout.fragment_vector_room_settings, container, false);

        Activity activity = getActivity();

        if (activity instanceof VectorRoomDetailsActivity) {
            VectorRoomDetailsActivity vectorRoomDetailsActivity = (VectorRoomDetailsActivity)activity;

            mRoom = vectorRoomDetailsActivity.getRoom();
            mSession = vectorRoomDetailsActivity.getSession();

            finalizeInit();
        }

        return mViewHierarchy;
    }

    /**
     * Finalize the fragment initialization.
     */
    private void finalizeInit() {
    }
}
