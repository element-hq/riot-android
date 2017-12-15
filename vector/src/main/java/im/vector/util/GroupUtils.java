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

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.rest.model.group.GroupRoom;
import org.matrix.androidsdk.rest.model.group.GroupUser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.activity.VectorRoomActivity;

public class GroupUtils {
    private static final String LOG_TAG = GroupUtils.class.getSimpleName();

    /**
     * Create a list of groups by filtering the given list with the given pattern
     *
     * @param groupsToFilter
     * @param constraint
     * @return filtered groups
     */
    public static List<Group> getFilteredGroups(final List<Group> groupsToFilter, final CharSequence constraint) {
        final String filterPattern = constraint != null ? constraint.toString().trim() : null;
        if (!TextUtils.isEmpty(filterPattern)) {
            List<Group> filteredGroups = new ArrayList<>();
            Pattern pattern = Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE);

            for (final Group group : groupsToFilter) {
                if (pattern.matcher(group.getDisplayName()).find()) {
                    filteredGroups.add(group);
                }
            }
            return filteredGroups;
        } else {
            return groupsToFilter;
        }
    }

    /**
     * Create a list of groups by filtering the given list with the given pattern
     *
     * @param groupsUsersToFilter
     * @param constraint
     * @return filtered group users
     */
    public static List<GroupUser> getFilteredGroupUsers(final List<GroupUser> groupsUsersToFilter, final CharSequence constraint) {
        final String filterPattern = constraint != null ? constraint.toString().trim() : null;
        if (!TextUtils.isEmpty(filterPattern)) {
            List<GroupUser> filteredGroupUsers = new ArrayList<>();
            Pattern pattern = Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE);

            for (final GroupUser groupUser : groupsUsersToFilter) {
                if (pattern.matcher(groupUser.getDisplayname()).find()) {
                    filteredGroupUsers.add(groupUser);
                }
            }
            return filteredGroupUsers;
        } else {
            return groupsUsersToFilter;
        }
    }

    /**
     * Create a list of groups by filtering the given list with the given pattern
     *
     * @param groupRoomsToFilter
     * @param constraint
     * @return filtered group users
     */
    public static List<GroupRoom> getFilteredGroupRooms(final List<GroupRoom> groupRoomsToFilter, final CharSequence constraint) {
        final String filterPattern = constraint != null ? constraint.toString().trim() : null;
        if (!TextUtils.isEmpty(filterPattern)) {
            List<GroupRoom> filteredGroupRooms = new ArrayList<>();
            Pattern pattern = Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE);

            for (final GroupRoom groupRoom : groupRoomsToFilter) {
                if (pattern.matcher(groupRoom.getDisplayName()).find()) {
                    filteredGroupRooms.add(groupRoom);
                }
            }
            return filteredGroupRooms;
        } else {
            return groupRoomsToFilter;
        }
    }

    /**
     * Open the detailed group user page
     *
     * @param fromActivity the caller activity
     * @param session      the session
     * @param groupUser    the group user
     */
    public static void openGroupUserPage(Activity fromActivity, MXSession session, GroupUser groupUser) {
        Intent userIntent = new Intent(fromActivity, VectorMemberDetailsActivity.class);
        userIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, groupUser.userId);

        if (!TextUtils.isEmpty(groupUser.avatarUrl)) {
            userIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_AVATAR_URL, groupUser.avatarUrl);
        }

        if (!TextUtils.isEmpty(groupUser.displayname)) {
            userIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_DISPLAY_NAME, groupUser.displayname);
        }

        userIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, session.getCredentials().userId);
        fromActivity.startActivity(userIntent);
    }

    /**
     * Open the detailed group room page
     *
     * @param fromActivity the caller activity
     * @param session      the session
     * @param groupRoom    the group room
     */
    public static void openGroupRoom(final Activity fromActivity, final MXSession session, final GroupRoom groupRoom, final SimpleApiCallback<Void> callback) {
        Room room = session.getDataHandler().getStore().getRoom(groupRoom.roomId);

        if ((null == room) || (null == room.getMember(session.getMyUserId()))) {
            final RoomPreviewData roomPreviewData = new RoomPreviewData(session, groupRoom.roomId, null, groupRoom.getAlias(), null);

            roomPreviewData.fetchPreviewData(new ApiCallback<Void>() {
                private void onDone() {
                    if (null != callback) {
                        callback.onSuccess(null);
                    }

                    CommonActivityUtils.previewRoom(fromActivity, roomPreviewData);
                }

                @Override
                public void onSuccess(Void info) {
                    onDone();
                }

                private void onError() {
                    roomPreviewData.setRoomState(groupRoom);
                    roomPreviewData.setRoomName(groupRoom.name);
                    onDone();
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
        } else {
            Intent roomIntent = new Intent(fromActivity, VectorRoomActivity.class);
            roomIntent.putExtra(VectorRoomActivity.EXTRA_MATRIX_ID, session.getMyUserId());
            roomIntent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, groupRoom.roomId);
            fromActivity.startActivity(roomIntent);

            if (null != callback) {
                callback.onSuccess(null);
            }
        }
    }
}
