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
    public static final String NOTIFICATION_AREA_HIDE_KEY = "NOTIFICATION_AREA_HIDE_KEY";

    /**
     * Notification area hide preference values.
     */
    private static final String ALWAYS = "always";
    private static final String NEVER = "never";
    private static final String WHEN_EMPTY = "when_empty";

    /**
     * Check is notification area always hide.
     * <p/>
     * Used for optimization.
     *
     * @param context the context
     * @return {@code true} if always hide, else {@code false}
     */
    public static boolean always(Context context) {
        setDefault(context);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(NOTIFICATION_AREA_HIDE_KEY, NEVER).equals(ALWAYS);
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
        String hideValue = sp.getString(NOTIFICATION_AREA_HIDE_KEY, NEVER);

        switch (hideValue) {
            case NEVER:
                return true;
            case WHEN_EMPTY:
                return false;
            default:
                return true;
        }
    }

    /**
     * Sets default value if nothing specified.
     *
     * @param context the context
     */
    protected static void setDefault(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        if (!sp.contains(NOTIFICATION_AREA_HIDE_KEY)) {
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(NOTIFICATION_AREA_HIDE_KEY, NEVER);
            editor.commit();
        }
    }
}
