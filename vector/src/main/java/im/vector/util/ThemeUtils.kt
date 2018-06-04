/*
 * Copyright 2017 OpenMarket Ltd
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


import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.preference.PreferenceManager
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.design.widget.TabLayout
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem

import java.util.HashMap

import im.vector.R
import im.vector.VectorApp
import im.vector.activity.AccountCreationActivity
import im.vector.activity.BugReportActivity
import im.vector.activity.CountryPickerActivity
import im.vector.activity.DeactivateAccountActivity
import im.vector.activity.FallbackLoginActivity
import im.vector.activity.HistoricalRoomsActivity
import im.vector.activity.LanguagePickerActivity
import im.vector.activity.LockScreenActivity
import im.vector.activity.LoggingOutActivity
import im.vector.activity.LoginActivity
import im.vector.activity.NotificationPrivacyActivity
import im.vector.activity.PhoneNumberAdditionActivity
import im.vector.activity.PhoneNumberVerificationActivity
import im.vector.activity.RoomDirectoryPickerActivity
import im.vector.activity.SplashActivity
import im.vector.activity.VectorBaseSearchActivity
import im.vector.activity.VectorCallViewActivity
import im.vector.activity.VectorGroupDetailsActivity
import im.vector.activity.VectorHomeActivity
import im.vector.activity.VectorMediasPickerActivity
import im.vector.activity.VectorMediasViewerActivity
import im.vector.activity.VectorMemberDetailsActivity
import im.vector.activity.VectorPublicRoomsActivity
import im.vector.activity.VectorRoomActivity
import im.vector.activity.VectorRoomCreationActivity
import im.vector.activity.VectorRoomDetailsActivity
import im.vector.activity.VectorSettingsActivity
import im.vector.activity.VectorUniversalLinkActivity

/**
 * Util class for managing themes.
 */
object ThemeUtils {
    // preference key
    const val APPLICATION_THEME_KEY = "APPLICATION_THEME_KEY"

    // the theme possible values
    private const val THEME_DARK_VALUE = "dark"
    private const val THEME_LIGHT_VALUE = "light"
    private const val THEME_BLACK_VALUE = "black"

    private val mColorByAttr = HashMap<Int, Int>()

    /**
     * Provides the selected application theme
     *
     * @param context the context
     * @return the selected application theme
     */
    fun getApplicationTheme(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(APPLICATION_THEME_KEY, THEME_LIGHT_VALUE)
    }

    /**
     * Update the application theme
     *
     * @param aTheme the new theme
     */
    fun setApplicationTheme(context: Context, aTheme: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(APPLICATION_THEME_KEY, aTheme)
                .apply()

        if (TextUtils.equals(aTheme, THEME_DARK_VALUE)) {
            VectorApp.getInstance().setTheme(R.style.AppTheme_Dark)
        } else if (TextUtils.equals(aTheme, THEME_BLACK_VALUE)) {
            VectorApp.getInstance().setTheme(R.style.AppTheme_Black)
        } else {
            VectorApp.getInstance().setTheme(R.style.AppTheme)
        }

        mColorByAttr.clear()
    }

