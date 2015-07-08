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

package org.matrix.console.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.console.Matrix;
import org.matrix.console.R;
import org.matrix.console.activity.CommonActivityUtils;
import org.matrix.console.activity.MXCActionBarActivity;
import org.matrix.console.activity.RoomActivity;
import org.matrix.console.adapters.ConsoleMessagesAdapter;
import org.matrix.console.db.ConsoleContentProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConsoleMessageListFragment extends MatrixMessageListFragment implements ConsoleMessagesAdapter.MessageLongClickListener, ConsoleMessagesAdapter.AvatarClickListener {

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
        // use the defaults message layouts
        // can set any adapters
        ConsoleMessagesAdapter adapter = new ConsoleMessagesAdapter(mSession, getActivity(), getMXMediasCache());
        adapter.setMessageLongClickListener(this);
        adapter.setAvatarClickListener(this);

        return adapter;
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
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final View progressView = getActivity().findViewById(R.id.loading_room_content_progress);

                if (null != progressView) {
                    progressView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /**
     * Dismiss any global spinner.
     */
    @Override
    public void dismissLoadingProgress() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final View progressView = getActivity().findViewById(R.id.loading_room_content_progress);

                if (null != progressView) {
                    progressView.setVisibility(View.GONE);
                }
            }
        });
    }

    /**
     * logout from the application
     */
    @Override
    public void logout() {
        CommonActivityUtils.logout(ConsoleMessageListFragment.this.getActivity());
    }

    /**
     * User actions when the user click on message row.
     * This example displays a menu to perform some actions on the message.
     */
    @Override
    public void onItemClick(int position) {
        final MessageRow messageRow = mAdapter.getItem(position);
        final List<Integer> textIds = new ArrayList<>();
        final List<Integer> iconIds = new ArrayList<Integer>();

        String mediaUrl = null;
        String mediaMimeType = null;
        Uri mediaUri = null;
        Message message = JsonUtils.toMessage(messageRow.getEvent().content);

        if (!Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(messageRow.getEvent().type) &&
            !Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(messageRow.getEvent().type) &&
            !Event.EVENT_TYPE_STATE_ROOM_NAME.equals(messageRow.getEvent().type))
        {
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
                                mediaUri = ConsoleContentProvider.absolutePathToUri(getActivity(), mediaFile.getAbsolutePath());
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

                if (selectedVal == R.string.resend) {
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
                }
            }
        });

        fragment.show(fm, TAG_FRAGMENT_MESSAGE_OPTIONS);
    }

    private void save(final Message message, final String mediaUrl, final String mediaMimeType) {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(getActivity());

        builderSingle.setTitle(getActivity().getText(R.string.save_files_in));
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.dialog_room_selection);

        ArrayList<String> entries = new ArrayList<String>();

        entries.add(getActivity().getText(R.string.downloads).toString());

        if (mediaMimeType.startsWith("image/")) {
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
                            if (getActivity() instanceof RoomActivity) {
                                ((RoomActivity)getActivity()).createDocument(message, mediaUrl, mediaMimeType);
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

    @Override
    public void onMessageLongClick(int position, Message message) {
        onItemClick(position);
    }

    @Override
    public Boolean onAvatarClick(String roomId, String userId) {
        return false;
    }

    @Override
    public Boolean onAvatarLongClick(String roomId, String userId) {
        return false;
    }

    @Override
    public Boolean onDisplayNameClick(String userId, String displayName) {
        if (getActivity() instanceof RoomActivity) {
            ((RoomActivity)getActivity()).appendTextToEditor(displayName);
            return true;
        }

        return false;
    }
}
