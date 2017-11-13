/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Pair;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.util.Log;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.activity.CommonActivityUtils;
import im.vector.activity.JitsiCallActivity;
import im.vector.activity.VectorCallViewActivity;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.activity.WidgetActivity;
import im.vector.contacts.ContactsManager;
import im.vector.contacts.PIDsRetriever;
import im.vector.ga.GAHelper;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.services.EventStreamService;
import im.vector.util.BugReporter;
import im.vector.util.CallsManager;
import im.vector.util.PhoneNumberUtils;
import im.vector.util.PreferencesManager;
import im.vector.util.RageShake;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorMarkdownParser;

/**
 * The main application injection point
 */
public class VectorApp extends MultiDexApplication {
    private static final String LOG_TAG = "VectorApp";

    // key to save the crash status
    private static final String PREFS_CRASH_KEY = "PREFS_CRASH_KEY";

    /**
     * The current instance.
     */
    private static VectorApp instance = null;

    /**
     * Rage shake detection to send a bug report.
     */
    private static final RageShake mRageShake = new RageShake();

    /**
     * Delay to detect if the application is in background.
     * If there is no active activity during the elapsed time, it means that the application is in background.
     */
    private static final long MAX_ACTIVITY_TRANSITION_TIME_MS = 4000;

    /**
     * The current active activity
     */
    private static Activity mCurrentActivity = null;

    /**
     * Background application detection
     */
    private Timer mActivityTransitionTimer;
    private TimerTask mActivityTransitionTimerTask;
    private boolean mIsInBackground = true;

    /**
     * Google analytics information.
     */
    public static int VERSION_BUILD = -1;
    private static String VECTOR_VERSION_STRING = "";
    private static String SDK_VERSION_STRING = "";

    /**
     * Tells if there a pending call whereas the application is backgrounded.
     */
    private boolean mIsCallingInBackground = false;

    /**
     * Monitor the created activities to detect memory leaks.
     */
    private final ArrayList<String> mCreatedActivities = new ArrayList<>();

    /**
     * Markdown parser
     */
    public VectorMarkdownParser mMarkdownParser;

    /**
     * Calls manager
     */
    private CallsManager mCallsManager;

    /**
     * @return the current instance
     */
    public static VectorApp getInstance() {
        return instance;
    }

    /**
     * The directory in which the logs are stored
     */
    public static File mLogsDirectoryFile = null;

    /**
     * The last time that removeMediasBefore has been called.
     */
    private long mLastMediasCheck = 0;

