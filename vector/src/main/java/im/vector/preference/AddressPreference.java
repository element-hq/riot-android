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
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import im.vector.R;

public class AddressPreference extends VectorCustomActionEditTextPreference {

    // members
    protected final Context mContext;
    protected ImageView mMainAddressIconView;
    protected boolean mIsMainIconVisible = false;

    public AddressPreference(Context context) {
        super(context);
        mContext = context;
    }

    public AddressPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public AddressPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        setWidgetLayoutResource(R.layout.vector_settings_address_preference);
        View view = super.onCreateView(parent);
        mMainAddressIconView = (ImageView)view.findViewById(R.id.main_address_icon_view);
        mMainAddressIconView.setVisibility(mIsMainIconVisible ? View.VISIBLE : View.GONE);
        return view;
    }

    /**
     * Set the main address icon visibility.
     * @param isVisible true to display the main icon
     */
    public void setMainIconVisible(boolean isVisible) {
        mIsMainIconVisible = isVisible;

        if (null != mMainAddressIconView) {
            mMainAddressIconView.setVisibility(mIsMainIconVisible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * @return the main icon view.
     */
    public View getMainIconView() {
        return mMainAddressIconView;
    }
}