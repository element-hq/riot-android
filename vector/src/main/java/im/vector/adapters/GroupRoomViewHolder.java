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
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.group.GroupRoom;
import org.matrix.androidsdk.util.Log;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.util.VectorUtils;

public class GroupRoomViewHolder extends RecyclerView.ViewHolder {
    private static final String LOG_TAG = GroupRoomViewHolder.class.getSimpleName();

    @BindView(R.id.contact_avatar)
    ImageView vContactAvatar;

    @BindView(R.id.contact_name)
    TextView vContactName;

    @Nullable
    @BindView(R.id.contact_desc)
    TextView vContactDesc;

    public GroupRoomViewHolder(final View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
    }

    /**
     * Refresh the holder layout
     *
     * @param context   the context
     * @param session   the session
     * @param groupRoom the group room
     */
    public void populateViews(final Context context, final MXSession session, final GroupRoom groupRoom) {
        // sanity check
        if (null == groupRoom) {
            Log.e(LOG_TAG, "## populateViews() : null groupRoom");
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

        vContactName.setText(groupRoom.getDisplayName());
        VectorUtils.loadUserAvatar(context, session, vContactAvatar, groupRoom.avatar_url, groupRoom.roomId, groupRoom.getDisplayName());

        if (null != vContactDesc) {
            vContactDesc.setText(groupRoom.topic);
        }
    }
}
