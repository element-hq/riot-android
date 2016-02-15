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
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.HomeserverConnectionConfig;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.ContentManager;
import im.vector.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * An adapter which can display receipts
 */
public class ReadReceiptsAdapter extends ArrayAdapter<ReceiptData> {

    protected Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;
    private Room mRoom;
    private MXMediasCache mMediasCache;
    private HomeserverConnectionConfig mHsConfig;


    public ReadReceiptsAdapter(Context context, HomeserverConnectionConfig hsConfig, int layoutResourceId, Room room, MXMediasCache mediasCache) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        mRoom = room;
        mMediasCache = mediasCache;
        mHsConfig = hsConfig;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

       TextView userNameTextView = (TextView) convertView.findViewById(R.id.accountAdapter_name);

        ReceiptData receipt = getItem(position);
        RoomMember member = mRoom.getMember(receipt.userId);

        if (null == member) {
            userNameTextView.setText(receipt.userId);
        } else {
            userNameTextView.setText(member.getName());
        }

        TextView tsTextView = (TextView) convertView.findViewById(R.id.read_receipt_ts);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        SpannableStringBuilder body = new SpannableStringBuilder(mContext.getString(im.vector.R.string.read_receipt) + " : " + dateFormat.format(new Date(receipt.originServerTs)));
        body.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, mContext.getString(im.vector.R.string.read_receipt).length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tsTextView.setText(body);


        ImageView imageView = (ImageView) convertView.findViewById(R.id.avatar_img);
        imageView.setTag(null);
        imageView.setImageResource(R.drawable.ic_contact_picture_holo_light);
        String url = member.avatarUrl;

        if (TextUtils.isEmpty(url)) {
            url = ContentManager.getIdenticonURL(member.getUserId());
        }

        if (!TextUtils.isEmpty(url)) {
            int size = getContext().getResources().getDimensionPixelSize(R.dimen.member_list_avatar_size);
            mMediasCache.loadAvatarThumbnail(mHsConfig, imageView, url, size);
        }

        // The presence ring
        ImageView presenceRing = (ImageView) convertView.findViewById(R.id.imageView_presenceRing);
        presenceRing.setColorFilter(mContext.getResources().getColor(android.R.color.transparent));

        return convertView;
    }
}
