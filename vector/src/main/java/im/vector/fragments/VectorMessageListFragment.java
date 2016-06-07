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
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
import android.support.v4.app.FragmentManager;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;
import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.MXCActionBarActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.activity.VectorMediasViewerActivity;
import im.vector.adapters.VectorMessagesAdapter;
import im.vector.db.VectorContentProvider;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.util.SlidableMediaInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class VectorMessageListFragment extends MatrixMessageListFragment implements VectorMessagesAdapter.VectorMessagesAdapterActionsListener {
    private static final String LOG_TAG = "VectorMessageListFrg";

    public interface IListFragmentEventListener{
        void onListTouch();
    }

    private static final String TAG_FRAGMENT_RECEIPTS_DIALOG = "TAG_FRAGMENT_RECEIPTS_DIALOG";
    private IListFragmentEventListener mHostActivityListener;

    // onMediaAction actions
    protected static final int ACTION_VECTOR_SHARE = R.id.ic_action_vector_share;
    protected static final int ACTION_VECTOR_FORWARD = R.id.ic_action_vector_forward;
    protected static final int ACTION_VECTOR_SAVE = R.id.ic_action_vector_save;
    protected static final int ACTION_VECTOR_OPEN = 123456;

    // spinners
    protected View mBackProgressView;
    protected View mForwardProgressView;
    protected View mMainProgressView;

    public static VectorMessageListFragment newInstance(String matrixId, String roomId, String eventId, String previewMode, int layoutResId) {
        VectorMessageListFragment f = new VectorMessageListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);
        args.putString(ARG_ROOM_ID, roomId);

        if (null != eventId) {
            args.putString(ARG_EVENT_ID, eventId);
        }

        if (null != previewMode) {
            args.putString(ARG_PREVIEW_MODE_ID, previewMode);
        }

        f.setArguments(args);
        return f;
    }

    /**
     * @return the fragment tag to use to restore the matrix messages fragement
     */
    protected String getMatrixMessagesFragmentTag() {
        return getClass().getName() + ".MATRIX_MESSAGE_FRAGMENT_TAG";
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
        try {
            mHostActivityListener = (IListFragmentEventListener) aHostActivity;
        }
        catch(ClassCastException e) {
            // if host activity does not provide the implementation, just ignore it
            Log.w(LOG_TAG,"## onAttach(): host activity does not implement IListFragmentEventListener " + aHostActivity);
            mHostActivityListener = null;
        }

        mBackProgressView = aHostActivity.findViewById(R.id.loading_room_paginate_back_progress);
        mForwardProgressView = aHostActivity.findViewById(R.id.loading_room_paginate_forward_progress);
        mMainProgressView = aHostActivity.findViewById(R.id.main_progress_layout);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mAdapter instanceof VectorMessagesAdapter) {
            ((VectorMessagesAdapter)mAdapter).onPause();
        }
    }

    /**
     * Called when the fragment is no longer attached to its activity.  This
     * is called after {@link #onDestroy()}.
     */
    @Override
    public void onDetach() {
        super.onDetach();
        mHostActivityListener = null;
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

        // notify host activity
        if(null != mHostActivityListener)
            mHostActivityListener.onListTouch();
    }

    /**
     * Cancel the messages selection mode.
     */
    public void cancelSelectionMode() {
        ((VectorMessagesAdapter)mAdapter).cancelSelectionMode();
    }

    /**
     * An action has been  triggered on an event.
     * @param event the event.
     * @param action an action ic_action_vector_XXX
     */
    public void onEventAction(final Event event, final int action) {
        if (action == R.id.ic_action_vector_resend_message) {
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
                    if (event.isUndeliverable()) {
                        // delete from the store
                        mSession.getDataHandler().getStore().deleteEvent(event);
                        mSession.getDataHandler().getStore().commit();

                        // remove from the adapter
                        mAdapter.removeEventById(event.eventId);
                        mAdapter.notifyDataSetChanged();
                    } else {
                        redactEvent(event.eventId);
                    }
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
                    String text;

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

                    Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();

                }
            });
        } else if ((action == R.id.ic_action_vector_share) || (action == R.id.ic_action_vector_forward) || (action == R.id.ic_action_vector_save)) {
            //
            Message message = JsonUtils.toMessage(event.content);

            String mediaUrl = null;
            String mediaMimeType = null;

            if (message instanceof ImageMessage) {
                ImageMessage imageMessage = (ImageMessage) message;

                mediaUrl = imageMessage.url;
                mediaMimeType = imageMessage.getMimeType();
            } else if (message instanceof VideoMessage) {
                VideoMessage videoMessage = (VideoMessage) message;

                mediaUrl = videoMessage.url;

                if (null != videoMessage.info) {
                    mediaMimeType = videoMessage.info.mimetype;
                }
            } else if (message instanceof FileMessage) {
                FileMessage fileMessage = (FileMessage) message;

                mediaUrl = fileMessage.url;
                mediaMimeType = fileMessage.getMimeType();
            }

            // media file ?
            if (null != mediaUrl) {
                onMediaAction(action, mediaUrl, mediaMimeType, message.body);
            } else if ((action == R.id.ic_action_vector_share) || (action == R.id.ic_action_vector_forward)) {
                // use the body
                final Intent sendIntent = new Intent();

                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, message.body);
                sendIntent.setType("text/plain");

                if (action == R.id.ic_action_vector_forward) {
                    CommonActivityUtils.sendFilesTo(getActivity(), sendIntent);
                } else {
                    startActivity(sendIntent);
                }
            }
        } else if (action == R.id.ic_action_vector_permalink) {
            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            String link = "https://vector.im/develop/#/room/" + event.roomId + "/" + event.eventId;

            // the $ character is not as a part of an url so escape it.
            ClipData clip = ClipData.newPlainText("", link.replace("$","%24"));
            clipboard.setPrimaryClip(clip);

            Toast.makeText(getActivity(), this.getResources().getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
        } else if  (action == R.id.ic_action_vector_report) {
            onMessageReport(event);
        }
    }

    /**
     * The user reports a content problem to the server
     * @param event the event to report
     */
    private void onMessageReport(final Event event) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.room_event_action_report_prompt_reason);

        // add a text input
        final EditText input = new EditText(getActivity());
        builder.setView(input);


        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String reason = input.getText().toString();

                mRoom.report(event.eventId, -100, reason, new SimpleApiCallback<Void>(getActivity()) {
                    @Override
                    public void onSuccess(Void info) {
                        // The user is trying to leave with unsaved changes. Warn about that
                        new AlertDialog.Builder(VectorApp.getCurrentActivity())
                                .setMessage(R.string.room_event_action_report_prompt_ignore_user)
                                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();

                                        ArrayList<String> userIdsList = new ArrayList<String>();
                                        userIdsList.add(event.sender);

                                        mSession.ignoreUsers(userIdsList, new SimpleApiCallback<Void>() {
                                            @Override
                                            public void onSuccess(Void info) {
                                            }
                                        });
                                    }
                                })
                                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                })
                                .create()
                                .show();
                    }
                });
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    /***
     * Manage save / share / foward actions on a media file
     * @param menuAction the menu action ACTION_VECTOR__XXX
     * @param mediaUrl the media URL (must be not null)
     * @param mediaMimeType the mime type
     * @param filename the filename
     */
    protected void onMediaAction(final int menuAction, final String mediaUrl, final String mediaMimeType, final String filename) {
        MXMediasCache mediasCache = Matrix.getInstance(getActivity()).getMediasCache();
        File file = mediasCache.mediaCacheFile(mediaUrl, mediaMimeType);

        // check if the media has already been downloaded
        if (null != file) {
            // download
            if ((menuAction == ACTION_VECTOR_SAVE) || (menuAction == ACTION_VECTOR_OPEN)) {
                String savedMediaPath = CommonActivityUtils.saveMediaIntoDownloads(getActivity(), file, filename, mediaMimeType);

                if (null != savedMediaPath) {

                    if (menuAction == ACTION_VECTOR_SAVE) {
                        Toast.makeText(getActivity(), getText(R.string.media_slider_saved), Toast.LENGTH_LONG).show();
                    } else {
                        CommonActivityUtils.openMedia(getActivity(), savedMediaPath, mediaMimeType);
                    }
                }
            } else {
                // shared / forward
                Uri mediaUri = null;

                File renamedFile = file;

                if (!TextUtils.isEmpty(filename))
                    try {
                        InputStream fin = new FileInputStream(file);
                        String tmpUrl = mediasCache.saveMedia(fin, filename, mediaMimeType);

                        if (null != tmpUrl) {
                            renamedFile = mediasCache.mediaCacheFile(tmpUrl, mediaMimeType);
                        }
                    } catch (Exception e) {
                    }


                if (null != renamedFile) {
                    try {
                        mediaUri = VectorContentProvider.absolutePathToUri(getActivity(), renamedFile.getAbsolutePath());
                    } catch (Exception e) {
                    }
                }

                if (null != mediaUri) {
                    final Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.setType(mediaMimeType);
                    sendIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);

                    if (menuAction == ACTION_VECTOR_FORWARD) {
                        CommonActivityUtils.sendFilesTo(getActivity(), sendIntent);
                    } else {
                        startActivity(sendIntent);
                    }
                }
            }
        } else {
            // else download it
            final String downloadId = mediasCache.downloadMedia(getActivity(), mSession.getHomeserverConfig(), mediaUrl, mediaMimeType);
            mAdapter.notifyDataSetChanged();

            if (null != downloadId) {
                mediasCache.addDownloadListener(downloadId, new MXMediasCache.DownloadCallback() {
                    @Override
                    public void onDownloadStart(String downloadId) {
                    }

                    @Override
                    public void onError(String downloadId, JsonElement jsonElement) {
                        MatrixError error = JsonUtils.toMatrixError(jsonElement);

                        if ((null != error) && error.isSupportedErrorCode()) {
                            Toast.makeText(VectorMessageListFragment.this.getActivity(), error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onDownloadProgress(String aDownloadId, int percentageProgress) {
                    }

                    @Override
                    public void onDownloadComplete(String aDownloadId) {
                        if (aDownloadId.equals(downloadId)) {

                            VectorMessageListFragment.this.getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    onMediaAction(menuAction, mediaUrl, mediaMimeType, filename);
                                }
                            });
                        }
                    }
                });
            }
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

    private void setViewVisibility(View view, int visibility) {
        if ((null != view) && (null != getActivity())) {
            view.setVisibility(visibility);
        }
    }

    @Override
    public void showLoadingBackProgress() {
        setViewVisibility(mBackProgressView, View.VISIBLE);
    }

    @Override
    public void hideLoadingBackProgress() {
        setViewVisibility(mBackProgressView, View.GONE);
    }

    @Override
    public void showLoadingForwardProgress() {
        setViewVisibility(mForwardProgressView, View.VISIBLE);
    }

    @Override
    public void hideLoadingForwardProgress() {
        setViewVisibility(mForwardProgressView, View.GONE);
    }

    @Override
    public void showInitLoading() {
        setViewVisibility(mMainProgressView, View.VISIBLE);
    }

    @Override
    public void hideInitLoading() {
        setViewVisibility(mMainProgressView, View.GONE);
    }

    public boolean onRowLongClick(int position) {
        return false;
    }

    /**
     * @return the image and video messages list
     */
    protected ArrayList<SlidableMediaInfo> listSlidableMessages() {
        ArrayList<SlidableMediaInfo> res = new ArrayList<SlidableMediaInfo>();

        for(int position = 0; position < mAdapter.getCount(); position++) {
            MessageRow row = mAdapter.getItem(position);
            Message message = JsonUtils.toMessage(row.getEvent().content);

            if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
                ImageMessage imageMessage = (ImageMessage)message;

                SlidableMediaInfo info = new SlidableMediaInfo();
                info.mMessageType = Message.MSGTYPE_IMAGE;
                info.mFileName = imageMessage.body;
                info.mMediaUrl = imageMessage.url;
                info.mRotationAngle = imageMessage.getRotation();
                info.mOrientation = imageMessage.getOrientation();
                info.mMimeType = imageMessage.getMimeType();
                info.mIdentifier = row.getEvent().eventId;
                res.add(info);
            } else if (Message.MSGTYPE_VIDEO.equals(message.msgtype)) {
                SlidableMediaInfo info = new SlidableMediaInfo();
                VideoMessage videoMessage = (VideoMessage)message;
                info.mMessageType = Message.MSGTYPE_VIDEO;
                info.mFileName = videoMessage.body;
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
    protected int getMediaMessagePosition(ArrayList<SlidableMediaInfo> mediaMessagesList, Message mediaMessage) {
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

        // video and images are displayed inside a medias slider.
        if (Message.MSGTYPE_IMAGE.equals(message.msgtype) || (Message.MSGTYPE_VIDEO.equals(message.msgtype))) {
            ArrayList<SlidableMediaInfo> mediaMessagesList = listSlidableMessages();
            int listPosition = getMediaMessagePosition(mediaMessagesList, message);

            if (listPosition >= 0) {
                Intent viewImageIntent = new Intent(getActivity(), VectorMediasViewerActivity.class);

                viewImageIntent.putExtra(VectorMediasViewerActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_WIDTH, mAdapter.getMaxThumbnailWith());
                viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_HEIGHT, mAdapter.getMaxThumbnailHeight());
                viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_INFO_LIST, mediaMessagesList);
                viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_INFO_LIST_INDEX, listPosition);

                getActivity().startActivity(viewImageIntent);
            }
        } else if (Message.MSGTYPE_FILE.equals(message.msgtype)) {
            FileMessage fileMessage = JsonUtils.toFileMessage(event.content);

            if (null != fileMessage.url) {
                onMediaAction(ACTION_VECTOR_OPEN, fileMessage.url, fileMessage.getMimeType(), fileMessage.body);
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
    public boolean onContentLongClick(int position) {
        return onRowLongClick(position);
    }

    /**
     * Define the action to perform when the user tap on an avatar
     * @param userId the user ID
     */
    public void onAvatarClick(String userId) {
        Intent roomDetailsIntent = new Intent(getActivity(), VectorMemberDetailsActivity.class);
        // in preview mode
        // the room is stored in a temporary store
        // so provide an handle to retrieve it
        if (null != getRoomPreviewData()) {
            roomDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_STORE_ID, new Integer(Matrix.getInstance(getActivity()).addTmpStore(mEventTimeLine.getStore())));
        }

        roomDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
        roomDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, userId);
        roomDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
        getActivity().startActivityForResult(roomDetailsIntent, VectorRoomActivity.GET_MENTION_REQUEST_CODE);
    }

    /**
     * Define the action to perform when the user performs a long tap on an avatar
     * @param userId the user ID
     * @return true if the long click event is managed
     */
    public boolean onAvatarLongClick(String userId) {
        if (getActivity() instanceof VectorRoomActivity) {
            RoomState state = mRoom.getLiveState();

            if (null != state) {
                String displayName = state.getMemberName(userId);
                if (!TextUtils.isEmpty(displayName)) {
                    ((VectorRoomActivity)getActivity()).insertInTextEditor(displayName);
                }
            }
        }
        return true;
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


    /**
     * Save the message the message content into a dedicated folder.
     * @param message the message.
     * @param mediaUrl the media url.
     * @param mediaMimeType the media mimetype.
     */
    protected void save(final Message message, final String mediaUrl, final String mediaMimeType) {
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

    public void onMoreReadReceiptClick(String eventId) {
        FragmentManager fm = getActivity().getSupportFragmentManager();

        VectorReadReceiptsDialogFragment fragment = (VectorReadReceiptsDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_RECEIPTS_DIALOG);
        if (fragment != null) {
            fragment.dismissAllowingStateLoss();
        }
        fragment = VectorReadReceiptsDialogFragment.newInstance(mSession, mRoom.getRoomId(), eventId);
        fragment.show(fm, TAG_FRAGMENT_RECEIPTS_DIALOG);
    }

    /**
     * The user taps on an URI.
     * @param uri the URI
     */
    public void onURLClick(Uri uri) {
        if (null != uri) {
            if (null != VectorUniversalLinkReceiver.parseUniversalLink(uri)) {
                // pop to the home activity
                Intent intent = new Intent(getActivity(), VectorHomeActivity.class);
                intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_UNIVERSAL_LINK, uri);
                getActivity().startActivity(intent);
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.putExtra(Browser.EXTRA_APPLICATION_ID, getActivity().getPackageName());
                getActivity().startActivity(intent);
            }
        }
    }
}
