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
package im.vector.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import im.vector.ErrorListener;
import im.vector.Matrix;
import im.vector.R;
import im.vector.ga.Analytics;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.services.EventStreamService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * SplashActivity displays a splash while loading and inittializing the client.
 */
public class SplashActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "SplashActivity";

    private Collection<MXSession> mSessions;
    private GcmRegistrationManager mGcmRegistrationManager;

    private boolean mInitialSyncComplete = false;
    private boolean mPusherRegistrationComplete = false;

    private HashMap<MXSession, IMXEventListener> mListeners;
    private HashMap<MXSession, IMXEventListener> mDoneListeners;

    /**
     * @return true if a store is corrupted.
     */
    private boolean hasCorruptedStore() {
        boolean hasCorruptedStore = false;
        ArrayList<MXSession> sessions = Matrix.getMXSessions(this);

        for(MXSession session : sessions) {
            if (session.isAlive()) {
                hasCorruptedStore |= session.getDataHandler().getStore().isCorrupted();
            }
        }
        return hasCorruptedStore;
    }

    /**
     * Close the splash screen if the stores are fully loaded.
     */
    private void finishIfReady() {
        Log.e(LOG_TAG, "finishIfReady " + mInitialSyncComplete + " " + mPusherRegistrationComplete);

        if (mInitialSyncComplete && mPusherRegistrationComplete) {
            Log.e(LOG_TAG, "finishIfRead start VectorHomeActivity");

            if (!hasCorruptedStore()) {
                // Go to the home page
                Intent intent = new Intent(SplashActivity.this, VectorHomeActivity.class);

                Bundle receivedBundle = getIntent().getExtras();

                if(null != receivedBundle) {
                    intent.putExtras(receivedBundle);
                }

                // display a spinner while managing the universal link
                if (intent.hasExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
                    intent.putExtra(VectorHomeActivity.EXTRA_WAITING_VIEW_STATUS, VectorHomeActivity.WAITING_VIEW_START);
                }

                // launch from a shared files menu
                if (getIntent().hasExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS)) {
                    intent.putExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS, getIntent().getParcelableExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS));
                }

                startActivity(intent);

                SplashActivity.this.finish();
            } else {
                CommonActivityUtils.logout(this);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.e(LOG_TAG, "onCreate");

        setContentView(R.layout.vector_activity_splash);

        mSessions =  Matrix.getInstance(getApplicationContext()).getSessions();

        if (mSessions == null) {
            Log.e(LOG_TAG, "onCreate no Sessions");
            finish();
            return;
        }

        mListeners = new HashMap<MXSession, IMXEventListener>();
        mDoneListeners = new HashMap<MXSession, IMXEventListener>();

        ArrayList<String> matrixIds = new ArrayList<String>();

        final long startTime = System.currentTimeMillis();

        for(MXSession session : mSessions) {
            final MXSession fSession = session;
            session.getDataHandler().getStore().open();

            final IMXEventListener eventListener = new MXEventListener() {
                @Override
                public void onInitialSyncComplete() {
                    super.onInitialSyncComplete();
                    boolean noMoreListener;

                    Log.e(LOG_TAG, "Session " + fSession.getCredentials().userId + " is initialized");

                    synchronized(LOG_TAG) {
                        mDoneListeners.put(fSession, mListeners.get(fSession));
                        // do not remove the listeners here
                        // it crashes the application because of the upper loop
                        //fSession.getDataHandler().removeListener(mListeners.get(fSession));
                        // remove from the pendings list

                        mListeners.remove(fSession);
                        noMoreListener = mInitialSyncComplete = (mListeners.size() == 0);
                    }

                    Analytics.sendEvent("Account", "Loading", fSession.getDataHandler().getStore().getRooms().size() + " rooms", System.currentTimeMillis() - startTime);

                    if (noMoreListener) {
                        finishIfReady();
                    }
                }
            };

            if (!fSession.getDataHandler().isInitialSyncComplete()) {
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
            matrixIds = new ArrayList<String>();

            for(MXSession session : mSessions) {
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

        mGcmRegistrationManager = Matrix.getInstance(getApplicationContext())
                .getSharedGcmRegistrationManager();
        mPusherRegistrationComplete = mGcmRegistrationManager.isGCMRegistred();

        if (!mPusherRegistrationComplete) {
            mGcmRegistrationManager.registerPusher(getApplicationContext(), new GcmRegistrationManager.GcmRegistrationIdListener() {
                @Override
                public void onPusherRegistered() {
                    Log.d(LOG_TAG, "The GCM registration is done");

                    // vector always uses GCM.
                    // there is no way to enable / disable it in the application settings

                    if (!Matrix.getInstance(SplashActivity.this).getSharedGcmRegistrationManager().useGCM()) {
                        Matrix.getInstance(SplashActivity.this).getSharedGcmRegistrationManager().setUseGCM(true);

                        SplashActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                CommonActivityUtils.onGcmUpdate(SplashActivity.this);
                            }
                        });
                    }

                    mPusherRegistrationComplete = true;
                    finishIfReady();
                }

                @Override
                public void onPusherRegistrationFailed() {
                    Log.d(LOG_TAG, "The GCM registration failed");

                    // fallback to the events service
                    Matrix.getInstance(SplashActivity.this).getSharedGcmRegistrationManager().setUseGCM(false);

                    SplashActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            CommonActivityUtils.onGcmUpdate(SplashActivity.this);
                        }
                    });

                    // can register it ignore
                    onPusherRegistered();
                }
            });
        } else if (mGcmRegistrationManager.useGCM()) {
            mGcmRegistrationManager.reregisterSessions(SplashActivity.this, null);
        }

        boolean noUpdate;

        synchronized(LOG_TAG) {
            mInitialSyncComplete = (mListeners.size() == 0);
            noUpdate = mInitialSyncComplete && mPusherRegistrationComplete;
        }

        // nothing to do ?
        // just dismiss the activity
        if (noUpdate) {
            // do not launch an activity if there was nothing new.
            Log.e(LOG_TAG, "nothing to do");
            finishIfReady();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Collection<MXSession> sessions = mDoneListeners.keySet();

        for(MXSession session : sessions) {
            if (session.isAlive()) {
                session.getDataHandler().removeListener(mDoneListeners.get(session));
                session.setFailureCallback(null);
            }
        }
    }
}
