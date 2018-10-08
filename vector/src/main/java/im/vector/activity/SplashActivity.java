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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import im.vector.ErrorListener;
import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.analytics.TrackingEvent;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.services.EventStreamService;
import im.vector.util.PreferencesManager;
import kotlin.Triple;

/**
 * SplashActivity displays a splash while loading and inittializing the client.
 */
public class SplashActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = SplashActivity.class.getSimpleName();

    public static final String EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID";
    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";

    private Map<MXSession, IMXEventListener> mListeners = new HashMap<>();
    private Map<MXSession, IMXEventListener> mDoneListeners = new HashMap<>();

    private final long mLaunchTime = System.currentTimeMillis();

    private static final String NEED_TO_CLEAR_CACHE_BEFORE_81200 = "NEED_TO_CLEAR_CACHE_BEFORE_81200";

    /* ==========================================================================================
     * UI
     * ========================================================================================== */

    @BindView(R.id.animated_logo_image_view)
    ImageView animatedLogo;

    /**
     * @return true if a store is corrupted.
     */
    private boolean hasCorruptedStore() {
        boolean hasCorruptedStore = false;
        List<MXSession> sessions = Matrix.getMXSessions(this);

        for (MXSession session : sessions) {
            if (session.isAlive()) {
                hasCorruptedStore |= session.getDataHandler().getStore().isCorrupted();
            }
        }
        return hasCorruptedStore;
    }

    /**
     * Close the splash screen if the stores are fully loaded.
     */
    private void onFinish() {
        Log.e(LOG_TAG, "##onFinish() : start VectorHomeActivity");
        final long finishTime = System.currentTimeMillis();
        final long duration = finishTime - mLaunchTime;
        final TrackingEvent event = new TrackingEvent.LaunchScreen(duration);
        VectorApp.getInstance().getAnalytics().trackEvent(event);

        if (!hasCorruptedStore()) {
            // Go to the home page
            Intent intent = new Intent(SplashActivity.this, VectorHomeActivity.class);

            Bundle receivedBundle = getIntent().getExtras();

            if (null != receivedBundle) {
                intent.putExtras(receivedBundle);
            }

            // display a spinner while managing the universal link
            if (intent.hasExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
                intent.putExtra(VectorHomeActivity.EXTRA_WAITING_VIEW_STATUS, VectorHomeActivity.WAITING_VIEW_START);
            }

            // launch from a shared files menu
            if (getIntent().hasExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS)) {
                intent.putExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS, getIntent().getParcelableExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS));
                getIntent().removeExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS);
            }

            if (getIntent().hasExtra(EXTRA_ROOM_ID) && getIntent().hasExtra(EXTRA_MATRIX_ID)) {
                Map<String, Object> params = new HashMap<>();

                params.put(VectorRoomActivity.EXTRA_MATRIX_ID, getIntent().getStringExtra(EXTRA_MATRIX_ID));
                params.put(VectorRoomActivity.EXTRA_ROOM_ID, getIntent().getStringExtra(EXTRA_ROOM_ID));
                intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, (HashMap) params);
            }

            startActivity(intent);
            finish();
        } else {
            CommonActivityUtils.logout(this);
        }
    }

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
        Collection<MXSession> sessions = Matrix.getInstance(getApplicationContext()).getSessions();

        if (sessions == null) {
            Log.e(LOG_TAG, "onCreate no Sessions");
            finish();
            return;
        }

        // Check if store is corrupted, due to change of type of some maps from HashMap to Map in Serialized objects
        // Only on Android 7.1+
        // Only if previous versionCode of the installation is < 81200
        // Only once
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
                && PreferenceManager.getDefaultSharedPreferences(this).getInt(PreferencesManager.VERSION_BUILD, 0) < 81200
                && PreferenceManager.getDefaultSharedPreferences(this).getBoolean(NEED_TO_CLEAR_CACHE_BEFORE_81200, true)) {
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putBoolean(NEED_TO_CLEAR_CACHE_BEFORE_81200, false)
                    .apply();

            // Force a clear cache
            Matrix.getInstance(this).reloadSessions(this);
            return;
        }

        // Load the Gif logo
        Glide.with(this)
                .load(R.drawable.riot_splash)
                .into(animatedLogo);

        List<String> matrixIds = new ArrayList<>();

        for (final MXSession session : sessions) {
            final MXSession fSession = session;

            final IMXEventListener eventListener = new MXEventListener() {
                private void onReady() {
                    boolean isAlreadyDone;

                    synchronized (LOG_TAG) {
                        isAlreadyDone = mDoneListeners.containsKey(fSession);
                    }

                    if (!isAlreadyDone) {
                        synchronized (LOG_TAG) {
                            boolean noMoreListener;

                            Log.e(LOG_TAG, "Session " + fSession.getCredentials().userId + " is initialized");

                            mDoneListeners.put(fSession, mListeners.get(fSession));
                            // do not remove the listeners here
                            // it crashes the application because of the upper loop
                            //fSession.getDataHandler().removeListener(mListeners.get(fSession));
                            // remove from the pending list

                            mListeners.remove(fSession);
                            noMoreListener = (mListeners.size() == 0);

                            if (noMoreListener) {
                                VectorApp.addSyncingSession(session);
                                onFinish();
                            }
                        }
                    }
                }

                // should be called if the application was already initialized
                @Override
                public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
                    onReady();
                }

                // first application launched
                @Override
                public void onInitialSyncComplete(String toToken) {
                    onReady();
                }
            };

            if (!fSession.getDataHandler().isInitialSyncComplete()) {
                session.getDataHandler().getStore().open();

                mListeners.put(fSession, eventListener);
                fSession.getDataHandler().addListener(eventListener);

                // Set the main error listener
                fSession.setFailureCallback(new ErrorListener(fSession, this));

                // session to activate
                matrixIds.add(session.getCredentials().userId);
            }
        }

        // when the events stream has been disconnected by the user
        // they must be awoken even if they are initialized
        if (Matrix.getInstance(this).mHasBeenDisconnected) {
            matrixIds = new ArrayList<>();

            for (MXSession session : sessions) {
                matrixIds.add(session.getCredentials().userId);
            }

            Matrix.getInstance(this).mHasBeenDisconnected = false;
        }

        if (EventStreamService.getInstance() == null) {
            // Start the event stream service
            Intent intent = new Intent(this, EventStreamService.class);
            intent.putExtra(EventStreamService.EXTRA_MATRIX_IDS, matrixIds.toArray(new String[matrixIds.size()]));
            intent.putExtra(EventStreamService.EXTRA_STREAM_ACTION, EventStreamService.StreamAction.START.ordinal());
            startService(intent);
        } else {
            EventStreamService.getInstance().startAccounts(matrixIds);
        }

        // trigger the GCM registration if required
        GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(getApplicationContext()).getSharedGCMRegistrationManager();

        if (!gcmRegistrationManager.isGcmRegistered()) {
            gcmRegistrationManager.checkRegistrations();
        } else {
            gcmRegistrationManager.forceSessionsRegistration(null);
        }

        boolean noUpdate;

        synchronized (LOG_TAG) {
            noUpdate = (mListeners.size() == 0);
        }

        // nothing to do ?
        // just dismiss the activity
        if (noUpdate) {
            // do not launch an activity if there was nothing new.
            Log.e(LOG_TAG, "nothing to do");
            onFinish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Collection<MXSession> sessions = mDoneListeners.keySet();

        for (MXSession session : sessions) {
            if (session.isAlive()) {
                session.getDataHandler().removeListener(mDoneListeners.get(session));
                session.setFailureCallback(null);
            }
        }
    }
}
