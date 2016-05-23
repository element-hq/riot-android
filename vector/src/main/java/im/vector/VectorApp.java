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

package im.vector;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.analytics.ExceptionParser;

import org.matrix.androidsdk.MXSession;

import im.vector.activity.CallViewActivity;
import im.vector.activity.CommonActivityUtils;
import im.vector.contacts.ContactsManager;
import im.vector.contacts.PIDsRetriever;
import im.vector.ga.Analytics;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.services.EventStreamService;
import im.vector.util.LogUtilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The main application injection point
 */
public class VectorApp extends Application {
    private static final String LOG_TAG = "VectorApp";

    private Timer mActivityTransitionTimer;
    private TimerTask mActivityTransitionTimerTask;
    private boolean mIsInBackground = true;
    private final long MAX_ACTIVITY_TRANSITION_TIME_MS = 2000;

    // google analytics
    private int VERSION_BUILD = -1;
    private String VECTOR_VERSION_STRING = "";
    private String SDK_VERSION_STRING = "";

    private Boolean mIsCallingInBackground = false;

    private static VectorApp instance = null;

    private EventEmitter<Activity> mOnActivityDestroyedListener;

    private static Bitmap mSavedPickerImagePreview = null;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        mOnActivityDestroyedListener = new EventEmitter<>();

        mActivityTransitionTimer = null;
        mActivityTransitionTimerTask = null;

        try {
            PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            VERSION_BUILD = pinfo.versionCode;
        }
        catch (PackageManager.NameNotFoundException e) {}

        VECTOR_VERSION_STRING = Matrix.getInstance(this).getVersion(true);

        // not the first launch
        if (null != Matrix.getInstance(this).getDefaultSession()) {
            SDK_VERSION_STRING = Matrix.getInstance(this).getDefaultSession().getVersion(true);
        } else {
            SDK_VERSION_STRING = "";
        }

        LogUtilities.setLogDirectory(new File(getCacheDir().getAbsolutePath() + "/logs"));
        LogUtilities.storeLogcat();

        initGoogleAnalytics(getApplicationContext());

