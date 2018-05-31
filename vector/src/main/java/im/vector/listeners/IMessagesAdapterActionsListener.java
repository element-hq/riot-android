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

package im.vector.listeners;

import android.net.Uri;

import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.rest.model.Event;

import java.util.List;

/**
 * Actions listeners
 */
public interface IMessagesAdapterActionsListener {
    /**
     * Call when the row is clicked.
     *
     * @param position the cell position.
     */
    void onRowClick(int position);

    /**
     * Call when the row is long clicked.
     *
     * @param position the cell position.
     * @return true if managed
     */
    boolean onRowLongClick(int position);

    /**
     * Called when a click is performed on the message content
     *
     * @param position the cell position
     */
    void onContentClick(int position);

    /**
     * Called when a long click is performed on the message content
     *
     * @param position the cell position
     * @return true if managed
     */
    boolean onContentLongClick(int position);

    /**
     * Define the action to perform when the user tap on an avatar
     *
     * @param userId the user ID
     */
    void onAvatarClick(String userId);

    /**
     * Define the action to perform when the user performs a long tap on an avatar
     *
     * @param userId the user ID
     * @return true if the long click event is managed
     */
    boolean onAvatarLongClick(String userId);

    /**
     * Define the action to perform when the user taps on the message sender
     *
     * @param userId      the sender user id.
     * @param displayName the sender display name.
     */
    void onSenderNameClick(String userId, String displayName);

    /**
     * A media download is done
     *
     * @param position the downloaded media list position.
     */
    void onMediaDownloaded(int position);

    /**
     * Define the action to perform when the user taps on the more read receipts button.
     *
     * @param eventId the eventID
     */
    void onMoreReadReceiptClick(String eventId);

    /**
     * Define the action to perform when the group flairs is clicked.
     *
     * @param userId the user id
     * @param groupIds the group ids list
     */
    void onGroupFlairClick(String userId, List<String> groupIds);

    /**
     * An url has been clicked in a message text.
     *
     * @param uri the uri.
     */
    void onURLClick(Uri uri);

    /**
     * Tells if an event body must be highlighted
     *
     * @param event the event
     * @return true to highlight it.
     */
    boolean shouldHighlightEvent(Event event);

    /**
     * An user id has been clicked in a message body.
     *
     * @param userId the user id.
     */
    void onMatrixUserIdClick(String userId);

    /**
     * A room alias has been clicked in a message body.
     *
     * @param roomAlias the roomAlias.
     */
    void onRoomAliasClick(String roomAlias);

    /**
     * A room id has been clicked in a message body.
     *
     * @param roomId the room id.
     */
    void onRoomIdClick(String roomId);

    /**
     * A message id has been clicked in a message body.
     *
     * @param messageId the message id.
     */
    void onMessageIdClick(String messageId);

    /**
     * A group id has been clicked in a message body.
     *
     * @param groupId the group id.
     */
    void onGroupIdClick(String groupId);

    /**
     * The required indexes are not anymore valid.
     */
    void onInvalidIndexes();

    /**
     * An action has been  triggered on an event.
     *
     * @param event   the event.
     * @param textMsg the text message
     * @param action  an action ic_action_vector_XXX
     */
    void onEventAction(final Event event, final String textMsg, final int action);

    /**
     * the user taps on the e2e icon
     *
     * @param event      the event
     * @param deviceInfo the deviceinfo
     */
    void onE2eIconClick(final Event event, final MXDeviceInfo deviceInfo);
}
