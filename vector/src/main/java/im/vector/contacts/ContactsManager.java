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

package im.vector.contacts;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.User;

import im.vector.Matrix;
import im.vector.VectorApp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Manage the local contacts
 */
public class ContactsManager {
    private static final String LOG_TAG = "ContactsManager";

    public interface ContactsManagerListener {
        /**
         * Called when the contacts list have been refreshed
         */
        void onRefresh();

        /**
         * Call when some contact PIDs have been retrieved
         */
        void onPIDsUpdate();

        /**
         * Called when an user presence has been updated
         */
        void onContactPresenceUpdate(Contact contact, String matrixId);
    }

    // the contacts list snapshot
    private static List<Contact> mContactsList = null;

    // the listeners
    private static ArrayList<ContactsManagerListener> mListeners = null;

    // a contacts population is in progress
    private static boolean mIsPopulating = false;

    // set to true when there is a pending
    private static boolean mIsRetrievingPids = false;
    private static boolean mArePidsRetrieved = false;
    // Trigger another PIDs retrieval when there is a valid data connection.
    private static boolean mRetryPIDsRetrievalOnConnect = false;

    private static final IMXNetworkEventListener mNetworkConnectivityReceiver = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            if (isConnected && mRetryPIDsRetrievalOnConnect) {
                retrievePids();
            }
        }
    };

    // retriever listener
    private static final PIDsRetriever.PIDsRetrieverListener mPIDsRetrieverListener = new PIDsRetriever.PIDsRetrieverListener() {
        /**
         * Warn the listeners about a contact information update.
         * @param contact the contact
         * @param mxid the mxid
         */
        private void onContactPresenceUpdate(Contact contact, Contact.MXID mxid) {
            if (null != mListeners) {
                for (ContactsManagerListener listener : mListeners) {
                    try {
                        listener.onContactPresenceUpdate(contact, mxid.mMatrixId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onContactPresenceUpdate failed " + e.getMessage());
                    }
                }
            }
        }

        /**
         * Warn that some PIDs have been retrieved from the contacts data.
         */
        private void onPIDsUpdate() {
            if (null != mListeners) {
                for (ContactsManagerListener listener : mListeners) {
                    try {
                        listener.onPIDsUpdate();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onPIDsUpdate failed " + e.getMessage());
                    }
                }
            }
        }

        @Override
        public void onFailure(String accountId) {
            // ignore the current response because the request has been cancelled
            if (!mIsRetrievingPids) {
                Log.d(LOG_TAG, "## Retrieve a PIDS success whereas it is not expected");
                return;
            }

            mIsRetrievingPids = false;
            mArePidsRetrieved = false;
            mRetryPIDsRetrievalOnConnect = true;
            Log.d(LOG_TAG, "## fail to retrieve the PIDs");

            // warn that the current request failed.
            // Thus, if the listeners display a spinner (or so), it should be hidden.
            onPIDsUpdate();
        }

        @Override
        public void onSuccess(final String accountId) {
            // ignore the current response because the request has been cancelled
            if (!mIsRetrievingPids) {
                Log.d(LOG_TAG, "## Retrieve a PIDS success whereas it is not expected");
                return;
            }

            Log.d(LOG_TAG, "## Retrieve IPDs successfully");

            mRetryPIDsRetrievalOnConnect = false;
            mIsRetrievingPids = false;
            mArePidsRetrieved = true;

            // warn that the contacts list have been updated
            onPIDsUpdate();

            // privacy
            // Log.d(LOG_TAG, "onPIDsRetrieved : the contact " + contact + " retrieves its 3PIds.");

            MXSession session = Matrix.getInstance(VectorApp.getInstance().getApplicationContext()).getSession(accountId);

            if ((null != session) && (null != mContactsList)) {
                for (final Contact contact : mContactsList) {
                    Set<String> medias = contact.getMatrixIdMedias();

                    for (String media : medias) {
                        final Contact.MXID mxid = contact.getMXID(media);
                        mxid.mUser = session.getDataHandler().getUser(mxid.mMatrixId);

                        // if the user is not known, get its presence
                        if (null == mxid.mUser) {
                            session.getPresenceApiClient().getPresence(mxid.mMatrixId, new ApiCallback<User>() {
                                @Override
                                public void onSuccess(User user) {
                                    Log.d(LOG_TAG, "retrieve the presence of " + mxid.mMatrixId + " :" + user);
                                    mxid.mUser = user;
                                    onContactPresenceUpdate(contact, mxid);
                                }

                                private void onError(String errorMessage) {
                                    Log.e(LOG_TAG, "cannot retrieve the presence of " + mxid.mMatrixId + " :" + errorMessage);
                                }

                                @Override
                                public void onNetworkError(Exception e) {
                                    onError(e.getMessage());
                                }

                                @Override
                                public void onMatrixError(MatrixError e) {
                                    onError(e.getMessage());
                                }

                                @Override
                                public void onUnexpectedError(Exception e) {
                                    onError(e.getMessage());
                                }
                            });
                        }
                    }
                }
            }
        }
    };

    /**
     * Provides an unique identifier of the contacts snapshot
     *
     * @return an unique identifier
     */
    public static int getLocalContactsSnapshotSession() {
        if (null != mContactsList) {
            return mContactsList.hashCode();
        } else {
            return 0;
        }
    }

    /**
     * Refresh the local contacts list snapshot.
     *
     * @return a local contacts list snapshot.
     */
    public static Collection<Contact> getLocalContactsSnapshot() {
        return mContactsList;
    }

    /**
     * Tell if the contacts snapshot list is ready
     *
     * @param context the context
     * @return true if the contacts snapshot list is ready
     */
    public static boolean didPopulateLocalContacts(Context context) {
        boolean res;
        boolean isPopulating;

        synchronized (LOG_TAG) {
            res = (null != mContactsList);
            isPopulating = mIsPopulating;
        }

        if (!res && !isPopulating) {
            refreshLocalContactsSnapshot(context);
        }

        return res;
    }

    /**
     * reset
     */
    public static void reset() {
        mListeners = null;
        clearSnapshot();
    }

    /**
     * Clear the current snapshot
     */
    public static void clearSnapshot() {
        synchronized (LOG_TAG) {
            mContactsList = null;
        }

        MXSession defaultSession = Matrix.getInstance(VectorApp.getInstance()).getDefaultSession();

        if (null != defaultSession) {
            defaultSession.getNetworkConnectivityReceiver().removeEventListener(mNetworkConnectivityReceiver);
        }
    }

    /**
     * Add a listener.
     *
     * @param listener the listener to add.
     */
    public static void addListener(ContactsManagerListener listener) {
        if (null == mListeners) {
            mListeners = new ArrayList<>();
        }

        mListeners.add(listener);
    }

    /**
     * Remove a listener.
     *
     * @param listener the listener to remove.
     */
    public static void removeListener(ContactsManagerListener listener) {
        if (null != mListeners) {
            mListeners.remove(listener);
        }
    }

    /**
     * Tells if the contacts PIDs have been retrieved
     *
     * @return true if the PIDs have been retrieved.
     */
    public static boolean arePIDsRetrieved() {
        return mArePidsRetrieved;
    }

    /**
     * Retrieve the contacts PIDs
     */
    public static void retrievePids() {
        Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mIsRetrievingPids) {
                    Log.d(LOG_TAG, "## retrievePids() : already in progress");
                } else if (mArePidsRetrieved) {
                    Log.d(LOG_TAG, "## retrievePids() : already done");
                } else {
                    Log.d(LOG_TAG, "## retrievePids() : Start search");
                    mIsRetrievingPids = true;
                    PIDsRetriever.getInstance().retrieveMatrixIds(VectorApp.getInstance(), mContactsList, false);
                }
            }
        });
    }

    /**
     * List the local contacts.
     *
     * @param context the context.
     */
    public static void refreshLocalContactsSnapshot(final Context context) {
        boolean isPopulating;

        synchronized (LOG_TAG) {
            isPopulating = mIsPopulating;
        }

        // test if there is a population is in progress
        if (isPopulating) {
            return;
        }

        synchronized (LOG_TAG) {
            mIsPopulating = true;
        }

        // refresh the contacts list in background
        Thread t = new Thread(new Runnable() {
            public void run() {
                long t0 = System.currentTimeMillis();
                ContentResolver cr = context.getContentResolver();
                HashMap<String, Contact> dict = new HashMap<>();

                // test if the user allows to access to the contact
                if (isContactBookAccessAllowed(context)) {
                    // get the names
                    Cursor namesCur = null;

                    try {
                        namesCur = cr.query(ContactsContract.Data.CONTENT_URI,
                                new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                                        ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID,
                                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                                },
                                ContactsContract.Data.MIMETYPE + " = ?",
                                new String[]{ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}, null);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Contact names query Msg=" + e.getMessage());
                    }

                    if (namesCur != null) {
                        try {
                            while (namesCur.moveToNext()) {
                                String displayName = namesCur.getString(namesCur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                                String contactId = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID));
                                String thumbnailUri = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI));

                                if (null != contactId) {
                                    Contact contact = dict.get(contactId);

                                    if (null == contact) {
                                        contact = new Contact(contactId);
                                        dict.put(contactId, contact);
                                    }

                                    if (null != displayName) {
                                        contact.setDisplayName(displayName);
                                    }

                                    if (null != thumbnailUri) {
                                        contact.setThumbnailUri(thumbnailUri);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Contact names query2 Msg=" + e.getMessage());
                        }

                        namesCur.close();
                    }

                    // get the phonenumbers
                    Cursor phonesCur = null;

                    try {
                        phonesCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                new String[]{ContactsContract.CommonDataKinds.Phone.DATA, // actual number
                                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                                },
                                null, null, null);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Phone numbers query Msg=" + e.getMessage());
                    }

                    if (null != phonesCur) {
                        try {
                            while (phonesCur.moveToNext()) {
                                String phone = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DATA));

                                if (!TextUtils.isEmpty(phone)) {
                                    String contactId = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID));

                                    if (null != contactId) {
                                        Contact contact = dict.get(contactId);
                                        if (null == contact) {
                                            contact = new Contact(contactId);
                                            dict.put(contactId, contact);
                                        }

                                        contact.addPhonenumber(phone);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Phone numbers query2 Msg=" + e.getMessage());
                        }

                        phonesCur.close();
                    }

                    // get the emails
                    Cursor emailsCur = null;

                    try {
                        emailsCur = cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                new String[]{ContactsContract.CommonDataKinds.Email.DATA, // actual email
                                        ContactsContract.CommonDataKinds.Email.CONTACT_ID},
                                null, null, null);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Emails query Msg=" + e.getMessage());
                    }

                    if (emailsCur != null) {
                        try {
                            while (emailsCur.moveToNext()) {
                                String email = emailsCur.getString(emailsCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
                                if (!TextUtils.isEmpty(email)) {
                                    String contactId = emailsCur.getString(emailsCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID));

                                    if (null != contactId) {
                                        Contact contact = dict.get(contactId);
                                        if (null == contact) {
                                            contact = new Contact(contactId);
                                            dict.put(contactId, contact);
                                        }

                                        contact.addEmailAdress(email);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Emails query2 Msg=" + e.getMessage());
                        }

                        emailsCur.close();
                    }
                }

                synchronized (LOG_TAG) {
                    mContactsList = new ArrayList<>(dict.values());
                    mIsPopulating = false;
                }

                if (0 != mContactsList.size()) {
                    long delta = System.currentTimeMillis() - t0;

                    VectorApp.sendGAStats(VectorApp.getInstance(),
                            VectorApp.GOOGLE_ANALYTICS_STATS_CATEGORY,
                            VectorApp.GOOGLE_ANALYTICS_STARTUP_CONTACTS_ACTION,
                            mContactsList.size() + " contacts in " + delta + " ms",
                            delta
                    );
                }

                // define the PIDs listener
                PIDsRetriever.getInstance().setPIDsRetrieverListener(mPIDsRetrieverListener);

                // trigger a PIDs retrieval
                // add a network listener to ensure that the PIDS will be retreived asap a valid network will be found.
                MXSession defaultSession = Matrix.getInstance(VectorApp.getInstance()).getDefaultSession();
                if (null != defaultSession) {
                    defaultSession.getNetworkConnectivityReceiver().addEventListener(mNetworkConnectivityReceiver);

                    // reset the PIDs retriever statuses
                    mIsRetrievingPids = false;
                    mArePidsRetrieved = false;

                    // the PIDs retrieval is done on demand.
                }

                if (null != mListeners) {
                    Handler handler = new Handler(Looper.getMainLooper());

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (ContactsManagerListener listener : mListeners) {
                                try {
                                    listener.onRefresh();
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "refreshLocalContactsSnapshot : onRefresh failed" + e.getMessage());
                                }
                            }
                        }
                    });
                }
            }
        });

        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    //================================================================================
    // Contacts book management (for android < M devices)
    //================================================================================
    public static final String CONTACTS_BOOK_ACCESS_KEY = "CONTACT_BOOK_ACCESS_KEY";

    /**
     * Tells if the contacts book access has been requested.
     * For android > M devices, it only tells if the permission has been granted.
     *
     * @param activity the calling activity
     * @return true it was requested once
     */
    public static boolean isContactBookAccessRequested(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            return (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.READ_CONTACTS));
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
            return preferences.contains(CONTACTS_BOOK_ACCESS_KEY);
        }
    }

    /**
     * Update the contacts book access.
     *
     * @param activity  the calling activity.
     * @param isAllowed true to allowed the contacts book access.
     */
    public static void setIsContactBookAccessAllowed(Activity activity, boolean isAllowed) {
        if (Build.VERSION.SDK_INT < 23) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(CONTACTS_BOOK_ACCESS_KEY, isAllowed);
            editor.commit();
        }
        mIsRetrievingPids = false;
        mArePidsRetrieved = false;
    }

    /**
     * Tells if the contacts book access has been granted
     *
     * @param context the context
     * @return true if it was granted.
     */
    private static boolean isContactBookAccessAllowed(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {
            return (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS));
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            return preferences.getBoolean(CONTACTS_BOOK_ACCESS_KEY, false);
        }
    }
}

