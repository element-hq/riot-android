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

import java.util.ArrayList;
import java.util.Arrays;

public class VectorUtils {

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
