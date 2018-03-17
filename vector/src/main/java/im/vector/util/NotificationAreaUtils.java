package im.vector.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Util to detect show or hide notification area.
 */
public class NotificationAreaUtils {

    /**
     * Preference key.
     */
    public static final String NOTIFICATION_AREA_SHOW_KEY = "NOTIFICATION_AREA_SHOW_KEY";

    /**
     * Notification area hide preference values.
     */
    private static final String ONLY_ERRORS = "only_errors";
    private static final String ALWAYS = "always";

    /**
     * Check is notification area always hide.
     * <p/>
     * Used for optimization.
     *
     * @param context the context
     * @return {@code true} if always hide, else {@code false}
     */
    public static boolean onlyErrors(Context context) {
        setDefault(context);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return ONLY_ERRORS.equals(sp.getString(NOTIFICATION_AREA_SHOW_KEY, ALWAYS));
    }

    /**
     * Determine to show or hide the notification area.
     *
     * @param context the context
     * @return {@code true} if invisible, else {@code false} for gone
     */
    public static boolean invisibleOrGone(Context context) {
        setDefault(context);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return ALWAYS.equals(sp.getString(NOTIFICATION_AREA_SHOW_KEY, ALWAYS));
    }

    /**
     * Sets default value if nothing specified.
     *
     * @param context the context
     */
    protected static void setDefault(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        if (!sp.contains(NOTIFICATION_AREA_SHOW_KEY)) {
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(NOTIFICATION_AREA_SHOW_KEY, ALWAYS);
            editor.commit();
        }
    }
}
