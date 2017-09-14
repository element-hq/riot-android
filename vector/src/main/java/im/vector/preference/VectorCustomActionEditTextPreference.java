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
import android.graphics.Typeface;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import org.matrix.androidsdk.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

// create an EditTextPreference with a dedicated click/long click methods.
// Android displays by an edit text dialog by default
// With this class, a custom behaviour can be designed.
public class VectorCustomActionEditTextPreference extends EditTextPreference {
    private static final String LOG_TAG = "VEditTextPreference";

    /**
     * Interface definition for a callback to be invoked when a preference is
     * long clicked.
     */
    public interface OnPreferenceLongClickListener {
        /**
         * Called when a Preference has been clicked.
         *
         * @param preference The Preference that was clicked.
         * @return True if the click was handled.
         */
        boolean onPreferenceLongClick(Preference preference);
    }

    int mTypeface = Typeface.NORMAL;

    // long press listener
    private OnPreferenceLongClickListener mOnClickLongListener;

    public VectorCustomActionEditTextPreference(Context context) {
        super(context);
    }

    public VectorCustomActionEditTextPreference(Context context, int aTypeface) {
        super(context);
        mTypeface = aTypeface;
    }

    public VectorCustomActionEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VectorCustomActionEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        addClickListeners(view);
        return view;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        addClickListeners(view);

        // display the title in multi-line to avoid ellipsing.
        try {
            TextView title = (TextView) view.findViewById(android.R.id.title);
            TextView summary = (TextView) view.findViewById(android.R.id.summary);
            if (title != null) {
                title.setSingleLine(false);
                title.setTypeface(null, mTypeface);
            }

            if (title != summary) {
                summary.setTypeface(null, mTypeface);
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "onBindView " + e.getMessage());
        }
    }

    /**
     *
     * @param view
     */
    private void addClickListeners(View view) {
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (null != getOnPreferenceLongClickListener()) {
                    return getOnPreferenceLongClickListener().onPreferenceLongClick(VectorCustomActionEditTextPreference.this);
                }
                return false;
            }
        });

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // call only the click listener
                if (getOnPreferenceClickListener() != null) {
                    getOnPreferenceClickListener().onPreferenceClick(VectorCustomActionEditTextPreference.this);
                }
            }
        });
    }

    /**
     * Sets the callback to be invoked when this Preference is long clicked.
     *
     * @param onPreferenceLongClickListener The callback to be invoked.
     */
    public void setOnPreferenceLongClickListener(OnPreferenceLongClickListener onPreferenceLongClickListener) {
        mOnClickLongListener = onPreferenceLongClickListener;
    }

    /**
     * Returns the callback to be invoked when this Preference is long clicked.
     *
     * @return The callback to be invoked.
     */
    public OnPreferenceLongClickListener getOnPreferenceLongClickListener() {
        return mOnClickLongListener;
    }
}