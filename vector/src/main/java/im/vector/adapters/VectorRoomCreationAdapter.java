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

package im.vector.adapters;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import im.vector.Matrix;
import im.vector.R;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
import im.vector.util.VectorUtils;

/**
 * This class displays a list of members
 */
public class VectorRoomCreationAdapter extends ArrayAdapter<ParticipantAdapterItem> {

    private static final String LOG_TAG = "VRoomCreationAdapter";

    // remove participants listener
    public interface IRoomCreationAdapterListener {
        /**
         * The user taps on the cross button
         * @param item the number of matched user
         */
        void OnRemoveParticipantClick(ParticipantAdapterItem item);
    }

    // layout info
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;

    // account info
    private final MXSession mSession;

    // used layout
    private final int mLayoutResourceId;

    // members list displaynames
    private ArrayList<String> mDisplayNamesList = new ArrayList<>();

    // the events listener
    private IRoomCreationAdapterListener mRoomCreationAdapterListener;

    /**
     * Create a room creation adapter.
     * @param context the context.
     * @param layoutResourceId the layout.
     * @param session the session.
     */
    public VectorRoomCreationAdapter(Context context, int layoutResourceId, MXSession session) {
        super(context, layoutResourceId);

        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mLayoutResourceId = layoutResourceId;
        mSession = session;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();

        mDisplayNamesList.clear();

        for(int i = 0; i < getCount(); i++) {
            ParticipantAdapterItem item = getItem(i);

            if (!TextUtils.isEmpty(item.mDisplayName)) {
                mDisplayNamesList.add(item.mDisplayName.toLowerCase());
            }
        }
    }

    /**
     * Defines a listener.
     * @param aListener the new listener.
     */
    public void setRoomCreationAdapterListener(IRoomCreationAdapterListener aListener) {
        mRoomCreationAdapterListener = aListener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        final ParticipantAdapterItem participant = getItem(position);

        // retrieve the ui items
        final ImageView thumbView = (ImageView) convertView.findViewById(R.id.filtered_list_avatar);
        final TextView nameTextView = (TextView) convertView.findViewById(R.id.filtered_list_name);
        final TextView statusTextView = (TextView) convertView.findViewById(R.id.filtered_list_status);
        final ImageView matrixUserBadge =  (ImageView) convertView.findViewById(R.id.filtered_list_matrix_user);

        // set the avatar
        if (null != participant.mAvatarBitmap) {
            thumbView.setImageBitmap(participant.mAvatarBitmap);
        } else {
             if ((null != participant.mUserId) && (android.util.Patterns.EMAIL_ADDRESS.matcher(participant.mUserId).matches())) {
                thumbView.setImageBitmap(VectorUtils.getAvatar(thumbView.getContext(), VectorUtils.getAvatarColor(participant.mUserId), "@@", true));
            } else {
                if (TextUtils.isEmpty(participant.mUserId)) {
                    VectorUtils.loadUserAvatar(mContext, mSession, thumbView, participant.mAvatarUrl, participant.mDisplayName, participant.mDisplayName);
                } else {

                    // try to provide a better display for a participant when the user is known.
                    if (TextUtils.equals(participant.mUserId, participant.mDisplayName) || TextUtils.isEmpty(participant.mAvatarUrl)) {
                        IMXStore store = mSession.getDataHandler().getStore();

                        if (null != store) {
                            User user = store.getUser(participant.mUserId);

                            if (null != user) {
                                if (TextUtils.equals(participant.mUserId, participant.mDisplayName) && !TextUtils.isEmpty(user.displayname)) {
                                    participant.mDisplayName = user.displayname;
                                }

                                if (null == participant.mAvatarUrl) {
                                    participant.mAvatarUrl = user.avatar_url;
                                }
                            }
                        }
                    }

                    VectorUtils.loadUserAvatar(mContext, mSession, thumbView, participant.mAvatarUrl, participant.mUserId, participant.mDisplayName);
                }
            }
        }

        boolean isMatrixUserId = !android.util.Patterns.EMAIL_ADDRESS.matcher(participant.mUserId).matches();

        // set the display name
        String displayname = participant.mDisplayName;
        String lowerCaseDisplayname = displayname.toLowerCase();

        // detect if the username is used by several users
        int pos = mDisplayNamesList.indexOf(lowerCaseDisplayname);

        if (pos >= 0) {
            if (pos == mDisplayNamesList.lastIndexOf(lowerCaseDisplayname)) {
                pos = -1;
            }
        }

        if ((pos >= 0) && isMatrixUserId) {
            displayname += " (" + participant.mUserId + ")";
        }

        // if a contact has a matrix id
        // display the matched email address in the display name
        if ((null != participant.mContact) && isMatrixUserId) {
            String firstEmail = participant.mContact.getEmails().get(0);

            if (!TextUtils.equals(displayname, firstEmail)) {
                displayname += " (" +  firstEmail + ")";
            }
        }

        nameTextView.setText(displayname);

        // set the presence
        String status = "";

        User user = null;
        MXSession matchedSession = null;
        // retrieve the linked user
        ArrayList<MXSession> sessions = Matrix.getMXSessions(mContext);

        for(MXSession session : sessions) {
            if (null == user) {
                matchedSession = session;
                user = session.getDataHandler().getUser(participant.mUserId);
            }
        }

        if (null != user) {
            status = VectorUtils.getUserOnlineStatus(mContext, matchedSession, participant.mUserId, new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    VectorRoomCreationAdapter.this.notifyDataSetChanged();
                }
            });
        }

        // the contact defines a matrix user but there is no way to get more information (presence, avatar)
        if (participant.mContact != null) {
            statusTextView.setText(participant.mUserId);
            matrixUserBadge.setVisibility(isMatrixUserId ? View.VISIBLE : View.GONE);
        }
        else {
            statusTextView.setText(status);
            matrixUserBadge.setVisibility(View.GONE);
        }

        // the checkbox is not managed here
        final CheckBox checkBox = (CheckBox)convertView.findViewById(R.id.filtered_list_checkbox);
        checkBox.setVisibility(View.GONE);

        final View removePartipantImageView = convertView.findViewById(R.id.filtered_list_remove_button);
        removePartipantImageView.setVisibility(View.VISIBLE);

        removePartipantImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mRoomCreationAdapterListener) {
                    try {
                        mRoomCreationAdapterListener.OnRemoveParticipantClick(participant);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## getView() : OnRemoveParticipantClick fails " + e.getMessage());
                    }
                }
            }
        });

        return convertView;
    }
}
