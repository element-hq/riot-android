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