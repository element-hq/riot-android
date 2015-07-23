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

package im.vector.adapters;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import java.util.List;

/**
 * Contains useful functions for adapters.
 */
public class AdapterUtils {
    private static final String LOG_TAG = "AdapterUtils";

    /** Checks if the device can send SMS messages.
     *
     * @param context Context for obtaining the package manager.
     * @return true if you can send SMS, false otherwise.
     */
    public static boolean canSendSms(Context context) {
        Uri smsUri = Uri.parse("smsto:12345");
        Intent smsIntent = new Intent(Intent.ACTION_SENDTO, smsUri);
        PackageManager smspackageManager = context.getPackageManager();
        List<ResolveInfo> smsresolveInfos = smspackageManager.queryIntentActivities(smsIntent, 0);
        if(smsresolveInfos.size() > 0) {
            return true;
        }
        else {
            return false;
        }
    }

    /** Launch a SMS intent if the device is capable.
     *
     * @param activity The parent activity (for context)
     * @param number The number to sms (not the full URI)
     * @param text The sms body
     */
    public static void launchSmsIntent(final Activity activity, String number, String text) {
        Log.i(LOG_TAG,"Launch SMS intent to "+number);
        // create sms intent
        Uri smsUri = Uri.parse("smsto:" + number);
        Intent smsIntent = new Intent(Intent.ACTION_SENDTO, smsUri);
        smsIntent.putExtra("sms_body", text);
        // make sure there is an activity which can handle the intent.
        PackageManager smspackageManager = activity.getPackageManager();
        List<ResolveInfo> smsresolveInfos = smspackageManager.queryIntentActivities(smsIntent, 0);
        if(smsresolveInfos.size() > 0) {
            activity.startActivity(smsIntent);
        }
    }

    /** Launch an email intent if the device is capable.
     *
     * @param activity The parent activity (for context)
     * @param addr The address to email (not the full URI)
     * @param text The email body
     */
    public static void launchEmailIntent(final Activity activity, String addr, String text) {
        Log.i(LOG_TAG,"Launch email intent from "+activity.getLocalClassName());
        // create email intent
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {addr});
        emailIntent.setType("text/plain");
        // make sure there is an activity which can handle the intent.
        PackageManager emailpackageManager = activity.getPackageManager();
        List<ResolveInfo> emailresolveInfos = emailpackageManager.queryIntentActivities(emailIntent, 0);
        if(emailresolveInfos.size() > 0) {
            activity.startActivity(emailIntent);
        }
    }
}
