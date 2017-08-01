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

package im.vector.util;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import android.support.annotation.IntDef;
import android.text.TextUtils;
import android.util.TypedValue;

import im.vector.R;
import im.vector.VectorApp;

/**
 * Util class for managing themes.
 */
public class ThemeUtils {
    // preference key
    public static final String APPLICATION_THEME_KEY = "APPLICATION_THEME_KEY";

    //  the existing theme style
    private static final int THEME_DARK_STYLE = R.style.Theme_Vector_Dark;
    private static final int THEME_LIGHT_STYLE = R.style.Theme_Vector_Light;

    // the theme description
    public static final String THEME_DARK_VALUE = "dark";
    public static final String THEME_LIGHT_VALUE = "light";

    // selected theme
    private static Integer mCurrentThemeStyle = THEME_LIGHT_STYLE;

    /**
     * Provides the selected application theme
     * @param context the context
     * @return the selected application theme
     */
    public static String getApplicationTheme(Context context) {
        String appTheme = THEME_LIGHT_VALUE;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        // defines a default value if not defined
        if (!sp.contains(APPLICATION_THEME_KEY)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(APPLICATION_THEME_KEY, THEME_LIGHT_VALUE);
            editor.commit();
        } else {
            appTheme = sp.getString(APPLICATION_THEME_KEY, THEME_LIGHT_VALUE);
        }

        return appTheme;
    }

    /**
     * Update the application theme
     * @param aTheme the new theme
     */
    public static void setApplicationTheme(Context context, String aTheme) {
        if (null != aTheme) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(APPLICATION_THEME_KEY, aTheme);
            editor.commit();
        }

        mCurrentThemeStyle = TextUtils.equals(aTheme, THEME_DARK_VALUE) ? THEME_DARK_STYLE : THEME_LIGHT_STYLE;
        VectorApp.getInstance().setTheme(mCurrentThemeStyle);
    }

    /**
     * Update the theme of the provided activity
     * @param activity the activity
     */
    public static void setActivityTheme(Activity activity) {
        if (null != activity) {
            activity.setTheme(mCurrentThemeStyle);

            // TODO manage action bar one
        }
    }
    
    /**
     * Translates color attributes to colors
     * @param c Context
     * @param colorAttribute Color Attribute
     * @return Requested Color
     */
    public static @ColorInt int getColor(Context c, @AttrRes final int colorAttribute) {
        TypedValue color = new TypedValue();
        c.getTheme().resolveAttribute(colorAttribute, color, true);
        return color.data;
    }
}
