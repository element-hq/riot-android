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
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import im.vector.R;
import im.vector.contacts.Contact;

import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * An adapter which can display m.room.member content.
 */
public class ContactsListAdapter extends ArrayAdapter<Contact> implements SectionIndexer {

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    // SectionIndexer
    private HashMap<String, Integer> mMapIndex;
    private String[] mSections;

    /**
     * Construct an adapter which will display a list of room members.
     * @param context Activity context
     * @param layoutResourceId The resource ID of the layout for each item. Must have TextViews with
     *                         the IDs: roomMembersAdapter_name, roomMembersAdapter_membership, and
     *                         an ImageView with the ID avatar_img.
     */
    public ContactsListAdapter(Context context, int layoutResourceId) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);

        // let the caller manages the refresh
        setNotifyOnChange(false);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        final Contact contact = getItem(position);

        // Member name
        TextView textView = (TextView) convertView.findViewById(R.id.contact_name);
        textView.setText(contact.getDisplayName());

        // matrix info
        ImageView matrixIcon = (ImageView) convertView.findViewById(R.id.imageView_matrix_user);
        TextView matrixIDTextView = (TextView) convertView.findViewById(R.id.contact_userId);

        if (contact.hasMatridIds(mContext)) {
            matrixIDTextView.setText(contact.getFirstMatrixId().mMatrixId);
            matrixIDTextView.setVisibility(View.VISIBLE);
            matrixIcon.setVisibility(View.VISIBLE);
        } else {
            matrixIDTextView.setVisibility(View.GONE);
            matrixIcon.setVisibility(View.GONE);
        }

        // member thumbnail
        ImageView imageView = (ImageView) convertView.findViewById(R.id.avatar_img);
        Bitmap bitmap = contact.getThumbnail(mContext);

        if (null == bitmap) {
            imageView.setImageResource(R.drawable.ic_contact_picture_holo_light);
        } else {
            imageView.setImageBitmap(bitmap);
        }

        return convertView;
    }

    public int getPositionForSection(int section) {
        return mMapIndex.get(mSections[section]);
    }

    public int getSectionForPosition(int position) {
        return 0;
    }

    public Object[] getSections() {
        return mSections;
    }

    @Override
    public void addAll(java.util.Collection<? extends Contact> collection) {
        super.addAll(collection);

        // compute the sections from the contacts list
        // assume that the contacts are displayed by display names
        mMapIndex = new LinkedHashMap<String, Integer>();

        int index = 0;

        // list the contacts by display name
        for (Contact contact : collection){
            if (!TextUtils.isEmpty(contact.getDisplayName())) {
                mMapIndex.put(contact.getDisplayName().substring(0, 1).toUpperCase(), index);
                index++;
            }
        }

        mSections = new String[mMapIndex.size()];
        mMapIndex.keySet().toArray(mSections);
    }

}
