package im.vector.activity;

import android.app.Activity;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

import im.vector.R;
import im.vector.util.ThemeUtils;

/**
 * A super activity for all vector activities
 */
public class VectorActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.activitySetTheme(this);
    }
}
