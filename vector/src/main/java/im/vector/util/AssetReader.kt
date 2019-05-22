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

import android.content.Context
import org.matrix.androidsdk.core.Log
import java.io.InputStreamReader

/**
 * Singleton to read asset files
 */
object AssetReader {

    /* ==========================================================================================
     * CACHE
     * ========================================================================================== */
    private val cache = HashMap<String, String>()

    /**
     * Read an asset from resource and return a String or null in cas of error.
     *
     * @param assetFilename Asset filename
     * @return the content of the asset file
     */
    fun readAssetFile(context: Context, assetFilename: String): String? {
        // Check if it is available in cache
        if (cache.contains(assetFilename)) {
            return cache[assetFilename]
        }

        var assetContent: String? = null

        try {
            val inputStream = context.assets.open(assetFilename)
            val buffer = CharArray(1024)
            val out = StringBuilder()

            val inputStreamReader = InputStreamReader(inputStream, "UTF-8")
            while (true) {
                val rsz = inputStreamReader.read(buffer, 0, buffer.size)
                if (rsz < 0)
                    break
                out.append(buffer, 0, rsz)
            }
            assetContent = out.toString()

            // Keep in cache
            cache[assetFilename] = assetContent

            inputStreamReader.close()
            inputStream.close()
        } catch (e: Exception) {
            Log.e("AssetReader", "## readAssetFile() failed : " + e.message, e)
        }

        return assetContent
    }

    fun clearCache() {
        cache.clear()
    }
}