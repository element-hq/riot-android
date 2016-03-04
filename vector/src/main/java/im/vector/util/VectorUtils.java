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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.util.LruCache;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.ImageUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import im.vector.R;
import im.vector.db.VectorContentProvider;

public class VectorUtils {

    public static final String LOG_TAG = "VectorUtils";

    public static final int REQUEST_FILES = 0;
    public static final int TAKE_IMAGE = 1;

    //==============================================================================================================
    // Rooms methods
    //==============================================================================================================

    /**
     * Returns the displayname to display for a public room.
     * @param publicRoom the public room.
     * @return the room display name.
     */
    public static String getPublicRoomDisplayName(PublicRoom publicRoom) {
        String displayname = publicRoom.name;

        if (TextUtils.isEmpty(displayname)) {

            if ((null != publicRoom.aliases) && (0 < publicRoom.aliases.size())) {
                displayname = publicRoom.aliases.get(0);
            } else {
                displayname = publicRoom.roomId;
            }
        } else if (!displayname.startsWith("#")  && (null != publicRoom.aliases) && (0 < publicRoom.aliases.size())) {
            displayname = displayname + " (" + publicRoom.aliases.get(0) + ")";
        }

        return displayname;
    }

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

        String myUserId = session.getMyUserId();

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
    static LruCache<String, Bitmap> mAvatarImageByKeyDict = new LruCache<String, Bitmap>(20 * 1024 * 1024);
    // the avatars background color
    static ArrayList<Integer> mColorList = new ArrayList<Integer>(Arrays.asList(0xff76cfa6, 0xff50e2c2, 0xfff4c371));