    private final BroadcastReceiver mLanguageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!TextUtils.equals(Locale.getDefault().toString(), getApplicationLocale().toString())) {
                Log.d(LOG_TAG, "## onReceive() : the locale has been updated to " + Locale.getDefault().toString() + ", restore the expected value " + getApplicationLocale().toString());
                updateApplicationSettings(getApplicationLocale(), getFontScale(), ThemeUtils.getApplicationTheme(context));

                if (null != getCurrentActivity()) {
                    restartActivity(getCurrentActivity());
                }
            }
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate();

        instance = this;
        mCallsManager = new CallsManager(this);
        mActivityTransitionTimer = null;
        mActivityTransitionTimerTask = null;

        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            VERSION_BUILD = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "fails to retrieve the package info " + e.getMessage());
        }

        VECTOR_VERSION_STRING = Matrix.getInstance(this).getVersion(true, true);

        // not the first launch
        if (null != Matrix.getInstance(this).getDefaultSession()) {
            SDK_VERSION_STRING = Matrix.getInstance(this).getDefaultSession().getVersion(true);
        } else {
            SDK_VERSION_STRING = "";
        }

        mLogsDirectoryFile = new File(getCacheDir().getAbsolutePath() + "/logs");

        org.matrix.androidsdk.util.Log.setLogDirectory(mLogsDirectoryFile);
        org.matrix.androidsdk.util.Log.init("RiotLog");

        // log the application version to trace update
        // useful to track backward compatibility issues

        Log.d(LOG_TAG, "----------------------------------------------------------------");
        Log.d(LOG_TAG, "----------------------------------------------------------------");
        Log.d(LOG_TAG, " Application version: " + VECTOR_VERSION_STRING);
        Log.d(LOG_TAG, " SDK version: " + SDK_VERSION_STRING);
        Log.d(LOG_TAG, " Local time: " + (new SimpleDateFormat("MM-dd HH:mm:ss.SSSZ", Locale.US)).format(new Date()));
        Log.d(LOG_TAG, "----------------------------------------------------------------");
        Log.d(LOG_TAG, "----------------------------------------------------------------\n\n\n\n");

        GAHelper.initGoogleAnalytics(getApplicationContext());

        mRageShake.start(this);

        // init the REST client
        MXSession.initUserAgent(getApplicationContext());

        this.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            final Map<String, String> mLocalesByActivity = new HashMap<>();

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                Log.d(LOG_TAG, "onActivityCreated " + activity);
                mCreatedActivities.add(activity.toString());
                ThemeUtils.setActivityTheme(activity);
            }

            @Override
            public void onActivityStarted(Activity activity) {
                Log.d(LOG_TAG, "onActivityStarted " + activity);
            }

            /**
             * Compute the locale status value
             * @param activity the activity
             * @return the local status value
             */
            private String getActivityLocaleStatus(Activity activity) {
                return getApplicationLocale().toString() + "_" + getFontScale() + "_" + ThemeUtils.getApplicationTheme(activity);
            }

            @Override
            public void onActivityResumed(final Activity activity) {
                Log.d(LOG_TAG, "onActivityResumed " + activity);
                setCurrentActivity(activity);

                String activityKey = activity.toString();

                if (mLocalesByActivity.containsKey(activityKey)) {
                    String prevActivityLocale = mLocalesByActivity.get(activityKey);

                    if (!TextUtils.equals(prevActivityLocale, getActivityLocaleStatus(activity))) {
                        Log.d(LOG_TAG, "## onActivityResumed() : restart the activity " + activity + " because of the locale update from " + prevActivityLocale + " to " + getActivityLocaleStatus(activity));
                        restartActivity(activity);
                        return;
                    }
                }

                // it should never happen as there is a broadcast receiver (mLanguageReceiver)
                if (!TextUtils.equals(Locale.getDefault().toString(), getApplicationLocale().toString())) {
                    Log.d(LOG_TAG, "## onActivityResumed() : the locale has been updated to " + Locale.getDefault().toString() + ", restore the expected value " + getApplicationLocale().toString());
                    updateApplicationSettings(getApplicationLocale(), getFontScale(), ThemeUtils.getApplicationTheme(activity));
                    restartActivity(activity);
                }

                listPermissionStatuses();
            }

            @Override
            public void onActivityPaused(Activity activity) {
                Log.d(LOG_TAG, "onActivityPaused " + activity);
                mLocalesByActivity.put(activity.toString(), getActivityLocaleStatus(activity));
                setCurrentActivity(null);
            }

            @Override
            public void onActivityStopped(Activity activity) {
                Log.d(LOG_TAG, "onActivityStopped " + activity);
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                Log.d(LOG_TAG, "onActivitySaveInstanceState " + activity);
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                Log.d(LOG_TAG, "onActivityDestroyed " + activity);
                mCreatedActivities.remove(activity.toString());
                mLocalesByActivity.remove(activity.toString());

                if (mCreatedActivities.size() > 1) {
                    Log.d(LOG_TAG, "onActivityDestroyed : \n" + mCreatedActivities);
                }
            }
        });

        // create the markdown parser
        try {
            mMarkdownParser = new VectorMarkdownParser(this);
        } catch (Exception e) {
            // reported by GA
            Log.e(LOG_TAG, "cannot create the mMarkdownParser " + e.getMessage());
        }

        // track external language updates
        // local update from the settings
        // or screen rotation !
        VectorApp.getInstance().registerReceiver(mLanguageReceiver, new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
        VectorApp.getInstance().registerReceiver(mLanguageReceiver, new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));

        PreferencesManager.fixMigrationIssues(this);
        initApplicationLocale();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!TextUtils.equals(Locale.getDefault().toString(), getApplicationLocale().toString())) {
            Log.d(LOG_TAG, "## onConfigurationChanged() : the locale has been updated to " + Locale.getDefault().toString() + ", restore the expected value " + getApplicationLocale().toString());
            updateApplicationSettings(getApplicationLocale(), getFontScale(), ThemeUtils.getApplicationTheme(this));
        }
    }

    /**
     * Parse a markdown text
     *
     * @param text     the text to parse
     * @param listener the result listener
     */
    public static void markdownToHtml(final String text, final VectorMarkdownParser.IVectorMarkdownParserListener listener) {
        if (null != getInstance().mMarkdownParser) {
            getInstance().mMarkdownParser.markdownToHtml(text, listener);
        } else {
            (new Handler(Looper.getMainLooper())).post(new Runnable() {
                @Override
                public void run() {
                    // GA issue
                    listener.onMarkdownParsed(text, null);
                }
            });
        }
    }

    /**
     * Suspend background threads.
     */
    private void suspendApp() {
        GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(VectorApp.this).getSharedGCMRegistrationManager();

        // suspend the events thread if the client uses GCM
        if (!gcmRegistrationManager.isBackgroundSyncAllowed() || (gcmRegistrationManager.useGCM() && gcmRegistrationManager.hasRegistrationToken())) {
            Log.d(LOG_TAG, "suspendApp ; pause the event stream");
            CommonActivityUtils.pauseEventStream(VectorApp.this);
        } else {
            Log.d(LOG_TAG, "suspendApp ; the event stream is not paused because GCM is disabled.");
        }

        // the sessions are not anymore seen as "online"
        ArrayList<MXSession> sessions = Matrix.getInstance(this).getSessions();

        for (MXSession session : sessions) {
            if (session.isAlive()) {
                session.setIsOnline(false);
                session.setSyncDelay(gcmRegistrationManager.isBackgroundSyncAllowed() ? gcmRegistrationManager.getBackgroundSyncDelay() : 0);
                session.setSyncTimeout(gcmRegistrationManager.getBackgroundSyncTimeOut());

                // remove older medias
                if ((System.currentTimeMillis() - mLastMediasCheck) < (24 * 60 * 60 * 1000)) {
                    mLastMediasCheck = System.currentTimeMillis();
                    session.removeMediasBefore(VectorApp.this, PreferencesManager.getMinMediasLastAccessTime(getApplicationContext()));
                }

                if (session.getDataHandler().areLeftRoomsSynced()) {
                    session.getDataHandler().releaseLeftRooms();
                }
            }
        }

        clearSyncingSessions();

        PIDsRetriever.getInstance().onAppBackgrounded();

        MyPresenceManager.advertiseAllUnavailable();
    }

    /**
     * Test if application is put in background.
     * i.e wait 2s before assuming that the application is put in background.
     */
    private void startActivityTransitionTimer() {
        Log.d(LOG_TAG, "## startActivityTransitionTimer()");

        try {
            mActivityTransitionTimer = new Timer();
            mActivityTransitionTimerTask = new TimerTask() {
                @Override
                public void run() {
                    // reported by GA
                    try {
                        if (mActivityTransitionTimerTask != null) {
                            mActivityTransitionTimerTask.cancel();
                            mActivityTransitionTimerTask = null;
                        }

                        if (mActivityTransitionTimer != null) {
                            mActivityTransitionTimer.cancel();
                            mActivityTransitionTimer = null;
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## startActivityTransitionTimer() failed " + e.getMessage());
                    }

                    if (null != mCurrentActivity) {
                        Log.e(LOG_TAG, "## startActivityTransitionTimer() : the timer expires but there is an active activity.");
                    } else {
                        VectorApp.this.mIsInBackground = true;
                        mIsCallingInBackground = (null != mCallsManager.getActiveCall());

                        // if there is a pending call
                        // the application is not suspended
                        if (!mIsCallingInBackground) {
                            Log.d(LOG_TAG, "Suspend the application because there was no resumed activity within " + (MAX_ACTIVITY_TRANSITION_TIME_MS / 1000) + " seconds");
                            CommonActivityUtils.displayMemoryInformation(null, " app suspended");
                            suspendApp();
                        } else {
                            Log.d(LOG_TAG, "App not suspended due to call in progress");
                        }
                    }
                }
            };

            mActivityTransitionTimer.schedule(mActivityTransitionTimerTask, MAX_ACTIVITY_TRANSITION_TIME_MS);
        } catch (Throwable throwable) {
            Log.e(LOG_TAG, "## startActivityTransitionTimer() : failed to start the timer " + throwable.getMessage());

            if (null != mActivityTransitionTimer) {
                mActivityTransitionTimer.cancel();
                mActivityTransitionTimer = null;
            }
        }
    }

    /**
     * List the used permissions statuses.
     */
    private void listPermissionStatuses() {
        if (Build.VERSION.SDK_INT >= 23) {
            final List<String> permissions = Arrays.asList(
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_CONTACTS);

            Log.d(LOG_TAG, "## listPermissionStatuses() : list the permissions used by the app");
            for (String permission : permissions) {
                Log.d(LOG_TAG, "Status of [" + permission + "] : " +
                        ((PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(instance, permission)) ? "PERMISSION_GRANTED" : "PERMISSION_DENIED"));
            }
        }
    }


    /**
     * Stop the background detection.
     */
    private void stopActivityTransitionTimer() {
        Log.d(LOG_TAG, "## stopActivityTransitionTimer()");

        if (mActivityTransitionTimerTask != null) {
            mActivityTransitionTimerTask.cancel();
            mActivityTransitionTimerTask = null;
        }

        if (mActivityTransitionTimer != null) {
            mActivityTransitionTimer.cancel();
            mActivityTransitionTimer = null;
        }

        if (isAppInBackground() && !mIsCallingInBackground) {

            // the event stream service has been killed
            if (null == EventStreamService.getInstance()) {
                CommonActivityUtils.startEventStreamService(VectorApp.this);
            } else {
                CommonActivityUtils.resumeEventStream(VectorApp.this);

                // try to perform a GCM registration if it failed
                // or if the GCM server generated a new push key
                GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(this).getSharedGCMRegistrationManager();

                if (null != gcmRegistrationManager) {
                    gcmRegistrationManager.checkRegistrations();
                }
            }

            // get the contact update at application launch
            ContactsManager.getInstance().clearSnapshot();
            ContactsManager.getInstance().refreshLocalContactsSnapshot();

            List<MXSession> sessions = Matrix.getInstance(this).getSessions();
            for (MXSession session : sessions) {
                session.getMyUser().refreshUserInfos(null);
                session.setIsOnline(true);
                session.setSyncDelay(0);
                session.setSyncTimeout(0);
                addSyncingSession(session);
            }

            mCallsManager.checkDeadCalls();
        }

        MyPresenceManager.advertiseAllOnline();

        mIsCallingInBackground = false;
        mIsInBackground = false;
    }

    /**
     * Update the current active activity.
     * It manages the application background / foreground when it is required.
     *
     * @param activity the current activity, null if there is no more one.
     */
    private void setCurrentActivity(Activity activity) {
        Log.d(LOG_TAG, "## setCurrentActivity() : from " + mCurrentActivity + " to " + activity);

        if (VectorApp.isAppInBackground() && (null != activity)) {
            Matrix matrixInstance = Matrix.getInstance(activity.getApplicationContext());

            // sanity check
            if (null != matrixInstance) {
                matrixInstance.refreshPushRules();
            }

            Log.d(LOG_TAG, "The application is resumed");
            // display the memory usage when the application is put iun foreground..
            CommonActivityUtils.displayMemoryInformation(activity, " app resumed with " + activity);
        }

        // wait 2s to check that the application is put in background
        if (null != getInstance()) {
            if (null == activity) {
                getInstance().startActivityTransitionTimer();
            } else {
                getInstance().stopActivityTransitionTimer();
            }
        } else {
            Log.e(LOG_TAG, "The application is resumed but there is no active instance");
        }

        mCurrentActivity = activity;

        if (null != mCurrentActivity) {
            KeyRequestHandler.getSharedInstance().processNextRequest();
        }
    }

    /**
     * @return the current active activity
     */
    public static Activity getCurrentActivity() {
        return mCurrentActivity;
    }

    /**
     * Return true if the application is in background.
     */
    public static boolean isAppInBackground() {
        return (null == mCurrentActivity) && (null != getInstance()) && getInstance().mIsInBackground;
    }

    /**
     * Restart an activity to manage language update
     *
     * @param activity the activity to restart
     */
    private void restartActivity(Activity activity) {
        // avoid restarting activities when it is not required
        // some of them has no text
        if (!(activity instanceof VectorMediasPickerActivity)
                && !(activity instanceof VectorCallViewActivity)
                && !(activity instanceof JitsiCallActivity)
                && !(activity instanceof WidgetActivity)) {
            activity.startActivity(activity.getIntent());
            activity.finish();
        }
    }

    //==============================================================================================================
    // Calls management.
    //==============================================================================================================

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

    //==============================================================================================================
    // cert management : store the active activities.
    //==============================================================================================================

    private final EventEmitter<Activity> mOnActivityDestroyedListener = new EventEmitter<>();

    /**
     * @return the EventEmitter list.
     */
    public EventEmitter<Activity> getOnActivityDestroyedListener() {
        return mOnActivityDestroyedListener;
    }

    //==============================================================================================================
    // Media pickers : image backup
    //==============================================================================================================

    private static Bitmap mSavedPickerImagePreview = null;

    /**
     * The image taken from the medias picker is stored in a static variable because
     * saving it would take too much time.
     *
     * @return the saved image from medias picker
     */
    public static Bitmap getSavedPickerImagePreview() {
        return mSavedPickerImagePreview;
    }

    /**
     * Save the image taken in the medias picker
     *
     * @param aSavedCameraImagePreview the bitmap.
     */
    public static void setSavedCameraImagePreview(Bitmap aSavedCameraImagePreview) {
        if (aSavedCameraImagePreview != mSavedPickerImagePreview) {
            // force to release memory
            // reported by GA
            // it seems that the medias picker might be refreshed
            // while leaving the activity
            // recycle the bitmap trigger a rendering issue
            // Canvas: trying to use a recycled bitmap...

            /*if (null != mSavedPickerImagePreview) {
                mSavedPickerImagePreview.recycle();
                mSavedPickerImagePreview = null;
                System.gc();
            }*/


            mSavedPickerImagePreview = aSavedCameraImagePreview;
        }
    }

    //==============================================================================================================
    // Syncing mxSessions
    //==============================================================================================================

    /**
     * syncing sessions
     */
    private static final HashSet<MXSession> mSyncingSessions = new HashSet<>();

    /**
     * Add a session in the syncing sessions list
     *
     * @param session the session
     */
    public static void addSyncingSession(MXSession session) {
        synchronized (mSyncingSessions) {
            mSyncingSessions.add(session);
        }
    }

    /**
     * Remove a session in the syncing sessions list
     *
     * @param session the session
     */
    public static void removeSyncingSession(MXSession session) {
        if (null != session) {
            synchronized (mSyncingSessions) {
                mSyncingSessions.remove(session);
            }
        }
    }

    /**
     * Clear syncing sessions list
     */
    public static void clearSyncingSessions() {
        synchronized (mSyncingSessions) {
            mSyncingSessions.clear();
        }
    }

    /**
     * Tell if a session is syncing
     *
     * @param session the session
     * @return true if the session is syncing
     */
    public static boolean isSessionSyncing(MXSession session) {
        boolean isSyncing = false;

        if (null != session) {
            synchronized (mSyncingSessions) {
                isSyncing = mSyncingSessions.contains(session);
            }
        }

        return isSyncing;
    }

    //==============================================================================================================
    // GA management
    //==============================================================================================================
    /**
     * GA tags
     */
    public static final String GOOGLE_ANALYTICS_STATS_CATEGORY = "stats";

    public static final String GOOGLE_ANALYTICS_STATS_ROOMS_ACTION = "rooms";
    public static final String GOOGLE_ANALYTICS_STARTUP_STORE_PRELOAD_ACTION = "storePreload";
    public static final String GOOGLE_ANALYTICS_STARTUP_MOUNT_DATA_ACTION = "mountData";
    public static final String GOOGLE_ANALYTICS_STARTUP_LAUNCH_SCREEN_ACTION = "launchScreen";
    public static final String GOOGLE_ANALYTICS_STARTUP_CONTACTS_ACTION = "Contacts";

    /**
     * Send a GA stats
     *
     * @param context  the context
     * @param category the category
     * @param action   the action
     * @param label    the label
     * @param value    the value
     */
    public static void sendGAStats(Context context, String category, String action, String label, long value) {
        GAHelper.sendGAStats(context, category, action, label, value);
    }

    /**
     * An uncaught exception has been triggered
     *
     * @param threadName the thread name
     * @param throwable  the throwable
     * @return the exception description
     */
    public static String uncaughtException(String threadName, Throwable throwable) {
        StringBuilder b = new StringBuilder();
        String appName = Matrix.getApplicationName();

        b.append(appName + " Build : " + VectorApp.VERSION_BUILD + "\n");
        b.append(appName + " Version : " + VectorApp.VECTOR_VERSION_STRING + "\n");
        b.append("SDK Version : " + VectorApp.SDK_VERSION_STRING + "\n");
        b.append("Phone : " + Build.MODEL.trim() + " (" + Build.VERSION.INCREMENTAL + " " + Build.VERSION.RELEASE + " " + Build.VERSION.CODENAME + ")\n");

        b.append("Memory statuses \n");

        long freeSize = 0L;
        long totalSize = 0L;
        long usedSize = -1L;
        try {
            Runtime info = Runtime.getRuntime();
            freeSize = info.freeMemory();
            totalSize = info.totalMemory();
            usedSize = totalSize - freeSize;
        } catch (Exception e) {
            e.printStackTrace();
        }
        b.append("usedSize   " + (usedSize / 1048576L) + " MB\n");
        b.append("freeSize   " + (freeSize / 1048576L) + " MB\n");
        b.append("totalSize   " + (totalSize / 1048576L) + " MB\n");

        b.append("Thread: ");
        b.append(threadName);

        Activity a = VectorApp.getCurrentActivity();
        if (a != null) {
            b.append(", Activity:");
            b.append(a.getLocalClassName());
        }

        b.append(", Exception: ");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        b.append(sw.getBuffer().toString());
        Log.e("FATAL EXCEPTION", b.toString());

        String bugDescription = b.toString();

        if (null != VectorApp.getInstance()) {
            VectorApp.getInstance().setAppCrashed(bugDescription);
        }

        return bugDescription;
    }

    /**
     * Warn that the application crashed
     *
     * @param description the crash description
     */
    private void setAppCrashed(String description) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREFS_CRASH_KEY, true);
        editor.commit();

        BugReporter.saveCrashReport(this, description);
    }

    /**
     * Tells if the application crashed
     *
     * @return true if the application crashed
     */
    public boolean didAppCrash() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance());
        return preferences.getBoolean(PREFS_CRASH_KEY, false);
    }


    /**
     * Clear the crash status
     */
    public void clearAppCrashStatus() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance());
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(PREFS_CRASH_KEY);
        editor.commit();
    }

    //==============================================================================================================
    // Locale management
    //==============================================================================================================

    // the supported application languages
    private static final Set<Locale> mApplicationLocales = new HashSet<>();

    private static final String APPLICATION_LOCALE_COUNTRY_KEY = "APPLICATION_LOCALE_COUNTRY_KEY";
    private static final String APPLICATION_LOCALE_VARIANT_KEY = "APPLICATION_LOCALE_VARIANT_KEY";
    private static final String APPLICATION_LOCALE_LANGUAGE_KEY = "APPLICATION_LOCALE_LANGUAGE_KEY";
    private static final String APPLICATION_FONT_SCALE_KEY = "APPLICATION_FONT_SCALE_KEY";

    public static final String FONT_SCALE_TINY = "FONT_SCALE_TINY";
    public static final String FONT_SCALE_SMALL = "FONT_SCALE_SMALL";
    public static final String FONT_SCALE_NORMAL = "FONT_SCALE_NORMAL";
    public static final String FONT_SCALE_LARGE = "FONT_SCALE_LARGE";
    public static final String FONT_SCALE_LARGER = "FONT_SCALE_LARGER";
    public static final String FONT_SCALE_LARGEST = "FONT_SCALE_LARGEST";
    public static final String FONT_SCALE_HUGE = "FONT_SCALE_HUGE";

    private static final Locale mApplicationDefaultLanguage = new Locale("en", "UK");

    private static final Map<Float, String> mPrefKeyByFontScale = new LinkedHashMap<Float, String>() {{
        put(0.70f, FONT_SCALE_TINY);
        put(0.85f, FONT_SCALE_SMALL);
        put(1.00f, FONT_SCALE_NORMAL);
        put(1.15f, FONT_SCALE_LARGE);
        put(1.30f, FONT_SCALE_LARGER);
        put(1.45f, FONT_SCALE_LARGEST);
        put(1.60f, FONT_SCALE_HUGE);
    }};

    private static final Map<String, Integer> mFontTextScaleIdByPrefKey = new LinkedHashMap<String, Integer>() {{
        put(FONT_SCALE_TINY, R.string.tiny);
        put(FONT_SCALE_SMALL, R.string.small);
        put(FONT_SCALE_NORMAL, R.string.normal);
        put(FONT_SCALE_LARGE, R.string.large);
        put(FONT_SCALE_LARGER, R.string.larger);
        put(FONT_SCALE_LARGEST, R.string.largest);
        put(FONT_SCALE_HUGE, R.string.huge);
    }};

    /**
     * Init the application locale from the saved one
     */
    private static void initApplicationLocale() {
        Context context = VectorApp.getInstance();
        Locale locale = getApplicationLocale();
        float fontScale = getFontScaleValue();
        String theme = ThemeUtils.getApplicationTheme(context);

        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.locale = locale;
        config.fontScale = fontScale;
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());

        // init the theme
        ThemeUtils.setApplicationTheme(context, theme);

        // init the known locales in background
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                getApplicationLocales(VectorApp.getInstance());
                return null;
            }
        };

        // should never crash
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Get the font scale
     *
     * @return the font scale
     */
    public static String getFontScale() {
        Context context = VectorApp.getInstance();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String scalePreferenceKey;

        if (!preferences.contains(APPLICATION_FONT_SCALE_KEY)) {
            float fontScale = context.getResources().getConfiguration().fontScale;

            scalePreferenceKey = FONT_SCALE_NORMAL;

            if (mPrefKeyByFontScale.containsKey(fontScale)) {
                scalePreferenceKey = mPrefKeyByFontScale.get(fontScale);
            }

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(APPLICATION_FONT_SCALE_KEY, scalePreferenceKey);
            editor.commit();
        } else {
            scalePreferenceKey = preferences.getString(APPLICATION_FONT_SCALE_KEY, FONT_SCALE_NORMAL);
        }

        return scalePreferenceKey;
    }

    /**
     * Provides the font scale value
     *
     * @return the font scale
     */
    private static float getFontScaleValue() {
        String fontScale = getFontScale();

        if (mPrefKeyByFontScale.containsValue(fontScale)) {
            for (Map.Entry<Float, String> entry : mPrefKeyByFontScale.entrySet()) {
                if (TextUtils.equals(entry.getValue(), fontScale)) {
                    return entry.getKey();
                }
            }
        }

        return 1.0f;
    }

    /**
     * Provides the font scale description
     *
     * @return the font description
     */
    public static String getFontScaleDescription() {
        Context context = VectorApp.getInstance();
        String fontScale = getFontScale();

        if (mFontTextScaleIdByPrefKey.containsKey(fontScale)) {
            return context.getString(mFontTextScaleIdByPrefKey.get(fontScale));
        }

        return context.getString(R.string.normal);
    }

    /**
     * Update the font size from the locale description.
     *
     * @param fontScaleDescription the font scale description
     */
    public static void updateFontScale(String fontScaleDescription) {
        Context context = VectorApp.getInstance();
        for (Map.Entry<String, Integer> entry : mFontTextScaleIdByPrefKey.entrySet()) {
            if (TextUtils.equals(context.getString(entry.getValue()), fontScaleDescription)) {
                saveFontScale(entry.getKey());
            }
        }

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.fontScale = getFontScaleValue();
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
    }

    /**
     * Provides the current application locale
     *
     * @return the application locale
     */
    public static Locale getApplicationLocale() {
        Context context = VectorApp.getInstance();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Locale locale;

        if (!preferences.contains(APPLICATION_LOCALE_LANGUAGE_KEY)) {
            locale = Locale.getDefault();

            // detect if the default language is used
            String defaultStringValue = getString(context, mApplicationDefaultLanguage, R.string.resouces_country);
            if (TextUtils.equals(defaultStringValue, getString(context, locale, R.string.resouces_country))) {
                locale = mApplicationDefaultLanguage;
            }

            saveApplicationLocale(locale);
        } else {
            locale = new Locale(preferences.getString(APPLICATION_LOCALE_LANGUAGE_KEY, ""),
                    preferences.getString(APPLICATION_LOCALE_COUNTRY_KEY, ""),
                    preferences.getString(APPLICATION_LOCALE_VARIANT_KEY, "")
            );
        }

        return locale;
    }

    /**
     * Provides the device locale
     *
     * @return the device locale
     */
    public static Locale getDeviceLocale() {
        Context context = VectorApp.getInstance();
        Locale locale = getApplicationLocale();

        try {
            PackageManager packageManager = context.getPackageManager();
            Resources resources = packageManager.getResourcesForApplication("android");
            locale = resources.getConfiguration().locale;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getDeviceLocale() failed " + e.getMessage());
        }

        return locale;
    }

    /**
     * Save the new application locale.
     */
    private static void saveApplicationLocale(Locale locale) {
        Context context = VectorApp.getInstance();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        SharedPreferences.Editor editor = preferences.edit();

        String language = locale.getLanguage();
        if (!TextUtils.isEmpty(language)) {
            editor.putString(APPLICATION_LOCALE_LANGUAGE_KEY, language);
        } else {
            editor.remove(APPLICATION_LOCALE_LANGUAGE_KEY);
        }

        String country = locale.getCountry();
        if (!TextUtils.isEmpty(country)) {
            editor.putString(APPLICATION_LOCALE_COUNTRY_KEY, country);
        } else {
            editor.remove(APPLICATION_LOCALE_COUNTRY_KEY);
        }

        String variant = locale.getVariant();
        if (!TextUtils.isEmpty(variant)) {
            editor.putString(APPLICATION_LOCALE_VARIANT_KEY, variant);
        } else {
            editor.remove(APPLICATION_LOCALE_VARIANT_KEY);
        }

        editor.commit();
    }

    /**
     * Save the new font scale
     *
     * @param textScale the text scale
     */
    private static void saveFontScale(String textScale) {
        Context context = VectorApp.getInstance();

        if (!TextUtils.isEmpty(textScale)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(APPLICATION_FONT_SCALE_KEY, textScale);
            editor.commit();
        }
    }

    /**
     * Update the application locale
     *
     * @param locale
     */
    public static void updateApplicationLocale(Locale locale) {
        updateApplicationSettings(locale, getFontScale(), ThemeUtils.getApplicationTheme(VectorApp.getInstance()));
    }

    /**
     * Update the application theme
     *
     * @param theme the new theme
     */
    public static void updateApplicationTheme(String theme) {
        ThemeUtils.setApplicationTheme(VectorApp.getInstance(), theme);
        updateApplicationSettings(getApplicationLocale(), getFontScale(), ThemeUtils.getApplicationTheme(VectorApp.getInstance()));
    }

    /**
     * Update the application locale.
     *
     * @param locale the locale
     * @param theme  the new theme
     */
    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    private static void updateApplicationSettings(Locale locale, String textSize, String theme) {
        Context context = VectorApp.getInstance();

        saveApplicationLocale(locale);
        saveFontScale(textSize);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.locale = locale;
        config.fontScale = getFontScaleValue();
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());

        ThemeUtils.setApplicationTheme(context, theme);
        PhoneNumberUtils.onLocaleUpdate();
    }

    /**
     * Compute a localised context
     * @param context the context
     * @return the localised context
     */
    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    public static Context getLocalisedContext(Context context) {
        try {
            Resources resources = context.getResources();
            Locale locale = getApplicationLocale();
            Configuration configuration = resources.getConfiguration();
            configuration.fontScale = getFontScaleValue();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.setLocale(locale);
                configuration.setLayoutDirection(locale);
                return context.createConfigurationContext(configuration);
            } else {
                configuration.locale = locale;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    configuration.setLayoutDirection(locale);
                }
                resources.updateConfiguration(configuration, resources.getDisplayMetrics());
                return context;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getLocalisedContext() failed : " + e.getMessage());
        }

        return context;
    }

    /**
     * Get String from a locale
     *
     * @param context    the context
     * @param locale     the locale
     * @param resourceId the string resource id
     * @return the localized string
     */
    private static String getString(Context context, Locale locale, int resourceId) {
        String result;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(locale);
            try {
                result = context.createConfigurationContext(config).getText(resourceId).toString();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getString() failed : " + e.getMessage());
                // use the default one
                result = context.getString(resourceId);
            }
        } else {
            Resources resources = context.getResources();
            Configuration conf = resources.getConfiguration();
            Locale savedLocale = conf.locale;
            conf.locale = locale;
            resources.updateConfiguration(conf, null);

            // retrieve resources from desired locale
            result = resources.getString(resourceId);

            // restore original locale
            conf.locale = savedLocale;
            resources.updateConfiguration(conf, null);
        }

        return result;
    }

    /**
     * Provides the supported application locales list
     *
     * @param context the context
     * @return the supported application locales list
     */
    public static List<Locale> getApplicationLocales(Context context) {
        if (mApplicationLocales.isEmpty()) {

            Set<Pair<String, String>> knownLocalesSet = new HashSet<>();

            try {
                final Locale[] availableLocales = Locale.getAvailableLocales();

                for (Locale locale : availableLocales) {
                    knownLocalesSet.add(new Pair<>(getString(context, locale, R.string.resouces_language), getString(context, locale, R.string.resouces_country)));
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getApplicationLocales() : failed " + e.getMessage());
                knownLocalesSet.add(new Pair<>(context.getString(R.string.resouces_language), context.getString(R.string.resouces_country)));
            }

            for (Pair<String, String> knownLocale : knownLocalesSet) {
                mApplicationLocales.add(new Locale(knownLocale.first, knownLocale.second));
            }
        }

        List<Locale> sortedLocalesList = new ArrayList<>(mApplicationLocales);

        // sort by human display names
        Collections.sort(sortedLocalesList, new Comparator<Locale>() {
            @Override
            public int compare(Locale lhs, Locale rhs) {
                return localeToLocalisedString(lhs).compareTo(localeToLocalisedString(rhs));
            }
        });

        return sortedLocalesList;
    }

    /**
     * Convert a locale to a string
     *
     * @param locale the locale to convert
     * @return the string
     */
    public static String localeToLocalisedString(Locale locale) {
        String res = locale.getDisplayLanguage(locale);

        if (!TextUtils.isEmpty(locale.getDisplayCountry(locale))) {
            res += " (" + locale.getDisplayCountry(locale) + ")";
        }

        return res;
    }
}
