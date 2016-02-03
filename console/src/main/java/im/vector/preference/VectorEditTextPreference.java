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
import android.preference.EditTextPreference;
import android.preference.PreferenceScreen;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

import im.vector.R;

public class VectorEditTextPreference extends EditTextPreference {

    // parameter
    View mLayout;
    Context mContext;

    public VectorEditTextPreference(Context context) {
        super(context);
        mContext = context;
    }

    public VectorEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public VectorEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    protected View onCreateView(ViewGroup parent) {
        mLayout = super.onCreateView(parent);
        return mLayout;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        InputMethodManager imm = (InputMethodManager)mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mLayout.getApplicationWindowToken(), 0);
    }
}