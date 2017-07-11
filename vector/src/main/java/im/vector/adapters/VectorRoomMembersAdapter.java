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
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.HomeserverConnectionConfig;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.view.PieFractionView;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import im.vector.R;

/**
 * An adapter which can display m.room.member content.
 */
public class VectorRoomMembersAdapter extends ArrayAdapter<RoomMember> {

    // the context
    protected Context mContext;

    // the layout
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    // the power levels information
    private PowerLevels mPowerLevels;
    private int mMaxPowerLevel;

    // the room state
    private RoomState mRoomState;

    // membership description by MEMBERSHIP_XXX
    private HashMap<String, String> mMembershipStrings = new HashMap<>();

    // User by user id
    private Map<String, User> mUserMap = new HashMap<>();

    // sort method
    private boolean mSortByLastActive = true;

    // display the membership
    private boolean mDisplayMembership = true;

    // the used medias cache
    private MXMediasCache mMediasCache = null;

    // member display name by user id
    private HashMap<String, String> mMembersSortMemberNameByUserId = new HashMap<>();

    // the home server config
    private final HomeserverConnectionConfig mHsConfig;

    /**
     * Construct an adapter which will display a list of room members.
     *
     * @param context           Activity context
     * @param hsConfig          teh home server config
     * @param layoutResourceId  The resource ID of the layout for each item. Must have TextViews with
     *                          the IDs: roomMembersAdapter_name, roomMembersAdapter_membership, and
     *                          an ImageView with the ID avatar_img.
     * @param roomState         the roomState.
     * @param mediasCache       the media cache
     * @param membershipStrings the membership strings by RoomMember.MEMBERSHIP_XX value
     */
    public VectorRoomMembersAdapter(Context context, HomeserverConnectionConfig hsConfig, int layoutResourceId, RoomState roomState, MXMediasCache mediasCache, HashMap<String, String> membershipStrings) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        mRoomState = roomState;

        // left the caller manages the refresh
        setNotifyOnChange(false);

        mMembershipStrings = membershipStrings;
        mMediasCache = mediasCache;

