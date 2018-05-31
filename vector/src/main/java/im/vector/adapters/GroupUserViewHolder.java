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
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.group.GroupUser;
import org.matrix.androidsdk.util.Log;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.util.VectorUtils;

public class GroupUserViewHolder extends RecyclerView.ViewHolder {
    private static final String LOG_TAG = GroupUserViewHolder.class.getSimpleName();

    @BindView(R.id.contact_avatar)
    ImageView vContactAvatar;

    @BindView(R.id.contact_name)
    TextView vContactName;

    public GroupUserViewHolder(final View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
    }

    /**
     * Refresh the holder layout
     *
     * @param context   the context
     * @param session   the session
     * @param groupUser the user
     */
    public void populateViews(final Context context, final MXSession session, final GroupUser groupUser) {
        // sanity check
        if (null == groupUser) {
            Log.e(LOG_TAG, "## populateViews() : null groupUser");
            return;
        }

        if (null == session) {
            Log.e(LOG_TAG, "## populateViews() : null session");
            return;
        }

        if (null == session.getDataHandler()) {
            Log.e(LOG_TAG, "## populateViews() : null dataHandler");
            return;
        }

        vContactName.setText(groupUser.getDisplayname());
        VectorUtils.loadUserAvatar(context, session, vContactAvatar, groupUser.avatarUrl, groupUser.userId, groupUser.getDisplayname());
    }
}
