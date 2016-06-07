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
import android.content.DialogInterface;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.v4.util.LruCache;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.AccountPicker;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.ImageUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import im.vector.R;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
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
                    displayName = context.getString(R.string.room_displayname_no_title);
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
        canvas.drawText(text, (canvas.getWidth() - textBounds.width() - textBounds.left) / 2, (canvas.getHeight() + textBounds.height() - textBounds.bottom) / 2, textPaint);

        // Return the avatar
        return bitmap;
    }

    /**
     * Return the char to display for a naem
     * @param name the name
     * @return teh first char
     */
    private static String getInitialLetter(String name) {
        String firstChar = " ";

        if (!TextUtils.isEmpty(name)) {
            int idx = 0;
            char initial = name.charAt(idx);

            if ((initial == '@' || initial == '#') && (name.length() > 1)) {
                idx++;
            }

            // string.codePointAt(0) would do this, but that isn't supported by
            // some browsers (notably PhantomJS).
            int chars = 1;
            char first = name.charAt(idx);

            // check if it’s the start of a surrogate pair
            if (first >= 0xD800 && first <= 0xDBFF && (name.length() > 2)) {
                char second = name.charAt(idx+1);
                if (second >= 0xDC00 && second <= 0xDFFF) {
                    chars++;
                }
            }

            firstChar = name.substring(idx, idx+chars);
        }

        return firstChar.toUpperCase();
    }

    /**
     * Returns an avatar from a text.
     * @param context the context.
     * @param aText the text.
     * @param create create the avatar if it does not exist
     * @return the avatar.
     */
    public static Bitmap getAvatar(Context context, int backgroundColor, String aText, boolean create) {
        String firstChar = getInitialLetter(aText);
        String key = firstChar + "_" + backgroundColor;

        // check if the avatar is already defined
        Bitmap thumbnail = mAvatarImageByKeyDict.get(key);

        if ((null == thumbnail) && create) {
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
    public static void setDefaultMemberAvatar(final ImageView imageView, final String userId, final String displayName) {
        // sanity checks
        if (null != imageView && !TextUtils.isEmpty(userId)) {
            final Bitmap bitmap = VectorUtils.getAvatar(imageView.getContext(), VectorUtils.getAvatarcolor(userId), TextUtils.isEmpty(displayName) ? userId : displayName, true);

            if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
                imageView.setImageBitmap(bitmap);
            } else {
                final String tag = userId + " - " + displayName;
                imageView.setTag(tag);

                mUIHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (TextUtils.equals(tag, (String) imageView.getTag())) {
                            imageView.setImageBitmap(bitmap);
                        }
                    }
                });
            }
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
     */
    public static void loadRoomAvatar(Context context, MXSession session, ImageView imageView, Room room) {
        if (null != room) {
            VectorUtils.loadUserAvatar(context, session, imageView, room.getAvatarUrl(), room.getRoomId(), VectorUtils.getRoomDisplayname(context, session, room));
        }
    }

    /**
     * Set the room member avatar in an imageview.
     * @param context the context
     * @param session the session
     * @param imageView the image view
     * @param roomMember the room member
     */
    public static void loadRoomMemberAvatar(Context context, MXSession session, ImageView imageView, RoomMember roomMember) {
        if (null != roomMember) {
            VectorUtils.loadUserAvatar(context, session, imageView, roomMember.avatarUrl, roomMember.getUserId(), roomMember.displayname);
        }
    }

    /**
     * Set the user avatar in an imageview.
     * @param context the context
     * @param session the session
     * @param imageView the image view
     * @param user the user
     */
    public static void loadUserAvatar(Context context, MXSession session, ImageView imageView, User user) {
        if (null != user) {
            VectorUtils.loadUserAvatar(context, session, imageView, user.getAvatarUrl(), user.user_id, user.displayname);
        }
    }

    // the background thread
    private static HandlerThread mImagesThread = null;
    private static android.os.Handler mImagesThreadHandler  = null;
    private static Handler mUIHandler = null;

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
    public static void loadUserAvatar(final Context context,final MXSession session, final ImageView imageView, final String avatarUrl, final String userId, final String displayName) {
        // sanity check
        if ((null == session) || (null == imageView) || !session.isAlive()) {
            return;
        }

        // reset the imageview tag
        imageView.setTag(null);

        if (session.getMediasCache().isAvartarThumbailCached(avatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size))) {
            session.getMediasCache().loadAvatarThumbnail(session.getHomeserverConfig(), imageView, avatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size));
        } else {
            if (null == mImagesThread) {
                mImagesThread = new HandlerThread("ImagesThread", Thread.MIN_PRIORITY);
                mImagesThread.start();
                mImagesThreadHandler = new android.os.Handler(mImagesThread.getLooper());
                mUIHandler = new Handler(Looper.getMainLooper());
            }

            final Bitmap bitmap = VectorUtils.getAvatar(imageView.getContext(), VectorUtils.getAvatarcolor(userId), TextUtils.isEmpty(displayName) ? userId : displayName, false);

            // test if the default avatar has been computed
            if (null != bitmap) {
                imageView.setImageBitmap(bitmap);

                final String tag = avatarUrl + userId + displayName;
                imageView.setTag(tag);

                if (!TextUtils.isEmpty(avatarUrl)) {
                    mImagesThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (TextUtils.equals(tag, (String) imageView.getTag())) {
                                session.getMediasCache().loadAvatarThumbnail(session.getHomeserverConfig(), imageView, avatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size));
                            }
                        }
                    });
                }
            } else {
                final String tmpTag0 = "00" + avatarUrl + "-" + userId + "--" + displayName;
                imageView.setTag(tmpTag0);

                // create the default avatar in the background thread
                mImagesThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (TextUtils.equals(tmpTag0, (String)imageView.getTag())) {
                            imageView.setTag(null);
                            setDefaultMemberAvatar(imageView, userId, displayName);

                            if (!TextUtils.isEmpty(avatarUrl)) {
                                final String tmpTag1 = "11" + avatarUrl + "-" + userId + "--" + displayName;
                                imageView.setTag(tmpTag1);

                                // wait that it is rendered to load the right one
                                mUIHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // test if the imageview tag has not been updated
                                        if (TextUtils.equals(tmpTag1, (String)imageView.getTag())) {
                                            final String tmptag2 = "22" + avatarUrl + userId + displayName;
                                            imageView.setTag(tmptag2);

                                            mImagesThreadHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    // test if the imageview tag has not been updated
                                                    if (TextUtils.equals(tmptag2, (String) imageView.getTag())) {
                                                        session.getMediasCache().loadAvatarThumbnail(session.getHomeserverConfig(), imageView, avatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size));
                                                    }
                                                }
                                            });
                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            }
        }
    }

    //==============================================================================================================
    // About / terms and conditions
    //==============================================================================================================

    private static AlertDialog mMainAboutDialog = null;

    /**
     * Provide the application version
     * @param activity the activity
     * @return the version. an empty string is not found.
     */
    public static String getApplicationVersion(final Activity activity) {
        return  im.vector.Matrix.getInstance(activity).getVersion(false);
    }

    /**
     * Display the licenses text.
     * @param activity the activity
     */
    public static void displayLicenses(final Activity activity) {

        if (null != mMainAboutDialog) {
            mMainAboutDialog.dismiss();
            mMainAboutDialog = null;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WebView view = (WebView) LayoutInflater.from(activity).inflate(R.layout.dialog_licenses, null);
                view.loadUrl("file:///android_asset/open_source_licenses.html");

                View titleView = LayoutInflater.from(activity).inflate(R.layout.dialog_licenses_header, null);

                view.setScrollbarFadingEnabled(false);
                mMainAboutDialog = new AlertDialog.Builder(activity)
                        .setCustomTitle(titleView)
                        .setView(view)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();

                mMainAboutDialog.show();
            }
        });
    }

    /**
     * Display the privacy policy.
     * @param activity the activity
     */
    public static void displayPrivacyPolicy(final Activity activity) {
        AlertDialog.Builder alert = new AlertDialog.Builder(activity);

        WebView wv = new WebView(activity);
        wv.loadUrl("https://vector.im/privacy.html");
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);

                return true;
            }
        });

        alert.setView(wv);
        alert.setPositiveButton(android.R.string.ok, null);
        alert.show();
    }

    //==============================================================================================================
    // List uris from intent
    //==============================================================================================================

    /**
     * List the media Uris provided in an intent
     * @param intent the intent.
     * @return the media URIs list
     */
    public static  ArrayList<Uri> listMediaUris(Intent intent) {
        ArrayList<Uri> uris = new ArrayList<Uri>();

        if (null != intent) {
            ClipData clipData = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                clipData = intent.getClipData();
            }

            // multiple data
            if (null != clipData) {
                int count = clipData.getItemCount();

                for (int i = 0; i < count; i++) {
                    ClipData.Item item = clipData.getItemAt(i);
                    Uri uri = item.getUri();

                    if (null != uri) {
                        uris.add(uri);
                    }
                }
            } else if (null != intent.getData()) {
                uris.add(intent.getData());
            }
        }

        return uris;
    }

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
     * Format a time interval in seconds to a string
     * @param context the context.
     * @param secondsInterval the time interval.
     * @return the formatted string
     */
    public static String formatSecondsIntervalFloored(Context context, long secondsInterval) {
        String formattedString;

        if (secondsInterval < 0) {
            formattedString = "0" + context.getResources().getString(R.string.format_time_s);
        } else {
            if (secondsInterval < 60) {
                formattedString = secondsInterval + context.getResources().getString(R.string.format_time_s);
            } else if (secondsInterval < 3600) {
                formattedString = (secondsInterval / 60) + context.getResources().getString(R.string.format_time_m);
            } else if (secondsInterval < 86400) {
                formattedString = (secondsInterval / 3600) + context.getResources().getString(R.string.format_time_h);
            } else {
                formattedString = (secondsInterval / 86400) + context.getResources().getString(R.string.format_time_d);
            }
        }

        return formattedString;
    }

    /**
     * Provide the user online status from his user Id.
     * if refreshCallback is set, try to refresh the user presence if it is not known
     * @param context the context.
     * @param session the session.
     * @param userId the userId.
     * @param refreshCallback the presence callback.
     * @return the online status desrcription.
     */
    public static String getUserOnlineStatus(final Context context, final MXSession session, final String userId, final SimpleApiCallback<Void> refreshCallback) {
        String presenceText = context.getResources().getString(R.string.room_participants_offline);

        // sanity checks
        if ((null == session) || (null == userId)) {
            return presenceText;
        }

        User user = session.getDataHandler().getStore().getUser(userId);

        if ((null != refreshCallback) && ((null == user) || (null == user.presence))) {
            Log.d(LOG_TAG, "Get the user presence : " + userId);

            session.refreshUserPresence(userId, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    if (null != refreshCallback) {
                        Log.d(LOG_TAG, "Got the user presence : " + userId);
                        try {
                            refreshCallback.onSuccess(null);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "getUserOnlineStatus refreshCallback failed");
                        }
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "getUserOnlineStatus onNetworkError " + e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "getUserOnlineStatus onMatrixError " + e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "getUserOnlineStatus onUnexpectedError " + e.getLocalizedMessage());
                }
            });
        }

        // unknown user
        if (null == user) {
            return presenceText;
        }

        if (TextUtils.equals(user.presence, User.PRESENCE_ONLINE)) {
            presenceText = context.getResources().getString(R.string.room_participants_online);
        } else if (TextUtils.equals(user.presence, User.PRESENCE_UNAVAILABLE)) {
            presenceText = context.getResources().getString(R.string.room_participants_idle);
        } else if (TextUtils.equals(user.presence, User.PRESENCE_OFFLINE) || (null == user.presence)) {
            presenceText = context.getResources().getString(R.string.room_participants_offline);
        }

        if ((null != user.currently_active) && user.currently_active) {
            presenceText += " " +  context.getResources().getString(R.string.room_participants_now);
        } else if ((null != user.lastActiveAgo) && (user.lastActiveAgo > 0)) {
            presenceText += " " + formatSecondsIntervalFloored(context, user.getRealLastActiveAgo() / 1000L) + " " + context.getResources().getString(R.string.room_participants_ago);
        }

        return presenceText;
    }

    //==============================================================================================================
    // Users list
    //==============================================================================================================

    /**
     * List the active users i.e the active rooms users (invited or joined) and the contacts with matrix id emails.
     * This function could require a long time to process so it should be called in background.
     * @param context the context
     * @param session the session.
     * @return a map indexed by the matrix id.
     */
    public static HashMap<String, ParticipantAdapterItem> listKnownParticipants(Context context, MXSession session) {
        // a hashmap is a lot faster than a list search
        HashMap<String, ParticipantAdapterItem> map = new HashMap<String, ParticipantAdapterItem>();

        // check known users
        Collection<User> users = session.getDataHandler().getStore().getUsers();

        // we don't need to populate the room members or each room
        // because an user is created for each joined / invited room member event
        for(User user : users) {
            map.put(user.user_id, new ParticipantAdapterItem(user));
        }

        // from contacts
        // there is no design to select an email from a contact
        /*Collection<Contact> contacts = ContactsManager.getLocalContactsSnapshot(context);

        for(Contact contact : contacts) {
            if (contact.hasMatridIds(context)) {
                Contact.MXID mxId = contact.getFirstMatrixId();

                if (null == map.get(mxId.mMatrixId)) {
                    map.put(mxId.mMatrixId, new ParticipantAdapterItem(contact, context));
                }
            } else {
                map.put(contact.hashCode() + "", new ParticipantAdapterItem(contact, context));
            }
        }*/

        return map;
    }


    //==============================================================================================================
    // URL parser
    //==============================================================================================================

    private static final Pattern mUrlPattern = Pattern.compile(
            "(?:^|[\\W])((ht|f)tp(s?):\\/\\/|www\\.)"
                    + "(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*"
                    + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    /**
     * List the URLs in a text.
     * @param text the text to parse
     * @return the list of URLss
     */
    public static List<String> listURLs(String text) {
        ArrayList<String> URLs = new ArrayList<>();

        // sanity checks
        if (!TextUtils.isEmpty(text)) {
            Matcher matcher = mUrlPattern.matcher(text);

            while (matcher.find()) {
                int matchStart = matcher.start(1);
                int matchEnd = matcher.end();

                String charBef = "";
                String charAfter = "";

                if (matchStart > 2) {
                    charBef = text.substring(matchStart-2, matchStart);
                }

                if ((matchEnd-1) < text.length()) {
                    charAfter = text.substring(matchEnd-1, matchEnd);
                }

                // keep the link between parenthesis, it might be a link [title](link)
                if (!TextUtils.equals(charAfter, ")") || !TextUtils.equals(charBef, "](") ) {
                    String url = text.substring(matchStart, matchEnd);

                    if (URLs.indexOf(url) < 0) {
                        URLs.add(url);
                    }
                }
            }
        }

        return URLs;
    }
}
