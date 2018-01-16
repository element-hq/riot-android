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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import im.vector.R;

/**
 * An adapter which can display string
 */
public class ImageSizesAdapter extends ArrayAdapter<ImageCompressionDescription> {
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final int mLayoutResourceId;

    /**
     * Construct an adapter which will display a list of image size
     *
     * @param context          Activity context
     * @param layoutResourceId The resource ID of the layout for each item.
     */
    public ImageSizesAdapter(Context context, int layoutResourceId) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        ImageCompressionDescription imageSizesDescription = getItem(position);

        TextView textView = convertView.findViewById(R.id.ImageSizesAdapter_format);
        textView.setText(imageSizesDescription.mCompressionText);

        textView = convertView.findViewById(R.id.ImageSizesAdapter_info);
        textView.setText(imageSizesDescription.mCompressionInfoText);
        return convertView;
    }
}
