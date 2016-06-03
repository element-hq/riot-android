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

import android.net.Uri;
import android.util.Log;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.ThreePid;

import java.util.ArrayList;
import java.util.HashMap;
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

    // public room listeners
    private static final ArrayList<PublicRoomsManagerListener> mListeners = new ArrayList<PublicRoomsManagerListener>();

    // when the homeserver url is set to vector.im
    // the manager lists the public rooms from vector.im and matrix.org
    private static EventsRestClient mMatrixEventsRestClient = null;

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
    public static void refresh(final PublicRoomsManagerListener listener) {
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

                        // when the home server is vector.im
                        // get the public rooms list from matrix.org
                        if (mSession.getHomeserverConfig().getHomeserverUri().toString().startsWith("https://vector.im")) {
                            Log.d(LOG_TAG, "Got the vector.im public rooms in " + (System.currentTimeMillis() - t0) + " ms");
                            refreshMatrixPublicRoomsList(t0, publicRooms);
                        } else {
                            Log.d(LOG_TAG, "Got the rooms public list : " + publicRooms.size() + " rooms in " + (System.currentTimeMillis() - t0) + " ms");
                            mPublicRoomsList = publicRooms;

                            for (PublicRoomsManagerListener listener : mListeners) {
                                listener.onRefresh();
                            }
                            mListeners.clear();
                            mRefreshInProgress = false;
                        }
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

    /**
     * List the matrix.org public rooms.
     * @param t0 public rooms request start time
     * @param publicRooms the known public rooms
     */
    private static void refreshMatrixPublicRoomsList(final long t0, final List<PublicRoom> publicRooms) {

        // create a dedicated events rest client
        if (null == mMatrixEventsRestClient) {
            final HomeserverConnectionConfig hsConfig = new HomeserverConnectionConfig(Uri.parse("https://matrix.org"));
            mMatrixEventsRestClient = new EventsRestClient(hsConfig);
        }

        Log.d(LOG_TAG, "refresh the matrix.org public rooms");

        mMatrixEventsRestClient.loadPublicRooms(new ApiCallback<List<PublicRoom>>() {
            private void onMerged(List<PublicRoom> publicRooms) {
                Log.d(LOG_TAG,"Got the merged rooms public list : "+publicRooms.size()+" rooms in "+(System.currentTimeMillis()-t0)+" ms");
                mPublicRoomsList = publicRooms;

                for(PublicRoomsManagerListener listener :mListeners) {
                    listener.onRefresh();
                }

                mListeners.clear();
                mRefreshInProgress=false;
            }

            @Override
            public void onSuccess(List<PublicRoom> matrixPublicRooms) {
                ArrayList<PublicRoom> mergedPublicRooms = new ArrayList<PublicRoom>();
                mergedPublicRooms.addAll(publicRooms);
                mergedPublicRooms.addAll(matrixPublicRooms);

                // avoid duplicated definitions
                HashMap<String, PublicRoom> publicRoomsMap = new HashMap<String, PublicRoom>();

                for(PublicRoom publicRoom : mergedPublicRooms) {

                    // prefer the vector.im public room definition
                    if (!publicRoomsMap.containsKey(publicRoom.roomId)) {
                        publicRoomsMap.put(publicRoom.roomId, publicRoom);
                    }
                }

                onMerged(new ArrayList<PublicRoom>(publicRoomsMap.values()));
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "fails to retrieve the matrix public room list " + e.getLocalizedMessage());
                onMerged(publicRooms);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "fails to retrieve the matrix public room list " + e.getLocalizedMessage());
                onMerged(publicRooms);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "fails to retrieve the matrix public room list " + e.getLocalizedMessage());
                onMerged(publicRooms);
            }
        });
    }
}

