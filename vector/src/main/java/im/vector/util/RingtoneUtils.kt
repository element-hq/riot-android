package im.vector.util

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.preference.PreferenceManager
import androidx.core.content.edit

/**
 * This file manages the sound ringtone for calls.
 * It allows you to use the default Riot Ringtone, or the standard ringtone or set a different one from the available choices
 * in Android.
 */

/**
 * Returns a Uri object that points to a specific Ringtone.
 *
 * If no Ringtone was explicitly set using Riot, it will return the Uri for the current system
 * ringtone for calls.
 *
 * @return the [Uri] of the currently set [Ringtone]
 * @see Ringtone
 */
fun getCallRingtoneUri(context: Context): Uri {
    val callRingtone: String? = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PreferencesManager.SETTINGS_CALL_RINGTONE_URI_PREFERENCE_KEY, null)

    callRingtone?.let {
        return Uri.parse(it)
    }

    // Use current system notification sound for incoming calls per default
    return RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
}

/**
 * Returns a Ringtone object that can then be played.
 *
 * If no Ringtone was explicitly set using Riot, it will return the current system ringtone
 * for calls.
 *
 * @return the currently set [Ringtone]
 * @see Ringtone
 */
fun getCallRingtone(context: Context): Ringtone {
    return RingtoneManager.getRingtone(context, getCallRingtoneUri(context))
}

/**
 * Returns a String with the name of the current Ringtone.
 *
 * If no Ringtone was explicitly set using Riot, it will return the name of the current system
 * ringtone for calls.
 *
 * @return the name of the currently set [Ringtone], or null
 * @see Ringtone
 */
fun getCallRingtoneName(context: Context): String? {
    return getCallRingtone(context).getTitle(context)
}

/**
 * Sets the selected ringtone for riot calls.
 *
 * @param ringtoneUri
 * @see Ringtone
 */
fun setCallRingtoneUri(context: Context, ringtoneUri: Uri) {
    PreferenceManager.getDefaultSharedPreferences(context)
            .edit {
                putString(PreferencesManager.SETTINGS_CALL_RINGTONE_URI_PREFERENCE_KEY, ringtoneUri.toString())
            }
}

/**
 * Set using Riot default ringtone
 */
fun useRiotDefaultRingtone(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PreferencesManager.SETTINGS_CALL_RINGTONE_USE_RIOT_PREFERENCE_KEY, true)
}

/**
 * Ask if default Riot ringtone has to be used
 */
fun setUseRiotDefaultRingtone(context: Context, useRiotDefault: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(context)
            .edit {
                putBoolean(PreferencesManager.SETTINGS_CALL_RINGTONE_USE_RIOT_PREFERENCE_KEY, useRiotDefault)
            }
}

