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

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;
import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.MXCActionBarActivity;
import im.vector.activity.MemberDetailsActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.activity.VectorMediasViewerActivity;
import im.vector.adapters.VectorMessagesAdapter;
import im.vector.db.VectorContentProvider;
import im.vector.util.SlidableMediaInfo;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConsoleMessageListFragment extends MatrixMessageListFragment implements VectorMessagesAdapter.VectorMessagesAdapterActionsListener {
    private static final String TAG_FRAGMENT_RECEIPTS_DIALOG = "ConsoleMessageListFragment.TAG_FRAGMENT_RECEIPTS_DIALOG";

    public static ConsoleMessageListFragment newInstance(String matrixId, String roomId, int layoutResId) {
        ConsoleMessageListFragment f = new ConsoleMessageListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);
        f.setArguments(args);
        return f;
    }

    @Override
    public MXSession getSession(String matrixId) {
        return Matrix.getMXSession(getActivity(), matrixId);
    }

    @Override
    public MXMediasCache getMXMediasCache() {
        return Matrix.getInstance(getActivity()).getMediasCache();
    }

    @Override
    public MessagesAdapter createMessagesAdapter() {
        VectorMessagesAdapter vectorMessagesAdapter = new VectorMessagesAdapter(mSession, getActivity(), getMXMediasCache());
        vectorMessagesAdapter.setVectorMessagesAdapterActionsListener(this);
        return vectorMessagesAdapter;
    }

    /**
     * The user scrolls the list.
     * Apply an expected behaviour
     * @param event the scroll event
     */
    @Override
    public void onListTouch(MotionEvent event) {
        // the user scroll over the keyboard
        // hides the keyboard
        if (mCheckSlideToHide && (event.getY() > mMessageListView.getHeight())) {
            mCheckSlideToHide = false;
            MXCActionBarActivity.dismissKeyboard(getActivity());
        }
    }

    /**
     * An action has been  triggered on an event.
     * @param event the event.
     * @param action an action ic_action_vector_XXX
     */
    public void onEventAction(final Event event, final int action) {

        if (action == R.id.ic_action_vector_view_profile) {
            if (null != event.getSender()) {
                Intent startRoomInfoIntent = new Intent(getActivity(), MemberDetailsActivity.class);
                startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_MEMBER_ID, event.getSender());
                startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                getActivity().startActivity(startRoomInfoIntent);
            }
        } else if (action == R.id.ic_action_vector_direct_message) {
            if (null != event.getSender()) {
                CommonActivityUtils.goToOneToOneRoom(mSession, event.getSender(), getActivity(), null);
            }
        } else if (action == R.id.ic_action_vector_paste_user_name) {
            String displayName = event.getSender();
            RoomState state = mRoom.getLiveState();

            if (null != state) {
                displayName = state.getMemberName(displayName);
            }

            onSenderNameClick(event.getSender(), displayName);
        } else if (action == R.id.ic_action_vector_paste_user_name) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resend(event);
                }
            });
        } else if (action == R.id.ic_action_vector_delete_message) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    redactEvent(event.eventId);
                }
            });
        } else if (action == R.id.ic_action_vector_resend_message) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resend(event);
                }
            });
        } else if (action == R.id.ic_action_vector_copy) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    String text = "";

                    if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type) ||
                            Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) ||
                            Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {

                        RoomState roomState = mRoom.getLiveState();
                        EventDisplay display = new EventDisplay(getActivity(), event, roomState);
                        text = display.getTextualDisplay().toString();
                    } else {
                        text = JsonUtils.toMessage(event.content).body;
                    }

                    ClipData clip = ClipData.newPlainText("", text);
                    clipboard.setPrimaryClip(clip);
                }
            });
        } else if (/*(action == R.id.ic_action_vector_share) || (action == R.id.ic_action_vector_forward) ||*/ (action == R.id.ic_action_vector_save)) {
            //
            String mediaUrl = null;
            String mediaMimeType = null;
            Uri mediaUri = null;
            Message message = JsonUtils.toMessage(event.content);

            // check if the media has been downloaded
            if ((message instanceof ImageMessage) || (message instanceof FileMessage)) {
                if (message instanceof ImageMessage) {
                    ImageMessage imageMessage = (ImageMessage) message;

                    mediaUrl = imageMessage.url;
                    mediaMimeType = imageMessage.getMimeType();
                } else {
                    FileMessage fileMessage = (FileMessage) message;

                    mediaUrl = fileMessage.url;
                    mediaMimeType = fileMessage.getMimeType();
                }

                MXMediasCache cache = Matrix.getInstance(getActivity()).getMediasCache();

                File mediaFile = cache.mediaCacheFile(mediaUrl, mediaMimeType);

                if (null != mediaFile) {
                    try {
                        mediaUri = VectorContentProvider.absolutePathToUri(getActivity(), mediaFile.getAbsolutePath());
                    } catch (Exception e) {
                    }
                }
            }

            if (action == R.id.ic_action_vector_save) {
                final Message fMessage = message;
                final String fmediaUrl = mediaUrl;
                final String fmediaMimeType = mediaMimeType;

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        save(fMessage, fmediaUrl, fmediaMimeType);
                    }
                });

            } /*else {
                final Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);

                if (null != mediaUri) {
                    sendIntent.setType(mediaMimeType);
                    sendIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
                } else {
                    sendIntent.putExtra(Intent.EXTRA_TEXT, message.body);
                    sendIntent.setType("text/plain");
                }

                if (action == R.id.ic_action_vector_forward) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            CommonActivityUtils.sendFilesTo(getActivity(), sendIntent);
                        }
                    });
                } else {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(sendIntent);
                        }
                    });
                }
            }*/
        }
    }

    /**
     * return true to display all the events.
     * else the unknown events will be hidden.
     */
    @Override
    public boolean isDisplayAllEvents() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        return preferences.getBoolean(getString(R.string.settings_key_display_all_events), false);
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
                        final View progressView = getActivity().findViewById(R.id.loading_room_content_progress);

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
                        final View progressView = getActivity().findViewById(R.id.loading_room_content_progress);

                        if (null != progressView) {
                            progressView.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }
    }

    /**
     * logout from the application
     */
    @Override
    public void logout() {
        CommonActivityUtils.logout(ConsoleMessageListFragment.this.getActivity());
    }

    public Boolean onRowLongClick(int position) {
        final MessageRow messageRow = mAdapter.getItem(position);
        final List<Integer> textIds = new ArrayList<>();
        final List<Integer> iconIds = new ArrayList<Integer>();

        String mediaUrl = null;
        String mediaMimeType = null;
        Uri mediaUri = null;
        Message message = JsonUtils.toMessage(messageRow.getEvent().content);

        if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(messageRow.getEvent().type) ||
                Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(messageRow.getEvent().type) ||
                Event.EVENT_TYPE_STATE_ROOM_NAME.equals(messageRow.getEvent().type) ||
                Message.MSGTYPE_EMOTE.equals(message.msgtype)
                ) {

            if (!messageRow.getEvent().getSender().equals(getSession().getCredentials().userId)) {
                textIds.add(R.string.paste_username);
                iconIds.add(R.drawable.ic_material_paste);
            }

            textIds.add(R.string.copy);
            iconIds.add(R.drawable.ic_material_copy);
        } else  {

            // copy the message body
            if (Event.EVENT_TYPE_MESSAGE.equals(messageRow.getEvent().type)) {

                if (!messageRow.getEvent().getSender().equals(getSession().getCredentials().userId)) {
                    textIds.add(R.string.paste_username);
                    iconIds.add(R.drawable.ic_material_paste);
                }

                if (Message.MSGTYPE_TEXT.equals(message.msgtype)) {
                    textIds.add(R.string.copy);
                    iconIds.add(R.drawable.ic_material_copy);
                }
            }

            if (messageRow.getEvent().canBeResent()) {
                textIds.add(R.string.resend);
                iconIds.add(R.drawable.ic_material_send);
            } else if (messageRow.getEvent().mSentState == Event.SentState.SENT) {
                textIds.add(R.string.redact);
                iconIds.add(R.drawable.ic_material_clear);
                if (Event.EVENT_TYPE_MESSAGE.equals(messageRow.getEvent().type)) {
                    Boolean supportShare = true;

                    // check if the media has been downloaded
                    if ((message instanceof ImageMessage) || (message instanceof FileMessage)) {
                        if (message instanceof ImageMessage) {
                            ImageMessage imageMessage = (ImageMessage) message;

                            mediaUrl = imageMessage.url;
                            mediaMimeType = imageMessage.getMimeType();
                        } else {
                            FileMessage fileMessage = (FileMessage) message;

                            mediaUrl = fileMessage.url;
                            mediaMimeType = fileMessage.getMimeType();
                        }

                        supportShare = false;
                        MXMediasCache cache = getMXMediasCache();

                        File mediaFile = cache.mediaCacheFile(mediaUrl, mediaMimeType);

                        if (null != mediaFile) {
                            try {
                                mediaUri = VectorContentProvider.absolutePathToUri(getActivity(), mediaFile.getAbsolutePath());
                                supportShare = true;
                            } catch (Exception e) {
                            }
                        }
                    }

                    if (supportShare) {
                        textIds.add(R.string.share);
                        iconIds.add(R.drawable.ic_material_share);

                        textIds.add(R.string.forward);
                        iconIds.add(R.drawable.ic_material_forward);

                        textIds.add(R.string.save);
                        iconIds.add(R.drawable.ic_material_save);
                    }
                }
            }
        }

        // display the JSON
        textIds.add(R.string.message_details);
        iconIds.add(R.drawable.ic_material_description);

        FragmentManager fm = getActivity().getSupportFragmentManager();
        IconAndTextDialogFragment fragment = (IconAndTextDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_MESSAGE_OPTIONS);

        if (fragment != null) {
            fragment.dismissAllowingStateLoss();
        }

        Integer[] lIcons = iconIds.toArray(new Integer[iconIds.size()]);
        Integer[] lTexts = textIds.toArray(new Integer[iconIds.size()]);

        final String  fmediaMimeType = mediaMimeType;
        final Uri fmediaUri = mediaUri;
        final String fmediaUrl = mediaUrl;
        final Message fMessage = message;

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
                            String text = "";

                            if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(messageRow.getEvent().type) ||
                                    Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(messageRow.getEvent().type) ||
                                    Event.EVENT_TYPE_STATE_ROOM_NAME.equals(messageRow.getEvent().type)) {

                                RoomState roomState = messageRow.getRoomState();
                                EventDisplay display = new EventDisplay(getActivity(), event, roomState);
                                text = display.getTextualDisplay().toString();
                            } else {
                                text = JsonUtils.toMessage(event.content).body;
                            }

                            ClipData clip = ClipData.newPlainText("", text);
                            clipboard.setPrimaryClip(clip);
                        }
                    });
                } else if (selectedVal == R.string.resend) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            resend(messageRow.getEvent());
                        }
                    });
                } else if (selectedVal == R.string.save) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            save(fMessage, fmediaUrl, fmediaMimeType);
                        }
                    });
                } else if (selectedVal == R.string.redact) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            redactEvent(messageRow.getEvent().eventId);
                        }
                    });
                } else if ((selectedVal == R.string.share) || (selectedVal == R.string.forward)) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Intent sendIntent = new Intent();
                            sendIntent.setAction(Intent.ACTION_SEND);

                            Event event = messageRow.getEvent();
                            Message message = JsonUtils.toMessage(event.content);

                            if (null != fmediaUri) {
                                sendIntent.setType(fmediaMimeType);
                                sendIntent.putExtra(Intent.EXTRA_STREAM, fmediaUri);
                            } else {
                                sendIntent.putExtra(Intent.EXTRA_TEXT, message.body);
                                sendIntent.setType("text/plain");
                            }

                            if (selectedVal == R.string.forward) {
                                CommonActivityUtils.sendFilesTo(getActivity(), sendIntent);
                            } else {
                                startActivity(sendIntent);
                            }
                        }
                    });
                } else if (selectedVal == R.string.message_details) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            FragmentManager fm =  getActivity().getSupportFragmentManager();

                            MessageDetailsFragment fragment = (MessageDetailsFragment) fm.findFragmentByTag(TAG_FRAGMENT_MESSAGE_DETAILS);
                            if (fragment != null) {
                                fragment.dismissAllowingStateLoss();
                            }
                            fragment = MessageDetailsFragment.newInstance(messageRow.getEvent().toString());
                            fragment.show(fm, TAG_FRAGMENT_MESSAGE_DETAILS);
                        }
                    });
                } else if (selectedVal == R.string.paste_username) {
                    String displayName = messageRow.getEvent().getSender();
                    RoomState state = messageRow.getRoomState();

                    if (null != state) {
                        displayName = state.getMemberName(displayName);
                    }

                    onSenderNameClick(messageRow.getEvent().getSender(), displayName);
                }
            }
        });

        fragment.show(fm, TAG_FRAGMENT_MESSAGE_OPTIONS);

        return true;
    }

    /**
     * @return the image and video messages list
     */
    private ArrayList<SlidableMediaInfo> listSlidableMessages() {
        ArrayList<SlidableMediaInfo> res = new ArrayList<SlidableMediaInfo>();

        for(int position = 0; position < mAdapter.getCount(); position++) {
            MessageRow row = mAdapter.getItem(position);
            Message message = JsonUtils.toMessage(row.getEvent().content);

            if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
                ImageMessage imageMessage = (ImageMessage)message;

                SlidableMediaInfo info = new SlidableMediaInfo();
                info.mMessageType = Message.MSGTYPE_IMAGE;
                info.mMediaUrl = imageMessage.url;
                info.mRotationAngle = imageMessage.getRotation();
                info.mOrientation = imageMessage.getOrientation();
                info.mMimeType = imageMessage.getMimeType();
                info.midentifier = row.getEvent().eventId;
                res.add(info);
            } else if (Message.MSGTYPE_VIDEO.equals(message.msgtype)) {
                SlidableMediaInfo info = new SlidableMediaInfo();
                VideoMessage videoMessage = (VideoMessage)message;

                info.mMessageType = Message.MSGTYPE_VIDEO;
                info.mMediaUrl = videoMessage.url;
                info.mThumbnailUrl = (null != videoMessage.info) ?  videoMessage.info.thumbnail_url : null;
                info.mMimeType = videoMessage.getVideoMimeType();
                res.add(info);
            }
        }

        return res;
    }

    /**
     * Returns the mediageMessage position in listMediaMessages.
     * @param mediaMessagesList the media messages list
     * @param mediaMessage the imageMessage
     * @return the imageMessage position. -1 if not found.
     */
    private int getMediaMessagePosition(ArrayList<SlidableMediaInfo> mediaMessagesList, Message mediaMessage) {
        String url = null;

        if (mediaMessage instanceof ImageMessage) {
            url = ((ImageMessage)mediaMessage).url;
        } else if (mediaMessage instanceof VideoMessage) {
            url = ((VideoMessage)mediaMessage).url;
        }

        // sanity check
        if (null == url) {
            return -1;
        }

        for(int index = 0; index < mediaMessagesList.size(); index++) {
            if (mediaMessagesList.get(index).mMediaUrl.equals(url)) {
                return index;
            }
        }

        return -1;
    }

    /**
     * Call when the row is clicked.
     * @param position the cell position.
     */
    public void onRowClick(int position) {
        MessageRow row = mAdapter.getItem(position);
        Event event = row.getEvent();

        // switch in section mode
        ((VectorMessagesAdapter)mAdapter).onEventTap(event.eventId);
    }

    /**
     * Called when a click is performed on the message content
     * @param position the cell position
     */
    public void onContentClick(int position) {
        MessageRow row = mAdapter.getItem(position);
        Event event = row.getEvent();

        VectorMessagesAdapter vectorMessagesAdapter = (VectorMessagesAdapter)mAdapter;

        if (vectorMessagesAdapter.isInSelectionMode()) {
            // cancel the selection mode.
            vectorMessagesAdapter.onEventTap(null);
            return;
        }

        Message message = JsonUtils.toMessage(event.content);

        // image message -> display it within the medias swipper
        if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
            ImageMessage imageMessage = JsonUtils.toImageMessage(event.content);

            if (null != imageMessage.url) {
                ArrayList<SlidableMediaInfo> mediaMessagesList = listSlidableMessages();
                int listPosition = getMediaMessagePosition(mediaMessagesList, imageMessage);

                if (listPosition >= 0) {
                    Intent viewImageIntent = new Intent(getActivity(), VectorMediasViewerActivity.class);

                    viewImageIntent.putExtra(VectorMediasViewerActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_WIDTH, mAdapter.getMaxThumbnailWith());
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_HEIGHT, mAdapter.getMaxThumbnailHeight());
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_INFO_LIST, mediaMessagesList);
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_INFO_LIST_INDEX, listPosition);

                    getActivity().startActivity(viewImageIntent);
                }
            }
        } else if (Message.MSGTYPE_FILE.equals(message.msgtype)) {
            FileMessage fileMessage = JsonUtils.toFileMessage(event.content);

            if (null != fileMessage.url) {
                File mediaFile =  mSession.getMediasCache().mediaCacheFile(fileMessage.url, fileMessage.getMimeType());

                // is the file already saved
                if (null != mediaFile) {
                    String savedMediaPath = CommonActivityUtils.saveMediaIntoDownloads(getActivity(), mediaFile, fileMessage.body, fileMessage.getMimeType());
                    CommonActivityUtils.openMedia(getActivity(), savedMediaPath, fileMessage.getMimeType());
                } else {
                    mSession.getMediasCache().downloadMedia(getActivity(), mSession.getHomeserverConfig(), fileMessage.url, fileMessage.getMimeType());
                    mAdapter.notifyDataSetChanged();
                }
            }
        } else if (Message.MSGTYPE_VIDEO.equals(message.msgtype)) {
            VideoMessage videoMessage = JsonUtils.toVideoMessage(event.content);

            if (null != videoMessage.url) {
                ArrayList<SlidableMediaInfo> mediaMessagesList = listSlidableMessages();
                int listPosition = getMediaMessagePosition(mediaMessagesList, videoMessage);

                if (listPosition >= 0) {
                    Intent viewImageIntent = new Intent(getActivity(), VectorMediasViewerActivity.class);

                    viewImageIntent.putExtra(VectorMediasViewerActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_WIDTH, getMaxThumbnailWith());
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_HEIGHT, getMaxThumbnailHeight());
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_INFO_LIST, mediaMessagesList);
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_INFO_LIST_INDEX, listPosition);

                    getActivity().startActivity(viewImageIntent);
                }
            }
        } else {
            // switch in section mode
            vectorMessagesAdapter.onEventTap(event.eventId);
        }
    }

    /**
     * Called when a long click is performed on the message content
     * @param position the cell position
     * @return true if managed
     */
    public Boolean onContentLongClick(int position) {
        return onRowLongClick(position);
    }

    /**
     * Define the action to perform when the user tap on an avatar
     * @param userId the user ID
     */
    public void onAvatarClick(String userId) {
        Intent startRoomInfoIntent = new Intent(getActivity(), MemberDetailsActivity.class);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_MEMBER_ID, userId);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
        getActivity().startActivity(startRoomInfoIntent);
    }

    /**
     * Define the action to perform when the user performs a long tap on an avatar
     * @param userId the user ID
     * @return true if the long clik event is managed
     */
    public Boolean onAvatarLongClick(String userId) {
        return false;
    }

    /**
     * Define the action to perform when the user taps on the message sender
     * @param userId
     * @param displayName
     */
    public void onSenderNameClick(String userId, String displayName) {
        if (getActivity() instanceof VectorRoomActivity) {
            ((VectorRoomActivity)getActivity()).insertInTextEditor(displayName);
        }
    }

    /**
     * A media download is done
     * @param position
     */
    public void onMediaDownloaded(int position) {
    }


    private void save(final Message message, final String mediaUrl, final String mediaMimeType) {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(getActivity());

        builderSingle.setTitle(getActivity().getText(R.string.save_files_in));
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.dialog_room_selection);

        ArrayList<String> entries = new ArrayList<String>();

        entries.add(getActivity().getText(R.string.downloads).toString());

        if ((null == mediaMimeType) || mediaMimeType.startsWith("image/")) {
            entries.add(getActivity().getText(R.string.gallery).toString());
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            entries.add(getActivity().getText(R.string.other).toString());
        }

        arrayAdapter.addAll(entries);

        final ArrayList<String> fEntries = entries;

        builderSingle.setNegativeButton(getActivity().getText(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builderSingle.setAdapter(arrayAdapter,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, final int which) {
                        dialog.dismiss();

                        MXMediasCache cache = getMXMediasCache();
                        File cacheFile = cache.mediaCacheFile(mediaUrl, mediaMimeType);

                        String entry = fEntries.get(which);
                        String savedFilename = null;

                        if (getActivity().getText(R.string.gallery).toString().equals(entry)) {
                            // save in the gallery
                            savedFilename = CommonActivityUtils.saveImageIntoGallery(getActivity(), cacheFile);
                        } else if (getActivity().getText(R.string.downloads).toString().equals(entry)) {
                            String filename = null;

                            if (message instanceof FileMessage)  {
                                filename = ((FileMessage)message).body;
                            }

                            // save into downloads
                            savedFilename = CommonActivityUtils.saveMediaIntoDownloads(getActivity(), cacheFile, filename, mediaMimeType);
                        } else {
                            if (getActivity() instanceof VectorRoomActivity) {
                                ((VectorRoomActivity)getActivity()).createDocument(message, mediaUrl, mediaMimeType);
                            }
                        }

                        if (null != savedFilename) {
                            final String fSavedFilename = new File(savedFilename).getName();
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getActivity(), getActivity().getString(R.string.file_is_saved, fSavedFilename), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                });
        builderSingle.show();
    }

    public void onReadReceiptClick(String eventId, String userId, ReceiptData receipt) {
        RoomMember member = mRoom.getMember(userId);

        // sanity check
        if (null != member) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

            SpannableStringBuilder body = new SpannableStringBuilder(getActivity().getString(R.string.read_receipt) + " : " + dateFormat.format(new Date(receipt.originServerTs)));
            body.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, getActivity().getString(R.string.read_receipt).length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            new AlertDialog.Builder(VectorApp.getCurrentActivity())
                    .setTitle(member.getName())
                    .setMessage(body)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create()
                    .show();
        }
    }

    public void onMoreReadReceiptClick(String eventId) {
        FragmentManager fm = getActivity().getSupportFragmentManager();

        ReadReceiptsDialogFragment fragment = (ReadReceiptsDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_RECEIPTS_DIALOG);
        if (fragment != null) {
            fragment.dismissAllowingStateLoss();
        }
        fragment = ReadReceiptsDialogFragment.newInstance(mSession, mRoom.getRoomId(), eventId);
        fragment.show(fm, TAG_FRAGMENT_RECEIPTS_DIALOG);
    }
}
