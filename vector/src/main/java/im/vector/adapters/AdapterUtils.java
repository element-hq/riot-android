/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.adapters;

import android.content.Context;
import android.text.format.DateUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import im.vector.R;
import im.vector.util.PreferencesManager;

/**
 * Contains useful functions for adapters.
 */
public class AdapterUtils {

    public static final long MS_IN_DAY = 1000 * 60 * 60 * 24;

    /**
     * Reset the time of a date
     *
     * @param date the date with time to reset
     * @return the 0 time date.
     */
    public static Date zeroTimeDate(Date date) {
        final GregorianCalendar gregorianCalendar = new GregorianCalendar();
        gregorianCalendar.setTime(date);
        gregorianCalendar.set(Calendar.HOUR_OF_DAY, 0);
        gregorianCalendar.set(Calendar.MINUTE, 0);
        gregorianCalendar.set(Calendar.SECOND, 0);
        gregorianCalendar.set(Calendar.MILLISECOND, 0);
        return gregorianCalendar.getTime();
    }

    /**
     * Returns the 12/24 h preference display
     *
     * @param context the context
     * @return the preferred display format
     */
    private static int getTimeDisplay(Context context) {
        return PreferencesManager.displayTimeIn12hFormat(context) ? DateUtils.FORMAT_12HOUR : DateUtils.FORMAT_24HOUR;
    }

    /**
     * Convert a time since epoch date to a string.
     *
     * @param context  the context.
     * @param ts       the time since epoch.
     * @param timeOnly true to return the time without the day.
     * @return the formatted date
     */
    public static String tsToString(Context context, long ts, boolean timeOnly) {
        long daysDiff = (new Date().getTime() - zeroTimeDate(new Date(ts)).getTime()) / MS_IN_DAY;

        String res;

        if (timeOnly) {
            res = DateUtils.formatDateTime(context, ts,
                    DateUtils.FORMAT_SHOW_TIME | getTimeDisplay(context));
        } else if (0 == daysDiff) {
            res = context.getString(R.string.today) + " " + DateUtils.formatDateTime(context, ts,
                    DateUtils.FORMAT_SHOW_TIME | getTimeDisplay(context));
        } else if (1 == daysDiff) {
            res = context.getString(R.string.yesterday) + " " + DateUtils.formatDateTime(context, ts,
                    DateUtils.FORMAT_SHOW_TIME | getTimeDisplay(context));
        } else if (7 > daysDiff) {
            res = DateUtils.formatDateTime(context, ts,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL | getTimeDisplay(context));
        } else if (365 > daysDiff) {
            res = DateUtils.formatDateTime(context, ts,
                    DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE);
        } else {
            res = DateUtils.formatDateTime(context, ts,
                    DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR);
        }

        return res;
    }
}
