/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

import org.jetbrains.annotations.NotNull;

import im.vector.R;
import kotlin.Triple;

/**
 * LoggingOutActivity displays an animation while a session log out is in progress.
 */
public class LoggingOutActivity extends MXCActionBarActivity {
    @NotNull
    @Override
    public Triple getOtherThemes() {
        return new Triple(R.style.AppTheme_NoActionBar_Dark, R.style.AppTheme_NoActionBar_Black, R.style.AppTheme_NoActionBar_Status);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.vector_activity_splash;
    }

    @Override
    public void initUiAndData() {
        // Nothing to do
    }
}
