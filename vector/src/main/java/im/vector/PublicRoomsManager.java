/*
 * Copyright 2016 OpenMarket Ltd
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

import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.PublicRoomsResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Manage the public rooms
 */
public class PublicRoomsManager {
    private static final String LOG_TAG = "PublicRoomsManager";

    public static final int PUBLIC_ROOMS_LIMIT = 20;
    public static final int PUBLIC_ROOMS_NOT_INITIALIZED = -1;

    public interface PublicRoomsManagerListener {
        /**
         * Called when the number of public rooms count have been updated
         */
        void onPublicRoomsCountRefresh(int publicRoomsCount);
    }

    // session
    private static MXSession mSession;

    // refresh status
    private static boolean mCountRefreshInProgress = false;

    // define the number of public rooms
    private static int mPublicRoomsCount = PUBLIC_ROOMS_NOT_INITIALIZED;

    // request key to avoid dispatching invalid data
    private static String mRequestKey = null;

    // pagination information
    private static String mRequestServer = null;
    private static String mSearchedPattern = null;
    private static String mForwardPaginationToken = null;

    // public room listeners
    private static final ArrayList<PublicRoomsManagerListener> mListeners = new ArrayList<>();

    /**
     * Set the current session
     * @param session the session
     */
    public static void setSession(MXSession session) {
        mSession = session;
    }

    /**
     * @return true if there is a public room requests in progress
     */
    public static boolean isRequestInProgress() {
        return !TextUtils.isEmpty(mRequestKey);
    }

    /**
     * @return true if there are some other public rooms to find.
     */
    public static boolean hasMoreResults() {
        return !TextUtils.isEmpty(mForwardPaginationToken);
    }

