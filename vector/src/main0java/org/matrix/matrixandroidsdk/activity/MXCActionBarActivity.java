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

package org.matrix.console.activity;

import android.support.v7.app.ActionBarActivity;

import org.matrix.vector.ConsoleApplication;
import org.matrix.vector.Matrix;
import org.matrix.vector.util.RageShake;

/**
 * extends ActionBarActivity to manage the rageshake
 */
public class MXCActionBarActivity extends ActionBarActivity {

    @Override
    protected void onPause() {
        super.onPause();
        RageShake.getInstance().setCurrentActivity(null);

        ((ConsoleApplication)getApplication()).startActivityTransitionTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        RageShake.getInstance().setCurrentActivity(this);

        // refresh the bing rules when the application is debackgrounded
        if (((ConsoleApplication)getApplication()).wasInBackground) {
            Matrix.getInstance(this).refreshPushRules();
        }

        ((ConsoleApplication)getApplication()).stopActivityTransitionTimer();
    }
}
