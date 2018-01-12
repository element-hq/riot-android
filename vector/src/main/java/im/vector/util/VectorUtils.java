/*
 * Copyright 2016 OpenMarket Ltd
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
package im.vector.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.v4.util.LruCache;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.rest.model.group.GroupProfile;
import org.matrix.androidsdk.rest.model.publicroom.PublicRoom;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.util.ResourceUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.adapters.ParticipantAdapterItem;

public class VectorUtils {

    private static final String LOG_TAG = VectorUtils.class.getSimpleName();

    //public static final int REQUEST_FILES = 0;
    public static final int TAKE_IMAGE = 1;

    //==============================================================================================================
    // permalink methods
    //==============================================================================================================

    /**
     * Provides a permalink for a room id and an eventId.
     * The eventId is optional.
     *
     * @param roomIdOrAlias the room id or alias.
     * @param eventId       the event id (optional)
     * @return the permalink
     */
    public static String getPermalink(String roomIdOrAlias, String eventId) {
        if (TextUtils.isEmpty(roomIdOrAlias)) {
            return null;
        }

        String link = "https://matrix.to/#/" + roomIdOrAlias;

        if (!TextUtils.isEmpty(eventId)) {
            link += "/" + eventId;
        }

        // the $ character is not as a part of an url so escape it.
        return link.replace("$", "%24");
    }

    //==============================================================================================================
    // Clipboard helper
    //==============================================================================================================

    /**
     * Copy a text to the clipboard.
     *
     * @param context the context
     * @param text    the text to copy
     */
    public static void copyToClipboard(Context context, CharSequence text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("", text));
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
    }

    //==============================================================================================================
    // Rooms methods
    //==============================================================================================================

    /**
     * Returns the public room display name..
     *
     * @param publicRoom the public room.
     * @return the room display name.
     */
    public static String getPublicRoomDisplayName(PublicRoom publicRoom) {
        String displayName = publicRoom.name;

        if (TextUtils.isEmpty(displayName)) {
            if (publicRoom.getAliases().size() > 0) {
                displayName = publicRoom.getAliases().get(0);
            } else {
                displayName = publicRoom.roomId;
            }
        } else if (!displayName.startsWith("#") && (0 < publicRoom.getAliases().size())) {
            displayName = displayName + " (" + publicRoom.getAliases().get(0) + ")";
        }

        return displayName;
    }

    /**
     * Provide a display name for a calling room
     *
     * @param context the application context.
     * @param session the room session.
     * @param room    the room.
     * @return the calling room display name.
     */
    public static String getCallingRoomDisplayName(Context context, MXSession session, Room room) {
        if ((null == context) || (null == session) || (null == room)) {
            return null;
        }

        Collection<RoomMember> roomMembers = room.getJoinedMembers();

        if (2 == roomMembers.size()) {
            ArrayList<RoomMember> roomMembersList = new ArrayList<>(roomMembers);

            if (TextUtils.equals(roomMembersList.get(0).getUserId(), session.getMyUserId())) {
                return room.getLiveState().getMemberName(roomMembersList.get(1).getUserId());
            } else {
                return room.getLiveState().getMemberName(roomMembersList.get(0).getUserId());
            }
        } else {
            return getRoomDisplayName(context, session, room);
        }
    }

    /**
     * Vector client formats the room display with a different manner than the SDK one.
     *
     * @param context the application context.
     * @param session the room session.
     * @param room    the room.
     * @return the room display name.
     */
    public static String getRoomDisplayName(Context context, MXSession session, Room room) {
        // sanity checks
        if (null == room) {
            return null;
        }

        try {

            // this algorithm is the one defined in
            // https://github.com/matrix-org/matrix-js-sdk/blob/develop/lib/models/room.js#L617
            // calculateRoomName(room, userId)

            RoomState roomState = room.getLiveState();

            if (!TextUtils.isEmpty(roomState.name)) {
                return roomState.name;
            }

            String alias = roomState.alias;

            if (TextUtils.isEmpty(alias) && (roomState.getAliases().size() > 0)) {
                alias = roomState.getAliases().get(0);
            }

            if (!TextUtils.isEmpty(alias)) {
                return alias;
            }

            String myUserId = session.getMyUserId();

            Collection<RoomMember> members = roomState.getDisplayableMembers();
            ArrayList<RoomMember> othersActiveMembers = new ArrayList<>();
            ArrayList<RoomMember> activeMembers = new ArrayList<>();

            for (RoomMember member : members) {
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

            String displayName;

            if (othersActiveMembers.size() == 0) {
                if (activeMembers.size() == 1) {
                    RoomMember member = activeMembers.get(0);

                    if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {

                        if (!TextUtils.isEmpty(member.mSender)) {
                            // extract who invited us to the room
                            displayName = context.getString(R.string.room_displayname_invite_from, roomState.getMemberName(member.mSender));
                        } else {
                            displayName = context.getString(R.string.room_displayname_room_invite);
                        }
                    } else {
                        displayName = context.getString(R.string.room_displayname_no_title);
                    }
                } else {
                    displayName = context.getString(R.string.room_displayname_no_title);
                }
            } else if (othersActiveMembers.size() == 1) {
                RoomMember member = othersActiveMembers.get(0);
                displayName = roomState.getMemberName(member.getUserId());
            } else if (othersActiveMembers.size() == 2) {
                RoomMember member1 = othersActiveMembers.get(0);
                RoomMember member2 = othersActiveMembers.get(1);

                displayName = context.getString(R.string.room_displayname_two_members, roomState.getMemberName(member1.getUserId()), roomState.getMemberName(member2.getUserId()));
            } else {
                RoomMember member = othersActiveMembers.get(0);
                displayName = context.getString(R.string.room_displayname_more_than_two_members, roomState.getMemberName(member.getUserId()), othersActiveMembers.size() - 1);
            }

            return displayName;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getRoomDisplayName() failed " + e.getMessage());
        }

        return room.getRoomId();
    }

    //==============================================================================================================
    // Avatars generation
    //==============================================================================================================

    // avatars cache
    static final private LruCache<String, Bitmap> mAvatarImageByKeyDict = new LruCache<>(20 * 1024 * 1024);
    // the avatars background color
    static final private ArrayList<Integer> mColorList = new ArrayList<>(Arrays.asList(0xff76cfa6, 0xff50e2c2, 0xfff4c371));

    /**
     * Provides the avatar background color from a text.
     *
     * @param text the text.
     * @return the color.
     */
    public static int getAvatarColor(String text) {
        long colorIndex = 0;

        if (!TextUtils.isEmpty(text)) {
            long sum = 0;

            for (int i = 0; i < text.length(); i++) {
                sum += text.charAt(i);
            }

            colorIndex = sum % mColorList.size();
        }

        return mColorList.get((int) colorIndex);
    }

    /**
     * Create a thumbnail avatar.
     *
     * @param context         the context
     * @param backgroundColor the background color
     * @param text            the text to display.
     * @return the generated bitmap
     */
    private static Bitmap createAvatarThumbnail(Context context, int backgroundColor, String text) {
        float densityScale = context.getResources().getDisplayMetrics().density;
        // the avatar size is 42dp, convert it in pixels.
        return createAvatar(backgroundColor, text, (int) (42 * densityScale));
    }

    /**
     * Create an avatar bitmap from a text.
     *
     * @param backgroundColor the background color.
     * @param text            the text to display.
     * @param pixelsSide      the avatar side in pixels
     * @return the generated bitmap
     */
    private static Bitmap createAvatar(int backgroundColor, String text, int pixelsSide) {
        android.graphics.Bitmap.Config bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;

        Bitmap bitmap = Bitmap.createBitmap(pixelsSide, pixelsSide, bitmapConfig);
        Canvas canvas = new Canvas(bitmap);

        canvas.drawColor(backgroundColor);

        // prepare the text drawing
        Paint textPaint = new Paint();
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setColor(Color.WHITE);
        // the text size is proportional to the avatar size.
        // by default, the avatar size is 42dp, the text size is 28 dp (not sp because it has to be fixed).
        textPaint.setTextSize(pixelsSide * 2 / 3);

        // get its size
        Rect textBounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), textBounds);

        // draw the text in center
        canvas.drawText(text, (canvas.getWidth() - textBounds.width() - textBounds.left) / 2, (canvas.getHeight() + textBounds.height() - textBounds.bottom) / 2, textPaint);

        // Return the avatar
        return bitmap;
    }

    /**
     * Return the char to display for a name
     *
     * @param name the name
     * @return teh first char
     */
    private static String getInitialLetter(String name) {
        String firstChar = " ";

        if (!TextUtils.isEmpty(name)) {
            int idx = 0;
            char initial = name.charAt(idx);

            if ((initial == '@' || initial == '#' || initial == '+') && (name.length() > 1)) {
                idx++;
            }

            // string.codePointAt(0) would do this, but that isn't supported by
            // some browsers (notably PhantomJS).
            int chars = 1;
            char first = name.charAt(idx);

            // LEFT-TO-RIGHT MARK
            if ((name.length() >= 2) && (0x200e == first)) {
                idx++;
                first = name.charAt(idx);
            }

            // check if it’s the start of a surrogate pair
            if (first >= 0xD800 && first <= 0xDBFF && (name.length() > (idx + 1))) {
                char second = name.charAt(idx + 1);
                if (second >= 0xDC00 && second <= 0xDFFF) {
                    chars++;
                }
            }

            firstChar = name.substring(idx, idx + chars);
        }

        return firstChar.toUpperCase(VectorApp.getApplicationLocale());
    }

    /**
     * Returns an avatar from a text.
     *
     * @param context the context.
     * @param aText   the text.
     * @param create  create the avatar if it does not exist
     * @return the avatar.
     */
    public static Bitmap getAvatar(Context context, int backgroundColor, String aText, boolean create) {
        String firstChar = getInitialLetter(aText);
        String key = firstChar + "_" + backgroundColor;

        // check if the avatar is already defined
        Bitmap thumbnail = mAvatarImageByKeyDict.get(key);

        if ((null == thumbnail) && create) {
            thumbnail = VectorUtils.createAvatarThumbnail(context, backgroundColor, firstChar);
            mAvatarImageByKeyDict.put(key, thumbnail);
        }

        return thumbnail;
    }

    /**
     * Set the default vector avatar for a member.
     *
     * @param imageView   the imageView to set.
     * @param userId      the member userId.
     * @param displayName the member display name.
     */
    private static void setDefaultMemberAvatar(final ImageView imageView, final String userId, final String displayName) {
        // sanity checks
        if (null != imageView && !TextUtils.isEmpty(userId)) {
            final Bitmap bitmap = VectorUtils.getAvatar(imageView.getContext(), VectorUtils.getAvatarColor(userId), TextUtils.isEmpty(displayName) ? userId : displayName, true);

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
     * Set the room avatar in an imageView.
     *
     * @param context   the context
     * @param session   the session
     * @param imageView the image view
     * @param room      the room
     */
    public static void loadRoomAvatar(Context context, MXSession session, ImageView imageView, Room room) {
        if (null != room) {
            VectorUtils.loadUserAvatar(context, session, imageView, room.getAvatarUrl(), room.getRoomId(), VectorUtils.getRoomDisplayName(context, session, room));
        }
    }

    /**
     * Set the group avatar in an imageView.
     *
     * @param context   the context
     * @param session   the session
     * @param imageView the image view
     * @param group     the group
     */
    public static void loadGroupAvatar(Context context, MXSession session, ImageView imageView, Group group) {
        if (null != group) {
            VectorUtils.loadUserAvatar(context, session, imageView, group.getAvatarUrl(), group.getGroupId(), group.getDisplayName());
        }
    }

    /**
     * Set the call avatar in an imageView.
     *
     * @param context   the context
     * @param session   the session
     * @param imageView the image view
     * @param room      the room
     */
    public static void loadCallAvatar(Context context, MXSession session, ImageView imageView, Room room) {
        // sanity check
        if ((null != room) && (null != session) && (null != imageView) && session.isAlive()) {
            // reset the imageView tag
            imageView.setTag(null);

            String callAvatarUrl = room.getCallAvatarUrl();
            String roomId = room.getRoomId();
            String displayName = VectorUtils.getRoomDisplayName(context, session, room);
            int pixelsSide = imageView.getLayoutParams().width;

            // when size < 0, it means that the render graph must compute it
            // so, we search the valid parent view with valid size
            if (pixelsSide < 0) {
                ViewParent parent = imageView.getParent();

                while ((pixelsSide < 0) && (null != parent)) {
                    if (parent instanceof View) {
                        View parentAsView = (View) parent;
                        pixelsSide = parentAsView.getLayoutParams().width;
                    }
                    parent = parent.getParent();
                }
            }

            // if the avatar is already cached, use it
            if (session.getMediasCache().isAvatarThumbnailCached(callAvatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size))) {
                session.getMediasCache().loadAvatarThumbnail(session.getHomeServerConfig(), imageView, callAvatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size));
            } else {
                Bitmap bitmap = null;

                if (pixelsSide > 0) {
                    // get the avatar bitmap.
                    bitmap = VectorUtils.createAvatar(VectorUtils.getAvatarColor(roomId), getInitialLetter(displayName), pixelsSide);
                }

                // until the dedicated avatar is loaded.
                session.getMediasCache().loadAvatarThumbnail(session.getHomeServerConfig(), imageView, callAvatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size), bitmap);
            }
        }
    }

    /**
     * Set the room member avatar in an imageView.
     *
     * @param context    the context
     * @param session    the session
     * @param imageView  the image view
     * @param roomMember the room member
     */
    public static void loadRoomMemberAvatar(Context context, MXSession session, ImageView imageView, RoomMember roomMember) {
        if (null != roomMember) {
            VectorUtils.loadUserAvatar(context, session, imageView, roomMember.getAvatarUrl(), roomMember.getUserId(), roomMember.displayname);
        }
    }

    /**
     * Set the user avatar in an imageView.
     *
     * @param context   the context
     * @param session   the session
     * @param imageView the image view
     * @param user      the user
     */
    public static void loadUserAvatar(Context context, MXSession session, ImageView imageView, User user) {
        if (null != user) {
            VectorUtils.loadUserAvatar(context, session, imageView, user.getAvatarUrl(), user.user_id, user.displayname);
        }
    }

    // the background thread
    private static HandlerThread mImagesThread = null;
    private static android.os.Handler mImagesThreadHandler = null;
    private static Handler mUIHandler = null;

    /**
     * Set the user avatar in an imageView.
     *
     * @param context     the context
     * @param session     the session
     * @param imageView   the image view
     * @param avatarUrl   the avatar url
     * @param userId      the user id
     * @param displayName the user display name
     */
    public static void loadUserAvatar(final Context context, final MXSession session, final ImageView imageView, final String avatarUrl, final String userId, final String displayName) {
        // sanity check
        if ((null == session) || (null == imageView) || !session.isAlive()) {
            return;
        }

        // reset the imageView tag
        imageView.setTag(null);

        if (session.getMediasCache().isAvatarThumbnailCached(avatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size))) {
            session.getMediasCache().loadAvatarThumbnail(session.getHomeServerConfig(), imageView, avatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size));
        } else {
            if (null == mImagesThread) {
                mImagesThread = new HandlerThread("ImagesThread", Thread.MIN_PRIORITY);
                mImagesThread.start();
                mImagesThreadHandler = new android.os.Handler(mImagesThread.getLooper());
                mUIHandler = new Handler(Looper.getMainLooper());
            }

            final Bitmap bitmap = VectorUtils.getAvatar(imageView.getContext(), VectorUtils.getAvatarColor(userId), TextUtils.isEmpty(displayName) ? userId : displayName, false);

            // test if the default avatar has been computed
            if (null != bitmap) {
                imageView.setImageBitmap(bitmap);

                if (!TextUtils.isEmpty(avatarUrl)) {
                    final String tag = avatarUrl + userId + displayName;
                    imageView.setTag(tag);

                    if (!MXMediasCache.isMediaUrlUnreachable(avatarUrl)) {
                        mImagesThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (TextUtils.equals(tag, (String) imageView.getTag())) {
                                    session.getMediasCache().loadAvatarThumbnail(session.getHomeServerConfig(), imageView, avatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size), bitmap);
                                }
                            }
                        });
                    }
                }
            } else {
                final String tmpTag0 = "00" + avatarUrl + "-" + userId + "--" + displayName;
                imageView.setTag(tmpTag0);

                // create the default avatar in the background thread
                mImagesThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (TextUtils.equals(tmpTag0, (String) imageView.getTag())) {
                            imageView.setTag(null);
                            setDefaultMemberAvatar(imageView, userId, displayName);

                            if (!TextUtils.isEmpty(avatarUrl) && !MXMediasCache.isMediaUrlUnreachable(avatarUrl)) {
                                final String tmpTag1 = "11" + avatarUrl + "-" + userId + "--" + displayName;
                                imageView.setTag(tmpTag1);

                                // wait that it is rendered to load the right one
                                mUIHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // test if the imageView tag has not been updated
                                        if (TextUtils.equals(tmpTag1, (String) imageView.getTag())) {
                                            final String tmptag2 = "22" + avatarUrl + userId + displayName;
                                            imageView.setTag(tmptag2);

                                            mImagesThreadHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    // test if the imageView tag has not been updated
                                                    if (TextUtils.equals(tmptag2, (String) imageView.getTag())) {
                                                        final Bitmap bitmap = VectorUtils.getAvatar(imageView.getContext(), VectorUtils.getAvatarColor(userId), TextUtils.isEmpty(displayName) ? userId : displayName, false);
                                                        session.getMediasCache().loadAvatarThumbnail(session.getHomeServerConfig(), imageView, avatarUrl, context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size), bitmap);
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
     *
     * @param context the application context
     * @return the version. an empty string is not found.
     */
    public static String getApplicationVersion(final Context context) {
        return im.vector.Matrix.getInstance(context).getVersion(false, true);
    }

    /**
     * Display the licenses text.
     */
    public static void displayThirdPartyLicenses() {
        final Activity activity = VectorApp.getCurrentActivity();

        if (null != activity) {
            if (null != mMainAboutDialog) {
                if (mMainAboutDialog.isShowing() && (null != mMainAboutDialog.getOwnerActivity())) {
                    try {
                        mMainAboutDialog.dismiss();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## displayThirdPartyLicenses() : " + e.getMessage());
                    }
                }
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
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mMainAboutDialog = null;
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    mMainAboutDialog = null;
                                }
                            })
                            .create();

                    mMainAboutDialog.show();
                }
            });
        }
    }

    /**
     * Open a webview above the current activity.
     *
     * @param context the application context
     * @param url     the url to open
     */
    private static void displayInWebview(final Context context, String url) {
        AlertDialog.Builder alert = new AlertDialog.Builder(context);

        WebView wv = new WebView(context);
        wv.loadUrl(url);
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

    /**
     * Display the term and conditions.
     */
    public static void displayAppTac() {
        if (null != VectorApp.getCurrentActivity()) {
            displayInWebview(VectorApp.getCurrentActivity(), "https://riot.im/tac");
        }
    }

    /**
     * Display the copyright.
     */
    public static void displayAppCopyright() {
        if (null != VectorApp.getCurrentActivity()) {
            displayInWebview(VectorApp.getCurrentActivity(), "https://riot.im/copyright");
        }
    }

    /**
     * Display the privacy policy.
     */
    public static void displayAppPrivacyPolicy() {
        if (null != VectorApp.getCurrentActivity()) {
            displayInWebview(VectorApp.getCurrentActivity(), "https://riot.im/privacy");
        }
    }

    //==============================================================================================================
    // List uris from intent
    //==============================================================================================================

    /**
     * Return a selected bitmap from an intent.
     *
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
                    ResourceUtils.Resource resource = ResourceUtils.openResource(context, thumbnailUri, null);

                    // sanity check
                    if ((null != resource) && resource.isJpegResource()) {
                        InputStream stream = resource.mContentStream;
                        int rotationAngle = ImageUtils.getRotationAngleForBitmap(context, thumbnailUri);

                        Log.d(LOG_TAG, "## getThumbnailUriFromIntent() :  " + thumbnailUri + " rotationAngle " + rotationAngle);

                        String mediaUrl = ImageUtils.scaleAndRotateImage(context, stream, resource.mMimeType, 1024, rotationAngle, mediasCache);
                        thumbnailUri = Uri.parse(mediaUrl);
                    } else if (null != resource) {
                        Log.d(LOG_TAG, "## getThumbnailUriFromIntent() : cannot manage " + thumbnailUri + " mMimeType " + resource.mMimeType);
                    } else {
                        Log.d(LOG_TAG, "## getThumbnailUriFromIntent() : cannot manage " + thumbnailUri + " --> cannot open the dedicated file");
                    }

                    return thumbnailUri;
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## getThumbnailUriFromIntent failed " + e.getMessage());
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
     *
     * @param context         the context.
     * @param secondsInterval the time interval.
     * @return the formatted string
     */
    private static String formatSecondsIntervalFloored(Context context, long secondsInterval) {
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
     *
     * @param context         the context.
     * @param session         the session.
     * @param userId          the userId.
     * @param refreshCallback the presence callback.
     * @return the online status description.
     */
    public static String getUserOnlineStatus(final Context context, final MXSession session, final String userId, final SimpleApiCallback<Void> refreshCallback) {
        // sanity checks
        if ((null == session) || (null == userId)) {
            return null;
        }

        final User user = session.getDataHandler().getStore().getUser(userId);

        // refresh the presence with this conditions
        boolean triggerRefresh = (null == user) || user.isPresenceObsolete();

        if ((null != refreshCallback) && triggerRefresh) {
            Log.d(LOG_TAG, "Get the user presence : " + userId);

            final String fPresence = (null != user) ? user.presence : null;

            session.refreshUserPresence(userId, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    boolean isUpdated = false;
                    User updatedUser = session.getDataHandler().getStore().getUser(userId);

                    // don't find any info for the user
                    if ((null == user) && (null == updatedUser)) {
                        Log.d(LOG_TAG, "Don't find any presence info of " + userId);
                    } else if ((null == user) && (null != updatedUser)) {
                        Log.d(LOG_TAG, "Got the user presence : " + userId);
                        isUpdated = true;
                    } else if (!TextUtils.equals(fPresence, updatedUser.presence)) {
                        isUpdated = true;
                        Log.d(LOG_TAG, "Got some new user presence info : " + userId);
                        Log.d(LOG_TAG, "currently_active : " + updatedUser.currently_active);
                        Log.d(LOG_TAG, "lastActiveAgo : " + updatedUser.lastActiveAgo);
                    }

                    if (isUpdated && (null != refreshCallback)) {
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
            return null;
        }

        String presenceText = null;
        if (TextUtils.equals(user.presence, User.PRESENCE_ONLINE)) {
            presenceText = context.getResources().getString(R.string.room_participants_online);
        } else if (TextUtils.equals(user.presence, User.PRESENCE_UNAVAILABLE)) {
            presenceText = context.getResources().getString(R.string.room_participants_idle);
        } else if (TextUtils.equals(user.presence, User.PRESENCE_OFFLINE) || (null == user.presence)) {
            presenceText = context.getResources().getString(R.string.room_participants_offline);
        }

        if (presenceText != null) {
            if ((null != user.currently_active) && user.currently_active) {
                presenceText += " " + context.getResources().getString(R.string.room_participants_now);
            } else if ((null != user.lastActiveAgo) && (user.lastActiveAgo > 0)) {
                presenceText += " " + formatSecondsIntervalFloored(context, user.getAbsoluteLastActiveAgo() / 1000L) + " " + context.getResources().getString(R.string.room_participants_ago);
            }
        }

        return presenceText;
    }

    //==============================================================================================================
    // Users list
    //==============================================================================================================

    /**
     * List the active users i.e the active rooms users (invited or joined) and the contacts with matrix id emails.
     * This function could require a long time to process so it should be called in background.
     *
     * @param session the session.
     * @return a map indexed by the matrix id.
     */
    public static Map<String, ParticipantAdapterItem> listKnownParticipants(MXSession session) {
        // check known users
        Collection<User> users = session.getDataHandler().getStore().getUsers();

        // a hash map is a lot faster than a list search
        Map<String, ParticipantAdapterItem> map = new HashMap<>(users.size());

        // we don't need to populate the room members or each room
        // because an user is created for each joined / invited room member event
        for (User user : users) {
            if (!MXCallsManager.isConferenceUserId(user.user_id)) {
                map.put(user.user_id, new ParticipantAdapterItem(user));
            }
        }

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

    //==============================================================================================================
    // ExpandableListView tools
    //==============================================================================================================

    /**
     * Provides the visible child views.
     * The map key is the group position.
     * The map values are the visible child views.
     *
     * @param expandableListView the listview
     * @param adapter            the linked adapter
     * @return visible views map
     */
    public static HashMap<Integer, List<Integer>> getVisibleChildViews(ExpandableListView expandableListView, BaseExpandableListAdapter adapter) {
        HashMap<Integer, List<Integer>> map = new HashMap<>();

        long firstPackedPosition = expandableListView.getExpandableListPosition(expandableListView.getFirstVisiblePosition());

        int firstGroupPosition = ExpandableListView.getPackedPositionGroup(firstPackedPosition);
        int firstChildPosition = ExpandableListView.getPackedPositionChild(firstPackedPosition);

        long lastPackedPosition = expandableListView.getExpandableListPosition(expandableListView.getLastVisiblePosition());

        int lastGroupPosition = ExpandableListView.getPackedPositionGroup(lastPackedPosition);
        int lastChildPosition = ExpandableListView.getPackedPositionChild(lastPackedPosition);

        for (int groupPos = firstGroupPosition; groupPos <= lastGroupPosition; groupPos++) {
            ArrayList<Integer> list = new ArrayList<>();

            int startChildPos = (groupPos == firstGroupPosition) ? firstChildPosition : 0;
            int endChildPos = (groupPos == lastGroupPosition) ? lastChildPosition : adapter.getChildrenCount(groupPos) - 1;

            for (int index = startChildPos; index <= endChildPos; index++) {
                list.add(index);
            }

            map.put(groupPos, list);
        }

        return map;
    }
}
