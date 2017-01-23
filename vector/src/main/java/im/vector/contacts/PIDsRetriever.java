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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import im.vector.Matrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * retrieve the contact matrix IDs
 */
public class PIDsRetriever {
    private static final String LOG_TAG = "PIDsRetriever";

    public interface PIDsRetrieverListener {
        /**
         * Called when the contacts PIDs are retrieved.
         */
        void onSuccess(String accountId);

        /**
         * Called the PIDs retrieval fails.
         */
        void onFailure(String accountId);
    }

    // current instance
    private static PIDsRetriever mPIDsRetriever = null;

    /**
     * @return the PIDsRetriever instance.
     */
    public static PIDsRetriever getInstance() {
        if (null == mPIDsRetriever) {
            mPIDsRetriever = new PIDsRetriever();
        }

        return mPIDsRetriever;
    }

    // MatrixID <-> email
    private final HashMap<String, Contact.MXID> mMatrixIdsByElement = new HashMap<>();

    // listeners list
    private PIDsRetrieverListener mListener = null;

    /**
     * Set the listener.
     * @param listener the listener.
     */
    public void setPIDsRetrieverListener(PIDsRetrieverListener listener) {
        mListener = listener;
    }

    /**
     * Clear the email to matrix id conversion table
     */
    public void onAppBackgrounded() {
        mMatrixIdsByElement.clear();
    }

    /**
     * reset
     */
    public void reset() {
        mMatrixIdsByElement.clear();
        mListener = null;
    }

    /**ce (email, phonenumber...)
     * @param item the item to retrieve
     * @return the linked MXID if it exists
     */
    public Contact.MXID getMXID(String item) {
        if (null != item) {
            Contact.MXID mxId = mMatrixIdsByElement.get(item);

            // test if a valid matrix id has been retrieved
            if ((null != mxId) && !TextUtils.isEmpty(mxId.mMatrixId)) {
                return mxId;
            }
        }

        return null;
    }

    /**
     * Retrieve the matrix ids for a list of contacts with the local cache.
     * @param contacts the contacts list
     * @return the email addresses which are not cached.
     */
    private List<String> retrieveMatrixIds(List<Contact> contacts) {
        ArrayList<String> requestedAddresses = new ArrayList<>();

        for (Contact contact : contacts) {
            // check if the emails have only been checked
            // i.e. requested their match PID to the identity server.
            for (String email : contact.getEmails()) {
                if (mMatrixIdsByElement.containsKey(email)) {
                    Contact.MXID mxid = mMatrixIdsByElement.get(email);

                    if (null != mxid) {
                        contact.put(email, mxid);
                    }
                } else {
                    if (!requestedAddresses.contains(email)) {
                        requestedAddresses.add(email);
                    }
                }
            }
        }

        return requestedAddresses;
    }

    /**
     * Retrieve the matrix IDs from the contact fields (only emails are supported by now).
     * Update the contact fields with the found Matrix Ids.
     * The update could require some remote requests : they are done only localUpdateOnly is false.
     * @param context the context.
     * @param contacts the contacts list.
     * @param localUpdateOnly true to only support refresh from local information.
     * @return true if the matrix Ids have been retrieved
     */
    public void retrieveMatrixIds(final Context context, final List<Contact> contacts, final boolean localUpdateOnly) {
        Log.e(LOG_TAG, String.format("retrieveMatrixIds starts for %d contacts", contacts == null ? 0 : contacts.size()));
        // sanity checks
        if ((null == contacts) || (0 == contacts.size())) {
            if (null != mListener) {
                Handler handler = new Handler(Looper.getMainLooper());

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        MXSession session = Matrix.getInstance(context.getApplicationContext()).getDefaultSession();

                        if (null != session) {
                            mListener.onSuccess(session.getMyUserId());
                        }
                    }
                });
            }

            return;
        }

        List<String> missingEmails = retrieveMatrixIds(contacts);

        if (!localUpdateOnly && !missingEmails.isEmpty()) {
            ArrayList<String> medias = new ArrayList<>();

            for (int index = 0; index < missingEmails.size(); index++) {
                medias.add("email");
            }

            final List<String> fRequestedAddresses = missingEmails;
            Collection<MXSession> sessions = Matrix.getInstance(context.getApplicationContext()).getSessions();

            for (MXSession session : sessions) {
                final String accountId = session.getCredentials().userId;

                session.lookup3Pids(fRequestedAddresses, medias, new ApiCallback<List<String>>() {
                    @Override
                    public void onSuccess(final List<String> pids) {
                        Log.e(LOG_TAG, "lookup3Pids success " + pids.size());
                        // update the local cache
                        for(int index = 0; index < fRequestedAddresses.size(); index++) {
                            String email = fRequestedAddresses.get(index);
                            String mxId = pids.get(index);

                            if (!TextUtils.isEmpty(mxId)) {
                                mMatrixIdsByElement.put(email, new Contact.MXID(mxId, accountId));
                            }
                        }

                        retrieveMatrixIds(contacts);

                        // warn the listener of the update
                        if (null != mListener) {
                            mListener.onSuccess(accountId);
                        }
                    }

                    /**
                     * Common error routine
                     * @param errorMessage the error message
                     */
                    private void onError(String errorMessage) {
                        Log.e(LOG_TAG, "## retrieveMatrixIds() : failed " + errorMessage);

                        if (null != mListener) {
                            mListener.onFailure(accountId);
                        }
                    }

                    // ignore the network errors
                    // will be checked again later
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

