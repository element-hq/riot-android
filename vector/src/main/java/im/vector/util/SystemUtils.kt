/*
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

package im.vector.util

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Tells if the application ignores battery optimizations.
 *
 * Ignoring them allows the app to run in background to make background sync with the homeserver.
 * This user option appears on Android M but Android O enforces its usage and kills apps not
 * authorised by the user to run in background.
 *
 * @param context the context
 * @return true if battery optimisations are ignored
 */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    // no issue before Android M, battery optimisations did not exist
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            || (context.getSystemService(Context.POWER_SERVICE) as PowerManager?)?.isIgnoringBatteryOptimizations(context.packageName) == true
}
