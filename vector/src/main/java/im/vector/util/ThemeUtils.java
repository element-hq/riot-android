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
import android.support.annotation.AttrRes;
import android.support.annotation.ColorRes;
import android.util.TypedValue;

import java.util.HashMap;
import java.util.Map;
import im.vector.R;

/**
 * Util class for managing themes.
 */
public class ThemeUtils {

    private static Integer currentTheme = null;

    // Maps theme names to formal ID's
    private static HashMap<String, Integer> themeMap = new HashMap<>();
    static {
        themeMap.put("dark", R.style.Theme_Vector_Dark);
        themeMap.put("light", R.style.Theme_Vector_Light);
    }


    public static void setTheme(String theme) {
        if (themeMap.containsKey(theme)) {
            if (themeMap.get(theme) != currentTheme) {
                currentTheme = themeMap.get(theme);
            }
        }
    }

    public static void activitySetTheme(Activity a) {
        if (currentTheme != null) {
            a.setTheme(currentTheme);
        } else {
            // Just in case we had some problem anywhere reading, set the theme to light.
            a.setTheme(themeMap.get("light"));
        }
    }

    /**
     * Translates color attributes to colors
     * @param c Context
     * @param colorAttribute Color Attribute
     * @return Requested Color
     */
    public static @ColorRes int getColor(Context c, @AttrRes final int colorAttribute) {
        TypedValue color = new TypedValue();
        c.getTheme().resolveAttribute(colorAttribute, color, true);
        return color.data;
    }

}
