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
    protected Context mContext;
    protected ImageView mMainAddressIconView;

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
        View layout = super.onCreateView(parent);
        mMainAddressIconView = (ImageView)layout.findViewById(R.id.main_address_icon_view);
        return layout;
    }

    /**
     * Set the main address icon visibility.
     * @param visibility the new visibility
     */
    public void setMainIconVisibility(int visibility) {
        mMainAddressIconView.setVisibility(visibility);
    }
}