    /**
     * Provides the avatar background color from a text.
     * @param text the text.
     * @return the color.
     */
    public static int getAvatarcolor(String text) {
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
    private static Bitmap createAvatar(Context context, int backgroundColor, String text) {
        android.graphics.Bitmap.Config bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;

        // the bitmap size
        int thumbnailSide = 42;

        float densityScale = context.getResources().getDisplayMetrics().density;
        int side = (int)(thumbnailSide * densityScale);

        Bitmap bitmap = Bitmap.createBitmap(side, side, bitmapConfig);
        Canvas canvas = new Canvas(bitmap);

        canvas.drawColor(backgroundColor);

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
    public static Bitmap getAvatar(Context context, int backgroundColor, String aText) {
        // ignore some characters
        if (!TextUtils.isEmpty(aText) && (aText.startsWith("@") || aText.startsWith("#"))) {
            aText = aText.substring(1);
        }

        String firstChar = " ";

        if (!TextUtils.isEmpty(aText)) {
            firstChar = aText.substring(0, 1).toUpperCase();
        }

        String key = firstChar + "_" + backgroundColor;

        // check if the avatar is already defined
        Bitmap thumbnail = mAvatarImageByKeyDict.get(key);

        if (null == thumbnail) {
            thumbnail = VectorUtils.createAvatar(context, backgroundColor, firstChar);
            mAvatarImageByKeyDict.put(key, thumbnail);
        }

        return thumbnail;
    }

    /**
     * Set the default vector avatar for a member.
     * @param imageView the imageView to set.
     * @param userId the member userId.
     * @param displayName the member display name.
     */
    public static void setDefaultMemberAvatar(ImageView imageView, String userId, String displayName) {
        // sanity checks
        if (null != imageView && !TextUtils.isEmpty(userId)) {
            imageView.setImageBitmap(VectorUtils.getAvatar(imageView.getContext(), VectorUtils.getAvatarcolor(userId), TextUtils.isEmpty(displayName) ? userId : displayName));
        }
    }

    /**
     * Set the default vector room avatar.
     * @param imageView the image view.
     * @param roomId the room id.
     * @param displayName the room displayname.
     */
    public static void setDefaultRoomVectorAvatar(ImageView imageView, String roomId, String displayName) {
        VectorUtils.setDefaultMemberAvatar(imageView, roomId, displayName);
    }

    /**
     * Set the room avatar in an imageview.
     * @param context the context
     * @param session the session
     * @param imageView the image view
     * @param room the room
     * @return the download Id
     */
    public static String loadRoomAvatar(Context context, MXSession session, ImageView imageView, Room room) {
        if (null != room) {
            return VectorUtils.loadUserAvatar(context, session, imageView, room.getAvatarUrl(), room.getRoomId(), VectorUtils.getRoomDisplayname(context, session, room));
        }

        return null;
    }

    /**
     * Set the room member avatar in an imageview.
     * @param context the context
     * @param session the session
     * @param imageView the image view
     * @param roomMember the room member
     * @return
     */
    public static String loadRoomMemberAvatar(Context context, MXSession session, ImageView imageView, RoomMember roomMember) {
        if (null != roomMember) {
            return VectorUtils.loadUserAvatar(context, session, imageView, roomMember.avatarUrl, roomMember.getUserId(), roomMember.displayname);
        }

        return null;
    }

    /**
     * Set the user avatar in an imageview.
     * @param context the context
     * @param session the session
     * @param imageView the image view
     * @param user the user
     * @return
     */
    public static String loadUserAvatar(Context context, MXSession session, ImageView imageView, User user) {
        if (null != user) {
            return VectorUtils.loadUserAvatar(context, session, imageView, user.getAvatarUrl(), user.user_id, user.displayname);
        }

        return null;
    }

    /**
     * Set the user avatar in an imageview.
     * @param context the context
     * @param session the session
     * @param imageView the image view
     * @param avatarUrl the avatar url
     * @param userId the user id
     * @param displayName the user displayname
     * @return the download Id
     */
    public static String loadUserAvatar(Context context, MXSession session, ImageView imageView, String avatarUrl, String userId, String displayName) {
        // sanity check
        if ((null == session) || (null == imageView)) {
            return null;
        }

        String downloadId = session.getMediasCache().loadAvatarThumbnail(session.getHomeserverConfig(), imageView, avatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size));

        // if there is no avatar URL
        // or if the bitmap is not downloaded (loadAvatarThumbnail returns a download ID if the image is not downloaded
        if (TextUtils.isEmpty(avatarUrl) || (null != downloadId)) {
            setDefaultMemberAvatar(imageView, userId, displayName);
        }

        return downloadId;
    }

    //==============================================================================================================
    // About / terms and conditions
    //==============================================================================================================

    // trick to trap the clink on the Licenses link
    private static class MovementCheck extends LinkMovementMethod {

        public Activity mActivity = null;

        @Override
        public boolean onTouchEvent(TextView widget,
                                    Spannable buffer, MotionEvent event ) {
            int action = event.getAction();

            if (action == MotionEvent.ACTION_UP) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= widget.getTotalPaddingLeft();
                y -= widget.getTotalPaddingTop();

                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);
                if (link.length != 0) {
                    // display the license
                    displayLicense(mActivity);
                    return true;
                }
            }

            return super.onTouchEvent(widget, buffer, event);
        }
    }

    private static AlertDialog mMainAboutDialog = null;
    private static String mLicenseString = null;
    private static MovementCheck mMovementCheck = null;

    /**
     * Provide the application version
     * @param activity the activity
     * @return the version. an empty string is not found.
     */
    public static String getApplicationVersion(final Activity activity) {
        return  im.vector.Matrix.getInstance(activity).getVersion(false);
    }

    /**
     * Init the license text to display.
     * It is extracted from a resource raw file.
     * @param activity the activity
     */
    private static void initLicenseText(Activity activity) {

        if (null == mLicenseString) {
            // build a local license file
            InputStream inputStream = activity.getResources().openRawResource(R.raw.all_licenses);
            StringBuilder buf = new StringBuilder();

            try {
                String str;
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

                while ((str = in.readLine()) != null) {
                    buf.append(str);
                    buf.append("\n");
                }

                in.close();
            } catch (Exception e) {

            }

            mLicenseString = buf.toString();
        }
    }

    /**
     * Display the licenses text.
     * @param activity the activity
     */
    public static void displayLicense(final Activity activity) {

        initLicenseText(activity);

        if (null != mMainAboutDialog) {
            mMainAboutDialog.dismiss();
            mMainAboutDialog = null;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setPositiveButton(android.R.string.ok, null)
                        .setMessage(mLicenseString)
                        .setTitle("Third Part licenses")
                        .create();
                dialog.show();
            }
        });
    }

    /**
     * Display third party licenses
     * @param activity the activity
     */
    public static void displayAbout(final Activity activity) {
        initLicenseText(activity);

        // sanity check
        if (null == mLicenseString) {
            return;
        }

        File cachedLicenseFile = new File(activity.getFilesDir(), "Licenses.txt");
        // convert the file to content:// uri
        Uri uri = VectorContentProvider.absolutePathToUri(activity, cachedLicenseFile.getAbsolutePath());

        if (null == uri) {
            return;
        }

        String message = "<div class=\"banner\"> <div class=\"l-page no-clear align-center\"> <h2 class=\"s-heading\">"+ activity.getString(R.string.settings_title_config) + "</h2> </div> </div>";

        String versionName = getApplicationVersion(activity);

        message += "<strong>Vector version</strong> <br>" + versionName;
        message += "<p><strong>SDK version</strong> <br>" + versionName;
        message += "<div class=\"banner\"> <div class=\"l-page no-clear align-center\"> <h2 class=\"s-heading\">Third Party Library Licenses</h2> </div> </div>";
        message += "<a href=\"" + uri.toString() + "\">Licenses</a>";

        Spanned text = Html.fromHtml(message);

        mMainAboutDialog = new AlertDialog.Builder(activity)
                .setPositiveButton(android.R.string.ok, null)
                .setMessage(text)
                .setIcon(R.drawable.ic_menu_small_matrix_transparent)
                .create();
        mMainAboutDialog.show();

        if (null == mMovementCheck) {
            mMovementCheck = new MovementCheck();
        }

        mMovementCheck.mActivity = activity;

        // allow link to be clickable
        ((TextView)mMainAboutDialog.findViewById(android.R.id.message)).setMovementMethod(mMovementCheck);
    }

    //==============================================================================================================
    // About / terms and conditions
    //==============================================================================================================

    /**
     * Return a selected bitmap from an intent.
     * @param intent the intent
     * @return the bitmap uri
     */
    @SuppressLint("NewApi")
    public static Uri getThumbnailUriFromIntent(Context context, final Intent intent, MXMediasCache mediasCache) {
        // sanity check
        if ((null != intent) && (null != context) && (null != mediasCache)) {
            Uri thumbnailUri = null;
            ClipData clipData = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                clipData = intent.getClipData();
            }

            // multiple data
            if (null != clipData) {
                if (clipData.getItemCount() > 0) {
                    thumbnailUri = clipData.getItemAt(0).getUri();
                }
            } else if (null != intent.getData()) {
                thumbnailUri = intent.getData();
            }

            if (null != thumbnailUri) {
                try {
                    ResourceUtils.Resource resource = ResourceUtils.openResource(context, thumbnailUri);

                    // sanity check
                    if (null != resource) {
                        if ("image/jpg".equals(resource.mimeType) || "image/jpeg".equals(resource.mimeType)) {
                            InputStream stream = resource.contentStream;
                            int rotationAngle = ImageUtils.getRotationAngleForBitmap(context, thumbnailUri);

                            String mediaUrl = ImageUtils.scaleAndRotateImage(context, stream, resource.mimeType, 1024, rotationAngle, mediasCache);
                            thumbnailUri = Uri.parse(mediaUrl);
                        }
                    }

                    return thumbnailUri;

                } catch (Exception e) {

                }
            }
        }

        return null;
    }

    //==============================================================================================================
    // User presence
    //==============================================================================================================

    /**
     * Provide the user online status from his user Id.
     * @param context the context.
     * @param session the session.
     * @param userId the userId.
     * @return the online status desrcription.
     */
    public static String getUserOnlineStatus(Context context, MXSession session, String userId) {

        // sanity checks
        if ((null == session) || (null == userId)) {
            return null;
        }

        User user = session.getDataHandler().getStore().getUser(userId);

        // unknown user
        if (null == user) {
            return null;
        }

        String onlineStatus = "";

        if (null == user.presence) {
            onlineStatus = "";
        } else if ((null != user.currently_active) && user.currently_active) {
            onlineStatus = context.getResources().getString(R.string.presence_online_now);
        } else if (User.PRESENCE_ONLINE.equals(user.presence)) {
            onlineStatus = context.getResources().getString(R.string.room_participants_active);
        } else {
            Long lastActiveMs = user.lastActiveAgo;

            if (null == lastActiveMs) {
                lastActiveMs = (long) -1;
            }

            if (-1 != lastActiveMs) {
                long lastActivehour = lastActiveMs / 1000 / 60 / 60;
                long lastActiveDays = lastActivehour / 24;

                if (lastActivehour < 1) {
                    onlineStatus = context.getString(R.string.room_participants_active_less_1_hour);
                } else if (lastActivehour < 24) {
                    onlineStatus = context.getString(R.string.room_participants_active_less_x_hours, lastActivehour);
                } else {
                    onlineStatus = context.getString(R.string.room_participants_active_less_x_days, lastActiveDays);
                }
            }
        }

        return onlineStatus;
    }
}
