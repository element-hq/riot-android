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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import org.matrix.androidsdk.util.Log;

import im.vector.R;

/**
 * This class should fix the android issue
 * https://code.google.com/p/android/issues/detail?id=26194
 *
 */
public class VectorSwitchPreference extends CheckBoxPreference {
    private static final String LOG_TAG = "VectorSwitchPreference";


    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context The Context that will style this preference
     * @param attrs Style attributes that differ from the default
     * @param defStyle Theme attribute defining the default style options
     */
    public VectorSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context The Context that will style this preference
     * @param attrs Style attributes that differ from the default
     */
    public VectorSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public VectorSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    /**
     * Construct a new SwitchPreference with default style options.
     *
     * @param context The Context that will style this preference
     */
    public VectorSwitchPreference(Context context) {
        super(context, null);
        init();
    }

    @Override
    protected void onBindView(View view) {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Clean listener before invoke SwitchPreference.onBindView
            ViewGroup viewGroup = (ViewGroup) view;
            clearListenerInViewGroup(viewGroup);
        }
        super.onBindView(view);

        // display the title in multi-line to avoid ellipsing.
        try {
            TextView textView = (TextView) view.findViewById(android.R.id.title);
            if (textView != null) {
                textView.setSingleLine(false);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "onBindView " + e.getMessage());
        }
    }

    /**
     * Clear listener in Switch for specify ViewGroup.
     *
     * @param viewGroup The ViewGroup that will need to clear the listener.
     */
    private void clearListenerInViewGroup(ViewGroup viewGroup) {
        if (null == viewGroup) {
            return;
        }

        int count = viewGroup.getChildCount();
        for(int n = 0; n < count; ++n) {
            View childView = viewGroup.getChildAt(n);
            if(childView instanceof Switch) {
                final Switch switchView = (Switch) childView;
                switchView.setOnCheckedChangeListener(null);
                return;
            } else if (childView instanceof ViewGroup){
                ViewGroup childGroup = (ViewGroup)childView;
                clearListenerInViewGroup(childGroup);
            }
        }
    }

    private void init() {
        // Force the use of SwitchCompat component
        setWidgetLayoutResource(R.layout.preference_switch_layout);
    }

}