        // get the contact update at application launch
        ContactsManager.refreshLocalContactsSnapshot(this);
    }

    public static VectorApp getInstance() {
        return instance;
    }

    public EventEmitter<Activity> getOnActivityDestroyedListener() {
        return mOnActivityDestroyedListener;
    }
    /**
     * Suspend background threads.
     */
    private void suspendApp() {
        // suspend the events thread if the client uses GCM
        if (Matrix.getInstance(VectorApp.this).getSharedGcmRegistrationManager().useGCM()) {
            CommonActivityUtils.pauseEventStream(VectorApp.this);
        }
        PIDsRetriever.getIntance().onAppBackgrounded();

        MyPresenceManager.advertiseAllUnavailable();
    }

    /**
     * The application is warned that a call is ended.
     */
    public void onCallEnd() {
        if (isAppInBackground() && mIsCallingInBackground) {
            Log.d(LOG_TAG, "onCallEnd : Suspend the events thread because the call was ended whereas the application was in background");
            suspendApp();
        }

        mIsCallingInBackground = false;
    }

    private void startActivityTransitionTimer() {
        this.mActivityTransitionTimer = new Timer();
        this.mActivityTransitionTimerTask = new TimerTask() {
            public void run() {
                if (VectorApp.this.mActivityTransitionTimerTask != null) {
                    VectorApp.this.mActivityTransitionTimerTask.cancel();
                    VectorApp.this.mActivityTransitionTimerTask = null;
                }

                if (VectorApp.this.mActivityTransitionTimer != null) {
                    VectorApp.this.mActivityTransitionTimer.cancel();
                    VectorApp.this.mActivityTransitionTimer = null;
                }

                VectorApp.this.mIsInBackground = true;
                mIsCallingInBackground = (null != CallViewActivity.getActiveCall());

                // if there is a pending call
                // the application is not suspended
                if (!mIsCallingInBackground) {
                    Log.d(LOG_TAG, "Suspend the application because there was no resumed activity within 2 seconds");
                    suspendApp();
                }
            }
        };

        this.mActivityTransitionTimer.schedule(mActivityTransitionTimerTask, MAX_ACTIVITY_TRANSITION_TIME_MS);
    }

    private void stopActivityTransitionTimer() {
        if (this.mActivityTransitionTimerTask != null) {
            this.mActivityTransitionTimerTask.cancel();
            this.mActivityTransitionTimerTask = null;
        }

        if (this.mActivityTransitionTimer != null) {
            this.mActivityTransitionTimer.cancel();
            this.mActivityTransitionTimer = null;
        }

        if (isAppInBackground() && !mIsCallingInBackground) {

            // the event stream service has been killed
            if (null == EventStreamService.getInstance()) {
                CommonActivityUtils.startEventStreamService(VectorApp.this);
            } else {
                CommonActivityUtils.resumeEventStream(VectorApp.this);

                // try to perform a GCM registration if it failed
                // or if the GCM server generated a new push key
                GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(this).getSharedGcmRegistrationManager();
                if (null != gcmRegistrationManager) {
                    gcmRegistrationManager.checkPusherRegistration(this);
                }
            }

            // get the contact update at application launch
            ContactsManager.refreshLocalContactsSnapshot(this);

            ArrayList<MXSession> sessions = Matrix.getInstance(this).getSessions();
            for(MXSession session : sessions) {
                session.getMyUser().refreshUserInfos(null);
            }
        }

        MyPresenceManager.advertiseAllOnline();

        this.mIsCallingInBackground = false;
        this.mIsInBackground = false;
    }

    static private Activity mCurrentActivity = null;
    public static void setCurrentActivity(Activity activity) {
        if (VectorApp.isAppInBackground() && (null != activity)) {
            Matrix matrixInstance =  Matrix.getInstance(activity.getApplicationContext());

            // sanity check
            if (null != matrixInstance) {
                matrixInstance.refreshPushRules();
            }

            Log.e("debackground", "The application is resumed");
            // display the memory usage when the application is debackgrounded.
            CommonActivityUtils.displayMemoryInformation(activity);
        }

        // wait 2s to check that the application is put in background
        if (null != getInstance()) {
            if (null == activity) {
                getInstance().startActivityTransitionTimer();
            } else {
                getInstance().stopActivityTransitionTimer();
            }
        }

        mCurrentActivity = activity;
    }
    public static Activity getCurrentActivity() { return mCurrentActivity; }

    /**
     * Return true if the application is in background.
     */
    public static boolean isAppInBackground() {
        return (null == mCurrentActivity) && (null != getInstance()) && getInstance().mIsInBackground;
    }

    //==============================================================================================================
    // Google analytics
    //==============================================================================================================
    /**
     * Update the GA use.
     * @param context the context
     * @param value the new value
     */
    public void setUseGA(Context context, boolean value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(context.getString(R.string.ga_use_settings), value);
        editor.commit();

        initGoogleAnalytics(context);
    }

    /**
     * Tells if GA can be used
     * @param context the context
     * @return null if not defined, true / false when defined
     */
    public Boolean useGA(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (preferences.contains(context.getString(R.string.ga_use_settings))) {
            return preferences.getBoolean(context.getString(R.string.ga_use_settings), false);
        } else {
            return null;
        }
    }

    /**
     * Initialize the google analytics
     */
    public void initGoogleAnalytics(Context context) {
        Boolean useGA = useGA(context);

        if (null == useGA) {
            Log.e(LOG_TAG, "Google Analytics use is not yet initialized");
            return;
        }

        if (!useGA) {
            Log.e(LOG_TAG, "The user decides to do not use Google Analytics");
            return;
        }

        // pull tracker resource ID from res/values/analytics.xml
        int trackerResId = getResources().getIdentifier("ga_trackingId", "string", getPackageName());
        if (trackerResId == 0) {
            Log.e(LOG_TAG, "Unable to find tracker id for Google Analytics");
            return;
        }

        String trackerId = getString(trackerResId);
        Log.d(LOG_TAG, "Tracker ID: "+trackerId);
        // init google analytics with this tracker ID
        if (!TextUtils.isEmpty(trackerId)) {
            Analytics.initialiseGoogleAnalytics(this, trackerId, new ExceptionParser() {
                @Override
                public String getDescription(String threadName, Throwable throwable) {
                    StringBuilder b = new StringBuilder();

                    b.append("Vector Build : " + VERSION_BUILD + "\n");
                    b.append("Vector Version : " + VECTOR_VERSION_STRING + "\n");
                    b.append("SDK Version : " + SDK_VERSION_STRING + "\n");
                    b.append("Phone : " + Build.MODEL.trim() + " (" + Build.VERSION.INCREMENTAL + " " + Build.VERSION.RELEASE + " " + Build.VERSION.CODENAME + ")\n");
                    b.append("Thread: ");
                    b.append(threadName);

                    Activity a = VectorApp.getCurrentActivity();
                    if (a != null) {
                        b.append(", Activity:");
                        b.append(a.getLocalClassName());
                    }

                    b.append(", Exception: ");
                    b.append(Analytics.getStackTrace(throwable));
                    Log.e("FATAL EXCEPTION", b.toString());
                    return b.toString();
                }
            });
        }
    }

    public static Bitmap getSavedPickerImagePreview(){
        return mSavedPickerImagePreview;
    }

    public static void setSavedCameraImagePreview(Bitmap aSavedCameraImagePreview){
        if (aSavedCameraImagePreview != mSavedPickerImagePreview) {
            // force to release memory
            if (null != mSavedPickerImagePreview) {
                mSavedPickerImagePreview.recycle();
                mSavedPickerImagePreview = null;
                System.gc();
            }

            mSavedPickerImagePreview = aSavedCameraImagePreview;
        }
    }
}

