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

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.VectorGroupsListAdapter;

/**
 * A dialog fragment showing the group ids list
 */
public class VectorUserGroupsDialogFragment extends DialogFragment {
    private static final String LOG_TAG = VectorUserGroupsDialogFragment.class.getSimpleName();

    private static final String ARG_SESSION_ID = "ARG_SESSION_ID";
    private static final String ARG_USER_ID = "ARG_USER_ID";
    private static final String ARG_GROUPS_ID = "ARG_GROUPS_ID";

    public static VectorUserGroupsDialogFragment newInstance(String sessionId, String userId, List<String> groupIds) {
        VectorUserGroupsDialogFragment f = new VectorUserGroupsDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        args.putString(ARG_USER_ID, userId);
        args.putStringArrayList(ARG_GROUPS_ID, new ArrayList<>(groupIds));
        f.setArguments(args);
        return f;
    }

    private MXSession mSession;
    private String mUserId;
    private ArrayList<String> mGroupIds;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSession = Matrix.getInstance(getContext()).getSession(getArguments().getString(ARG_SESSION_ID));
        mUserId = getArguments().getString(ARG_USER_ID);
        mGroupIds = getArguments().getStringArrayList(ARG_GROUPS_ID);

        // sanity check
        if ((mSession == null) || TextUtils.isEmpty(mUserId)) {
            Log.e(LOG_TAG, "## onCreate() : invalid parameters");
            dismiss();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_dialog_groups_list, container, false);
        ListView listView = v.findViewById(R.id.listView_groups);

        final VectorGroupsListAdapter adapter = new VectorGroupsListAdapter(getActivity(), R.layout.adapter_item_group_view, mSession);
        adapter.addAll(mGroupIds);
        listView.setAdapter(adapter);

        return v;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        d.setTitle(getString(R.string.groups_list));
        return d;
    }
}
