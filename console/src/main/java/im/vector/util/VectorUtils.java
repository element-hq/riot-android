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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
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
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.RoomMember;

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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import im.vector.R;
import im.vector.db.ConsoleContentProvider;

public class VectorUtils {

    public static final String LOG_TAG = "VectorUtils";
    
    public static final int REQUEST_FILES = 0;
    public static final int TAKE_IMAGE = 1;

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
        String version = "";

        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            version = pInfo.versionName;
        } catch (Exception e) {

        }
        return version;
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
        Uri uri = ConsoleContentProvider.absolutePathToUri(activity, cachedLicenseFile.getAbsolutePath());

        if (null == uri) {
            return;
        }

        String message = "<div class=\"banner\"> <div class=\"l-page no-clear align-center\"> <h2 class=\"s-heading\">"+ activity.getString(R.string.settings_title_config) + "</h2> </div> </div>";

        String versionName = getApplicationVersion(activity);

        message += "<strong>matrixConsole version</strong> <br>" + versionName;
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
     * return the bitmap from a resource.
     * @param mediaUri the media URI.
     * @return the bitmap, null if it fails.
     */
    public static Bitmap getBitmapFromuri(Context context, Uri mediaUri) {
        if (null != mediaUri) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            ResourceUtils.Resource resource = ResourceUtils.openResource(context, mediaUri);

            // sanity checks
            if ((null != resource) && (null != resource.contentStream)) {
                return BitmapFactory.decodeStream(resource.contentStream, null, options);
            }
        }

        return null;
    }
}
