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

package im.vector.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import im.vector.R;

public class VectorSearchRoomFilesListFragment extends VectorSearchFilesListFragment {

    static final int MESSAGES_PAGINATION_LIMIT = 100;
    static final String LOG_TAG = "VectorSearchMedias";

    // set to false when there is no more available message in the room history
    private boolean mCanPaginateBack = true;

    /**
     * static constructor
     * @param matrixId the session Id.
     * @param layoutResId the used layout.
     * @return
     */
    public static VectorSearchRoomFilesListFragment newInstance(String matrixId, String roomId, int layoutResId) {
        VectorSearchRoomFilesListFragment frag = new VectorSearchRoomFilesListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);

        if (null != roomId) {
            args.putString(ARG_ROOM_ID, roomId);
        }

        frag.setArguments(args);
        return frag;
    }

    /**
     * @return the fragment tag to use to restore the matrix messages fragement
     */
    protected String getMatrixMessagesFragmentTag() {
        return "im.vector.VectorSearchRoomFilesListFragment.getMatrixMessagesFragmentTag";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        return view;
    }

    /**
     * Cancel the catching requests.
     */
    public void cancelCatchingRequests() {
        super.cancelCatchingRequests();
        mCanPaginateBack = true;
        mNextBatch = mRoom.getLiveState().getToken();
    }


    @Override
    public void onResume() {
        super.onResume();

        cancelCatchingRequests();
        startFilesSearch(null);
    }

    /**
     * Display a global spinner or any UI item to warn the user that there are some pending actions.
     */
    @Override
    public void displayLoadingProgress() {
        if (null != getActivity()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (null != getActivity()) {
                        final View progressView = getActivity().findViewById(R.id.search_load_oldest_progress);

                        if (null != progressView) {
                            progressView.setVisibility(View.VISIBLE);
                        }
                    }
                }
            });
        }
    }

    /**
     * Dismiss any global spinner.
     */
    @Override
    public void dismissLoadingProgress() {
        if (null != getActivity()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (null != getActivity()) {
                        final View progressView = getActivity().findViewById(R.id.search_load_oldest_progress);

                        if (null != progressView) {
                            progressView.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }
    }

    /**
     * Update the searched pattern.
     */
    public void startFilesSearch(final OnSearchResultListener onSearchResultListener) {
        // please wait
        if (mIsCatchingUp) {
            return;
        }

        mIsCatchingUp = true;

        // add the listener to list to warn when the search is done.
        if (null != onSearchResultListener) {
            mSearchListeners.add(onSearchResultListener);
        }

        // will be called when resumed
        if (null == mMessageListView) {
            return;
        }

        mMessageListView.setVisibility(View.GONE);

        remoteRoomHistoryRequest(new ArrayList<Event>(), new ApiCallback<ArrayList<Event>>() {
            @Override
            public void onSuccess(ArrayList<Event> eventsChunk) {
                ArrayList<MessageRow> messageRows = new ArrayList<MessageRow>(eventsChunk.size());
                RoomState liveState = mRoom.getLiveState();

                for (Event event : eventsChunk) {
                    messageRows.add(new MessageRow(event, liveState));
                }

                Collections.reverse(messageRows);

                mAdapter.clear();
                mAdapter.addAll(messageRows);

                // scroll to the bottom
                scrollToBottom();
                mMessageListView.setVisibility(View.VISIBLE);

                for(OnSearchResultListener listener : mSearchListeners) {
                    try {
                        listener.onSearchSucceed(messageRows.size());
                    } catch (Exception e) {
                    }
                }

                mIsCatchingUp = false;
                mSearchListeners.clear();
            }

            private void onError() {
                mMessageListView.setVisibility(View.GONE);

                // clear the results list if teh search fails
                mAdapter.clear();

                for(OnSearchResultListener listener : mSearchListeners) {
                    try {
                        listener.onSearchFailed();
                    } catch (Exception e) {
                    }
                }

                mIsCatchingUp = false;
                mSearchListeners.clear();
            }

            @Override
            public void onNetworkError(Exception e) {
                onError();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError();
            }
        });
    }

    /**
     * Search the pattern on a pagination server side.
     */
    public void requestHistory() {
        // please wait
        if (mIsCatchingUp || !mCanPaginateBack) {
            return;
        }

        mIsCatchingUp = true;

        final int firstPos = mMessageListView.getFirstVisiblePosition();
        final int countBeforeUpdate = mAdapter.getCount();

        displayLoadingProgress();

        remoteRoomHistoryRequest(new ArrayList<Event>(), new ApiCallback<ArrayList<Event>>() {
            @Override
            public void onSuccess(final ArrayList<Event> eventChunks) {
                VectorSearchRoomFilesListFragment.this.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // is there any result to display
                        if (0 != eventChunks.size()) {
                            mAdapter.setNotifyOnChange(false);

                            for (Event event : eventChunks) {
                                MessageRow row = new MessageRow(event, mRoom.getLiveState());
                                mAdapter.insert(row, 0);
                            }

                            // Scroll the list down to where it was before adding rows to the top
                            mUiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    // refresh the list only at the end of the sync
                                    // else the one by one message refresh gives a weird UX
                                    // The application is almost frozen during the
                                    mAdapter.notifyDataSetChanged();

                                    // do not use count because some messages are not displayed
                                    // so we compute the new pos
                                    mMessageListView.setSelection(firstPos + (mAdapter.getCount() - countBeforeUpdate));
                                    mIsCatchingUp = false;
                                }
                            });
                        } else {
                            mIsCatchingUp = false;
                        }

                        VectorSearchRoomFilesListFragment.this.dismissLoadingProgress();

                    }
                });

            }

            private void onError() {
                mIsCatchingUp = false;
                VectorSearchRoomFilesListFragment.this.dismissLoadingProgress();
            }

            // the request will be auto restarted when a valid network will be found
            @Override
            public void onNetworkError(Exception e) {
                onError();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError();
            }
        });
    }

    /**
     * Filter and append the found events
     * @param events the matched events list
     * @param eventsToAppend the retrieved events list.
     */
    private void appendEvents(ArrayList<Event> events, List<Event> eventsToAppend) {
        // filter
        ArrayList<Event> filteredEvents = new ArrayList<Event>(eventsToAppend.size());
        for(Event event : eventsToAppend) {
            if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
                Message message = JsonUtils.toMessage(event.content);

                if (Message.MSGTYPE_FILE.equals(message.msgtype) ||
                        Message.MSGTYPE_IMAGE.equals(message.msgtype) ||
                        Message.MSGTYPE_VIDEO.equals(message.msgtype)) {
                    filteredEvents.add(event);
                }
            }
        }

        events.addAll(filteredEvents);
    }

    /**
     * Search some files until find out at least 10 matching messages.
     * @param events the result events lists
     * @param callback the result callback
     */
    private void remoteRoomHistoryRequest(final ArrayList<Event> events, final ApiCallback<ArrayList<Event>> callback) {
        mRoom.remoteRoomHistoryRequest(mNextBatch, MESSAGES_PAGINATION_LIMIT, new ApiCallback<TokensChunkResponse<Event>>() {
            @Override
            public void onSuccess(TokensChunkResponse<Event> eventsChunk) {
                if ((null == mNextBatch) || TextUtils.equals(eventsChunk.start, mNextBatch)) {

                    // no more message in the history
                    if (0 == eventsChunk.chunk.size()) {
                        mCanPaginateBack = false;
                        callback.onSuccess(events);
                    } else {
                        // append the retrieved one
                        appendEvents(events, eventsChunk.chunk);
                        mNextBatch = eventsChunk.end;

                        if (events.size() >= 10) {
                            callback.onSuccess(events);
                        } else {
                            remoteRoomHistoryRequest(events, callback);
                        }
                    }
                }
            }

            private void onError() {
                callback.onSuccess(events);
            }

            @Override
            public void onNetworkError(Exception e) {
                onError();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError();
            }
        });
    }


}
