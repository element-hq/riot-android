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

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import im.vector.R;

import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.db.MXMediasCache;

/**
 * An adapter which can display m.room.member content.
 */
public class AccountsAdapter extends ArrayAdapter<MXSession> {

    protected Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    private MXMediasCache mMediasCache = null;

    /**
     * Construct an adapter which will display a list of accounst
     * @param context Activity context
     * @param layoutResourceId The resource ID of the layout for each item.
     *                         the IDs: roomMembersAdapter_name, roomMembersAdap
     * @param mediasCache the media cache
     */
    public AccountsAdapter(Context context, int layoutResourceId, MXMediasCache mediasCache) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        mMediasCache = mediasCache;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        MXSession session = getItem(position);
        MyUser myUser = session.getMyUser();

        TextView displayNameTextView = (TextView)convertView.findViewById(R.id.accountAdapter_name);
        TextView matrixIdTextView  = (TextView)convertView.findViewById(R.id.accountAdapte_userId);

        if (TextUtils.isEmpty(myUser.displayname)) {
            displayNameTextView.setText(myUser.user_id);
            matrixIdTextView.setText("");
        } else {
            displayNameTextView.setText(myUser.displayname);
            matrixIdTextView.setText(myUser.user_id);
        }

        ImageView avatarView = (ImageView)convertView.findViewById(R.id.avatar_img);
        avatarView.setImageResource(R.drawable.ic_contact_picture_holo_light);

        if (!TextUtils.isEmpty(myUser.getAvatarUrl())) {
            int size = getContext().getResources().getDimensionPixelSize(R.dimen.member_list_avatar_size);
            mMediasCache.loadAvatarThumbnail(session.getHomeserverConfig(), avatarView, myUser.getAvatarUrl(), size);
        }

        return convertView;
    }
}
