/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.AbstractMessagesAdapter;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.fragments.MatrixMessagesFragment;
import org.matrix.androidsdk.listeners.MXMediaDownloadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.crypto.EncryptedEventContent;
import org.matrix.androidsdk.rest.model.crypto.EncryptedFileInfo;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.message.FileMessage;
import org.matrix.androidsdk.rest.model.message.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.message.VideoMessage;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.MXCActionBarActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorMediasViewerActivity;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.VectorMessagesAdapter;
import im.vector.db.VectorContentProvider;
import im.vector.listeners.IMessagesAdapterActionsListener;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.util.SlidableMediaInfo;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorUtils;
import im.vector.widgets.WidgetsManager;

public class VectorMessageListFragment extends MatrixMessageListFragment implements IMessagesAdapterActionsListener {
    private static final String LOG_TAG = VectorMessageListFragment.class.getSimpleName();

    public interface IListFragmentEventListener {
        void onListTouch();
    }

    private static final String TAG_FRAGMENT_RECEIPTS_DIALOG = "TAG_FRAGMENT_RECEIPTS_DIALOG";
    private static final String TAG_FRAGMENT_USER_GROUPS_DIALOG = "TAG_FRAGMENT_USER_GROUPS_DIALOG";

    private IListFragmentEventListener mHostActivityListener;

    // onMediaAction actions
    // private static final int ACTION_VECTOR_SHARE = R.id.ic_action_vector_share;
    private static final int ACTION_VECTOR_FORWARD = R.id.ic_action_vector_forward;
    private static final int ACTION_VECTOR_SAVE = R.id.ic_action_vector_save;
    static final int ACTION_VECTOR_OPEN = 123456;

    // spinners
    private View mBackProgressView;
    private View mForwardProgressView;
    private View mMainProgressView;

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreateView");

        View v = super.onCreateView(inflater, container, savedInstanceState);

        Bundle args = getArguments();

        // when an event id is defined, display a thick green line to its left
        if (args.containsKey(ARG_EVENT_ID) && (mAdapter instanceof VectorMessagesAdapter)) {
            ((VectorMessagesAdapter) mAdapter).setSearchedEventId(args.getString(ARG_EVENT_ID, ""));
        }

        if (null != mRoom) {
            ((VectorMessagesAdapter) mAdapter).mIsRoomEncrypted = mRoom.isEncrypted();
        }

        mMessageListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onRowClick(position);
            }
        });

        v.setBackgroundColor(ThemeUtils.getColor(getActivity(), R.attr.riot_primary_background_color));

        return v;
    }

    @Override
    public MatrixMessagesFragment createMessagesFragmentInstance(String roomId) {
        return VectorMessagesFragment.newInstance(getSession(), roomId, this);
    }

    /**
     * @return the fragment tag to use to restore the matrix messages fragment
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
        } catch (ClassCastException e) {
            // if host activity does not provide the implementation, just ignore it
            Log.w(LOG_TAG, "## onAttach(): host activity does not implement IListFragmentEventListener " + aHostActivity);
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
            VectorMessagesAdapter adapter = ((VectorMessagesAdapter) mAdapter);

            adapter.setVectorMessagesAdapterActionsListener(null);
            adapter.onPause();
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter instanceof VectorMessagesAdapter) {
            VectorMessagesAdapter adapter = ((VectorMessagesAdapter) mAdapter);
            adapter.setVectorMessagesAdapterActionsListener(this);
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

        mBackProgressView = null;
        mForwardProgressView = null;
        mMainProgressView = null;
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
    public AbstractMessagesAdapter createMessagesAdapter() {
        return new VectorMessagesAdapter(mSession, getActivity(), getMXMediasCache());
    }

    /**
     * The user scrolls the list.
     * Apply an expected behaviour
     *
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
        if (null != mHostActivityListener)
            mHostActivityListener.onListTouch();
    }

    @Override
    protected boolean canAddEvent(Event event) {
        return TextUtils.equals(WidgetsManager.WIDGET_EVENT_TYPE, event.getType()) || super.canAddEvent(event);
    }

    /**
     * Update the encrypted status of the room
     *
     * @param isEncrypted true when the room is encrypted
     */
    public void setIsRoomEncrypted(boolean isEncrypted) {
        if (((VectorMessagesAdapter) mAdapter).mIsRoomEncrypted != isEncrypted) {
            ((VectorMessagesAdapter) mAdapter).mIsRoomEncrypted = isEncrypted;
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Get the message list view
     *
     * @return message list view
     */
    public ListView getMessageListView() {
        return mMessageListView;
    }

    /**
     * Get the message adapter
     *
     * @return message adapter
     */
    public AbstractMessagesAdapter getMessageAdapter() {
        return mAdapter;
    }

    /**
     * Cancel the messages selection mode.
     */
    public void cancelSelectionMode() {
        if (null != mAdapter) {
            ((VectorMessagesAdapter) mAdapter).cancelSelectionMode();
        }
    }

    private final ApiCallback<Void> mDeviceVerificationCallback = new ApiCallback<Void>() {
        @Override
        public void onSuccess(Void info) {
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onNetworkError(Exception e) {
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onMatrixError(MatrixError e) {
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onUnexpectedError(Exception e) {
            mAdapter.notifyDataSetChanged();
        }
    };

    /**
     * the user taps on the e2e icon
     *
     * @param event      the event
     * @param deviceInfo the deviceinfo
     */
    public void onE2eIconClick(final Event event, final MXDeviceInfo deviceInfo) {
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        EncryptedEventContent encryptedEventContent = JsonUtils.toEncryptedEventContent(event.getWireContent().getAsJsonObject());

        View layout = inflater.inflate(R.layout.encrypted_event_info, null);

        TextView textView;

        textView = layout.findViewById(R.id.encrypted_info_user_id);
        textView.setText(event.getSender());

        textView = layout.findViewById(R.id.encrypted_info_curve25519_identity_key);
        if (null != deviceInfo) {
            textView.setText(encryptedEventContent.sender_key);
        } else {
            textView.setText(getActivity().getString(R.string.encryption_information_none));
        }

        textView = layout.findViewById(R.id.encrypted_info_claimed_ed25519_fingerprint_key);
        if (null != deviceInfo) {
            textView.setText(deviceInfo.fingerprint());
        } else {
            textView.setText(getActivity().getString(R.string.encryption_information_none));
        }

        textView = layout.findViewById(R.id.encrypted_info_algorithm);
        textView.setText(encryptedEventContent.algorithm);

        textView = layout.findViewById(R.id.encrypted_info_session_id);
        textView.setText(encryptedEventContent.session_id);

        View decryptionErrorLabelTextView = layout.findViewById(R.id.encrypted_info_decryption_error_label);
        textView = layout.findViewById(R.id.encrypted_info_decryption_error);

        if (null != event.getCryptoError()) {
            decryptionErrorLabelTextView.setVisibility(View.VISIBLE);
            textView.setVisibility(View.VISIBLE);
            textView.setText("**" + event.getCryptoError().getLocalizedMessage() + "**");
        } else {
            decryptionErrorLabelTextView.setVisibility(View.GONE);
            textView.setVisibility(View.GONE);
        }

        View noDeviceInfoLayout = layout.findViewById(R.id.encrypted_info_no_device_information_layout);
        View deviceInfoLayout = layout.findViewById(R.id.encrypted_info_sender_device_information_layout);

        if (null != deviceInfo) {
            noDeviceInfoLayout.setVisibility(View.GONE);
            deviceInfoLayout.setVisibility(View.VISIBLE);

            textView = layout.findViewById(R.id.encrypted_info_name);
            textView.setText(deviceInfo.displayName());

            textView = layout.findViewById(R.id.encrypted_info_device_id);
            textView.setText(deviceInfo.deviceId);

            textView = layout.findViewById(R.id.encrypted_info_verification);

            if (deviceInfo.isUnknown() || deviceInfo.isUnverified()) {
                textView.setText(getActivity().getString(R.string.encryption_information_not_verified));
            } else if (deviceInfo.isVerified()) {
                textView.setText(getActivity().getString(R.string.encryption_information_verified));
            } else {
                textView.setText(getActivity().getString(R.string.encryption_information_blocked));
            }

            textView = layout.findViewById(R.id.encrypted_ed25519_fingerprint);
            textView.setText(deviceInfo.fingerprint());
        } else {
            noDeviceInfoLayout.setVisibility(View.VISIBLE);
            deviceInfoLayout.setVisibility(View.GONE);
        }

        builder.setView(layout);
        builder.setTitle(R.string.encryption_information_title);

        builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // nothing to do
            }
        });

        // the current id cannot be blocked, verified...
        if (!TextUtils.equals(encryptedEventContent.device_id, mSession.getCredentials().deviceId)) {
            if ((null == event.getCryptoError()) && (null != deviceInfo)) {
                if (deviceInfo.isUnverified() || deviceInfo.isUnknown()) {
                    builder.setNegativeButton(R.string.encryption_information_verify, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            CommonActivityUtils.displayDeviceVerificationDialog(deviceInfo, event.getSender(), mSession, getActivity(), mDeviceVerificationCallback);
                        }
                    });

                    builder.setPositiveButton(R.string.encryption_information_block, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, deviceInfo.deviceId, event.getSender(), mDeviceVerificationCallback);
                        }
                    });
                } else if (deviceInfo.isVerified()) {
                    builder.setNegativeButton(R.string.encryption_information_unverify, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, deviceInfo.deviceId, event.getSender(), mDeviceVerificationCallback);
                        }
                    });

                    builder.setPositiveButton(R.string.encryption_information_block, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, deviceInfo.deviceId, event.getSender(), mDeviceVerificationCallback);
                        }
                    });
                } else { // BLOCKED
                    builder.setNegativeButton(R.string.encryption_information_verify, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            CommonActivityUtils.displayDeviceVerificationDialog(deviceInfo, event.getSender(), mSession, getActivity(), mDeviceVerificationCallback);
                        }
                    });

                    builder.setPositiveButton(R.string.encryption_information_unblock, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, deviceInfo.deviceId, event.getSender(), mDeviceVerificationCallback);
                        }
                    });
                }
            }
        }

        final android.support.v7.app.AlertDialog dialog = builder.create();
        dialog.show();

        if (null == deviceInfo) {
            mSession.getCrypto().getDeviceList().downloadKeys(Collections.singletonList(event.getSender()), true, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
                @Override
                public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                    Activity activity = getActivity();

                    if ((null != activity) && !activity.isFinishing() && dialog.isShowing()) {
                        EncryptedEventContent encryptedEventContent = JsonUtils.toEncryptedEventContent(event.getWireContent().getAsJsonObject());

                        MXDeviceInfo deviceInfo = mSession.getCrypto().deviceWithIdentityKey(encryptedEventContent.sender_key, event.getSender(), encryptedEventContent.algorithm);

                        if (null != deviceInfo) {
                            dialog.cancel();
                            onE2eIconClick(event, deviceInfo);
                        }
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                }

                @Override
                public void onMatrixError(MatrixError e) {
                }

                @Override
                public void onUnexpectedError(Exception e) {
                }
            });
        }
    }

    /**
     * An action has been  triggered on an event.
     *
     * @param event   the event.
     * @param textMsg the event text
     * @param action  an action ic_action_vector_XXX
     */
    public void onEventAction(final Event event, final String textMsg, final int action) {
        if (action == R.id.ic_action_vector_resend_message) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resend(event);
                }
            });
        } else if (action == R.id.ic_action_vector_redact_message) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                    alertDialogBuilder.setMessage(getString(R.string.redact) + " ?");

                    // set dialog message
                    alertDialogBuilder
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            if (event.isUndeliverable() || event.isUnkownDevice()) {
                                                // delete from the store
                                                mSession.getDataHandler().deleteRoomEvent(event);

                                                // remove from the adapter
                                                mAdapter.removeEventById(event.eventId);
                                                mAdapter.notifyDataSetChanged();
                                                mEventSendingListener.onMessageRedacted(event);
                                            } else {
                                                redactEvent(event.eventId);
                                            }
                                        }
                                    })
                            .setNegativeButton(R.string.cancel,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                        }
                                    });

                    // create alert dialog
                    AlertDialog alertDialog = alertDialogBuilder.create();
                    // show it
                    alertDialog.show();
                }
            });
        } else if (action == R.id.ic_action_vector_copy) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    VectorUtils.copyToClipboard(getActivity(), textMsg);
                }
            });
        } else if ((action == R.id.ic_action_vector_cancel_upload) || (action == R.id.ic_action_vector_cancel_download)) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(getActivity())
                            .setMessage((action == R.id.ic_action_vector_cancel_upload) ? R.string.attachment_cancel_upload : R.string.attachment_cancel_download)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    mRoom.cancelEventSending(event);

                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mAdapter.notifyDataSetChanged();
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
        } else if (action == R.id.ic_action_vector_quote) {
            Activity attachedActivity = getActivity();

            if ((null != attachedActivity) && (attachedActivity instanceof VectorRoomActivity)) {
                // Quote all paragraphs instead
                String[] messageParagraphs = textMsg.split("\n\n");
                String quotedTextMsg = "";
                for (int i = 0; i < messageParagraphs.length; i++) {
                    if (!messageParagraphs[i].trim().equals("")) {
                        quotedTextMsg += "> " + messageParagraphs[i];
                    }

                    if (!((i + 1) == messageParagraphs.length)) {
                        quotedTextMsg += "\n\n";
                    }
                }
                ((VectorRoomActivity) attachedActivity).insertQuoteInTextEditor(quotedTextMsg + "\n\n");
            }
        } else if ((action == R.id.ic_action_vector_share) || (action == R.id.ic_action_vector_forward) || (action == R.id.ic_action_vector_save)) {
            //
            Message message = JsonUtils.toMessage(event.getContent());

            String mediaUrl = null;
            String mediaMimeType = null;
            EncryptedFileInfo encryptedFileInfo = null;

            if (message instanceof ImageMessage) {
                ImageMessage imageMessage = (ImageMessage) message;

                mediaUrl = imageMessage.getUrl();
                mediaMimeType = imageMessage.getMimeType();
                encryptedFileInfo = imageMessage.file;
            } else if (message instanceof VideoMessage) {
                VideoMessage videoMessage = (VideoMessage) message;

                mediaUrl = videoMessage.getUrl();
                encryptedFileInfo = videoMessage.file;

                if (null != videoMessage.info) {
                    mediaMimeType = videoMessage.info.mimetype;
                }
            } else if (message instanceof FileMessage) {
                FileMessage fileMessage = (FileMessage) message;

                mediaUrl = fileMessage.getUrl();
                mediaMimeType = fileMessage.getMimeType();
                encryptedFileInfo = fileMessage.file;
            }

            // media file ?
            if (null != mediaUrl) {
                onMediaAction(action, mediaUrl, mediaMimeType, message.body, encryptedFileInfo);
            } else if ((action == R.id.ic_action_vector_share) || (action == R.id.ic_action_vector_forward) || (action == R.id.ic_action_vector_quote)) {
                // use the body
                final Intent sendIntent = new Intent();

                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, textMsg);
                sendIntent.setType("text/plain");

                if (action == R.id.ic_action_vector_forward) {
                    CommonActivityUtils.sendFilesTo(getActivity(), sendIntent);
                } else {
                    startActivity(sendIntent);
                }
            }
        } else if (action == R.id.ic_action_vector_permalink) {
            VectorUtils.copyToClipboard(getActivity(), VectorUtils.getPermalink(event.roomId, event.eventId));
        } else if (action == R.id.ic_action_vector_report) {
            onMessageReport(event);
        } else if ((action == R.id.ic_action_view_source) || (action == R.id.ic_action_view_decrypted_source)) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                    View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_event_content, null);
                    TextView textview = view.findViewById(R.id.event_content_text_view);

                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    textview.setText(gson.toJson(JsonUtils.toJson((action == R.id.ic_action_view_source) ? event : event.getClearEvent())));
                    builder.setView(view);

                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });

                    builder.create().show();
                }
            });
        } else if (action == R.id.ic_action_device_verification) {
            onE2eIconClick(event, ((VectorMessagesAdapter) mAdapter).getDeviceInfo(event.eventId));
        }
    }

    /**
     * The user reports a content problem to the server
     *
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
                        new AlertDialog.Builder(getActivity())
                                .setMessage(R.string.room_event_action_report_prompt_ignore_user)
                                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();

                                        ArrayList<String> userIdsList = new ArrayList<>();
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
     * Manage save / share / forward actions on a media file
     *
     * @param menuAction    the menu action ACTION_VECTOR__XXX
     * @param mediaUrl      the media URL (must be not null)
     * @param mediaMimeType the mime type
     * @param filename      the filename
     */
    void onMediaAction(final int menuAction, final String mediaUrl, final String mediaMimeType, final String filename, final EncryptedFileInfo encryptedFileInfo) {
        MXMediasCache mediasCache = Matrix.getInstance(getActivity()).getMediasCache();
        // check if the media has already been downloaded
        if (mediasCache.isMediaCached(mediaUrl, mediaMimeType)) {
            mediasCache.createTmpMediaFile(mediaUrl, mediaMimeType, encryptedFileInfo, new SimpleApiCallback<File>() {
                @Override
                public void onSuccess(File file) {
                    // sanity check
                    if (null == file) {
                        return;
                    }

                    if ((menuAction == ACTION_VECTOR_SAVE) || (menuAction == ACTION_VECTOR_OPEN)) {
                        CommonActivityUtils.saveMediaIntoDownloads(getActivity(), file, filename, mediaMimeType, new SimpleApiCallback<String>() {
                            @Override
                            public void onSuccess(String savedMediaPath) {
                                if (null != savedMediaPath) {
                                    if (menuAction == ACTION_VECTOR_SAVE) {
                                        Toast.makeText(getActivity(), getText(R.string.media_slider_saved), Toast.LENGTH_LONG).show();
                                    } else {
                                        CommonActivityUtils.openMedia(getActivity(), savedMediaPath, mediaMimeType);
                                    }
                                }
                            }
                        });
                    } else {
                        if (null != filename) {
                            File dstFile = new File(file.getParent(), filename);

                            if (dstFile.exists()) {
                                dstFile.delete();
                            }

                            file.renameTo(dstFile);
                            file = dstFile;
                        }

                        // shared / forward
                        Uri mediaUri = null;
                        try {
                            mediaUri = VectorContentProvider.absolutePathToUri(getActivity(), file.getAbsolutePath());
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "onMediaAction VectorContentProvider.absolutePathToUri: " + e.getMessage());
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
                }
            });
        } else {
            // else download it
            final String downloadId = mediasCache.downloadMedia(getActivity().getApplicationContext(), mSession.getHomeServerConfig(), mediaUrl, mediaMimeType, encryptedFileInfo);
            mAdapter.notifyDataSetChanged();

            if (null != downloadId) {
                mediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {
                    @Override
                    public void onDownloadError(String downloadId, JsonElement jsonElement) {
                        MatrixError error = JsonUtils.toMatrixError(jsonElement);

                        if ((null != error) && error.isSupportedErrorCode() && (null != VectorMessageListFragment.this.getActivity())) {
                            Toast.makeText(VectorMessageListFragment.this.getActivity(), error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onDownloadComplete(String aDownloadId) {
                        if (aDownloadId.equals(downloadId)) {

                            VectorMessageListFragment.this.getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    onMediaAction(menuAction, mediaUrl, mediaMimeType, filename, encryptedFileInfo);
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
    ArrayList<SlidableMediaInfo> listSlidableMessages() {
        ArrayList<SlidableMediaInfo> res = new ArrayList<>();

        for (int position = 0; position < mAdapter.getCount(); position++) {
            MessageRow row = mAdapter.getItem(position);
            Message message = JsonUtils.toMessage(row.getEvent().getContent());

            if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
                ImageMessage imageMessage = (ImageMessage) message;

                SlidableMediaInfo info = new SlidableMediaInfo();
                info.mMessageType = Message.MSGTYPE_IMAGE;
                info.mFileName = imageMessage.body;
                info.mMediaUrl = imageMessage.getUrl();
                info.mRotationAngle = imageMessage.getRotation();
                info.mOrientation = imageMessage.getOrientation();
                info.mMimeType = imageMessage.getMimeType();
                info.mEncryptedFileInfo = imageMessage.file;
                res.add(info);
            } else if (Message.MSGTYPE_VIDEO.equals(message.msgtype)) {
                SlidableMediaInfo info = new SlidableMediaInfo();
                VideoMessage videoMessage = (VideoMessage) message;
                info.mMessageType = Message.MSGTYPE_VIDEO;
                info.mFileName = videoMessage.body;
                info.mMediaUrl = videoMessage.getUrl();
                info.mThumbnailUrl = (null != videoMessage.info) ? videoMessage.info.thumbnail_url : null;
                info.mMimeType = videoMessage.getMimeType();
                info.mEncryptedFileInfo = videoMessage.file;
                res.add(info);
            }
        }

        return res;
    }

    /**
     * Returns the mediaMessage position in listMediaMessages.
     *
     * @param mediaMessagesList the media messages list
     * @param mediaMessage      the imageMessage
     * @return the imageMessage position. -1 if not found.
     */
    int getMediaMessagePosition(ArrayList<SlidableMediaInfo> mediaMessagesList, Message mediaMessage) {
        String url = null;

        if (mediaMessage instanceof ImageMessage) {
            url = ((ImageMessage) mediaMessage).getUrl();
        } else if (mediaMessage instanceof VideoMessage) {
            url = ((VideoMessage) mediaMessage).getUrl();
        }

        // sanity check
        if (null == url) {
            return -1;
        }

        for (int index = 0; index < mediaMessagesList.size(); index++) {
            if (mediaMessagesList.get(index).mMediaUrl.equals(url)) {
                return index;
            }
        }

        return -1;
    }

    @Override
    public void onRowClick(int position) {
        try {
            MessageRow row = mAdapter.getItem(position);
            Event event = row.getEvent();

            // switch in section mode
            ((VectorMessagesAdapter) mAdapter).onEventTap(event.eventId);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onRowClick() failed " + e.getMessage());
        }
    }

    @Override
    public void onContentClick(int position) {
        try {
            MessageRow row = mAdapter.getItem(position);
            Event event = row.getEvent();

            VectorMessagesAdapter vectorMessagesAdapter = (VectorMessagesAdapter) mAdapter;

            if (vectorMessagesAdapter.isInSelectionMode()) {
                // cancel the selection mode.
                vectorMessagesAdapter.onEventTap(null);
                return;
            }

            Message message = JsonUtils.toMessage(event.getContent());

            // video and images are displayed inside a medias slider.
            if (Message.MSGTYPE_IMAGE.equals(message.msgtype) || (Message.MSGTYPE_VIDEO.equals(message.msgtype))) {
                ArrayList<SlidableMediaInfo> mediaMessagesList = listSlidableMessages();
                int listPosition = getMediaMessagePosition(mediaMessagesList, message);

                if (listPosition >= 0) {
                    Intent viewImageIntent = new Intent(getActivity(), VectorMediasViewerActivity.class);

                    viewImageIntent.putExtra(VectorMediasViewerActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_WIDTH, mAdapter.getMaxThumbnailWidth());
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_HEIGHT, mAdapter.getMaxThumbnailHeight());
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_INFO_LIST, mediaMessagesList);
                    viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_INFO_LIST_INDEX, listPosition);

                    getActivity().startActivity(viewImageIntent);
                }
            } else if (Message.MSGTYPE_FILE.equals(message.msgtype) || Message.MSGTYPE_AUDIO.equals(message.msgtype)) {
                FileMessage fileMessage = JsonUtils.toFileMessage(event.getContent());

                if (null != fileMessage.getUrl()) {
                    onMediaAction(ACTION_VECTOR_OPEN, fileMessage.getUrl(), fileMessage.getMimeType(), fileMessage.body, fileMessage.file);
                }
            } else {
                // switch in section mode
                vectorMessagesAdapter.onEventTap(event.eventId);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onContentClick() failed " + e.getMessage());
        }
    }

    @Override
    public boolean onContentLongClick(int position) {
        return onRowLongClick(position);
    }

    @Override
    public void onAvatarClick(String userId) {
        try {
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
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onAvatarClick() failed " + e.getMessage());
        }
    }

    @Override
    public boolean onAvatarLongClick(String userId) {
        if (getActivity() instanceof VectorRoomActivity) {
            try {
                RoomState state = mRoom.getLiveState();

                if (null != state) {
                    String displayName = state.getMemberName(userId);
                    if (!TextUtils.isEmpty(displayName)) {
                        ((VectorRoomActivity) getActivity()).insertUserDisplayNameInTextEditor(displayName);
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## onAvatarLongClick() failed " + e.getMessage());
            }
        }
        return true;
    }

    @Override
    public void onSenderNameClick(String userId, String displayName) {
        if (getActivity() instanceof VectorRoomActivity) {
            try {
                ((VectorRoomActivity) getActivity()).insertUserDisplayNameInTextEditor(displayName);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## onSenderNameClick() failed " + e.getMessage());
            }
        }
    }

    @Override
    public void onMediaDownloaded(int position) {
    }

    @Override
    public void onMoreReadReceiptClick(String eventId) {
        try {
            FragmentManager fm = getActivity().getSupportFragmentManager();

            VectorReadReceiptsDialogFragment fragment = (VectorReadReceiptsDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_RECEIPTS_DIALOG);
            if (fragment != null) {
                fragment.dismissAllowingStateLoss();
            }
            fragment = VectorReadReceiptsDialogFragment.newInstance(mSession.getMyUserId(), mRoom.getRoomId(), eventId);
            fragment.show(fm, TAG_FRAGMENT_RECEIPTS_DIALOG);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onMoreReadReceiptClick() failed " + e.getMessage());
        }
    }

    @Override
    public void onGroupFlairClick(String userId, List<String> groupIds) {
        try {
            FragmentManager fm = getActivity().getSupportFragmentManager();

            VectorUserGroupsDialogFragment fragment = (VectorUserGroupsDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_USER_GROUPS_DIALOG);
            if (fragment != null) {
                fragment.dismissAllowingStateLoss();
            }
            fragment = VectorUserGroupsDialogFragment.newInstance(mSession.getMyUserId(), userId, groupIds);
            fragment.show(fm, TAG_FRAGMENT_USER_GROUPS_DIALOG);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onGroupFlairClick() failed " + e.getMessage());
        }
    }

    @Override
    public void onURLClick(Uri uri) {
        try {
            if (null != uri) {
                HashMap<String, String> universalParams = VectorUniversalLinkReceiver.parseUniversalLink(uri);

                if (null != universalParams) {
                    // open the member sheet from the current activity
                    if (universalParams.containsKey(VectorUniversalLinkReceiver.ULINK_MATRIX_USER_ID_KEY)) {
                        Intent roomDetailsIntent = new Intent(getActivity(), VectorMemberDetailsActivity.class);
                        roomDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, universalParams.get(VectorUniversalLinkReceiver.ULINK_MATRIX_USER_ID_KEY));
                        roomDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                        getActivity().startActivityForResult(roomDetailsIntent, VectorRoomActivity.GET_MENTION_REQUEST_CODE);
                    } else {
                        // pop to the home activity
                        Intent intent = new Intent(getActivity(), VectorHomeActivity.class);
                        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_UNIVERSAL_LINK, uri);
                        getActivity().startActivity(intent);
                    }
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.putExtra(Browser.EXTRA_APPLICATION_ID, getActivity().getPackageName());
                    getActivity().startActivity(intent);
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onURLClick() failed " + e.getMessage());
        }
    }

    @Override
    public void onMatrixUserIdClick(final String userId) {
        try {
            // start member details UI
            Intent roomDetailsIntent = new Intent(getActivity(), VectorMemberDetailsActivity.class);
            roomDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
            roomDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, userId);
            roomDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
            startActivity(roomDetailsIntent);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onMatrixUserIdClick() failed " + e.getMessage());
        }
    }

    @Override
    public void onRoomAliasClick(String roomAlias) {
        try {
            onURLClick(Uri.parse(VectorUtils.getPermalink(roomAlias, null)));
        } catch (Exception e) {
            Log.e(LOG_TAG, "onRoomAliasClick failed " + e.getLocalizedMessage());
        }
    }

    @Override
    public void onRoomIdClick(String roomId) {
        try {
            onURLClick(Uri.parse(VectorUtils.getPermalink(roomId, null)));
        } catch (Exception e) {
            Log.e(LOG_TAG, "onRoomIdClick failed " + e.getLocalizedMessage());
        }
    }

    @Override
    public void onMessageIdClick(String messageId) {
        try {
            onURLClick(Uri.parse(VectorUtils.getPermalink(mRoom.getRoomId(), messageId)));
        } catch (Exception e) {
            Log.e(LOG_TAG, "onRoomIdClick failed " + e.getLocalizedMessage());
        }
    }

    @Override
    public void onGroupIdClick(String groupId) {
        try {
            onURLClick(Uri.parse(VectorUtils.getPermalink(groupId, null)));
        } catch (Exception e) {
            Log.e(LOG_TAG, "onRoomIdClick failed " + e.getLocalizedMessage());
        }
    }

    private int mInvalidIndexesCount = 0;

    @Override
    public void onInvalidIndexes() {
        mInvalidIndexesCount++;

        // it should happen once
        // else we assume that the adapter is really corrupted
        // It seems better to close the linked activity to avoid infinite refresh.
        if (1 == mInvalidIndexesCount) {
            mMessageListView.post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });
        } else {
            mMessageListView.post(new Runnable() {
                @Override
                public void run() {
                    if (null != getActivity()) {
                        getActivity().finish();
                    }
                }
            });
        }
    }

    private final HashMap<String, Boolean> mHighlightStatusByEventId = new HashMap<>();

    @Override
    public boolean shouldHighlightEvent(Event event) {
        // sanity check
        if ((null == event) || (null == event.eventId)) {
            return false;
        }

        String eventId = event.eventId;
        Boolean status = mHighlightStatusByEventId.get(eventId);

        if (null != status) {
            return status;
        }

        boolean res = (null != mSession.getDataHandler().getBingRulesManager().fulfilledHighlightBingRule(event));
        mHighlightStatusByEventId.put(eventId, res);

        return res;
    }
}
