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

package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.matrixandroidsdk.R;

import java.util.ArrayList;

/**
 * An adapter which can display m.room.member content.
 */
public class MembersInvitationAdapter extends ArrayAdapter<RoomMember> {

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    private ArrayList<Integer> mSectionIndexes;
    private ArrayList<String> mSectionTitles;
    private ArrayList<RoomMember> mSelectedMembers;

    private MXMediasCache mMediasCache;

    /**
     * Construct an adapter which will display a list of room members.
     * @param context Activity context
     * @param layoutResourceId The resource ID of the layout for each item. Must have TextViews with
     *                         the IDs: roomMembersAdapter_name, roomMembersAdapter_membership, and
     *                         an ImageView with the ID avatar_img.
     */
    public MembersInvitationAdapter(Context context, int layoutResourceId, MXMediasCache mediasCache) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);

        mSelectedMembers = new ArrayList<RoomMember>();
        mMediasCache = mediasCache;

        // left the caller manages the refresh
        setNotifyOnChange(false);
    }

    /**
     * Defines the section titles.
     * @param sectionIndexes The section start indexes
     * @param sectionTitles The section titles.
     */
    public void setSectionTiles(ArrayList<Integer> sectionIndexes, ArrayList<String> sectionTitles) {
        mSectionIndexes = sectionIndexes;
        mSectionTitles = sectionTitles;
    }

    /**
     * Return the selected members.
     * @return the selected members.
     */
    public ArrayList<RoomMember> getSelectedMembers() {
        return mSelectedMembers;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        final RoomMember member = getItem(position);

        // Member name
        TextView textView = (TextView) convertView.findViewById(R.id.roomMembersAdapter_name);
        textView.setText((member.displayname == null) ? member.getUserId() : member.displayname);

        // member ID
        textView = (TextView) convertView.findViewById(R.id.roomMembersAdapter_userId);
        textView.setText(member.getUserId());

        // member thumbnail
        ImageView imageView = (ImageView) convertView.findViewById(R.id.avatar_img);
        imageView.setTag(null);
        imageView.setImageResource(R.drawable.ic_contact_picture_holo_light);
        String url = member.avatarUrl;

        if (TextUtils.isEmpty(url)) {
            url = ContentManager.getIdenticonURL(member.getUserId());
        }

        if (!TextUtils.isEmpty(url)) {
            int size = getContext().getResources().getDimensionPixelSize(R.dimen.member_list_avatar_size);
            mMediasCache.loadAvatarThumbnail(imageView, url, size);
        }

        // The presence ring
        ImageView presenceRing = (ImageView) convertView.findViewById(R.id.imageView_presenceRing);
        presenceRing.setVisibility(View.GONE);

        // divider
        textView = (TextView) convertView.findViewById(R.id.members_invitation_section_name);

        if (mSectionIndexes.indexOf(position) >= 0) {
            textView.setVisibility(View.VISIBLE);
            textView.setText(mSectionTitles.get(mSectionIndexes.indexOf(position)));
        } else {
            textView.setVisibility(View.GONE);
        }

        final CheckBox chkBox = (CheckBox) convertView.findViewById(R.id.checkbox_selected_member);
        chkBox.setChecked(mSelectedMembers.indexOf(member) >= 0);

        chkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (chkBox.isChecked()) {
                    mSelectedMembers.add(member);
                } else {
                    mSelectedMembers.remove(member);
                }
            }
        });

        return convertView;
    }
}
