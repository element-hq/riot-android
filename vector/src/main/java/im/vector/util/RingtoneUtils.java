package im.vector.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;

import im.vector.R;
import im.vector.VectorApp;

public class RingtoneUtils {
    public static final int CALL_RINGTONE_REQUEST_CODE = 999;
    private static Context context = VectorApp.getInstance();
    private static final SharedPreferences preferences = PreferenceManager.
            getDefaultSharedPreferences(context);

    public static Uri getCallRingtoneUri() {
        String callRingtone = preferences.getString(context.getResources()
                .getString(R.string.notification_sounds_settings_ringtone), null);
        if (callRingtone == null) { // Use current system notification sound for calls per default
            callRingtone = getCurrentSystemCallRingtone().toString();
        }
        return Uri.parse(callRingtone);
    }

    public static Ringtone getCallRingtone() {
        return RingtoneManager.getRingtone(context, getCallRingtoneUri());
    }

    public static String getCallRingtoneName() {
        return getCallRingtone().getTitle(context);
    }

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
