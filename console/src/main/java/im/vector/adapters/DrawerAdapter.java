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
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import im.vector.R;

/**
 * An adapter which can display room information.
 */
public class DrawerAdapter extends ArrayAdapter<DrawerAdapter.Entry> {

    private static final int NUM_ROW_TYPES = 2;

    private static final int ROW_TYPE_HEADER = 0;
    private static final int ROW_TYPE_ENTRY = 1;

    public class Entry {
        public int mIconResourceId;
        public String mText;

        public Entry(int iconResourceId, String text) {
            mIconResourceId = iconResourceId;
            mText = text;
        }
    };

    private LayoutInflater mLayoutInflater;
    private int mHeaderLayoutResourceId;
    private int mEntryLayoutResourceId;

    private Context mContext;

    public int mTextColor = Color.BLACK;

    /**
     * Construct an adapter to display a drawer adapter
     * @param context the context
     * @param headerLayoutResourceId the header resource id
     * @param entryLayoutResourceId the entry layout id
     */
    public DrawerAdapter(Context context, int headerLayoutResourceId,  int entryLayoutResourceId) {
        super(context, headerLayoutResourceId, entryLayoutResourceId);
        mContext = context;
        mHeaderLayoutResourceId = headerLayoutResourceId;
        mEntryLayoutResourceId = entryLayoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);

        // create a dummy section to insert the header
        this.add(new Entry(0, null));
        setNotifyOnChange(true);
    }

    /**
     * Add a new entry in the adapter
     * @param iconResourceId the entry icon
     * @param text the entry text
     */
    public void add(int iconResourceId, String text) {
        this.add(new Entry(iconResourceId, text));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (0 == position) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(mHeaderLayoutResourceId, parent, false);
            }
            return convertView;
        } else {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(mEntryLayoutResourceId, parent, false);
            }

            Entry entry = getItem(position);

            TextView textView = (TextView) convertView.findViewById(R.id.adapter_drawer_text);
            textView.setText(entry.mText);
            textView.setTextColor(mTextColor);

            ImageView imageView = (ImageView) convertView.findViewById(R.id.adapter_drawer_thumbnail);
            imageView.setImageResource(entry.mIconResourceId);

            return convertView;
        }
    }

    @Override
    public int getViewTypeCount() {
        return NUM_ROW_TYPES;
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0) ? ROW_TYPE_HEADER : ROW_TYPE_ENTRY;
    }
}
