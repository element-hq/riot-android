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

import android.annotation.SuppressLint;
import android.content.Context;
import android.preference.ListPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import im.vector.R;

/**
 * Customize ListPreference class to add a warning icon to the right side of the list.
 */
public class VectorListPreference extends ListPreference {

    /**
     * Interface definition for a callback to be invoked when the warning icon is clicked.
     */
    public interface OnPreferenceWarningIconClickListener {
        /**
         * Called when a warning icon has been clicked.
         *
         * @param preference The Preference that was clicked.
         */
        void onWarningIconClick(Preference preference);
    }

    //
    private View mWarningIconView;
    private boolean mIsWarningIconVisible = false;
    private OnPreferenceWarningIconClickListener mWarningIconClickListener;

    public VectorListPreference(Context context) {
        super(context);
    }

    public VectorListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @SuppressLint("NewApi")
    public VectorListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        setWidgetLayoutResource(R.layout.vector_settings_list_preference_with_warning);
        View view = super.onCreateView(parent);

        mWarningIconView = view.findViewById(R.id.list_preference_warning_icon);
        mWarningIconView.setVisibility(mIsWarningIconVisible ? View.VISIBLE : View.GONE);

        mWarningIconView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mWarningIconClickListener) {
                    mWarningIconClickListener.onWarningIconClick(VectorListPreference.this);
                }
            }
        });

        return view;
    }

    /**
     * Sets the callback to be invoked when this warning icon is clicked.
     *
     * @param onPreferenceWarningIconClickListener The callback to be invoked.
     */
    public void setOnPreferenceWarningIconClickListener(OnPreferenceWarningIconClickListener onPreferenceWarningIconClickListener) {
        mWarningIconClickListener = onPreferenceWarningIconClickListener;
    }

    /**
     * Set the warning icon visibility.
     * @param isVisible to display the icon
     */
    public void setWarningIconVisible(boolean isVisible) {
        mIsWarningIconVisible = isVisible;

        if (null != mWarningIconView) {
            mWarningIconView.setVisibility(mIsWarningIconVisible ? View.VISIBLE : View.GONE);
        }
    }
}