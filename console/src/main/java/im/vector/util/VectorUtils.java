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
package im.vector.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.v4.util.LruCache;
import android.text.TextUtils;
import android.widget.ImageView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import im.vector.R;

public class VectorUtils {

    //==============================================================================================================
    // Rooms methods
    //==============================================================================================================

    /**
     * Vector client formats the room display with a different manner than the SDK one.
     * @param context the application context.
     * @param session the room session.
     * @param room the room.
     * @return the room displayname.
     */
    public static String getRoomDisplayname(Context context, MXSession session, Room room) {
        // sanity checks
        if (null == room) {
            return null;
        }

        // this algo is the one defined in
        // https://github.com/matrix-org/matrix-js-sdk/blob/develop/lib/models/room.js#L617
        // calculateRoomName(room, userId)

        RoomState roomState = room.getLiveState();

        if (!TextUtils.isEmpty(roomState.name)) {
            return roomState.name;
        }

        String alias = roomState.alias;

        if (TextUtils.isEmpty(alias)) {
            // For rooms where canonical alias is not defined, we use the 1st alias as a workaround
            List<String> aliases = roomState.aliases;

            if ((null != aliases) && (aliases.size() > 0)) {
                alias = aliases.get(0);
            }
        }

        if (!TextUtils.isEmpty(alias)) {
            return alias;
        }

        String myUserId = session.getMyUser().userId;

        Collection<RoomMember> members = roomState.getMembers();
        ArrayList<RoomMember> othersActiveMembers = new ArrayList<RoomMember>();
        ArrayList<RoomMember> activeMembers = new ArrayList<RoomMember>();

        for(RoomMember member : members) {
            if (!TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_LEAVE)) {
                if (!TextUtils.equals(member.getUserId(), myUserId)) {
                    othersActiveMembers.add(member);
                }
                activeMembers.add(member);
            }
        }

        Collections.sort(othersActiveMembers, new Comparator<RoomMember>() {
            @Override
            public int compare(RoomMember m1, RoomMember m2) {
                long diff = m1.getOriginServerTs() - m2.getOriginServerTs();

                return (diff == 0) ? 0 : ((diff < 0) ? -1 : +1);
            }
        });

        String displayName = "";

        if (othersActiveMembers.size() == 0) {
            if (activeMembers.size() == 1) {
                RoomMember member = activeMembers.get(0);

                if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {

                    if (!TextUtils.isEmpty(member.getInviterId())) {
                        // extract who invited us to the room
                        displayName = context.getString(R.string.room_displayname_invite_from, roomState.getMemberName(member.getInviterId()));
                    } else {
                        displayName = context.getString(R.string.room_displayname_room_invite);
                    }
                }
                else {
                    displayName = myUserId;
                }
            }
        }
        else if (othersActiveMembers.size() == 1) {
            RoomMember member = othersActiveMembers.get(0);
            displayName = roomState.getMemberName(member.getUserId());
        }
        else if (othersActiveMembers.size() == 2) {
            RoomMember member1 = othersActiveMembers.get(0);
            RoomMember member2 = othersActiveMembers.get(1);

            displayName = context.getString(R.string.room_displayname_two_members, roomState.getMemberName(member1.getUserId()), roomState.getMemberName(member2.getUserId()));
        }
        else {
            RoomMember member = othersActiveMembers.get(0);
            displayName = context.getString(R.string.room_displayname_more_than_two_members, roomState.getMemberName(member.getUserId()), othersActiveMembers.size() - 1);
        }

        return displayName;
    }

    //==============================================================================================================
    // Avatars generation
    //==============================================================================================================

    // avatars cache
    static LruCache<String, Bitmap> mAvatarImageByKeyDict = new LruCache<String, Bitmap>(2 * 1024 * 1024);
    // the avatars background color
    static ArrayList<Integer> mColorList = new ArrayList<Integer>(Arrays.asList(0xff76cfa6, 0xff50e2c2, 0xfff4c371));

    /**
     * Provides the avatar background color from a text.
     * @param text the text.
     * @return the color.
     */
    private static int getAvatarcolor(String text) {
        long colorIndex = 0;

        if (!TextUtils.isEmpty(text)) {
            long sum = 0;

            for(int i = 0; i < text.length(); i++) {
                sum += text.charAt(i);
            }

            colorIndex = sum % mColorList.size();
        }

        return mColorList.get((int)colorIndex);
    }

    /**
     * Create an avatar bitmap from a text.
     * @param context the context.
     * @param text the text to display.
     * @return the generated bitmap
     */
    private static Bitmap createAvatar(Context context, String text) {
        android.graphics.Bitmap.Config bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;

        // the bitmap size
        int thumbnailSide = 42;

        float densityScale = context.getResources().getDisplayMetrics().density;
        int side = (int)(thumbnailSide * densityScale);

        Bitmap bitmap = Bitmap.createBitmap(side, side, bitmapConfig);
        Canvas canvas = new Canvas(bitmap);

        // prepare the text drawing
        Paint textPaint = new Paint();
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28 * densityScale);

        // get its size
        Rect textBounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), textBounds);

        // draw the text in center
        canvas.drawText(text, (canvas.getWidth() - textBounds.width() - textBounds.left) / 2 , (canvas.getHeight() + textBounds.height() - textBounds.bottom) / 2, textPaint);

        // Return the avatar
        return bitmap;
    }

    /**
     * Returns an avatar from a text.
     * @param context the context.
     * @param aText the text.
     * @return the avatar.
     */
    public static Bitmap getAvatar(Context context, String aText) {
        // ignore some characters
        if (!TextUtils.isEmpty(aText) && (aText.startsWith("@") || aText.startsWith("#"))) {
            aText = aText.substring(1);
        }

        String firstChar = " ";

        if (!TextUtils.isEmpty(aText)) {
            firstChar = aText.substring(0, 1).toUpperCase();
        }

        // check if the avatar is already defined
        Bitmap thumbnail = mAvatarImageByKeyDict.get(firstChar);

        if (null == thumbnail) {
            thumbnail = VectorUtils.createAvatar(context, firstChar);
            mAvatarImageByKeyDict.put(firstChar, thumbnail);
        }

        return thumbnail;
    }

    /**
     * Set the avatar for a text.
     * @param imageView the imageView to set.
     * @param text the text.
     */
    public static void setTextAvatar(ImageView imageView, String text) {
        VectorUtils.setMemberAvatar(imageView, text, text);
    }

    /**
     * Set the avatar for a member.
     * @param imageView the imageView to set.
     * @param userId the member userId.
     * @param displayName the member display name.
     */
    public static void setMemberAvatar(ImageView imageView, String userId, String displayName) {
        // sanity checks
        if (null != imageView && !TextUtils.isEmpty(userId)) {
            imageView.setBackgroundColor(VectorUtils.getAvatarcolor(userId));
            imageView.setImageBitmap(VectorUtils.getAvatar(imageView.getContext(), TextUtils.isEmpty(displayName) ? userId : displayName));
        }
    }

    /**
     * Set the room avatar.
     * @param imageView the image view.
     * @param roomId the room id.
     * @param displayName the room displayname.
     */
    public static void setRoomVectorAvatar(ImageView imageView, String roomId, String displayName) {
        VectorUtils.setMemberAvatar(imageView, roomId, displayName);
    }
}
