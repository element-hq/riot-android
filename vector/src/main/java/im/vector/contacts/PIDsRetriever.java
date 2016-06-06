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

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import im.vector.Matrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * retrieve the contact matrix IDs
 */
public class PIDsRetriever {

    public interface PIDsRetrieverListener {
        /**
         * Called when the contact PIDs are retrieved
         */
        void onPIDsRetrieved(String accountId, Contact contact, boolean has3PIDs);
    }

    private static PIDsRetriever mPIDsRetriever = null;

    public static PIDsRetriever getIntance() {
        if (null == mPIDsRetriever) {
            mPIDsRetriever = new PIDsRetriever();
        }

        return mPIDsRetriever;
    }

    // MatrixID <-> email
    private HashMap<String, Contact.MXID> mMatrixIdsByElement = new HashMap<String, Contact.MXID>();

    private PIDsRetrieverListener mListener = null;

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

    /**
     * Retrieve the matrix IDs from the contact fields (only emails are supported by now).
     * Update the contact fields with the found Matrix Ids.
     * The update could require some remote requests : they are done only localUpdateOnly is false.
     * @param context the context.
     * @param contact The contact to update.
     * @param localUpdateOnly true to only support refresh from local information.
     * @return true if the matrix Ids have been retrieved
     */
    public boolean retrieveMatrixIds(Context context, final Contact contact, boolean localUpdateOnly) {
        ArrayList<String> requestedAddresses = new ArrayList<String>();

        // check if the emails have only been checked
        // i.e. requested their match PID to the identity server.
        for(String email : contact.mEmails) {
            if (mMatrixIdsByElement.containsKey(email)) {
               Contact.MXID mxid = mMatrixIdsByElement.get(email);

                if (null != mxid) {
                    contact.put(email, mxid);
                }
            } else {
                requestedAddresses.add(email);
            }
        }

        // the lookup has not been done on some emails
        if ((requestedAddresses.size() > 0) && (!localUpdateOnly)) {
            ArrayList<String> medias = new ArrayList<String>();

            for (int index = 0; index < requestedAddresses.size(); index++) {
                medias.add("email");
            }

            final ArrayList<String> fRequestedAddresses = requestedAddresses;
            Collection<MXSession> sessions = Matrix.getInstance(context.getApplicationContext()).getSessions();

            for (MXSession session : sessions) {
                final String accountId = session.getCredentials().userId;

                session.lookup3Pids(fRequestedAddresses, medias, new ApiCallback<ArrayList<String>>() {
                    @Override
                    public void onSuccess(ArrayList<String> pids) {
                        boolean foundPIDs = false;

                        // update the global dict
                        // and the contact dict
                        for (int i = 0; i < fRequestedAddresses.size(); i++) {
                            String address = fRequestedAddresses.get(i);
                            String pid = pids.get(i);

                            mMatrixIdsByElement.put(address, new Contact.MXID(pid, accountId));

                            if (pid.length() != 0) {
                                foundPIDs = true;
                                contact.put(address, new Contact.MXID(pid, accountId));
                            }
                        }

                        // warn the listener of the update
                        if (null != mListener) {
                            mListener.onPIDsRetrieved(accountId, contact, foundPIDs);
                        }
                    }

                    // ignore the network errors
                    // will be checked again later
                    @Override
                    public void onNetworkError(Exception e) {

                    }

                    @Override
                    public void onMatrixError(MatrixError e) {

                    }

                    @Override
                    public void onUnexpectedError(Exception e) {

                    }
                });
            }
        }

        // detect if the matrix Ids have been cached.
        return (0 == requestedAddresses.size());
    }
}

