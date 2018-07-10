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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.VectorRoomActivity;
import im.vector.util.VectorUtils;
import im.vector.view.VectorCircularImageView;

import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class describes a list of auto-completed users
 */
public class AutoCompletedUserAdapter extends ArrayAdapter<User> {
    // the context
    private final Context mContext;

    // the layout inflater
    private final LayoutInflater mLayoutInflater;

    // the layout to draw items
    private final int mLayoutResourceId;

    // the session
    private final MXSession mSession;

    // the filter
    private android.widget.Filter mFilter;

    // cannot use the parent list
    private List<User> mUsersList = new ArrayList<>();

    // tell if the current search is on matrix IDs
    private boolean mIsSearchingMatrixId = false;

    // tells if the matrix Id is pasted even if the search is done with an username
    private boolean mProvideMatrixIdOnly = false;

    /**
     * Comparators
     */
    private static final Comparator<User> mUserComparatorByUserId = new Comparator<User>() {
        @Override
        public int compare(User user1, User user2) {
            return user1.user_id.compareToIgnoreCase(user2.user_id);
        }
    };

    private static final Comparator<User> mUserComparatorByDisplayname = new Comparator<User>() {
        @Override
        public int compare(User user1, User user2) {
            String displayName1 = TextUtils.isEmpty(user1.displayname) ? user1.user_id : user1.displayname;
            String displayName2 = TextUtils.isEmpty(user2.displayname) ? user2.user_id : user2.displayname;

            return displayName1.compareToIgnoreCase(displayName2);
        }
    };

    /**
     * Construct an adapter which will display a list of users
     *
     * @param context          Activity context
     * @param layoutResourceId The resource ID of the layout for each item.
     * @param session          the session
     * @param users            the users list
     */
    public AutoCompletedUserAdapter(Context context, int layoutResourceId, MXSession session, Collection<User> users) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        mSession = session;
        addAll(users);
        mUsersList = new ArrayList<>(users);
    }

    /**
     * Tells if the pasted text is always the user matrix id
     * even if the matched pattern is a display name.
     *
     * @param provideMatrixIdOnly true to always paste an user Id.
     */
    public void setProvideMatrixIdOnly(boolean provideMatrixIdOnly) {
        mProvideMatrixIdOnly = provideMatrixIdOnly;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent, true);
    }

    /**
     * Get the updated view for a specified position.
     *
     * @param position the position
     * @param convertView the convert view
     * @param parent the parent view
     * @param loadAvatar true to refresh the avatar
     *
     * @return the view
     */
    public View getView(int position, View convertView, ViewGroup parent, boolean loadAvatar) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        User user = getItem(position);

        if (loadAvatar) {
            VectorCircularImageView avatarView = convertView.findViewById(R.id.item_user_auto_complete_avatar);
            VectorUtils.loadUserAvatar(mContext, mSession, avatarView, user.getAvatarUrl(), user.user_id, user.displayname);
        }

        TextView userNameTextView = convertView.findViewById(R.id.item_user_auto_complete_name);

        if (!mIsSearchingMatrixId) {
            String value = user.displayname;

            if (mProvideMatrixIdOnly) {
                value += " (" + user.user_id + ")";
            }

            userNameTextView.setText(value);
        } else {
            userNameTextView.setText(user.user_id);
        }

        return convertView;
    }

    @Override
    public android.widget.Filter getFilter() {
        if (mFilter == null) {
            mFilter = new AutoCompletedUserFilter();
        }
        return mFilter;
    }

    private class AutoCompletedUserFilter extends android.widget.Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();
            List<User> newValues;

            if (prefix == null || prefix.length() == 0) {
                newValues = new ArrayList<>();
                mIsSearchingMatrixId = true;
            } else {
                newValues = new ArrayList<>();
                String prefixString = prefix.toString().toLowerCase(VectorApp.getApplicationLocale());
                mIsSearchingMatrixId = prefixString.startsWith("@");

                if (mIsSearchingMatrixId) {
                    for (User user : mUsersList) {
                        if ((null != user.user_id) && user.user_id.toLowerCase(VectorApp.getApplicationLocale()).startsWith(prefixString)) {
                            newValues.add(user);
                        }
                    }
                } else {
                    for (User user : mUsersList) {
                        if ((null != user.displayname) && user.displayname.toLowerCase(VectorApp.getApplicationLocale()).startsWith(prefixString)) {
                            newValues.add(user);
                        }
                    }
                }
            }

            // sort the results
            if (mIsSearchingMatrixId) {
                Collections.sort(newValues, mUserComparatorByUserId);
            } else {
                Collections.sort(newValues, mUserComparatorByDisplayname);
            }

            results.values = newValues;
            results.count = newValues.size();

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            clear();
            addAll((List<User>) results.values);
            if (results.count > 0) {
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            User user = (User) resultValue;
            return (mIsSearchingMatrixId || mProvideMatrixIdOnly) ?
                    user.user_id :
                    VectorRoomActivity.sanitizeDisplayname(user.displayname);
        }
    }
}
