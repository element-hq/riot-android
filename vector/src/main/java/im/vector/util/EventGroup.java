/*
 * Copyright 2017 Vector Creations Ltd
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

package im.vector.util;

import android.content.Context;
import android.text.TextUtils;

import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.rest.model.Event;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.vector.R;
import im.vector.adapters.AdapterUtils;

import org.matrix.androidsdk.util.Log;

/**
 * A EventGroup is a special event that can contain MessageRows
 */
public class EventGroup extends Event {
    private static final String LOG_TAG = EventGroup.class.getSimpleName();

    // events rows map
    private final Map<String, MessageRow> mRowsMap;

    // rows list (ordered)
    private final List<MessageRow> mRows;

    // true the merged events are expanded
    private boolean mIsExpanded;

    // hidden event ids list
    private final Set<String> mHiddenEventIds;

    /**
     * Constructors
     */
    public EventGroup(Set<String> hiddenGroupIds) {
        // defines an MessageRowGroup unique ID
        eventId = getClass().getName() + '@' + Integer.toHexString(hashCode()) + "-" + System.currentTimeMillis();

        // init field
        mRowsMap = new HashMap<>();
        mRows = new ArrayList<>();
        mHiddenEventIds = hiddenGroupIds;
    }

    /**
     * Tells if the message row is supported.
     *
     * @param row the message row
     * @return true it is supported
     */
    public static boolean isSupported(MessageRow row) {
        return (null != row) && (null != row.getEvent()) &&
                TextUtils.equals(row.getEvent().getType(), Event.EVENT_TYPE_STATE_ROOM_MEMBER) &&
                // do not merge the call invitation events
                !TextUtils.equals(row.getEvent().stateKey, MXCallsManager.getConferenceUserId(row.getEvent().roomId));
    }

    /**
     * Tells if a messageRow is defined for the provided eventId.
     *
     * @param eventId the event id
     * @return true if a matched MessageRow exists
     */
    public boolean contains(String eventId) {
        return (null != eventId) && mRowsMap.containsKey(eventId);
    }

    /**
     * Tells if a messageRow is defined in this group.
     *
     * @param row the message row
     * @return true if the messageRow is defined in this group.
     */
    private boolean contains(MessageRow row) {
        return (null != row) && (null != row.getEvent()) && mRowsMap.containsKey(row.getEvent().eventId);
    }

    /**
     * Update the event Ts to the first item.
     */
    private void refreshOriginServerTs() {
        if (mRows.size() > 0) {
            this.originServerTs = mRows.get(0).getEvent().originServerTs;
        }
    }

    /**
     * Update inner members after adding a new MessageRow.
     *
     * @param row the added MessageRow.
     */
    private void onRowAdded(MessageRow row) {
        // update the map
        String addedEventId = row.getEvent().eventId;
        mRowsMap.put(addedEventId, row);

        // the group is is hidden if t
        if (mRowsMap.size() > 1) {
            if (mHiddenEventIds.contains(eventId)) {
                mHiddenEventIds.remove(eventId);

                if (mIsExpanded) {
                    mHiddenEventIds.removeAll(mRowsMap.keySet());
                } else {
                    mHiddenEventIds.addAll(mRowsMap.keySet());
                }

            } else {
                if (mIsExpanded) {
                    mHiddenEventIds.remove(addedEventId);
                } else {
                    mHiddenEventIds.add(addedEventId);
                }
            }
        } else {
            mHiddenEventIds.removeAll(mRowsMap.keySet());
            mHiddenEventIds.add(eventId);
        }

        refreshOriginServerTs();
    }

    /**
     * Add a new message row
     *
     * @param row the new message row
     */
    public void add(MessageRow row) {
        if (!contains(row)) {
            mRows.add(row);
            onRowAdded(row);
        }
    }

    /**
     * Add a message row to the list top
     *
     * @param row the new message row
     */
    public void addToFront(MessageRow row) {
        if (!contains(row)) {
            if (mRows.size() > 0) {
                mRows.add(0, row);
            } else {
                mRows.add(row);
            }
            onRowAdded(row);
        }
    }

    /**
     * @return true if there is no more items
     */
    public boolean isEmpty() {
        return mRows.isEmpty();
    }

    /**
     * @return true if the group is expanded .
     */
    public boolean isExpanded() {
        return mIsExpanded;
    }

    /**
     * Update the expand status.
     *
     * @param isExpanded the new expand status
     */
    public void setIsExpanded(boolean isExpanded) {
        if (mRows.size() < 2) {
            Log.e(LOG_TAG, "## setIsExpanded() : cannot collapse a group when there is only one item");
            mIsExpanded = true;
            mHiddenEventIds.add(eventId);
        } else {
            mIsExpanded = isExpanded;
        }

        if (mIsExpanded) {
            mHiddenEventIds.removeAll(mRowsMap.keySet());
        } else {
            mHiddenEventIds.addAll(mRowsMap.keySet());
        }
    }

    /**
     * Tells if a message row can be added to this group.
     *
     * @param row the message row to test
     * @return true if the message row can be added
     */
    public boolean canAddRow(MessageRow row) {
        return isEmpty() ||
                (AdapterUtils.zeroTimeDate(new Date(row.getEvent().getOriginServerTs())).getTime() ==
                        AdapterUtils.zeroTimeDate(new Date(getOriginServerTs())).getTime());
    }

    /**
     * Remove an entry by its eventId.
     *
     * @param eventIdToRemove the event id to remove
     */
    public void removeByEventId(String eventIdToRemove) {
        if (null != eventIdToRemove) {
            MessageRow row = mRowsMap.get(eventIdToRemove);

            if (null != row) {
                mRowsMap.remove(eventIdToRemove);
                mRows.remove(row);

                mHiddenEventIds.remove(eventIdToRemove);
            }

            if (mRowsMap.size() == 1) {
                mHiddenEventIds.removeAll(mRowsMap.keySet());
                mHiddenEventIds.add(eventId);
            }

            refreshOriginServerTs();
        }
    }

    /**
     * @return a copy of the rows
     */
    public List<MessageRow> getRows() {
        return new ArrayList<>(mRows);
    }

    /**
     * Provides a message rows list to display unique avatars
     *
     * @param maxCount the max number of items
     * @return the messages row list
     */
    public List<MessageRow> getAvatarRows(int maxCount) {
        Set<String> senders = new HashSet<>();
        List<MessageRow> rows = new ArrayList<>();

        for (MessageRow row : mRows) {
            String rowSender = row.getEvent().sender;

            if ((null != rowSender) && !senders.contains(rowSender)) {
                rows.add(row);
                senders.add(rowSender);

                if (senders.size() == maxCount) {
                    break;
                }
            }
        }

        return rows;
    }

    public java.lang.String toString(Context context) {
        return context.getResources().getQuantityString(R.plurals.membership_changes, mRowsMap.size(), mRowsMap.size());
    }
}
