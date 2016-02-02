package im.vector.pref;

import android.content.Context;
import android.preference.EditTextPreference;
import android.preference.PreferenceScreen;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

import im.vector.R;

public class VectorCustomActionEditTextPreference extends VectorEditTextPreference {

    public VectorCustomActionEditTextPreference(Context context) {
        super(context);
    }

    public VectorCustomActionEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VectorCustomActionEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void performClick(PreferenceScreen preferenceScreen) {
        // call only the click listener
        if (getOnPreferenceClickListener() != null && getOnPreferenceClickListener().onPreferenceClick(this)) {
            getOnPreferenceClickListener().onPreferenceClick(this);
        }
    }
}