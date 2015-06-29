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

package org.matrix.console.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.console.Matrix;
import org.matrix.console.R;
import org.matrix.androidsdk.adapters.RoomSummaryAdapter;

import java.util.ArrayList;
import java.util.Collection;

/**
 * An adapter which can display room information.
 */
public class ConsoleRoomSummaryAdapter extends RoomSummaryAdapter {

    private ArrayList<MXSession> mSessions = null;

    public ConsoleRoomSummaryAdapter(Context context, Collection<MXSession> sessions, int layoutResourceId, int headerLayoutResourceId)  {
        super(context, sessions.size(), layoutResourceId, headerLayoutResourceId);
        mSessions = new ArrayList<MXSession>(sessions);
    }

    public int getUnreadMessageBackgroundColor() {
        return mContext.getResources().getColor(R.color.room_summary_unread_background);
    }

    public int getHighlightMessageBackgroundColor() {
        return mContext.getResources().getColor(R.color.room_summary_highlight_background);
    }

    public int getPublicHighlightMessageBackgroundColor() {
        return mContext.getResources().getColor(R.color.room_summary_public_highlight_background);
    }

    /**
     * Retrieve a Room from a room summary
     * @param roomSummary the room roomId to retrieve.
     * @return the Room.
     */
    public Room roomFromRoomSummary(RoomSummary roomSummary) {
        return Matrix.getMXSession(mContext, roomSummary.getMatrixId()).getDataHandler().getStore().getRoom(roomSummary.getRoomId());
    }

    public String memberDisplayName(String matrixId, String userId) {
        User user = Matrix.getMXSession(mContext, matrixId).getDataHandler().getStore().getUser(userId);

        if ((null != user) && !TextUtils.isEmpty(user.displayname)) {
            return user.displayname;
        }

        return userId;
    }

    public boolean displayPublicRooms() {
        // the user can force to clear the public rooms with the recents ones
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getBoolean(mContext.getString(R.string.settings_key_display_public_rooms_recents), true);
    }

    public String myRoomsTitle(int section) {
        if (mSessions.size() == 1) {
            return mContext.getResources().getString(R.string.my_rooms);
        } else {
            return mSessions.get(section).getMyUser().userId;
        }
    }

    public String publicRoomsTitle(int section) {
        String title = mContext.getResources().getString(R.string.action_public_rooms);

        if ((null != mPublicRoomsHomeServerLists) && (mPublicRoomsHomeServerLists.size() > 1)) {
            title += "\n" + mPublicRoomsHomeServerLists.get(section - mSessions.size());
        }

        return title;
    }

    @Override
    public void removeSection(int section) {
        super.removeSection(section);
        mSessions.remove(section);
    }
}