        mHsConfig = hsConfig;
    }

    public int lastSeenTextColor() {
        return mContext.getResources().getColor(R.color.member_list_last_seen_text);
    }

    public int presenceOnlineColor() {
        return mContext.getResources().getColor(R.color.presence_online);
    }

    public int presenceOfflineColor() {
        return mContext.getResources().getColor(R.color.presence_offline);
    }

    public int presenceUnavailableColor() {
        return mContext.getResources().getColor(R.color.presence_unavailable);
    }

    /**
     * Set the sort method.
     *
     * @param useLastActive true to sort by last active ts, false to sort by alphabetical order
     */
    public void sortByLastActivePresence(boolean useLastActive) {
        mSortByLastActive = useLastActive;
    }

    /**
     * Tell if the membership must be displayed in the cells
     *
     * @param withMembership true to display the membership in the cell
     */
    public void displayMembership(boolean withMembership) {
        mDisplayMembership = withMembership;
    }

    /**
     * Return the cached member display name by its user id
     *
     * @param userId the user id
     * @return the display name
     */
    private String getCachedMemberName(String userId) {
        // sanity check
        if (null == userId) {
            return null;
        }

        if (mMembersSortMemberNameByUserId.containsKey(userId)) {
            return mMembersSortMemberNameByUserId.get(userId);
        }

        String memberName = mRoomState.getMemberName(userId);
        mMembersSortMemberNameByUserId.put(userId, memberName);
        return memberName;
    }


    // Comparator to order members alphabetically
    private Comparator<RoomMember> alphaComparator = new Comparator<RoomMember>() {
        @Override
        public int compare(RoomMember member1, RoomMember member2) {
            String lhs = getCachedMemberName(member1.getUserId());
            String rhs = getCachedMemberName(member2.getUserId());

            if (member1.membership.equals(member2.membership)) {
                if (lhs == null) {
                    return -1;
                } else if (rhs == null) {
                    return 1;
                }
                if (lhs.startsWith("@")) {
                    lhs = lhs.substring(1);
                }
                if (rhs.startsWith("@")) {
                    rhs = rhs.substring(1);
                }
                return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
            } else {
                // sort by membership

                // display the joined members before the other one
                if (member1.membership.equals(RoomMember.MEMBERSHIP_JOIN)) {
                    return -1;
                } else if (member2.membership.equals(RoomMember.MEMBERSHIP_JOIN)) {
                    return +1;
                } else if (member1.membership.equals(RoomMember.MEMBERSHIP_INVITE)) {
                    return -1;
                } else if (member2.membership.equals(RoomMember.MEMBERSHIP_INVITE)) {
                    return +1;
                } else if (member1.membership.equals(RoomMember.MEMBERSHIP_LEAVE)) {
                    return -1;
                } else if (member2.membership.equals(RoomMember.MEMBERSHIP_LEAVE)) {
                    return +1;
                }

                return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
            }
        }
    };

    // Comparator to order members by last active time
    private Comparator<RoomMember> lastActiveComparator = new Comparator<RoomMember>() {
        @Override
        public int compare(RoomMember member1, RoomMember member2) {
            if (member1.membership.equals(member2.membership)) {
                User lUser = mUserMap.get(member1.getUserId());
                User rUser = mUserMap.get(member2.getUserId());

                // Null cases
                if ((lUser == null) || (lUser.lastActiveAgo == null)) {
                    if ((rUser == null) || (rUser.lastActiveAgo == null)) {
                        // Fall back to alphabetical order
                        return alphaComparator.compare(member1, member2);
                    }
                    return 1;
                }
                if ((rUser == null) || (rUser.lastActiveAgo == null)) {
                    return -1;
                }

                // Non-null cases
                long lLastActive = lUser.getAbsoluteLastActiveAgo();
                long rLastActive = rUser.getAbsoluteLastActiveAgo();
                if (lLastActive < rLastActive) return -1;
                if (lLastActive > rLastActive) return 1;

                // Fall back to alphabetical order
                return alphaComparator.compare(member1, member2);
            } else {
                // sort by membership

                // display the joined members before the other one
                if (member1.membership.equals(RoomMember.MEMBERSHIP_JOIN)) {
                    return -1;
                } else if (member2.membership.equals(RoomMember.MEMBERSHIP_JOIN)) {
                    return +1;
                } else if (member1.membership.equals(RoomMember.MEMBERSHIP_INVITE)) {
                    return -1;
                } else if (member2.membership.equals(RoomMember.MEMBERSHIP_INVITE)) {
                    return +1;
                } else if (member1.membership.equals(RoomMember.MEMBERSHIP_LEAVE)) {
                    return -1;
                } else if (member2.membership.equals(RoomMember.MEMBERSHIP_LEAVE)) {
                    return +1;
                }

                String lhs = getCachedMemberName(member1.getUserId());
                String rhs = getCachedMemberName(member2.getUserId());

                return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
            }
        }
    };

    /**
     * Sort the members list with the dedicated sort methods (last presence or alphabetical).
     */
    public void sortMembers() {
        // create a dictionnary to avoid computing the member name at each sort step.
        // mRoomState.getMemberName(userId) can be complex
        mMembersSortMemberNameByUserId = new HashMap<>();

        if (mSortByLastActive) {
            sort(lastActiveComparator);
        } else {
            sort(alphaComparator);
        }
    }

    /**
     * Update the power levels information (to display each a member is administrator...).
     *
     * @param powerLevels the new power levels.
     */
    public void setPowerLevels(PowerLevels powerLevels) {
        mPowerLevels = powerLevels;
        if (powerLevels != null) {
            // Process power levels to find the max. The display will show power levels as a fraction of this
            mMaxPowerLevel = powerLevels.users_default;
            Iterator it = powerLevels.users.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> pair = (Map.Entry<String, Integer>) it.next();
                if (pair.getValue() > mMaxPowerLevel) mMaxPowerLevel = pair.getValue();
            }
        }
        notifyDataSetChanged();
    }

    /**
     * Store the user in the known users dictionnary.
     *
     * @param user the user to add.
     * @return true if the user is added.
     */
    public boolean saveUser(User user) {
        if (user != null) {
            if (!mUserMap.containsKey(user.user_id)) {
                mUserMap.put(user.user_id, user);
                return true;
            }
        }

        return false;
    }

    /**
     * Delete an user from the known users list.
     *
     * @param user the user to remove
     */
    public void deleteUser(User user) {
        if (user != null) {
            if (mUserMap.containsKey(user.user_id)) {
                mUserMap.remove(user.user_id);
            }
        }
    }

    /**
     * Update the user information of a known user.
     *
     * @param userId the user id
     * @param member its new room memeber definition.
     */
    public void updateMember(String userId, RoomMember member) {
        for (int i = 0; i < getCount(); i++) {
            RoomMember m = getItem(i);
            if (userId.equals(m.getUserId())) {
                // Copy members
                m.displayname = member.displayname;
                m.setAvatarUrl(member.getAvatarUrl());
                m.membership = member.membership;
                notifyDataSetChanged();
                break;
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        RoomMember member = getItem(position);
        User user = mUserMap.get(member.getUserId());

        // Member name and last seen time
        TextView textView = (TextView) convertView.findViewById(R.id.roomMembersAdapter_name);

        if ((user == null) || (user.lastActiveAgo == null)) {
            textView.setText(member.getName());
        } else {
            String memberName = member.getName();
            String lastActiveDisplay = "(" + buildLastActiveDisplay(mContext, user.getAbsoluteLastActiveAgo()) + ")";

            SpannableStringBuilder ssb = new SpannableStringBuilder(memberName + " " + lastActiveDisplay);
            int lastSeenTextColor = lastSeenTextColor();
            ssb.setSpan(new ForegroundColorSpan(lastSeenTextColor), memberName.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(ssb);
        }

        textView = (TextView) convertView.findViewById(R.id.roomMembersAdapter_membership);

        if ((user != null) && User.PRESENCE_OFFLINE.equals(user.presence) && !RoomMember.MEMBERSHIP_LEAVE.equals(member.membership) && !RoomMember.MEMBERSHIP_BAN.equals(member.membership)) {
            textView.setText(User.PRESENCE_OFFLINE);
            textView.setTextColor(presenceOfflineColor());
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setText(mMembershipStrings.get(member.membership));
            textView.setTextColor(Color.BLACK);
            textView.setVisibility((mDisplayMembership || RoomMember.MEMBERSHIP_INVITE.equals(member.membership)) ? View.VISIBLE : View.GONE);
        }

        textView = (TextView) convertView.findViewById(R.id.roomMembersAdapter_userId);
        textView.setText(member.getUserId());

        ImageView imageView = (ImageView) convertView.findViewById(R.id.avatar_img);
        imageView.setTag(null);
        imageView.setImageResource(R.drawable.ic_contact_picture_holo_light);
        String url = member.getAvatarUrl();

        if (TextUtils.isEmpty(url)) {
            url = ContentManager.getIdenticonURL(member.getUserId());
        }

        if (!TextUtils.isEmpty(url)) {
            int size = getContext().getResources().getDimensionPixelSize(R.dimen.member_list_avatar_size);
            mMediasCache.loadAvatarThumbnail(mHsConfig, imageView, url, size);
        }

        // The presence ring
        ImageView presenceRing = (ImageView) convertView.findViewById(R.id.imageView_presenceRing);
        presenceRing.setColorFilter(mContext.getResources().getColor(android.R.color.transparent));
        if (user != null) {
            if (User.PRESENCE_ONLINE.equals(user.presence)) {
                presenceRing.setColorFilter(presenceOnlineColor());
            } else if (User.PRESENCE_UNAVAILABLE.equals(user.presence)) {
                presenceRing.setColorFilter(presenceUnavailableColor());
            } else if (User.PRESENCE_OFFLINE.equals(user.presence)) {
                presenceRing.setColorFilter(presenceUnavailableColor());
            }
        }

        // The power level disc
        PieFractionView pieFractionView = (PieFractionView) convertView.findViewById(R.id.powerDisc);
        if ((mPowerLevels == null) || (0 == mMaxPowerLevel)) {
            pieFractionView.setVisibility(View.GONE);
        } else {
            int powerLevel = mPowerLevels.getUserPowerLevel(member.getUserId());
            pieFractionView.setVisibility((powerLevel == 0) ? View.GONE : View.VISIBLE);
            pieFractionView.setFraction(powerLevel * 100 / mMaxPowerLevel);
        }

        // the invited members are displayed with alpha 0.5
        if (member.membership.equals(RoomMember.MEMBERSHIP_INVITE)) {
            convertView.setAlpha(0.3f);
        } else {
            convertView.setAlpha(1.0f);
        }

        if (member.membership.equals(RoomMember.MEMBERSHIP_LEAVE) || member.membership.equals(RoomMember.MEMBERSHIP_BAN)) {
            convertView.setBackgroundResource(android.R.color.darker_gray);
        } else {
            convertView.setBackgroundResource(android.R.color.transparent);
        }

        return convertView;
    }

    /**
     * Stringify the member last active ago.
     *
     * @param context       the context
     * @param lastActiveAgo the last active ago.
     * @return the stringify value.
     */
    public static String buildLastActiveDisplay(Context context, long lastActiveAgo) {
        lastActiveAgo /= 1000; // In seconds
        if (lastActiveAgo < 60) {
            return context.getString(R.string.last_seen_secs, lastActiveAgo);
        }

        lastActiveAgo /= 60; // In minutes
        if (lastActiveAgo < 60) {
            return context.getString(R.string.last_seen_mins, lastActiveAgo);
        }

        lastActiveAgo /= 60; // In hours
        if (lastActiveAgo < 24) {
            return context.getString(R.string.last_seen_hours, lastActiveAgo);
        }

        lastActiveAgo /= 24; // In days
        return context.getString(R.string.last_seen_days, lastActiveAgo);
    }
}