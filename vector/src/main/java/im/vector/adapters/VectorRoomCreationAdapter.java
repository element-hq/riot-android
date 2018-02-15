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
import android.text.TextUtils;

import org.matrix.androidsdk.util.Log;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.util.VectorUtils;

/**
 * This class displays a list of members to create a room.
 */
public class VectorRoomCreationAdapter extends ArrayAdapter<ParticipantAdapterItem> {

    private static final String LOG_TAG = VectorRoomCreationAdapter.class.getSimpleName();

    // remove participants listener
    public interface IRoomCreationAdapterListener {
        /**
         * The user taps on the cross button
         *
         * @param item the number of matched user
         */
        void OnRemoveParticipantClick(ParticipantAdapterItem item);
    }

    // layout info
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;

    // account info
    private final MXSession mSession;

    // used layouts
    private final int mMemberLayoutResourceId;
    private final int mAddMemberLayoutResourceId;

    // members list display names
    private final ArrayList<String> mDisplayNamesList = new ArrayList<>();

    // the events listener
    private IRoomCreationAdapterListener mRoomCreationAdapterListener;

    /**
     * Create a room creation adapter.
     *
     * @param context                   the context.
     * @param addMemberLayoutResourceId the add member layout.
     * @param memberLayoutResourceId    the member layout id
     * @param session                   the session.
     */
    public VectorRoomCreationAdapter(Context context, int addMemberLayoutResourceId, int memberLayoutResourceId, MXSession session) {
        super(context, memberLayoutResourceId);

        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mAddMemberLayoutResourceId = addMemberLayoutResourceId;
        mMemberLayoutResourceId = memberLayoutResourceId;
        mSession = session;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();

        // list the names to concat user id if several users have the same display name
        mDisplayNamesList.clear();

        for (int i = 0; i < getCount(); i++) {
            ParticipantAdapterItem item = getItem(i);

            if (!TextUtils.isEmpty(item.mDisplayName)) {
                mDisplayNamesList.add(item.mDisplayName.toLowerCase(VectorApp.getApplicationLocale()));
            }
        }
    }

    /**
     * Defines a listener.
     *
     * @param aListener the new listener.
     */
    public void setRoomCreationAdapterListener(IRoomCreationAdapterListener aListener) {
        mRoomCreationAdapterListener = aListener;
    }

    @Override
    public int getViewTypeCount() {
        // add member section and member
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return (0 == position) ? 0 : 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (0 == position) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(mAddMemberLayoutResourceId, parent, false);
            }

            return convertView;
        }

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mMemberLayoutResourceId, parent, false);
        }

        final ParticipantAdapterItem participant = getItem(position);

        // retrieve the ui items
        final ImageView thumbView = convertView.findViewById(R.id.filtered_list_avatar);
        final TextView nameTextView = convertView.findViewById(R.id.filtered_list_name);
        final TextView statusTextView = convertView.findViewById(R.id.filtered_list_status);
        final ImageView matrixUserBadge = convertView.findViewById(R.id.filtered_list_matrix_user);

        // display the avatar
        participant.displayAvatar(mSession, thumbView);

        // the display name
        nameTextView.setText(participant.getUniqueDisplayName(mDisplayNamesList));

        // set the presence
        String status = "";

        User user = null;
        MXSession matchedSession = null;
        // retrieve the linked user
        ArrayList<MXSession> sessions = Matrix.getMXSessions(mContext);

        for (MXSession session : sessions) {
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

            boolean isMatrixUserId = !android.util.Patterns.EMAIL_ADDRESS.matcher(participant.mUserId).matches();
            matrixUserBadge.setVisibility(isMatrixUserId ? View.VISIBLE : View.GONE);
        } else {
            statusTextView.setText(status);
            matrixUserBadge.setVisibility(View.GONE);
        }

        // the checkbox is not managed here
        final CheckBox checkBox = convertView.findViewById(R.id.filtered_list_checkbox);
        checkBox.setVisibility(View.GONE);

        final View removeParticipantImageView = convertView.findViewById(R.id.filtered_list_remove_button);
        removeParticipantImageView.setVisibility(View.VISIBLE);

        removeParticipantImageView.setOnClickListener(new View.OnClickListener() {
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
