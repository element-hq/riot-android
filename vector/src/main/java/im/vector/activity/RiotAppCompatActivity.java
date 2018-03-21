/*
 * Copyright 2015 OpenMarket Ltd
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

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import im.vector.VectorApp;

public class RiotAppCompatActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(VectorApp.getLocalisedContext(base));
    }

    //==============================================================================================
    // Handle loading view (also called wainting view or spinner view
    //==============================================================================================

    public View waitingView;

    /**
     * Show teh waiting view
     */
    public void showWaitingView() {
        if (null != waitingView) {
            waitingView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide the waiting view
     */
    public void stopWaitingView() {
        if (null != waitingView) {
            waitingView.setVisibility(View.GONE);
        }
    }

    /**
     * Tells if the waiting view is currently displayed
     *
     * @return true if the waiting view is displayed
     */
    public boolean isWaitingViewVisible() {
        return (null != waitingView) && (View.VISIBLE == waitingView.getVisibility());
    }
}
