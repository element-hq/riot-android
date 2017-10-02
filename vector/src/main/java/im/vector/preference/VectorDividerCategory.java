/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.preference;

import android.content.Context;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import im.vector.R;

/**
 * Customize PreferenceCategory class to redefine some attributes.
 *
 */
public class VectorDividerCategory extends PreferenceCategory {

    private final Context mContext;

    public VectorDividerCategory(Context context) {
        super(context);
        mContext = context;
    }

    public VectorDividerCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public VectorDividerCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View layout = super.onCreateView(parent);

        LayoutInflater layoutInflater = (android.view.LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View tmplayout = layoutInflater.inflate(R.layout.vector_preference_divider, parent, false);

        if (null != tmplayout) {
            layout = tmplayout;
        }

        return layout;
    }
}