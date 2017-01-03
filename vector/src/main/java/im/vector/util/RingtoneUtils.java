package im.vector.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;

import im.vector.R;
import im.vector.VectorApp;

/**
 * This class manages the sound notifications for calls.
 * It allows you to use the standard ringtone or set a different one from the available choices
 * in Android.
 */
public class RingtoneUtils {
    public static final int CALL_RINGTONE_REQUEST_CODE = 999;
    private static Context context = VectorApp.getInstance();
    private static final SharedPreferences preferences = PreferenceManager.
            getDefaultSharedPreferences(context);

    /**
     * Returns a Uri object that points to a specific Ringtone.
     * <p>
     * If no Ringtone was explicitly set using Riot, it will return the Uri for the current system
     * ringtone for calls.
     * @return      the {@link Uri} of the currently set {@link Ringtone}
     * @see         Ringtone
     */
    public static Uri getCallRingtoneUri() {
        String callRingtone = preferences.getString(context.getResources()
                .getString(R.string.notification_sounds_settings_ringtone), null);
        if (callRingtone == null) { // Use current system notification sound for calls per default
            callRingtone = getCurrentSystemCallRingtone().toString();
        }
        return Uri.parse(callRingtone);
    }

    /**
     * Returns a Ringtone object that can then be played.
     * <p>
     * If no Ringtone was explicitly set using Riot, it will return the current system ringtone
     * for calls.
     * @return      the currently set {@link Ringtone}
     * @see         Ringtone
     */
    public static Ringtone getCallRingtone() {
        return RingtoneManager.getRingtone(context, getCallRingtoneUri());
    }

    /**
     * Returns a String with the name of the current Ringtone.
     * <p>
     * If no Ringtone was explicitly set using Riot, it will return the name of thecurrent system
     * ringtone for calls.
     * @return      the name of the currently set {@link Ringtone}
     * @see         Ringtone
     */
    public static String getCallRingtoneName() {
        return getCallRingtone().getTitle(context);
    }

    /**
     * Sets the selected ringtone for riot calls.
     * @param       ringtoneUri
     * @see         Ringtone
     */
    public static void setCallRingtoneUri(Uri ringtoneUri) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(context.getResources().getString(R.string.notification_sounds_settings_ringtone),
                ringtoneUri.toString());
        editor.commit();
    }

    private static Uri getCurrentSystemCallRingtone() {
        return RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE);
    }
}
