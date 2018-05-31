/*
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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.util.Log;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.util.VectorUtils;

public class GroupViewHolder extends RecyclerView.ViewHolder {
    private static final String LOG_TAG = GroupViewHolder.class.getSimpleName();

    @BindView(R.id.room_avatar)
    ImageView vGroupAvatar;

    @BindView(R.id.group_name)
    TextView vGroupName;

    @BindView(R.id.group_topic)
    @Nullable
    TextView vGroupTopic;

    @BindView(R.id.group_members_count)
    TextView vGroupMembersCount;

    @BindView(R.id.group_more_action_click_area)
    @Nullable
    View vGroupMoreActionClickArea;

    @BindView(R.id.group_more_action_anchor)
    @Nullable
    View vGroupMoreActionAnchor;

    public GroupViewHolder(final View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
    }

    /**
     * Refresh the holder layout
     *
     * @param context                 the context
     * @param group                   the group
     * @param isInvitation            true if it is an invitation
     * @param moreGroupActionListener the more actions listener
     */
    public void populateViews(final Context context, final MXSession session, final Group group, final AbsAdapter.GroupInvitationListener invitationListener, final boolean isInvitation,
                              final AbsAdapter.MoreGroupActionListener moreGroupActionListener) {
        // sanity check
        if (null == group) {
            Log.e(LOG_TAG, "## populateViews() : null group");
            return;
        }

        if (isInvitation) {
            vGroupMembersCount.setText("!");
            vGroupMembersCount.setTypeface(null, Typeface.BOLD);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(100);
            shape.setColor(ContextCompat.getColor(context, R.color.vector_fuchsia_color));
            vGroupMembersCount.setBackground(shape);
            vGroupMembersCount.setVisibility(View.VISIBLE);
        } else {
            vGroupMembersCount.setVisibility(View.GONE);
        }

        vGroupName.setText(group.getDisplayName());
        vGroupName.setTypeface(null, Typeface.NORMAL);

        VectorUtils.loadGroupAvatar(context, session, vGroupAvatar, group);

        vGroupTopic.setText(group.getShortDescription());

        if (vGroupMoreActionClickArea != null && vGroupMoreActionAnchor != null) {
            vGroupMoreActionClickArea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != moreGroupActionListener) {
                        moreGroupActionListener.onMoreActionClick(vGroupMoreActionAnchor, group);
                    }
                }
            });
        }
    }
}
