/*
 * Copyright 2019 New Vector Ltd
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

import android.annotation.SuppressLint;
import android.app.Activity;

/**
 * Dummy Activity, which will never be created
 */
@SuppressLint("Registered")
public class JitsiCallActivity extends Activity {
    public static final boolean IS_SUPPORTED = false;

    /**
     * The linked widget
     */
    public static final String EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID";

    /**
     * set to true to start a video call
     */
    public static final String EXTRA_ENABLE_VIDEO = "EXTRA_ENABLE_VIDEO";

}