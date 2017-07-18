package im.vector.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

import im.vector.R;
import im.vector.util.ThemeUtils;

/**
 * A Base class for all vector activities that want to extend AppCompat
 */
public class VectorAppCompatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.activitySetTheme(this);
    }
}
