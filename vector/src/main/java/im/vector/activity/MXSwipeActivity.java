/*
 * Copyright 2014 OpenMarket Ltd
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
import android.view.KeyEvent;

import com.liuguangqiang.swipeback.SwipeBackActivity;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import im.vector.MyPresenceManager;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.Matrix;

/**
 * Add the swipe gesture to an activity to dismiss it.
 * This class is almost the same than MXCActionBarActivity.
 * SwipeBackActivity does not allow to disable the swipe action
 * It cannot inherit from this class.
 */
public class MXSwipeActivity extends SwipeBackActivity {

    protected MXSession mSession = null;
    protected Room mRoom = null;

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

    public MXSession getSession() {
        return mSession;
    }

    public Room getRoom() {
        return mRoom;
    }

    @Override
    public void finish() {
        super.finish();

        // create a "lollipop like " animation
        // not sure it is the save animation curve
        //
        // ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(...
        // ActivityCompat.startActivity(activity, new Intent(activity, DetailActivity.class),  options.toBundle());
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            this.overridePendingTransition(R.anim.anim_slide_nothing, R.anim.anim_slide_out_bottom);
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

    @Override
    protected void onPause() {
        super.onPause();
        Matrix.removeSessionErrorListener(this);
        MXCActionBarActivity.dismissDialogs(this);
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
}