    /**
     * Set the activity theme according to the selected one.
     *
     * @param activity the activity
     */
    fun setActivityTheme(activity: Activity) {
        if (TextUtils.equals(getApplicationTheme(activity), THEME_DARK_VALUE)) {
            if (activity is BugReportActivity) {
                activity.setTheme(R.style.AppTheme_Dark)
            } else if (activity is AccountCreationActivity) {
                activity.setTheme(R.style.AppTheme_Dark)
            } else if (activity is DeactivateAccountActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Dark)
            } else if (activity is CountryPickerActivity) {
                activity.setTheme(R.style.CountryPickerTheme_Dark)
            } else if (activity is FallbackLoginActivity) {
                activity.setTheme(R.style.AppTheme_Dark)
            } else if (activity is HistoricalRoomsActivity) {
                activity.setTheme(R.style.HomeActivityTheme_Dark)
            } else if (activity is LanguagePickerActivity) {
                activity.setTheme(R.style.CountryPickerTheme_Dark)
            } else if (activity is NotificationPrivacyActivity) {
                activity.setTheme(R.style.CountryPickerTheme_Dark)
            } else if (activity is LoginActivity) {
                activity.setTheme(R.style.LoginAppTheme_Dark)
            } else if (activity is PhoneNumberAdditionActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Dark)
            } else if (activity is PhoneNumberVerificationActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Dark)
            } else if (activity is RoomDirectoryPickerActivity) {
                activity.setTheme(R.style.DirectoryPickerTheme_Dark)
            } else if (activity is SplashActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Dark)
            } else if (activity is LoggingOutActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Dark)
            } else if (activity is VectorBaseSearchActivity) {
                activity.setTheme(R.style.SearchesAppTheme_Dark)
            } else if (activity is VectorCallViewActivity) {
                activity.setTheme(R.style.CallActivityTheme_Dark)
            } else if (activity is VectorHomeActivity) {
                activity.setTheme(R.style.HomeActivityTheme_Dark)
            } else if (activity is VectorMediasPickerActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_FullScreen_Dark)
            } else if (activity is VectorMediasViewerActivity) {
                activity.setTheme(R.style.AppTheme_Dark)
            } else if (activity is VectorMemberDetailsActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Dark)
            } else if (activity is VectorPublicRoomsActivity) {
                activity.setTheme(R.style.AppTheme_Dark)
            } else if (activity is VectorRoomActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Dark)
            } else if (activity is VectorRoomCreationActivity) {
                activity.setTheme(R.style.AppTheme_Dark)
            } else if (activity is VectorRoomDetailsActivity) {
                activity.setTheme(R.style.AppTheme_Dark)
            } else if (activity is VectorSettingsActivity) {
                activity.setTheme(R.style.AppTheme_Dark)
            } else if (activity is VectorUniversalLinkActivity) {
                activity.setTheme(R.style.AppTheme_Dark)
            } else if (activity is LockScreenActivity) {
                activity.setTheme(R.style.Vector_Lock_Dark)
            } else if (activity is VectorGroupDetailsActivity) {
                activity.setTheme(R.style.AppTheme_Dark)
            }
        }

        if (TextUtils.equals(getApplicationTheme(activity), THEME_BLACK_VALUE)) {
            if (activity is BugReportActivity) {
                activity.setTheme(R.style.AppTheme_Black)
            } else if (activity is AccountCreationActivity) {
                activity.setTheme(R.style.AppTheme_Black)
            } else if (activity is DeactivateAccountActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Black)
            } else if (activity is CountryPickerActivity) {
                activity.setTheme(R.style.CountryPickerTheme_Black)
            } else if (activity is FallbackLoginActivity) {
                activity.setTheme(R.style.AppTheme_Black)
            } else if (activity is HistoricalRoomsActivity) {
                activity.setTheme(R.style.HomeActivityTheme_Black)
            } else if (activity is LanguagePickerActivity) {
                activity.setTheme(R.style.CountryPickerTheme_Black)
            } else if (activity is NotificationPrivacyActivity) {
                activity.setTheme(R.style.CountryPickerTheme_Black)
            } else if (activity is LoginActivity) {
                activity.setTheme(R.style.LoginAppTheme_Black)
            } else if (activity is PhoneNumberAdditionActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Black)
            } else if (activity is PhoneNumberVerificationActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Black)
            } else if (activity is RoomDirectoryPickerActivity) {
                activity.setTheme(R.style.DirectoryPickerTheme_Black)
            } else if (activity is SplashActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Black)
            } else if (activity is LoggingOutActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Black)
            } else if (activity is VectorBaseSearchActivity) {
                activity.setTheme(R.style.SearchesAppTheme_Black)
            } else if (activity is VectorCallViewActivity) {
                activity.setTheme(R.style.CallActivityTheme_Black)
            } else if (activity is VectorHomeActivity) {
                activity.setTheme(R.style.HomeActivityTheme_Black)
            } else if (activity is VectorMediasPickerActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_FullScreen_Black)
            } else if (activity is VectorMediasViewerActivity) {
                activity.setTheme(R.style.AppTheme_Black)
            } else if (activity is VectorMemberDetailsActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Black)
            } else if (activity is VectorPublicRoomsActivity) {
                activity.setTheme(R.style.AppTheme_Black)
            } else if (activity is VectorRoomActivity) {
                activity.setTheme(R.style.AppTheme_NoActionBar_Black)
            } else if (activity is VectorRoomCreationActivity) {
                activity.setTheme(R.style.AppTheme_Black)
            } else if (activity is VectorRoomDetailsActivity) {
                activity.setTheme(R.style.AppTheme_Black)
            } else if (activity is VectorSettingsActivity) {
                activity.setTheme(R.style.AppTheme_Black)
            } else if (activity is VectorUniversalLinkActivity) {
                activity.setTheme(R.style.AppTheme_Black)
            } else if (activity is LockScreenActivity) {
                activity.setTheme(R.style.Vector_Lock_Black)
            } else if (activity is VectorGroupDetailsActivity) {
                activity.setTheme(R.style.AppTheme_Black)
            }
        }

        mColorByAttr.clear()
    }

    /**
     * Set the TabLayout colors.
     * It seems that there is no proper way to manage it with the manifest file.
     *
     * @param activity the activity
     * @param layout   the layout
     */
    fun setTabLayoutTheme(activity: Activity, layout: TabLayout) {
        if (activity is VectorGroupDetailsActivity) {
            val textColor: Int
            val underlineColor: Int
            val backgroundColor: Int

            if (TextUtils.equals(getApplicationTheme(activity), THEME_LIGHT_VALUE)) {
                textColor = ContextCompat.getColor(activity, android.R.color.white)
                underlineColor = textColor
                backgroundColor = ContextCompat.getColor(activity, R.color.tab_groups)
            } else {
                textColor = ContextCompat.getColor(activity, R.color.tab_groups)
                underlineColor = textColor
                backgroundColor = getColor(activity, R.attr.primary_color)
            }

            layout.setTabTextColors(textColor, textColor)
            layout.setSelectedTabIndicatorColor(underlineColor)
            layout.setBackgroundColor(backgroundColor)
        }
    }

    /**
     * Translates color attributes to colors
     *
     * @param c              Context
     * @param colorAttribute Color Attribute
     * @return Requested Color
     */
    @ColorInt
    fun getColor(c: Context, @AttrRes colorAttribute: Int): Int {
        if (mColorByAttr.containsKey(colorAttribute)) {
            return mColorByAttr[colorAttribute] as Int
        }

        var matchedColor: Int

        try {
            val color = TypedValue()
            c.theme.resolveAttribute(colorAttribute, color, true)
            matchedColor = color.data
        } catch (e: Exception) {
            matchedColor = ContextCompat.getColor(c, android.R.color.holo_red_dark)
        }

        mColorByAttr[colorAttribute] = matchedColor

        return matchedColor
    }

    /**
     * Get the resource Id applied to the current theme
     *
     * @param c          the context
     * @param resourceId the resource id
     * @return the resource Id for the current theme
     */
    fun getResourceId(c: Context, resourceId: Int): Int {
        if (TextUtils.equals(getApplicationTheme(c), THEME_DARK_VALUE)) {

            if (resourceId == R.drawable.line_divider_light) {
                return R.drawable.line_divider_dark
            }
        }
        return resourceId
    }

    /**
     * Update the menu icons colors
     *
     * @param menu  the menu
     * @param color the color
     */
    fun tintMenuIcons(menu: Menu, color: Int) {
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val drawable = item.icon
            if (drawable != null) {
                val wrapped = DrawableCompat.wrap(drawable)
                drawable.mutate()
                DrawableCompat.setTint(wrapped, color)
                item.icon = drawable
            }
        }
    }

    /**
     * Tint the drawable with a theme attribute
     *
     * @param context   the context
     * @param drawable  the drawable to tint
     * @param attribute the theme color
     * @return the tinted drawable
     */
    fun tintDrawable(context: Context, drawable: Drawable, @AttrRes attribute: Int): Drawable {
        return tintDrawableWithColor(drawable, getColor(context, attribute))
    }

    /**
     * Tint the drawable with a color integer
     *
     * @param drawable the drawable to tint
     * @param color    the color
     * @return the tinted drawable
     */
    fun tintDrawableWithColor(drawable: Drawable, @ColorInt color: Int): Drawable {
        val tinted = DrawableCompat.wrap(drawable)
        drawable.mutate()
        DrawableCompat.setTint(tinted, color)
        return tinted
    }
}
