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

import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoom;

import java.util.ArrayList;
import java.util.List;

/**
 * Manage the public rooms
 */
public class PublicRoomsManager {
    private static final String LOG_TAG = "PublicRoomsManager";

    public interface PublicRoomsManagerListener {
        /**
         * Called when the public rooms list have been refreshed
         */
        void onRefresh();
    }

    // session
    private static MXSession mSession;

    // current public Rooms List
    private static List<PublicRoom> mPublicRoomsList = null;

    // refresh status
    private static boolean mRefreshInProgress = false;

    private static final ArrayList<PublicRoomsManagerListener> mListeners = new ArrayList<PublicRoomsManagerListener>();

    /**
     * Set the current session
     * @param session the session
     */
    public static void setSession(MXSession session) {
        mSession = session;
    }

    /**
     * @return the public rooms list
     */
    public static List<PublicRoom> getPublicRooms() {
        return mPublicRoomsList;
    }

    /**
     * Refresh the public rooms list
     * @param listener the update listener
     */
    public static void refresh(PublicRoomsManagerListener listener) {
        if (null != mSession) {
            if (mRefreshInProgress) {
                if (null != listener) {
                    mListeners.add(listener);
                }
            } else {
                mRefreshInProgress = true;

                final long t0 = System.currentTimeMillis();

                // use any session to get the public rooms list
                mSession.getEventsApiClient().loadPublicRooms(new SimpleApiCallback<List<PublicRoom>>() {
                    @Override
                    public void onSuccess(final List<PublicRoom> publicRooms) {
                        Log.d(LOG_TAG, "Got the rooms public list : " + publicRooms.size() + " rooms in " + (System.currentTimeMillis() - t0) + " ms");
                        mPublicRoomsList = publicRooms;

                        for(PublicRoomsManagerListener listener : mListeners) {
                            listener.onRefresh();
                        }
                        mListeners.clear();
                        mRefreshInProgress = false;
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        super.onNetworkError(e);
                        mRefreshInProgress = false;
                        Log.e(LOG_TAG, "fails to retrieve the public room list " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        super.onMatrixError(e);
                        mRefreshInProgress = false;
                        Log.e(LOG_TAG, "fails to retrieve the public room list " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        super.onUnexpectedError(e);
                        mRefreshInProgress = false;
                        Log.e(LOG_TAG, "fails to retrieve the public room list " + e.getLocalizedMessage());
                    }
                });
            }
        }
    }
}

