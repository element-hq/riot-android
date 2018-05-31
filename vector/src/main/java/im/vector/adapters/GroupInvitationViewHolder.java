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
import android.view.View;
import android.widget.Button;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.group.Group;

import butterknife.BindView;
import im.vector.R;

public class GroupInvitationViewHolder extends GroupViewHolder {

    @BindView(R.id.group_invite_reject_button)
    Button vRejectButton;

    @BindView(R.id.group_invite_join_button)
    Button vJoinButton;

    GroupInvitationViewHolder(View itemView) {
        super(itemView);
    }

    @Override
    public void populateViews(final Context context, final MXSession session, final Group group, final AbsAdapter.GroupInvitationListener invitationListener, final boolean isInvitation,
                              final AbsAdapter.MoreGroupActionListener moreGroupActionListener) {
        super.populateViews(context, session, group, invitationListener, true, moreGroupActionListener);

        vJoinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != invitationListener) {
                    invitationListener.onJoinGroup(session, group.getGroupId());
                }
            }
        });

        vRejectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != invitationListener) {
                    invitationListener.onRejectInvitation(session, group.getGroupId());
                }
            }
        });
    }
}