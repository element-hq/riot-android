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

package im.vector.settings

import android.content.res.Configuration
import android.preference.PreferenceManager
import android.text.TextUtils
import androidx.core.content.edit
import im.vector.R
import im.vector.VectorApp

/**
 * Object to manage the Font Scale choice of the user
 */
object FontScale {
    // Key for the SharedPrefs
    private const val APPLICATION_FONT_SCALE_KEY = "APPLICATION_FONT_SCALE_KEY"

    // Possible values for the SharedPrefs
    private const val FONT_SCALE_TINY = "FONT_SCALE_TINY"
    private const val FONT_SCALE_SMALL = "FONT_SCALE_SMALL"
    private const val FONT_SCALE_NORMAL = "FONT_SCALE_NORMAL"
    private const val FONT_SCALE_LARGE = "FONT_SCALE_LARGE"
    private const val FONT_SCALE_LARGER = "FONT_SCALE_LARGER"
    private const val FONT_SCALE_LARGEST = "FONT_SCALE_LARGEST"
    private const val FONT_SCALE_HUGE = "FONT_SCALE_HUGE"

    private val fontScaleToPrefValue = mapOf(
            0.70f to FONT_SCALE_TINY,
            0.85f to FONT_SCALE_SMALL,
            1.00f to FONT_SCALE_NORMAL,
            1.15f to FONT_SCALE_LARGE,
            1.30f to FONT_SCALE_LARGER,
            1.45f to FONT_SCALE_LARGEST,
            1.60f to FONT_SCALE_HUGE
    )

    private val prefValueToNameResId = mapOf(
            FONT_SCALE_TINY to R.string.tiny,
            FONT_SCALE_SMALL to R.string.small,
            FONT_SCALE_NORMAL to R.string.normal,
            FONT_SCALE_LARGE to R.string.large,
            FONT_SCALE_LARGER to R.string.larger,
            FONT_SCALE_LARGEST to R.string.largest,
            FONT_SCALE_HUGE to R.string.huge
    )

    /**
     * Get the font scale value from SharedPrefs. Init the SharedPrefs if necessary
     *
     * @return the font scale
     */
    fun getFontScalePrefValue(): String {
        val context = VectorApp.getInstance()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        var scalePreferenceValue: String

        if (!preferences.contains(APPLICATION_FONT_SCALE_KEY)) {
            val fontScale = context.resources.configuration.fontScale

            scalePreferenceValue = FONT_SCALE_NORMAL

            if (fontScaleToPrefValue.containsKey(fontScale)) {
                scalePreferenceValue = fontScaleToPrefValue[fontScale] as String
            }

            preferences.edit {
                putString(APPLICATION_FONT_SCALE_KEY, scalePreferenceValue)
            }
        } else {
            scalePreferenceValue = preferences.getString(APPLICATION_FONT_SCALE_KEY, FONT_SCALE_NORMAL)
        }

        return scalePreferenceValue
    }

    /**
     * Provides the font scale value
     *
     * @return the font scale
     */
    fun getFontScale(): Float {
        val fontScale = getFontScalePrefValue()

        if (fontScaleToPrefValue.containsValue(fontScale)) {
            for (entry in fontScaleToPrefValue) {
                if (TextUtils.equals(entry.value, fontScale)) {
                    return entry.key
                }
            }
        }

        return 1.0f
    }

    /**
     * Provides the font scale description
     *
     * @return the font description
     */
    fun getFontScaleDescription(): String {
        val context = VectorApp.getInstance()
        val fontScale = getFontScalePrefValue()

        return if (prefValueToNameResId.containsKey(fontScale)) {
            context.getString(prefValueToNameResId[fontScale] as Int)
        } else context.getString(R.string.normal)
    }

    /**
     * Update the font size from the locale description.
     *
     * @param fontScaleDescription the font scale description
     */
    fun updateFontScale(fontScaleDescription: String) {
        val context = VectorApp.getInstance()
        for (entry in prefValueToNameResId) {
            if (TextUtils.equals(context.getString(entry.value), fontScaleDescription)) {
                saveFontScale(entry.key)
            }
        }

        val config = Configuration(context.resources.configuration)
        config.fontScale = getFontScale()
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    /**
     * Save the new font scale
     *
     * @param scaleValue the text scale
     */
    fun saveFontScale(scaleValue: String) {
        val context = VectorApp.getInstance()

        if (!TextUtils.isEmpty(scaleValue)) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit {
                        putString(APPLICATION_FONT_SCALE_KEY, scaleValue)
                    }
        }
    }
}