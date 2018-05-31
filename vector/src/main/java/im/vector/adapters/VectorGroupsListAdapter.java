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

package im.vector.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.rest.model.group.GroupProfile;

import java.util.HashMap;
import java.util.Map;

import im.vector.R;
import im.vector.activity.VectorGroupDetailsActivity;
import im.vector.util.VectorUtils;

/**
 * An adapter which can display groups list
 */
public class VectorGroupsListAdapter extends ArrayAdapter<String> {

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final int mLayoutResourceId;
    private final MXSession mSession;

    private Map<String, Group> mGroupByGroupId = new HashMap<>();

    public VectorGroupsListAdapter(Context context, int layoutResourceId, MXSession session) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        mSession = session;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        final String groupId = getItem(position);
        Group group = mGroupByGroupId.get(groupId);

        if (null == group) {
            group = mSession.getGroupsManager().getGroup(groupId);

            if (null != group) {
                mGroupByGroupId.put(groupId, group);
            }
        }

        boolean needRefresh = (null == group);

        if (null == group) {
            group = new Group(groupId);
        }

        convertView.findViewById(R.id.group_members_count).setVisibility(View.GONE);

        final TextView groupName = convertView.findViewById(R.id.group_name);
        groupName.setTag(groupId);
        groupName.setText(group.getDisplayName());
        groupName.setTypeface(null, Typeface.NORMAL);

        final ImageView groupAvatar = convertView.findViewById(R.id.room_avatar);
        VectorUtils.loadGroupAvatar(mContext, mSession, groupAvatar, group);

        final TextView groupTopic = convertView.findViewById(R.id.group_topic);
        groupTopic.setText(group.getShortDescription());

        convertView.findViewById(R.id.group_more_action_click_area).setVisibility(View.INVISIBLE);
        convertView.findViewById(R.id.group_more_action_anchor).setVisibility(View.INVISIBLE);
        convertView.findViewById(R.id.group_more_action_ic).setVisibility(View.INVISIBLE);

        if (needRefresh) {
            mSession.getGroupsManager().getGroupProfile(groupId, new ApiCallback<GroupProfile>() {
                @Override
                public void onSuccess(GroupProfile groupProfile) {
                    if (TextUtils.equals((String) groupName.getTag(), groupId)) {
                        Group updatedGroup = mGroupByGroupId.get(groupId);

                        if (null == updatedGroup) {
                            updatedGroup = new Group(groupId);
                            updatedGroup.setGroupProfile(groupProfile);
                            mGroupByGroupId.put(groupId, updatedGroup);
                        }

                        groupName.setText(updatedGroup.getDisplayName());
                        VectorUtils.loadGroupAvatar(mContext, mSession, groupAvatar, updatedGroup);
                        groupTopic.setText(updatedGroup.getShortDescription());
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                }

                @Override
                public void onMatrixError(MatrixError e) {
                }

                @Override
                public void onUnexpectedError(Exception e) {
                }
            });
        }

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, VectorGroupDetailsActivity.class);
                intent.putExtra(VectorGroupDetailsActivity.EXTRA_GROUP_ID, groupId);
                intent.putExtra(VectorGroupDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                mContext.startActivity(intent);
            }
        });


        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("", groupId);
                clipboard.setPrimaryClip(clip);

                Toast.makeText(mContext, mContext.getResources().getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        return convertView;
    }
}
