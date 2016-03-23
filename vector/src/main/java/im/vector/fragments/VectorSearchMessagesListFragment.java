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

package im.vector.fragments;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;

import im.vector.R;
import im.vector.activity.VectorBaseSearchActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorRoomMessagesContextActivity;
import im.vector.activity.VectorUnifiedSearchActivity;
import im.vector.adapters.VectorMessagesAdapter;
import im.vector.adapters.VectorSearchMessagesListAdapter;

public class VectorSearchMessagesListFragment extends VectorMessageListFragment {

    // parameters
    protected String mPendingPattern;
    protected String mSearchingPattern;
    protected ArrayList<OnSearchResultListener> mSearchListeners = new ArrayList<OnSearchResultListener>();

    protected View mProgressView = null;

    /**
     * static constructor
     * @param matrixId the session Id.
     * @param layoutResId the used layout.
     * @return
     */
    public static VectorSearchMessagesListFragment newInstance(String matrixId, String roomId, int layoutResId) {
        VectorSearchMessagesListFragment frag = new VectorSearchMessagesListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);

        if (null != roomId) {
            args.putString(ARG_ROOM_ID, roomId);
        }

        frag.setArguments(args);
        return frag;
    }

    @Override
    public MessagesAdapter createMessagesAdapter() {
        return new VectorSearchMessagesListAdapter(mSession, getActivity(), (null == mRoom), getMXMediasCache());
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelSearch();

        if (mIsMediaSearch) {
            mSession.cancelSearchMediaName();
        } else {
            mSession.cancelSearchMessageText();
        }
        mSearchingPattern = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() instanceof VectorBaseSearchActivity.IVectorSearchActivity) {
            ((VectorBaseSearchActivity.IVectorSearchActivity)getActivity()).refreshSearch();
        }
    }

    /**
     * Called when a fragment is first attached to its activity.
     * {@link #onCreate(Bundle)} will be called after this.
     *
     * @param aHostActivity parent activity
     */
    @Override
    public void onAttach(Activity aHostActivity) {
        super.onAttach(aHostActivity);
        mProgressView = getActivity().findViewById(R.id.search_load_oldest_progress);
    }

    /**
     * The user scrolls the list.
     * Apply an expected behaviour
     * @param event the scroll event
     */
    @Override
    public void onListTouch(MotionEvent event) {
    }

    /**
     * return true to display all the events.
     * else the unknown events will be hidden.
     */
    @Override
    public boolean isDisplayAllEvents() {
        return true;
    }

    /**
     * Display a global spinner or any UI item to warn the user that there are some pending actions.
     */
    @Override
    public void displayLoadingProgress() {
        if (null != mProgressView) {
            mProgressView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Dismiss any global spinner.
     */
    @Override
    public void dismissLoadingProgress() {
        if (null != mProgressView) {
            mProgressView.setVisibility(View.GONE);
        }
    }

    /**
     * Scroll the fragment to the bottom
     */
    @Override
    public void scrollToBottom() {
        if (0 != mAdapter.getCount()) {
            mMessageListView.setSelection(mAdapter.getCount() - 1);
        }
    }

    /**
     * Tell if the search is allowed for a dedicated pattern
     * @param pattern the searched pattern.
     * @return true if the search is allowed.
     */
    protected boolean allowSearch(String pattern) {
        // ConsoleMessageListFragment displays the list of unfiltered messages when there is no pattern
        // in the search case, clear the list and hide it
        return !TextUtils.isEmpty(pattern);
    }

    /**
     * Update the searched pattern.
     * @param pattern the pattern to find out. null to disable the search mode
     */
    @Override
    public void searchPattern(final String pattern, final OnSearchResultListener onSearchResultListener) {
        // add the listener to list to warn when the search is done.
        if (null != onSearchResultListener) {
            mSearchListeners.add(onSearchResultListener);
        }

        // wait that the fragment is displayed
        if (null == mMessageListView) {
            mPendingPattern = pattern;
            return;
        }

        // please wait
        if (TextUtils.equals(mSearchingPattern, pattern)) {
            mSearchListeners.add(onSearchResultListener);
            return;
        }

        if (!allowSearch(pattern)) {
            mPattern = null;
            mMessageListView.setVisibility(View.GONE);

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (OnSearchResultListener listener : mSearchListeners) {
                        try {
                            listener.onSearchSucceed(0);
                        } catch (Exception e) {
                        }
                    }
                    mSearchListeners.clear();
                    mSearchingPattern = null;
                }
            });
        } else {
            // start a search
            mAdapter.clear();
            mSearchingPattern = pattern;

            if (mAdapter instanceof VectorSearchMessagesListAdapter) {
                ((VectorSearchMessagesListAdapter) mAdapter).setTextToHighlight(pattern);
            }

            super.searchPattern(pattern, mIsMediaSearch,  new OnSearchResultListener() {
                @Override
                public void onSearchSucceed(int nbrMessages) {
                    // the pattern has been updated while search
                    if (!TextUtils.equals(pattern, mSearchingPattern)) {
                        mAdapter.clear();
                        mMessageListView.setVisibility(View.GONE);
                    } else {
                        // scroll to the bottom
                        scrollToBottom();
                        mMessageListView.setVisibility(View.VISIBLE);

                        for (OnSearchResultListener listener : mSearchListeners) {
                            try {
                                listener.onSearchSucceed(nbrMessages);
                            } catch (Exception e) {

                            }
                        }
                        mSearchListeners.clear();
                        mSearchingPattern = null;
                    }
                }

                @Override
                public void onSearchFailed() {
                    mMessageListView.setVisibility(View.GONE);

                    // clear the results list if teh search fails
                    mAdapter.clear();

                    for (OnSearchResultListener listener : mSearchListeners) {
                        try {
                            listener.onSearchFailed();
                        } catch (Exception e) {
                        }
                    }
                    mSearchListeners.clear();
                    mSearchingPattern = null;
                }
            });
        }
    }

    @Override
    public boolean onRowLongClick(int position) {
        onContentClick(position);
        return true;
    }

    @Override
    public void onContentClick(int position) {
        MessageRow row = mAdapter.getItem(position);
        Event event = row.getEvent();

        Message message = JsonUtils.toMessage(event.content);

        // medias are managed by the mother class
        if (Message.MSGTYPE_IMAGE.equals(message.msgtype) || Message.MSGTYPE_VIDEO.equals(message.msgtype) || Message.MSGTYPE_FILE.equals(message.msgtype)) {
            super.onContentClick(position);
        } else {

            final MessageRow messageRow = mAdapter.getItem(position);
            final List<Integer> textIds = new ArrayList<>();
            final List<Integer> iconIds = new ArrayList<Integer>();

            textIds.add(R.string.copy);
            iconIds.add(R.drawable.ic_material_copy);

            FragmentManager fm = getActivity().getSupportFragmentManager();
            IconAndTextDialogFragment fragment = (IconAndTextDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_MESSAGE_OPTIONS);

            if (fragment != null) {
                fragment.dismissAllowingStateLoss();
            }

            Integer[] lIcons = iconIds.toArray(new Integer[iconIds.size()]);
            Integer[] lTexts = textIds.toArray(new Integer[iconIds.size()]);

            fragment = IconAndTextDialogFragment.newInstance(lIcons, lTexts);
            fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
                @Override
                public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
                    final Integer selectedVal = textIds.get(position);

                    if (selectedVal == R.string.copy) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                                Event event = messageRow.getEvent();
                                EventDisplay display = new EventDisplay(getActivity(), event, null);

                                ClipData clip = ClipData.newPlainText("", display.getTextualDisplay().toString());
                                clipboard.setPrimaryClip(clip);

                                Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });

            fragment.show(fm, TAG_FRAGMENT_MESSAGE_OPTIONS);
        }
    }

    /**
     * Called when a long click is performed on the message content
     * @param position the cell position
     * @return true if managed
     */
    @Override
    public boolean onContentLongClick(int position) {
        Event event = mAdapter.getItem(position).getEvent();

        // pop to the home activity
        Intent intent = new Intent(getActivity(), VectorRoomMessagesContextActivity.class);
        intent.putExtra(VectorRoomMessagesContextActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
        intent.putExtra(VectorRoomMessagesContextActivity.EXTRA_ROOM_ID, event.roomId);
        intent.putExtra(VectorRoomMessagesContextActivity.EXTRA_EVENT_ID, event.eventId);

        getActivity().startActivity(intent);
        return true;


        //return onRowLongClick(position);
    }

    //==============================================================================================================
    // rooms events management : ignore any update on the adapter while searching
    //==============================================================================================================

    @Override
    public void onLiveEvent(final Event event, final RoomState roomState) {
    }

    @Override
    public void onLiveEventsChunkProcessed() {
    }

    @Override
    public void onBackEvent(final Event event, final RoomState roomState) {
    }

    @Override
    public void onReceiptEvent(List<String> senderIds){
    }
}