    /**
     * Trigger a public rooms request.
     * @param callback the asynchronous callback.
     */
    private static void launchPublicRoomsRequest(final ApiCallback<List<PublicRoom>> callback) {
        final String fToken = mRequestKey;

        //final String server, final String pattern, final String since, final ApiCallback<PublicRoomsResponse> callback
        mSession.getEventsApiClient().loadPublicRooms(mRequestServer, mSearchedPattern, mForwardPaginationToken, PUBLIC_ROOMS_LIMIT, new ApiCallback<PublicRoomsResponse>() {
            @Override
            public void onSuccess(PublicRoomsResponse publicRoomsResponse) {
                // check if the request response is still expected
                if (TextUtils.equals(fToken, mRequestKey)) {
                    List<PublicRoom> list = publicRoomsResponse.chunk;

                    // avoid the null case
                    if (null == list) {
                        list = new ArrayList<>();
                    }

                    Log.d(LOG_TAG, "## launchPublicRoomsRequest() : retrieves " + list.size() + " rooms");

                    mForwardPaginationToken = publicRoomsResponse.next_batch;

                    if (null != callback) {
                        callback.onSuccess(list);
                    }

                    mRequestKey = null;
                } else {
                    Log.d(LOG_TAG, "## launchPublicRoomsRequest() : the request has been cancelled");
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                // check if the request response is still expected
                if (TextUtils.equals(fToken, mRequestKey)) {
                    Log.d(LOG_TAG, "## launchPublicRoomsRequest() : onNetworkError " + e.getMessage());

                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                    mRequestKey = null;
                } else {
                    Log.d(LOG_TAG, "## launchPublicRoomsRequest() : the request has been cancelled");
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                // check if the request response is still expected
                if (TextUtils.equals(fToken, mRequestKey)) {
                    Log.d(LOG_TAG, "## launchPublicRoomsRequest() : MatrixError " + e.getLocalizedMessage());

                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                    mRequestKey = null;
                } else {
                    Log.d(LOG_TAG, "## launchPublicRoomsRequest() : the request has been cancelled");
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                // check if the request response is still expected
                if (TextUtils.equals(fToken, mRequestKey)) {
                    Log.d(LOG_TAG, "## launchPublicRoomsRequest() : onUnexpectedError " + e.getLocalizedMessage());

                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                    mRequestKey = null;
                } else {
                    Log.d(LOG_TAG, "## launchPublicRoomsRequest() : the request has been cancelled");
                }
            }
        });
    }

    /**
     * Start a new public rooms search
     * @param server set the server in which searches, null if any
     * @param pattern the pattern to search
     * @param callback the asynchronous callback
     */
    public static void startPublicRoomsSearch(final String server, final String pattern, final ApiCallback<List<PublicRoom>> callback) {
        Log.d(LOG_TAG, "## startPublicRoomsSearch() " + " : server " + server + " pattern " + pattern);

        // on android, a request cannot be cancelled
        // so define a key to detect if the request makes senses
        mRequestKey =  "startPublicRoomsSearch" + System.currentTimeMillis();

        // init the parameters
        mRequestServer = server;
        mSearchedPattern = pattern;
        mForwardPaginationToken = null;

        launchPublicRoomsRequest(callback);
    }

    /**
     * Forward paginate the public rooms search.
     * @param callback the asynchronous callback
     * @return true if the pagination starts
     */
    public static boolean forwardPaginate(final ApiCallback<List<PublicRoom>> callback) {
        Log.d(LOG_TAG, "## forwardPaginate() " + " : server " + mRequestServer + " pattern " + mSearchedPattern + " mForwardPaginationToken " + mForwardPaginationToken);

        if (isRequestInProgress()) {
            Log.d(LOG_TAG, "## forwardPaginate() : a request is already in progress");
            return false;
        }

        if (TextUtils.isEmpty(mForwardPaginationToken)) {
            Log.d(LOG_TAG, "## forwardPaginate() : there is no forward token");
            return false;
        }

        // on android, a request cannot be cancelled
        // so define a key to detect if the request makes senses
        mRequestKey =  "forwardPaginate" + System.currentTimeMillis();

        launchPublicRoomsRequest(callback);

        return true;
    }

    /**
     * @return the number of public rooms or {@link #PUBLIC_ROOMS_NOT_INITIALIZED} it not yet initialized.
     */
    public static int getPublicRoomsCount() {
        return mPublicRoomsCount;
    }

    /**
     * Refresh the public rooms count
     * @param listener the update listener
     */
    public static void refreshPublicRoomsCount(final PublicRoomsManagerListener listener) {
        if (null == mSession) {
            return;
        }
        if (null != listener) {
            mListeners.add(listener);
        }
        if (mCountRefreshInProgress) {
            return;
        }
        mCountRefreshInProgress = true;

        // use any session to get the public rooms list
        mSession.getEventsApiClient().getPublicRoomsCount(new SimpleApiCallback<Integer>() {
            @Override
            public void onSuccess(final Integer publicRoomsCount) {
                Log.d(LOG_TAG, "## refreshPublicRoomsCount() : Got the rooms public list count : " + publicRoomsCount);
                mPublicRoomsCount = publicRoomsCount != null ? publicRoomsCount : 0;

                for (PublicRoomsManagerListener listener : mListeners) {
                    listener.onPublicRoomsCountRefresh(mPublicRoomsCount);
                }
                mListeners.clear();
                mCountRefreshInProgress = false;
            }

            @Override
            public void onNetworkError(Exception e) {
                super.onNetworkError(e);
                mCountRefreshInProgress = false;
                Log.e(LOG_TAG, "## refreshPublicRoomsCount() : fails to retrieve the public room list " + e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                super.onMatrixError(e);
                mCountRefreshInProgress = false;
                Log.e(LOG_TAG, "## refreshPublicRoomsCount() : fails to retrieve the public room list " + e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                super.onUnexpectedError(e);
                mCountRefreshInProgress = false;
                Log.e(LOG_TAG, "## refreshPublicRoomsCount() : fails to retrieve the public room list " + e.getLocalizedMessage());
            }
        });
    }
}

