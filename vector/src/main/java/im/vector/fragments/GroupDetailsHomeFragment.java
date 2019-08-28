/*
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

package im.vector.fragments;

import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.matrix.androidsdk.rest.model.group.Group;

import butterknife.BindView;
import im.vector.R;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.VectorImageGetter;
import im.vector.util.VectorUtils;

public class GroupDetailsHomeFragment extends GroupDetailsBaseFragment {
    private static final String LOG_TAG = GroupDetailsHomeFragment.class.getSimpleName();

    @BindView(R.id.group_avatar)
    ImageView mGroupAvatar;

    @BindView(R.id.group_name_text_view)
    TextView mGroupNameTextView;

    @BindView(R.id.group_topic_text_view)
    TextView mGroupTopicTextView;

    @BindView(R.id.group_members_icon_view)
    ImageView mGroupMembersIconView;

    @BindView(R.id.group_members_text_view)
    TextView mGroupMembersTextView;

    @BindView(R.id.group_rooms_icon_view)
    ImageView mGroupRoomsIconView;

    @BindView(R.id.group_rooms_text_view)
    TextView mGroupRoomsTextView;

    @BindView(R.id.html_text_view)
    TextView mGroupHtmlTextView;

    @BindView(R.id.no_html_text_view)
    TextView noLongDescriptionTextView;

    private VectorImageGetter mImageGetter;

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (null == mImageGetter) {
            mImageGetter = new VectorImageGetter(mSession);
        }
    }

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_group_details_home;
    }

    @Override
    public void onPause() {
        super.onPause();
        mImageGetter.setListener(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshViews();

        mImageGetter.setListener(new VectorImageGetter.OnImageDownloadListener() {
            @Override
            public void onImageDownloaded(String source) {
                // invalidate the text
                refreshLongDescription();
            }
        });
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */
    @Override
    protected void initViews() {
        mGroupMembersIconView.setImageDrawable(ThemeUtils.INSTANCE.tintDrawableWithColor(ContextCompat.getDrawable(mActivity, R.drawable.riot_tab_groups),
                mGroupMembersTextView.getCurrentTextColor()));
        mGroupRoomsIconView.setImageDrawable(ThemeUtils.INSTANCE.tintDrawableWithColor(ContextCompat.getDrawable(mActivity, R.drawable.riot_tab_rooms),
                mGroupMembersTextView.getCurrentTextColor()));
    }

    /*
     * *********************************************************************************************
     * Data management
     * *********************************************************************************************
     */

    @Override
    public void refreshViews() {
        if (!isAdded()) {
            return;
        }

        Group group = mActivity.getGroup();

        VectorUtils.loadGroupAvatar(mActivity, mSession, mGroupAvatar, group);

        mGroupNameTextView.setText(group.getDisplayName());

        mGroupTopicTextView.setText(group.getShortDescription());
        mGroupTopicTextView.setVisibility(TextUtils.isEmpty(mGroupTopicTextView.getText()) ? View.GONE : View.VISIBLE);

        int roomCount = (null != group.getGroupRooms()) ? group.getGroupRooms().getEstimatedRoomCount() : 0;
        int memberCount = (null != group.getGroupUsers()) ? group.getGroupUsers().getEstimatedUsersCount() : 1;

        mGroupRoomsTextView.setText(getResources().getQuantityString(R.plurals.group_rooms, roomCount, roomCount));
        mGroupMembersTextView.setText(getResources().getQuantityString(R.plurals.group_members, memberCount, memberCount));

        if (!TextUtils.isEmpty(group.getLongDescription())) {
            mGroupHtmlTextView.setVisibility(View.VISIBLE);
            refreshLongDescription();
            noLongDescriptionTextView.setVisibility(View.GONE);
        } else {
            noLongDescriptionTextView.setVisibility(View.VISIBLE);
            mGroupHtmlTextView.setVisibility(View.GONE);
        }
    }

    /**
     * Update the long description text
     */
    private void refreshLongDescription() {
        if (null != mGroupHtmlTextView) {
            Group group = mActivity.getGroup();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mGroupHtmlTextView.setText(Html.fromHtml(group.getLongDescription(), Html.FROM_HTML_MODE_LEGACY, mImageGetter, null));
            } else {
                mGroupHtmlTextView.setText(Html.fromHtml(group.getLongDescription(), mImageGetter, null));
            }
        }
    }
}
