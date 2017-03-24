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

package im.vector.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
import im.vector.util.VectorUtils;

public class ContactAdapter extends AbsListAdapter<ParticipantAdapterItem, ContactAdapter.ContactViewHolder> {

    private final Context mContext;
    private final MXSession mSession;

    private final Comparator<ParticipantAdapterItem> mComparator;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public ContactAdapter(final Context context, final Comparator<ParticipantAdapterItem> comparator, final OnSelectItemListener<ParticipantAdapterItem> listener) {
        super(R.layout.adapter_item_contact_view, listener);
        mContext = context;
        mSession = Matrix.getInstance(context).getDefaultSession();
        mComparator = comparator;
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */
    @Override
    public void setItems(final List<ParticipantAdapterItem> items, final Filter.FilterListener listener) {
        Collections.sort(items, mComparator);
        super.setItems(items, listener);
    }

    /**
     * Update the adapter item corresponding to the given user id
     *
     * @param user
     */
    public void updateItemWithUser(final User user) {
        for (int i = 0; i < mItems.size(); i++) {
            ParticipantAdapterItem item = mItems.get(i);
            if (TextUtils.equals(user.user_id, item.mUserId)) {
                notifyItemChanged(i);
            }
        }
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected ContactViewHolder createViewHolder(View itemView) {
        return new ContactViewHolder(itemView);
    }

    @Override
    protected void populateViewHolder(ContactViewHolder viewHolder, ParticipantAdapterItem item) {
        viewHolder.populateViews(item);
    }

    @Override
    protected List<ParticipantAdapterItem> getFilterItems(List<ParticipantAdapterItem> items, String pattern) {
        List<ParticipantAdapterItem> filteredContacts = new ArrayList<>();

        final String formattedPattern = pattern != null ? pattern.toLowerCase().trim().toLowerCase() : "";
        for (final ParticipantAdapterItem item : items) {
            if (item.startsWith(formattedPattern)) {
                filteredContacts.add(item);
            }
        }

        return filteredContacts;
    }

    /*
     * *********************************************************************************************
     * View holder
     * *********************************************************************************************
     */

    class ContactViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.contact_avatar)
        ImageView vContactAvatar;

        @BindView(R.id.contact_badge)
        ImageView vContactBadge;

        @BindView(R.id.contact_name)
        TextView vContactName;

        @BindView(R.id.contact_desc)
        TextView vContactDesc;

        private ContactViewHolder(final View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        private void populateViews(final ParticipantAdapterItem participant) {
            participant.displayAvatar(mSession, vContactAvatar);
            vContactName.setText(participant.getUniqueDisplayName(null));

            /*
             * Get the description to be displayed below the name
             * For local contact, it is the medium (email, phone number)
             * For other contacts, it is the presence
             */
            if (participant.mContact != null) {
                boolean isMatrixUserId = MXSession.PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER.matcher(participant.mUserId).matches();
                vContactBadge.setVisibility(isMatrixUserId ? View.VISIBLE : View.GONE);

                if (participant.mContact.getEmails().size() > 0) {
                    vContactDesc.setText(participant.mContact.getEmails().get(0));
                } else {
                    vContactDesc.setText(participant.mContact.getPhonenumbers().get(0).mRawPhoneNumber);
                }
            } else {
                loadContactPresence(vContactDesc, participant);
                vContactBadge.setVisibility(View.GONE);
            }
        }

        /**
         * Get the presence for the given contact
         *
         * @param textView
         * @param item
         */
        private void loadContactPresence(final TextView textView, final ParticipantAdapterItem item) {
            User user = null;
            MXSession matchedSession = null;
            // retrieve the linked user
            ArrayList<MXSession> sessions = Matrix.getMXSessions(mContext);

            for (MXSession session : sessions) {
                if (null == user) {
                    matchedSession = session;
                    user = session.getDataHandler().getUser(item.mUserId);
                }
            }

            if (null != user) {
                final MXSession finalMatchedSession = matchedSession;
                final String presence = VectorUtils.getUserOnlineStatus(mContext, matchedSession, item.mUserId, new SimpleApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        if (textView != null) {
                            textView.setText(VectorUtils.getUserOnlineStatus(mContext, finalMatchedSession, item.mUserId, null));
                            // TODO
//                            Collections.sort(mItems, mComparator);
//                            setItems(mItems, null);
//                            notifyDataSetChanged();
                        }
                    }
                });
                textView.setText(presence);
            }
        }
    }
}
