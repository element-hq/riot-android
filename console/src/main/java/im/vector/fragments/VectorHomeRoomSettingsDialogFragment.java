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

package im.vector.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.support.v4.app.DialogFragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.Arrays;
import java.util.List;

import im.vector.R;

/**
 * A dialog fragment showing a list of icon + text entry
 */
public class VectorHomeRoomSettingsDialogFragment extends DialogFragment {
    private static final String LOG_TAG = "RoomSettingsDialogFragment";

    // params
    public static final String ARG_NOTIFS_ENABLE = "im.vector.fragments.VectorHomeRoomSettingsDialogFragment.ARG_NOTIFS_ENABLE";
    public static final String ARG_FAV_ENABLE = "im.vector.fragments.VectorHomeRoomSettingsDialogFragment.ARG_FAV_ENABLE";
    public static final String ARG_DEPRIOR_ENABLE = "im.vector.fragments.VectorHomeRoomSettingsDialogFragment.ARG_DEPRIOR_ENABLE";
    public static final String ARG_ROOM_NAME = "im.vector.fragments.VectorHomeRoomSettingsDialogFragment.ARG_ROOM_NAME";

    // provide the selection index
    public static final int NOTIF_POSITION = 0;
    public static final int FAV_POSITION  = 1;
    public static final int DEPRIOR_POSITION  = 2;
    public static final int LEAVE_POSITION  = 3;

    // members
    private String mTitle = null;
    private boolean mNotifEnabled = false;
    private boolean mFavEnabled = false;
    private boolean mLowPriorEnabled = false;

    /**
     * Interface definition for a callback to be invoked when an item in this
     */
    public interface OnItemClickListener {
        /**
         * Callback method to be invoked when an item is clicked.
         * @param dialogFragment the dialog.
         * @param position The clicked position (must be XX_POSITION)
         */
        public void onItemClick(final VectorHomeRoomSettingsDialogFragment dialogFragment, int position);
    }

    private OnItemClickListener mOnItemClickListener;

    public static VectorHomeRoomSettingsDialogFragment newInstance(String title, Boolean isNotifEnabled, Boolean isFavEnabled , Boolean isDepriorEnabled)  {
        VectorHomeRoomSettingsDialogFragment f = new VectorHomeRoomSettingsDialogFragment();
        Bundle args = new Bundle();

        if (null != title) {
            args.putString(ARG_ROOM_NAME, title);
        }

        args.putBoolean(ARG_NOTIFS_ENABLE, isNotifEnabled);
        args.putBoolean(ARG_FAV_ENABLE, isFavEnabled);
        args.putBoolean(ARG_DEPRIOR_ENABLE, isDepriorEnabled);

        f.setArguments(args);
        return f;
    }

    /**
     * Register a callback to be invoked when this view is clicked.
     *
     */
    public void setOnClickListener(OnItemClickListener l) {
        mOnItemClickListener = l;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNotifEnabled = getArguments().getBoolean(ARG_NOTIFS_ENABLE);
        mFavEnabled = getArguments().getBoolean(ARG_FAV_ENABLE);
        mLowPriorEnabled = getArguments().getBoolean(ARG_DEPRIOR_ENABLE);

        if (getArguments().containsKey(ARG_ROOM_NAME)) {
            mTitle = getArguments().getString(ARG_ROOM_NAME);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (!TextUtils.isEmpty(mTitle)) {
            getDialog().setTitle(mTitle);
        }

        View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_dialog_vector_home_room_settings, null);

        List<Integer> resourcesIds = Arrays.asList(R.id.notifications_tick, R.id.fav_tick, R.id.low_prior_tick, R.id.leave_room_cross);
        List<Boolean> showStatuses = Arrays.asList(mNotifEnabled, mFavEnabled, mLowPriorEnabled, true);

        for(int index = 0; index < resourcesIds.size(); index++) {
            View item = view.findViewById(resourcesIds.get(index));
            item.setVisibility(showStatuses.get(index) ? View.VISIBLE : View.INVISIBLE);

            final int fPos = index;

            ((View)item.getParent()).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mOnItemClickListener) {
                        mOnItemClickListener.onItemClick(VectorHomeRoomSettingsDialogFragment.this, fPos);
                    }
                }
            });
        }

        return view;
    }


}
