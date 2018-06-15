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
import org.matrix.androidsdk.util.Log

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
fun Bitmap.createSquareBitmap(): Bitmap? = when {
    width == height -> this
    width > height ->
        try {
            // larger than high
            Bitmap.createBitmap(this, (width - height) / 2, 0, height, height)
        } catch (e: Exception) {
            Log.e("BitmapUtil", "## createSquareBitmap " + e.message)
            this
        }
    else ->
        try {
            // higher than large
            Bitmap.createBitmap(this, 0, (height - width) / 2, width, width)
        } catch (e: Exception) {
            Log.e("BitmapUtil", "## createSquareBitmap " + e.message)
            this
        }
}
