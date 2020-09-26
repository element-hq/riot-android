/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2018 New Vector Ltd
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

package im.vector.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import im.vector.Matrix;
import im.vector.MyPresenceManager;
import im.vector.R;
import im.vector.VectorApp;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * extends ActionBarActivity to manage the rageshake
 */
public abstract class MXCActionBarActivity extends VectorAppCompatActivity {
    // TODO Make this protected
    public static final String EXTRA_MATRIX_ID = "MXCActionBarActivity.EXTRA_MATRIX_ID";

    protected MXSession mSession = null;
    Room mRoom = null;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        CommonActivityUtils.onLowMemory(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        CommonActivityUtils.onTrimMemory(this, level);
    }

    /**
     * Return the used MXSession from an intent.
     *
     * @param intent the intent
     * @return the MXSession if it exists, or null.
     */
    @Nullable
    protected MXSession getSession(Intent intent) {
        String matrixId = null;

        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            matrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        return Matrix.getInstance(this).getSession(matrixId);
    }

    public MXSession getSession() {
        return mSession;
    }

    public Room getRoom() {
        return mRoom;
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);

        // create a "lollipop like " animation
        // not sure it is the save animation curve
        // appcompat does not support (it does nothing)
        //
        // ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(...
        // ActivityCompat.startActivity(activity, new Intent(activity, DetailActivity.class),  options.toBundle());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            overridePendingTransition(R.anim.anim_slide_in_bottom, R.anim.anim_slide_nothing);
        } else {
            // the animation is enabled in the theme
        }
    }

    @Override
    public void finish() {
        super.finish();

        // create a "lollipop like " animation
        // not sure it is the save animation curve
        //
        // ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(...
        // ActivityCompat.startActivity(activity, new Intent(activity, DetailActivity.class),  options.toBundle());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            overridePendingTransition(R.anim.anim_slide_nothing, R.anim.anim_slide_out_bottom);
        } else {
            // the animation is enabled in the theme
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_MENU) && (null == getSupportActionBar())) {
            // This is to fix a bug in the v7 support lib. If there is no options menu and you hit MENU, it will crash with a
            // NPE @ android.support.v7.app.ActionBarImplICS.getThemedContext(ActionBarImplICS.java:274)
            // This can safely be removed if we add in menu options on this screen
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Dismiss any opened dialog.
     *
     * @param activity the parent activity.
     */
    private static void dismissDialogs(FragmentActivity activity) {
        // close any opened dialog
        FragmentManager fm = activity.getSupportFragmentManager();
        java.util.List<Fragment> fragments = fm.getFragments();

        if (null != fragments) {
            for (Fragment fragment : fragments) {
                // VectorUnknownDevicesFragment must not be dismissed
                // The user has to update the device statuses
                if (fragment instanceof DialogFragment) {
                    ((DialogFragment) fragment).dismissAllowingStateLoss();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Matrix.removeSessionErrorListener(this);
        // Keep the unused method for now, and track unwanted side effects.
        // Also the history of the body is strange, ylecollen has added code with comment, and remove it the next day, leaving the comment.
        // dismissDialogs(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VectorApp.getInstance().getOnActivityDestroyedListener().fire(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Matrix.setSessionErrorListener(this);

        // the online presence must be displayed ASAP.
        if ((null != Matrix.getInstance(this)) && (null != Matrix.getInstance(this).getSessions())) {
            MyPresenceManager.createPresenceManager(this, Matrix.getInstance(this).getSessions());
            MyPresenceManager.advertiseAllOnline();
        }
    }

    /**
     * Dismiss the soft keyboard if one view in the activity has the focus.
     *
     * @param activity the activity
     */
    public static void dismissKeyboard(Activity activity) {
        // hide the soft keyboard
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager inputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
}
