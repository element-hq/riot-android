/*
 * Copyright 2018 New Vector Ltd
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

package im.vector.util

import android.graphics.Bitmap
import android.graphics.Canvas
import org.matrix.androidsdk.core.Log

private const val LOG_TAG = "BitmapUtil"

/**
 * Create a centered square bitmap from another one.
 *
 * if height == width
 * +-------+
 * |XXXXXXX|
 * |XXXXXXX|
 * |XXXXXXX|
 * +-------+
 *
 * if width > height
 * +------+-------+------+
 * |      |XXXXXXX|      |
 * |      |XXXXXXX|      |
 * |      |XXXXXXX|      |
 * +------+-------+------+
 *
 * if height > width
 * +-------+
 * |       |
 * |       |
 * +-------+
 * |XXXXXXX|
 * |XXXXXXX|
 * |XXXXXXX|
 * +-------+
 * |       |
 * |       |
 * +-------+
 *
 * @param bitmap the bitmap to "square"
 * @return the squared bitmap
 */
fun Bitmap.createSquareBitmap(): Bitmap = when {
    width == height -> this
    width > height ->
        try {
            // larger than high
            Bitmap.createBitmap(this, (width - height) / 2, 0, height, height)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## createSquareBitmap " + e.message, e)
            this
        }
    else ->
        try {
            // higher than large
            Bitmap.createBitmap(this, 0, (height - width) / 2, width, width)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## createSquareBitmap " + e.message, e)
            this
        }
}

/**
 * Add a background color to the Bitmap
 */
fun Bitmap.addBackgroundColor(backgroundColor: Int): Bitmap {
    // Create new bitmap based on the size and config of the old
    val newBitmap = Bitmap.createBitmap(width, height, config ?: Bitmap.Config.ARGB_8888)

    // Reported by the Play Store
    if (newBitmap == null) {
        Log.e(LOG_TAG, "## unable to add background color")

        return this
    }

    Canvas(newBitmap).let {
        // Paint background
        it.drawColor(backgroundColor)

        // Draw the old bitmap on top of the new background
        it.drawBitmap(this, 0f, 0f, null)
    }

    return newBitmap
}