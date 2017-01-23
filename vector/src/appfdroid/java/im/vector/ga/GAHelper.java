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
package im.vector.ga;
;
import android.content.Context;

import org.matrix.androidsdk.util.Log;

public class GAHelper {

    //==============================================================================================================
    // Google analytics
    //==============================================================================================================

    /**
     * Tells if the GA use can be updated
     * @return true if it can be updated
     */
    public static boolean isGAUseUpdatable() {
        return false;
    }

    /**
     * Update the GA use.
     * @param context the context
     * @param value the new value
     */
    public static void setUseGA(Context context, boolean value) {
    }

    /**
     * Tells if GA can be used
     * @param context the context
     * @return null if not defined, true / false when defined
     */
    public static Boolean useGA(Context context) {
        return false;
    }

    /**
     * Initialize the google analytics
     */
    public static void initGoogleAnalytics(Context context) {
    }

    /**
     * Send a GA stats
     * @param context the context
     * @param category the category
     * @param action the action
     * @param label the label
     * @param value the value
     */
    public static void sendGAStats(Context context, String category, String action, String label, long value) {
    }
}
