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

package im.vector.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.media.RingtoneManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.preference.*
import android.provider.Settings
import android.support.design.widget.TextInputEditText
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.edit
import androidx.core.widget.toast
import com.bumptech.glide.Glide
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import im.vector.Matrix
import im.vector.R
import im.vector.VectorApp
import im.vector.activity.*
import im.vector.contacts.ContactsManager
import im.vector.gcm.GcmRegistrationManager
import im.vector.preference.*
import im.vector.settings.FontScale
import im.vector.util.*
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.data.MyUser
import org.matrix.androidsdk.data.Pusher
import org.matrix.androidsdk.data.RoomMediaMessage
import org.matrix.androidsdk.db.MXMediasCache
import org.matrix.androidsdk.listeners.IMXNetworkEventListener
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.listeners.MXMediaUploadListener
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.callback.SimpleApiCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.bingrules.BingRule
import org.matrix.androidsdk.rest.model.group.Group
import org.matrix.androidsdk.rest.model.pid.ThirdPartyIdentifier
import org.matrix.androidsdk.rest.model.pid.ThreePid
import org.matrix.androidsdk.rest.model.sync.DeviceInfo
import org.matrix.androidsdk.rest.model.sync.DevicesListResponse
import org.matrix.androidsdk.util.BingRulesManager
import org.matrix.androidsdk.util.Log
import org.matrix.androidsdk.util.ResourceUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

// TODO Extend PreferenceFragmentCompat() from support-v7
class VectorSettingsPreferencesFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    // members
    private lateinit var mSession: MXSession

    // disable some updates if there is
    private val mNetworkListener = IMXNetworkEventListener { refreshDisplay() }
    // events listener
    private val mEventsListener = object : MXEventListener() {
        override fun onBingRulesUpdate() {
            refreshPreferences()
            refreshDisplay()
        }

        override fun onAccountInfoUpdate(myUser: MyUser) {
            // refresh the settings value
            PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance().applicationContext).edit {
                putString(PreferencesManager.SETTINGS_DISPLAY_NAME_PREFERENCE_KEY, myUser.displayname)
            }

            refreshDisplay()
        }
    }

    private var mLoadingView: View? = null

    private var mDisplayedEmails = ArrayList<String>()
    private var mDisplayedPhoneNumber = ArrayList<String>()

    private var mMyDeviceInfo: DeviceInfo? = null

    private var mDisplayedPushers = ArrayList<Pusher>()

    // devices: device IDs and device names
    private var mDevicesNameList: List<DeviceInfo> = ArrayList()
    // used to avoid requesting to enter the password for each deletion
    private var mAccountPassword: String? = null

    // current publicised group list
    private var mPublicisedGroups: MutableSet<String>? = null

    /* ==========================================================================================
     * Preferences
     * ========================================================================================== */

    private val mUserSettingsCategory by lazy {
        findPreference(PreferencesManager.SETTINGS_USER_SETTINGS_PREFERENCE_KEY) as PreferenceCategory
    }
    private val mUserAvatarPreference by lazy {
        findPreference(PreferencesManager.SETTINGS_PROFILE_PICTURE_PREFERENCE_KEY) as UserAvatarPreference
    }
    private val mDisplayNamePreference by lazy {
        findPreference(PreferencesManager.SETTINGS_DISPLAY_NAME_PREFERENCE_KEY) as EditTextPreference
    }
    private val mPasswordPreference by lazy {
        findPreference(PreferencesManager.SETTINGS_CHANGE_PASSWORD_PREFERENCE_KEY)
    }

    // Local contacts
    private val mContactSettingsCategory by lazy {
        findPreference(PreferencesManager.SETTINGS_CONTACT_PREFERENCE_KEYS) as PreferenceCategory
    }
    private val mContactPhonebookCountryPreference by lazy {
        findPreference(PreferencesManager.SETTINGS_CONTACTS_PHONEBOOK_COUNTRY_PREFERENCE_KEY) as VectorCustomActionEditTextPreference
    }

    // cryptography
    private val mCryptographyCategory by lazy {
        findPreference(PreferencesManager.SETTINGS_CRYPTOGRAPHY_PREFERENCE_KEY) as PreferenceCategory
    }
    private val mCryptographyCategoryDivider by lazy {
        findPreference(PreferencesManager.SETTINGS_CRYPTOGRAPHY_DIVIDER_PREFERENCE_KEY) as PreferenceCategory
    }
    // displayed pushers
    private val mPushersSettingsDivider by lazy {
        findPreference(PreferencesManager.SETTINGS_NOTIFICATIONS_TARGET_DIVIDER_PREFERENCE_KEY) as PreferenceCategory
    }
    private val mPushersSettingsCategory by lazy {
        findPreference(PreferencesManager.SETTINGS_NOTIFICATIONS_TARGETS_PREFERENCE_KEY) as PreferenceCategory
    }
    private val mDevicesListSettingsCategory by lazy {
        findPreference(PreferencesManager.SETTINGS_DEVICES_LIST_PREFERENCE_KEY) as PreferenceCategory
    }
    private val mDevicesListSettingsCategoryDivider by lazy {
        findPreference(PreferencesManager.SETTINGS_DEVICES_DIVIDER_PREFERENCE_KEY) as PreferenceCategory
    }
    // displayed the ignored users list
    private val mIgnoredUserSettingsCategoryDivider by lazy {
        findPreference(PreferencesManager.SETTINGS_IGNORE_USERS_DIVIDER_PREFERENCE_KEY) as PreferenceCategory
    }
    private val mIgnoredUserSettingsCategory by lazy {
        findPreference(PreferencesManager.SETTINGS_IGNORED_USERS_PREFERENCE_KEY) as PreferenceCategory
    }
    // background sync category
    private val mSyncRequestTimeoutPreference by lazy {
        // ? Cause it can be removed
        findPreference(PreferencesManager.SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY) as EditTextPreference?
    }
    private val mSyncRequestDelayPreference by lazy {
        // ? Cause it can be removed
        findPreference(PreferencesManager.SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY) as EditTextPreference?
    }
    private val mLabsCategory by lazy {
        findPreference(PreferencesManager.SETTINGS_LABS_PREFERENCE_KEY) as PreferenceCategory
    }
    private val mGroupsFlairCategory by lazy {
        findPreference(PreferencesManager.SETTINGS_GROUPS_FLAIR_KEY) as PreferenceCategory
    }
    private val backgroundSyncCategory by lazy {
        findPreference(PreferencesManager.SETTINGS_BACKGROUND_SYNC_PREFERENCE_KEY)
    }
    private val backgroundSyncDivider by lazy {
        findPreference(PreferencesManager.SETTINGS_BACKGROUND_SYNC_DIVIDER_PREFERENCE_KEY)
    }
    private val backgroundSyncPreference by lazy {
        findPreference(PreferencesManager.SETTINGS_ENABLE_BACKGROUND_SYNC_PREFERENCE_KEY) as CheckBoxPreference
    }
    private val mRingtonePreference by lazy {
        findPreference(PreferencesManager.SETTINGS_NOTIFICATION_RINGTONE_SELECTION_PREFERENCE_KEY)
    }
    private val notificationsSettingsCategory by lazy {
        findPreference(PreferencesManager.SETTINGS_NOTIFICATIONS_KEY) as PreferenceCategory
    }
    private val mNotificationPrivacyPreference by lazy {
        findPreference(PreferencesManager.SETTINGS_NOTIFICATION_PRIVACY_PREFERENCE_KEY)
    }
    private val selectedLanguagePreference by lazy {
        findPreference(PreferencesManager.SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY) as VectorCustomActionEditTextPreference
    }
    private val textSizePreference by lazy {
        findPreference(PreferencesManager.SETTINGS_INTERFACE_TEXT_SIZE_KEY) as VectorCustomActionEditTextPreference
    }
    private val cryptoInfoDeviceNamePreference by lazy {
        findPreference(PreferencesManager.SETTINGS_ENCRYPTION_INFORMATION_DEVICE_NAME_PREFERENCE_KEY) as VectorCustomActionEditTextPreference
    }
    private val cryptoInfoDeviceIdPreference by lazy {
        findPreference(PreferencesManager.SETTINGS_ENCRYPTION_INFORMATION_DEVICE_ID_PREFERENCE_KEY) as VectorCustomActionEditTextPreference
    }

    private val exportPref by lazy {
        findPreference(PreferencesManager.SETTINGS_ENCRYPTION_EXPORT_E2E_ROOM_KEYS_PREFERENCE_KEY) as VectorCustomActionEditTextPreference
    }

    private val importPref by lazy {
        findPreference(PreferencesManager.SETTINGS_ENCRYPTION_IMPORT_E2E_ROOM_KEYS_PREFERENCE_KEY) as VectorCustomActionEditTextPreference
    }

    private val cryptoInfoTextPreference by lazy {
        findPreference(PreferencesManager.SETTINGS_ENCRYPTION_INFORMATION_DEVICE_KEY_PREFERENCE_KEY) as VectorCustomActionEditTextPreference
    }
    // encrypt to unverified devices
    private val sendToUnverifiedDevicesPref by lazy {
        findPreference(PreferencesManager.SETTINGS_ENCRYPTION_NEVER_SENT_TO_PREFERENCE_KEY) as CheckBoxPreference
    }

    /* ==========================================================================================
     * Life cycle
     * ========================================================================================== */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContext = activity.applicationContext

        // retrieve the arguments
        val sessionArg = Matrix.getInstance(appContext).getSession(arguments.getString(ARG_MATRIX_ID))

        // sanity checks
        if (null == sessionArg || !sessionArg.isAlive) {
            activity.finish()
            return
        }

        mSession = sessionArg

        // define the layout
        addPreferencesFromResource(R.xml.vector_settings_preferences)

        // Avatar
        mUserAvatarPreference.let {
            it.setSession(mSession)
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                onUpdateAvatarClick()
                false
            }
        }

        // Display name
        mDisplayNamePreference.let {
            it.summary = mSession.myUser.displayname
            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                onDisplayNameClick(if (null == newValue) null else (newValue as String).trim())
                false
            }
        }

        // Password
        mPasswordPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            onPasswordUpdateClick()
            false
        }

        // User Email and phone
        // Add phone and add email buttons first
        addButtons()

        refreshEmailsList()
        refreshPhoneNumbersList()

        // Contacts
        setContactsPreferences()

        // user interface preferences
        setUserInterfacePreferences()

        // Url preview
        (findPreference(PreferencesManager.SETTINGS_SHOW_URL_PREVIEW_KEY) as VectorSwitchPreference).let {
            it.isChecked = mSession.isURLPreviewEnabled

            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                if (null != newValue && newValue as Boolean != mSession.isURLPreviewEnabled) {
                    displayLoadingView()
                    mSession.setURLPreviewStatus(newValue, object : ApiCallback<Void> {
                        override fun onSuccess(info: Void?) {
                            it.isChecked = mSession.isURLPreviewEnabled
                            hideLoadingView()
                        }

                        private fun onError(errorMessage: String) {
                            if (null != activity) {
                                Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show()
                            }
                            onSuccess(null)
                        }

                        override fun onNetworkError(e: Exception) {
                            onError(e.localizedMessage)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            onError(e.localizedMessage)
                        }

                        override fun onUnexpectedError(e: Exception) {
                            onError(e.localizedMessage)
                        }
                    })
                }

                false
            }
        }

        // Themes
        findPreference(ThemeUtils.APPLICATION_THEME_KEY)
                .onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            if (newValue is String) {
                VectorApp.updateApplicationTheme(newValue)
                activity.startActivity(activity.intent)
                activity.finish()
                true
            } else {
                false
            }
        }

        // Flair
        refreshGroupFlairsList()

        // push rules

        // Notification privacy
        mNotificationPrivacyPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(NotificationPrivacyActivity.getIntent(activity))
            true
        }
        refreshNotificationPrivacy()

        // Ringtone
        mRingtonePreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)

            if (null != PreferencesManager.getNotificationRingTone(activity)) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, PreferencesManager.getNotificationRingTone(activity))
            }
            activity.startActivityForResult(intent, REQUEST_NOTIFICATION_RINGTONE)
            false
        }
        refreshNotificationRingTone()

        for (resourceText in mPushesRuleByResourceId.keys) {
            val preference = findPreference(resourceText)

            if (null != preference) {
                if (preference is CheckBoxPreference) {
                    preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValueAsVoid ->
                        // on some old android APIs,
                        // the callback is called even if there is no user interaction
                        // so the value will be checked to ensure there is really no update.
                        onPushRuleClick(preference.key, newValueAsVoid as Boolean)
                        true
                    }
                } else if (preference is BingRulePreference) {
                    preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                        AlertDialog.Builder(activity)
                                .setSingleChoiceItems(preference.bingRuleStatuses,
                                        preference.ruleStatusIndex
                                ) { d, index ->
                                    val rule = preference.createRule(index)
                                    d.cancel()

                                    if (null != rule) {
                                        displayLoadingView()
                                        mSession.dataHandler
                                                .bingRulesManager
                                                .updateRule(preference.rule,
                                                        rule,
                                                        object : BingRulesManager.onBingRuleUpdateListener {
                                                            private fun onDone() {
                                                                refreshDisplay()
                                                                hideLoadingView()
                                                            }

                                                            override fun onBingRuleUpdateSuccess() {
                                                                onDone()
                                                            }

                                                            override fun onBingRuleUpdateFailure(errorMessage: String) {
                                                                if (null != activity) {
                                                                    Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show()
                                                                }
                                                                onDone()
                                                            }
                                                        })
                                    }
                                }
                                .show()
                        true
                    }
                }
            }
        }

        // background sync tuning settings
        // these settings are useless and hidden if the app is registered to the GCM push service
        val gcmMgr = Matrix.getInstance(appContext)!!.sharedGCMRegistrationManager
        if (gcmMgr.useGCM() && gcmMgr.hasRegistrationToken()) {
            // Hide the section
            preferenceScreen.removePreference(backgroundSyncDivider)
            preferenceScreen.removePreference(backgroundSyncCategory)
        } else {
            backgroundSyncPreference.let {
                it.isChecked = gcmMgr.isBackgroundSyncAllowed

                it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, aNewValue ->
                    val newValue = aNewValue as Boolean

                    if (newValue != gcmMgr.isBackgroundSyncAllowed) {
                        gcmMgr.isBackgroundSyncAllowed = newValue
                    }

                    displayLoadingView()

                    Matrix.getInstance(activity)!!.sharedGCMRegistrationManager
                            .forceSessionsRegistration(object : GcmRegistrationManager.ThirdPartyRegistrationListener {

                                override fun onThirdPartyRegistered() {
                                    hideLoadingView()
                                }

                                override fun onThirdPartyRegistrationFailed() {
                                    hideLoadingView()
                                }

                                override fun onThirdPartyUnregistered() {
                                    hideLoadingView()
                                }

                                override fun onThirdPartyUnregistrationFailed() {
                                    hideLoadingView()
                                }
                            })

                    true
                }
            }
        }

        // Push target
        refreshPushersList()

        // Ignore users
        refreshIgnoredUsersList()

        // Lab
        val useCryptoPref = findPreference(PreferencesManager.SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_PREFERENCE_KEY) as CheckBoxPreference
        val cryptoIsEnabledPref = findPreference(PreferencesManager.SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_IS_ACTIVE_PREFERENCE_KEY)

        if (mSession.isCryptoEnabled) {
            mLabsCategory.removePreference(useCryptoPref)

            cryptoIsEnabledPref.isEnabled = false
        } else {
            mLabsCategory.removePreference(cryptoIsEnabledPref)

            useCryptoPref.isChecked = false

            useCryptoPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValueAsVoid ->
                if (TextUtils.isEmpty(mSession.credentials.deviceId)) {
                    AlertDialog.Builder(activity)
                            .setMessage(R.string.room_settings_labs_end_to_end_warnings)
                            .setPositiveButton(R.string.logout) { dialog, which ->
                                dialog.dismiss()
                                CommonActivityUtils.logout(activity)
                            }
                            .setNegativeButton(R.string.cancel) { dialog, which ->
                                dialog.dismiss()
                                useCryptoPref.isChecked = false
                            }
                            .setOnCancelListener { dialog ->
                                dialog.dismiss()
                                useCryptoPref.isChecked = false
                            }
                            .show()
                } else {
                    val newValue = newValueAsVoid as Boolean

                    if (mSession.isCryptoEnabled != newValue) {
                        displayLoadingView()

                        mSession.enableCrypto(newValue, object : ApiCallback<Void> {
                            private fun refresh() {
                                if (null != activity) {
                                    activity.runOnUiThread {
                                        hideLoadingView()
                                        useCryptoPref.isChecked = mSession.isCryptoEnabled

                                        if (mSession.isCryptoEnabled) {
                                            mLabsCategory.removePreference(useCryptoPref)
                                            mLabsCategory.addPreference(cryptoIsEnabledPref)
                                        }
                                    }
                                }
                            }

                            override fun onSuccess(info: Void?) {
                                useCryptoPref.isEnabled = false
                                refresh()
                            }

                            override fun onNetworkError(e: Exception) {
                                useCryptoPref.isChecked = false
                            }

                            override fun onMatrixError(e: MatrixError) {
                                useCryptoPref.isChecked = false
                            }

                            override fun onUnexpectedError(e: Exception) {
                                useCryptoPref.isChecked = false
                            }
                        })
                    }
                }

                true
            }
        }

        // SaveMode Management
        findPreference(PreferencesManager.SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY)
                .onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val sessions = Matrix.getMXSessions(activity)
            for (session in sessions) {
                session.setUseDataSaveMode(newValue as Boolean)
            }

            true
        }

        // Device list
        refreshDevicesList()

        // Advanced settings

        // user account
        findPreference(PreferencesManager.SETTINGS_LOGGED_IN_PREFERENCE_KEY)
                .summary = mSession.myUserId

        // home server
        findPreference(PreferencesManager.SETTINGS_HOME_SERVER_PREFERENCE_KEY)
                .summary = mSession.homeServerConfig.homeserverUri.toString()

        // identity server
        findPreference(PreferencesManager.SETTINGS_IDENTITY_SERVER_PREFERENCE_KEY)
                .summary = mSession.homeServerConfig.identityServerUri.toString()


        // Analytics

        // Analytics tracking management
        (findPreference(PreferencesManager.SETTINGS_USE_ANALYTICS_KEY) as CheckBoxPreference).let {
            // On if the analytics tracking is activated
            it.isChecked = PreferencesManager.useAnalytics(appContext)

            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                PreferencesManager.setUseAnalytics(appContext, newValue as Boolean)
                true
            }
        }

        // Rageshake Management
        (findPreference(PreferencesManager.SETTINGS_USE_RAGE_SHAKE_KEY) as CheckBoxPreference).let {
            it.isChecked = PreferencesManager.useRageshake(appContext)

            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                PreferencesManager.setUseRageshake(appContext, newValue as Boolean)
                true
            }
        }

        // Others

        // preference to start the App info screen, to facilitate App permissions access
        findPreference(APP_INFO_LINK_PREFERENCE_KEY)
                .onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (null != activity) {
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val uri = Uri.fromParts("package", appContext.packageName, null)
                intent.data = uri
                activity.applicationContext.startActivity(intent)
            }

            true
        }

        // application version
        (findPreference(PreferencesManager.SETTINGS_VERSION_PREFERENCE_KEY) as VectorCustomActionEditTextPreference).let {
            it.summary = VectorUtils.getApplicationVersion(appContext)

            it.setOnPreferenceLongClickListener {
                VectorUtils.copyToClipboard(appContext, VectorUtils.getApplicationVersion(appContext))
                true
            }
        }

        // olm version
        findPreference(PreferencesManager.SETTINGS_OLM_VERSION_PREFERENCE_KEY)
                .summary = mSession.getCryptoVersion(appContext, false)


        // copyright
        findPreference(PreferencesManager.SETTINGS_COPYRIGHT_PREFERENCE_KEY)
                .onPreferenceClickListener = Preference.OnPreferenceClickListener {
            VectorUtils.displayAppCopyright()
            false
        }

        // terms & conditions
        findPreference(PreferencesManager.SETTINGS_APP_TERM_CONDITIONS_PREFERENCE_KEY)
                .onPreferenceClickListener = Preference.OnPreferenceClickListener {
            VectorUtils.displayAppTac()
            false
        }

        // privacy policy
        findPreference(PreferencesManager.SETTINGS_PRIVACY_POLICY_PREFERENCE_KEY)
                .onPreferenceClickListener = Preference.OnPreferenceClickListener {
            VectorUtils.displayAppPrivacyPolicy()
            false
        }

        // third party notice
        findPreference(PreferencesManager.SETTINGS_THIRD_PARTY_NOTICES_PREFERENCE_KEY)
                .onPreferenceClickListener = Preference.OnPreferenceClickListener {
            VectorUtils.displayThirdPartyLicenses()
            false
        }

        // update keep medias period
        findPreference(PreferencesManager.SETTINGS_MEDIA_SAVING_PERIOD_KEY).let {
            it.summary = PreferencesManager.getSelectedMediasSavingPeriodString(activity)

            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                AlertDialog.Builder(activity)
                        .setSingleChoiceItems(PreferencesManager.getMediasSavingItemsChoicesList(activity),
                                PreferencesManager.getSelectedMediasSavingPeriod(activity)) { d, n ->
                            PreferencesManager.setSelectedMediasSavingPeriod(activity, n)
                            d.cancel()

                            it.summary = PreferencesManager.getSelectedMediasSavingPeriodString(activity)
                        }
                        .show()
                false
            }
        }

        // clear medias cache
        findPreference(PreferencesManager.SETTINGS_CLEAR_MEDIA_CACHE_PREFERENCE_KEY).let {
            MXMediasCache.getCachesSize(activity, object : SimpleApiCallback<Long>() {
                override fun onSuccess(size: Long) {
                    if (null != activity) {
                        it.summary = android.text.format.Formatter.formatFileSize(activity, size)
                    }
                }
            })

            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                displayLoadingView()

                val task = object : AsyncTask<Void?, Void?, Void?>() {
                    override fun doInBackground(vararg params: Void?): Void? {
                        mSession.mediasCache.clear()
                        Glide.get(activity).clearDiskCache()
                        return null
                    }

                    override fun onPostExecute(result: Void?) {
                        hideLoadingView()

                        MXMediasCache.getCachesSize(activity, object : SimpleApiCallback<Long>() {
                            override fun onSuccess(size: Long) {
                                it.summary = android.text.format.Formatter.formatFileSize(activity, size)
                            }
                        })
                    }
                }

                try {
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "## mSession.getMediasCache().clear() failed " + e.message)
                    task.cancel(true)
                    hideLoadingView()
                }

                false
            }
        }

        // clear cache
        findPreference(PreferencesManager.SETTINGS_CLEAR_CACHE_PREFERENCE_KEY).let {
            MXSession.getApplicationSizeCaches(activity, object : SimpleApiCallback<Long>() {
                override fun onSuccess(size: Long) {
                    if (null != activity) {
                        it.summary = android.text.format.Formatter.formatFileSize(activity, size)
                    }
                }
            })

            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                displayLoadingView()
                Matrix.getInstance(appContext).reloadSessions(appContext)
                false
            }
        }

        // Deactivate accounbt section

        // deactivate account
        findPreference(PreferencesManager.SETTINGS_DEACTIVATE_ACCOUNT_KEY)
                .onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(DeactivateAccountActivity.getIntent(activity))

            false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        if (null != view) {
            val listView = view.findViewById<View>(android.R.id.list)

            listView?.setPadding(0, 0, 0, 0)
        }

        return view
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        // if the user toggles the contacts book permission
        if (TextUtils.equals(key, ContactsManager.CONTACTS_BOOK_ACCESS_KEY)) {
            // reset the current snapshot
            ContactsManager.getInstance().clearSnapshot()
        }
    }

    override fun onResume() {
        super.onResume()

        // search the loading view from the upper view
        mLoadingView = view.findViewById(R.id.vector_settings_spinner_views)

        if (mSession.isAlive) {
            val context = activity.applicationContext

            mSession.dataHandler.addListener(mEventsListener)

            Matrix.getInstance(context)!!.addNetworkEventListener(mNetworkListener)

            mSession.myUser.refreshThirdPartyIdentifiers(object : SimpleApiCallback<Void>() {
                override fun onSuccess(info: Void?) {
                    // ensure that the activity still exists
                    if (null != activity) {
                        // and the result is called in the right thread
                        activity.runOnUiThread {
                            refreshEmailsList()
                            refreshPhoneNumbersList()
                        }
                    }
                }
            })

            Matrix.getInstance(context)!!
                    .sharedGCMRegistrationManager
                    .refreshPushersList(Matrix.getInstance(context)!!.sessions, object : SimpleApiCallback<Void>() {
                        override fun onSuccess(info: Void?) {
                            refreshPushersList()
                        }
                    })

            PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this)

            // refresh anything else
            refreshPreferences()
            refreshNotificationPrivacy()
            refreshDisplay()
            refreshBackgroundSyncPrefs()
        }
    }

    override fun onPause() {
        super.onPause()

        val context = activity.applicationContext

        if (mSession.isAlive) {
            mSession.dataHandler.removeListener(mEventsListener)
            Matrix.getInstance(context)!!.removeNetworkEventListener(mNetworkListener)
        }

        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this)
    }

    //==============================================================================================================
    // Display methods
    //==============================================================================================================

    /**
     * Display the loading view.
     */
    private fun displayLoadingView() {
        // search the loading view from the upper view
        if (null == mLoadingView) {
            var parent = view

            while (parent != null && mLoadingView == null) {
                mLoadingView = parent.findViewById(R.id.vector_settings_spinner_views)
                parent = parent.parent as View
            }
        }

        if (null != mLoadingView) {
            mLoadingView!!.visibility = View.VISIBLE
        }
    }

    /**
     * Hide the loading view.
     */
    private fun hideLoadingView() {
        if (null != mLoadingView) {
            mLoadingView!!.visibility = View.GONE
        }
    }

    /**
     * Hide the loading view and refresh the preferences.
     *
     * @param refresh true to refresh the display
     */
    private fun hideLoadingView(refresh: Boolean) {
        mLoadingView!!.visibility = View.GONE

        if (refresh) {
            refreshDisplay()
        }
    }

    /**
     * Refresh the preferences.
     */
    private fun refreshDisplay() {
        val isConnected = Matrix.getInstance(activity)!!.isConnected
        val appContext = activity.applicationContext

        val preferenceManager = preferenceManager

        // refresh the avatar
        mUserAvatarPreference.refreshAvatar()
        mUserAvatarPreference.isEnabled = isConnected

        // refresh the display name
        mDisplayNamePreference.summary = mSession.myUser.displayname
        mDisplayNamePreference.text = mSession.myUser.displayname
        mDisplayNamePreference.isEnabled = isConnected

        // change password
        mPasswordPreference.isEnabled = isConnected

        // update the push rules
        val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)

        val rules = mSession.dataHandler.pushRules()

        val gcmMgr = Matrix.getInstance(appContext)!!.sharedGCMRegistrationManager

        for (resourceText in mPushesRuleByResourceId.keys) {
            val preference = preferenceManager.findPreference(resourceText)

            if (null != preference) {
                if (preference is BingRulePreference) {
                    preference.isEnabled = null != rules && isConnected && gcmMgr.areDeviceNotificationsAllowed()
                    preference.setBingRule(mSession.dataHandler.pushRules().findDefaultRule(mPushesRuleByResourceId[resourceText]))
                } else if (preference is CheckBoxPreference) {
                    if (resourceText == PreferencesManager.SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY) {
                        preference.isChecked = gcmMgr.areDeviceNotificationsAllowed()
                    } else if (resourceText == PreferencesManager.SETTINGS_TURN_SCREEN_ON_PREFERENCE_KEY) {
                        preference.isChecked = gcmMgr.isScreenTurnedOn
                        preference.isEnabled = gcmMgr.areDeviceNotificationsAllowed()
                    } else {
                        preference.isEnabled = null != rules && isConnected
                        preference.isChecked = preferences.getBoolean(resourceText, false)
                    }
                }
            }
        }

        // If notifications are disabled for the current user account or for the current user device
        // The others notifications settings have to be disable too
        val areNotifAllowed = (rules != null
                && rules.findDefaultRule(BingRule.RULE_ID_DISABLE_ALL) != null
                && rules.findDefaultRule(BingRule.RULE_ID_DISABLE_ALL).isEnabled)

        mRingtonePreference.isEnabled = !areNotifAllowed && gcmMgr.areDeviceNotificationsAllowed()

        mNotificationPrivacyPreference.isEnabled = !areNotifAllowed && gcmMgr.areDeviceNotificationsAllowed() && gcmMgr.useGCM()
    }

    private fun addButtons() {
        // display the "add email" entry
        mUserSettingsCategory.addPreference(
                EditTextPreference(activity).apply {
                    setTitle(R.string.settings_add_email_address)
                    setDialogTitle(R.string.settings_add_email_address)
                    key = ADD_EMAIL_PREFERENCE_KEY
                    icon = ThemeUtils.tintDrawable(activity,
                            ContextCompat.getDrawable(activity, R.drawable.ic_add_black)!!, R.attr.settings_icon_tint_color)
                    order = 100
                    editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        addEmail((newValue as String).trim())
                        false
                    }
                }
        )

        // display the "add phone number" entry
        mUserSettingsCategory.addPreference(
                Preference(activity).apply {
                    setTitle(R.string.settings_add_phone_number)
                    key = ADD_PHONE_NUMBER_PREFERENCE_KEY
                    icon = ThemeUtils.tintDrawable(activity,
                            ContextCompat.getDrawable(activity, R.drawable.ic_add_black)!!, R.attr.settings_icon_tint_color)
                    order = 200

                    onPreferenceClickListener = Preference.OnPreferenceClickListener {
                        val intent = PhoneNumberAdditionActivity.getIntent(activity, mSession.credentials.userId)
                        startActivityForResult(intent, REQUEST_NEW_PHONE_NUMBER)
                        true
                    }
                }
        )
    }

    //==============================================================================================================
    // Update items  methods
    //==============================================================================================================

    /**
     * Update the password.
     */
    private fun onPasswordUpdateClick() {
        activity.runOnUiThread {
            val view = activity.layoutInflater.inflate(R.layout.fragment_dialog_change_password, null)

            val oldPasswordText = view.findViewById<EditText>(R.id.change_password_old_pwd_text)
            val newPasswordText = view.findViewById<EditText>(R.id.change_password_new_pwd_text)
            val confirmNewPasswordText = view.findViewById<EditText>(R.id.change_password_confirm_new_pwd_text)

            val dialog = AlertDialog.Builder(activity)
                    .setView(view)
                    .setTitle(R.string.settings_change_password)
                    .setPositiveButton(R.string.save) { dialog, which ->
                        if (null != activity) {
                            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(view.applicationWindowToken, 0)
                        }

                        val oldPwd = oldPasswordText.text.toString().trim()
                        val newPwd = newPasswordText.text.toString().trim()

                        displayLoadingView()

                        mSession.updatePassword(oldPwd, newPwd, object : ApiCallback<Void> {
                            private fun onDone(textId: Int) {
                                // check the activity still exists
                                if (null != activity) {
                                    // and the code is called in the right thread
                                    activity.runOnUiThread {
                                        hideLoadingView()
                                        Toast.makeText(activity,
                                                getString(textId),
                                                Toast.LENGTH_LONG).show()
                                    }
                                }
                            }

                            override fun onSuccess(info: Void?) {
                                onDone(R.string.settings_password_updated)
                            }

                            override fun onNetworkError(e: Exception) {
                                onDone(R.string.settings_fail_to_update_password)
                            }

                            override fun onMatrixError(e: MatrixError) {
                                onDone(R.string.settings_fail_to_update_password)
                            }

                            override fun onUnexpectedError(e: Exception) {
                                onDone(R.string.settings_fail_to_update_password)
                            }
                        })
                    }
                    .setNegativeButton(R.string.cancel) { dialog, which ->
                        if (null != activity) {
                            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(view.applicationWindowToken, 0)
                        }
                    }
                    .setOnCancelListener {
                        if (null != activity) {
                            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(view.applicationWindowToken, 0)
                        }
                    }
                    .show()

            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.isEnabled = false

            confirmNewPasswordText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val oldPwd = oldPasswordText.text.toString().trim()
                    val newPwd = newPasswordText.text.toString().trim()
                    val newConfirmPwd = confirmNewPasswordText.text.toString().trim()

                    saveButton.isEnabled = oldPwd.length > 0 && newPwd.length > 0 && TextUtils.equals(newPwd, newConfirmPwd)
                }

                override fun afterTextChanged(s: Editable) {}
            })
        }
    }

    /**
     * Update a push rule.
     */
    private fun onPushRuleClick(fResourceText: String, newValue: Boolean) {
        val gcmMgr = Matrix.getInstance(activity)!!.sharedGCMRegistrationManager

        Log.d(LOG_TAG, "onPushRuleClick $fResourceText : set to $newValue")

        if (fResourceText == PreferencesManager.SETTINGS_TURN_SCREEN_ON_PREFERENCE_KEY) {
            if (gcmMgr.isScreenTurnedOn != newValue) {
                gcmMgr.isScreenTurnedOn = newValue
            }
            return
        }

        if (fResourceText == PreferencesManager.SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY) {
            val isConnected = Matrix.getInstance(activity)!!.isConnected
            val isAllowed = gcmMgr.areDeviceNotificationsAllowed()

            // avoid useless update
            if (isAllowed == newValue) {
                return
            }

            gcmMgr.setDeviceNotificationsAllowed(!isAllowed)

            // when using GCM
            // need to register on servers
            if (isConnected && gcmMgr.useGCM() && (gcmMgr.isServerRegistred || gcmMgr.isServerUnRegistred)) {
                val listener = object : GcmRegistrationManager.ThirdPartyRegistrationListener {

                    private fun onDone() {
                        if (null != activity) {
                            activity.runOnUiThread {
                                hideLoadingView(true)
                                refreshPushersList()
                            }
                        }
                    }

                    override fun onThirdPartyRegistered() {
                        onDone()
                    }

                    override fun onThirdPartyRegistrationFailed() {
                        gcmMgr.setDeviceNotificationsAllowed(isAllowed)
                        onDone()
                    }

                    override fun onThirdPartyUnregistered() {
                        onDone()
                    }

                    override fun onThirdPartyUnregistrationFailed() {
                        gcmMgr.setDeviceNotificationsAllowed(isAllowed)
                        onDone()
                    }
                }

                displayLoadingView()
                if (gcmMgr.isServerRegistred) {
                    gcmMgr.unregister(listener)
                } else {
                    gcmMgr.register(listener)
                }
            }

            return
        }

        val ruleId = mPushesRuleByResourceId[fResourceText]
        val rule = mSession.dataHandler.pushRules().findDefaultRule(ruleId)

        // check if there is an update
        var curValue = null != rule && rule.isEnabled

        if (TextUtils.equals(ruleId, BingRule.RULE_ID_DISABLE_ALL) || TextUtils.equals(ruleId, BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS)) {
            curValue = !curValue
        }

        // on some old android APIs,
        // the callback is called even if there is no user interaction
        // so the value will be checked to ensure there is really no update.
        if (newValue == curValue) {
            return
        }

        if (null != rule) {
            displayLoadingView()
            mSession.dataHandler.bingRulesManager.updateEnableRuleStatus(rule, !rule.isEnabled, object : BingRulesManager.onBingRuleUpdateListener {
                private fun onDone() {
                    refreshDisplay()
                    hideLoadingView()
                }

                override fun onBingRuleUpdateSuccess() {
                    onDone()
                }

                override fun onBingRuleUpdateFailure(errorMessage: String) {
                    if (null != activity) {
                        Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                    onDone()
                }
            })
        }
    }

    /**
     * Update the displayname.
     */
    private fun onDisplayNameClick(value: String?) {
        if (!TextUtils.equals(mSession.myUser.displayname, value)) {
            displayLoadingView()

            mSession.myUser.updateDisplayName(value, object : ApiCallback<Void> {
                override fun onSuccess(info: Void?) {
                    // refresh the settings value
                    PreferenceManager.getDefaultSharedPreferences(activity).edit {
                        putString(PreferencesManager.SETTINGS_DISPLAY_NAME_PREFERENCE_KEY, value)
                    }

                    onCommonDone(null)

                    refreshDisplay()
                }

                override fun onNetworkError(e: Exception) {
                    onCommonDone(e.localizedMessage)
                }

                override fun onMatrixError(e: MatrixError) {
                    if (MatrixError.M_CONSENT_NOT_GIVEN == e.errcode) {
                        if (null != activity) {
                            activity.runOnUiThread {
                                hideLoadingView()
                                (activity as RiotAppCompatActivity).consentNotGivenHelper.displayDialog(e)
                            }
                        }
                    } else {
                        onCommonDone(e.localizedMessage)
                    }
                }

                override fun onUnexpectedError(e: Exception) {
                    onCommonDone(e.localizedMessage)
                }
            })
        }
    }

    /**
     * Update the avatar.
     */
    private fun onUpdateAvatarClick() {
        activity.runOnUiThread {
            if (CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO, activity)) {
                val intent = Intent(activity, VectorMediasPickerActivity::class.java)
                intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true)
                startActivityForResult(intent, VectorUtils.TAKE_IMAGE)
            }
        }
    }

    /**
     * Refresh the notification ring tone
     */
    private fun refreshNotificationRingTone() {
        mRingtonePreference.summary = PreferencesManager.getNotificationRingToneName(activity)
    }

    /**
     * Refresh the notification privacy setting
     */
    private fun refreshNotificationPrivacy() {
        val gcmRegistrationManager = Matrix.getInstance(activity)!!.sharedGCMRegistrationManager

        // this setting apply only with GCM for the moment
        if (gcmRegistrationManager.useGCM()) {
            val notificationPrivacyString = NotificationPrivacyActivity.getNotificationPrivacyString(activity.applicationContext,
                    gcmRegistrationManager.notificationPrivacy)
            mNotificationPrivacyPreference.summary = notificationPrivacyString
        } else {
            notificationsSettingsCategory.removePreference(mNotificationPrivacyPreference)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_NOTIFICATION_RINGTONE -> {
                    PreferencesManager.setNotificationRingTone(activity, data?.getParcelableExtra<Parcelable>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as Uri)

                    // test if the selected ring tone can be played
                    if (null == PreferencesManager.getNotificationRingToneName(activity)) {
                        PreferencesManager.setNotificationRingTone(activity, PreferencesManager.getNotificationRingTone(activity))
                    }

                    refreshNotificationRingTone()
                }
                REQUEST_E2E_FILE_REQUEST_CODE -> importKeys(data)
                REQUEST_NEW_PHONE_NUMBER -> refreshPhoneNumbersList()
                REQUEST_PHONEBOOK_COUNTRY -> onPhonebookCountryUpdate(data)
                REQUEST_LOCALE -> {
                    startActivity(activity.intent)
                    activity.finish()
                }
                VectorUtils.TAKE_IMAGE -> {
                    val thumbnailUri = VectorUtils.getThumbnailUriFromIntent(activity, data, mSession.mediasCache)

                    if (null != thumbnailUri) {
                        displayLoadingView()

                        val resource = ResourceUtils.openResource(activity, thumbnailUri, null)

                        if (null != resource) {
                            mSession.mediasCache.uploadContent(resource.mContentStream, null, resource.mMimeType, null, object : MXMediaUploadListener() {

                                override fun onUploadError(uploadId: String?, serverResponseCode: Int, serverErrorMessage: String?) {
                                    activity.runOnUiThread { onCommonDone(serverResponseCode.toString() + " : " + serverErrorMessage) }
                                }

                                override fun onUploadComplete(uploadId: String?, contentUri: String?) {
                                    activity.runOnUiThread {
                                        mSession.myUser.updateAvatarUrl(contentUri, object : ApiCallback<Void> {
                                            override fun onSuccess(info: Void?) {
                                                onCommonDone(null)
                                                refreshDisplay()
                                            }

                                            override fun onNetworkError(e: Exception) {
                                                onCommonDone(e.localizedMessage)
                                            }

                                            override fun onMatrixError(e: MatrixError) {
                                                if (MatrixError.M_CONSENT_NOT_GIVEN == e.errcode) {
                                                    if (null != activity) {
                                                        activity.runOnUiThread {
                                                            hideLoadingView()
                                                            (activity as RiotAppCompatActivity).consentNotGivenHelper.displayDialog(e)
                                                        }
                                                    }
                                                } else {
                                                    onCommonDone(e.localizedMessage)
                                                }
                                            }

                                            override fun onUnexpectedError(e: Exception) {
                                                onCommonDone(e.localizedMessage)
                                            }
                                        })
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    /**
     * Refresh the known information about the account
     */
    private fun refreshPreferences() {
        PreferenceManager.getDefaultSharedPreferences(activity).edit {
            putString(PreferencesManager.SETTINGS_DISPLAY_NAME_PREFERENCE_KEY, mSession.myUser.displayname)
            putString(PreferencesManager.SETTINGS_VERSION_PREFERENCE_KEY, VectorUtils.getApplicationVersion(activity))

            val mBingRuleSet = mSession.dataHandler.pushRules()

            if (null != mBingRuleSet) {
                for (resourceText in mPushesRuleByResourceId.keys) {
                    val preference = findPreference(resourceText)

                    if (null != preference && preference is CheckBoxPreference) {
                        val ruleId = mPushesRuleByResourceId[resourceText]

                        val rule = mBingRuleSet.findDefaultRule(ruleId)
                        var isEnabled = null != rule && rule.isEnabled

                        if (TextUtils.equals(ruleId, BingRule.RULE_ID_DISABLE_ALL) || TextUtils.equals(ruleId, BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS)) {
                            isEnabled = !isEnabled
                        } else if (isEnabled) {
                            val actions = rule!!.actions

                            // no action -> noting will be done
                            if (null == actions || actions.isEmpty()) {
                                isEnabled = false
                            } else if (1 == actions.size) {
                                try {
                                    isEnabled = !TextUtils.equals(actions[0] as String, BingRule.ACTION_DONT_NOTIFY)
                                } catch (e: Exception) {
                                    Log.e(LOG_TAG, "## refreshPreferences failed " + e.message)
                                }

                            }
                        }// check if the rule is only defined by don't notify

                        putBoolean(resourceText, isEnabled)
                    }
                }
            }
        }
    }

    /**
     * Display a dialog which asks confirmation for the deletion of a 3pid
     *
     * @param pid               the 3pid to delete
     * @param preferenceSummary the displayed 3pid
     */
    private fun displayDelete3PIDConfirmationDialog(pid: ThirdPartyIdentifier, preferenceSummary: CharSequence) {
        val mediumFriendlyName = ThreePid.getMediumFriendlyName(pid.medium, activity).toLowerCase(VectorApp.getApplicationLocale())
        val dialogMessage = getString(R.string.settings_delete_threepid_confirmation, mediumFriendlyName, preferenceSummary)

        AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_title_confirmation)
                .setMessage(dialogMessage)
                .setPositiveButton(R.string.remove) { dialog, which ->
                    dialog.dismiss()

                    displayLoadingView()

                    mSession.myUser.delete3Pid(pid, object : ApiCallback<Void> {
                        override fun onSuccess(info: Void?) {
                            when (pid.medium) {
                                ThreePid.MEDIUM_EMAIL -> refreshEmailsList()
                                ThreePid.MEDIUM_MSISDN -> refreshPhoneNumbersList()
                            }
                            onCommonDone(null)
                        }

                        override fun onNetworkError(e: Exception) {
                            onCommonDone(e.localizedMessage)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            onCommonDone(e.localizedMessage)
                        }

                        override fun onUnexpectedError(e: Exception) {
                            onCommonDone(e.localizedMessage)
                        }
                    })
                }
                .setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }
                .show()
    }

//==============================================================================================================
// ignored users list management
//==============================================================================================================

    /**
     * Refresh the ignored users list
     */
    private fun refreshIgnoredUsersList() {
        val ignoredUsersList = mSession.dataHandler.ignoredUserIds

        ignoredUsersList.sortWith(Comparator { u1, u2 ->
            u1.toLowerCase(VectorApp.getApplicationLocale()).compareTo(u2.toLowerCase(VectorApp.getApplicationLocale()))
        })

        val preferenceScreen = preferenceScreen

        preferenceScreen.removePreference(mIgnoredUserSettingsCategory)
        preferenceScreen.removePreference(mIgnoredUserSettingsCategoryDivider)
        mIgnoredUserSettingsCategory.removeAll()

        if (ignoredUsersList.size > 0) {
            preferenceScreen.addPreference(mIgnoredUserSettingsCategoryDivider)
            preferenceScreen.addPreference(mIgnoredUserSettingsCategory)

            for (userId in ignoredUsersList) {
                val preference = VectorCustomActionEditTextPreference(activity)

                preference.title = userId
                preference.key = IGNORED_USER_KEY_BASE + userId

                preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    AlertDialog.Builder(activity)
                            .setMessage(getString(R.string.settings_unignore_user, userId))
                            .setPositiveButton(R.string.yes) { dialog, which ->
                                dialog.dismiss()

                                displayLoadingView()

                                val idsList = ArrayList<String>()
                                idsList.add(userId)

                                mSession.unIgnoreUsers(idsList, object : ApiCallback<Void> {
                                    override fun onSuccess(info: Void?) {
                                        onCommonDone(null)
                                    }

                                    override fun onNetworkError(e: Exception) {
                                        onCommonDone(e.localizedMessage)
                                    }

                                    override fun onMatrixError(e: MatrixError) {
                                        onCommonDone(e.localizedMessage)
                                    }

                                    override fun onUnexpectedError(e: Exception) {
                                        onCommonDone(e.localizedMessage)
                                    }
                                })
                            }
                            .setNegativeButton(R.string.no) { dialog, which -> dialog.dismiss() }
                            .show()

                    false
                }

                mIgnoredUserSettingsCategory.addPreference(preference)
            }
        }
    }

//==============================================================================================================
// pushers list management
//==============================================================================================================

    /**
     * Refresh the pushers list
     */
    private fun refreshPushersList() {
        val gcmRegistrationManager = Matrix.getInstance(activity)!!.sharedGCMRegistrationManager
        val pushersList = ArrayList(gcmRegistrationManager.mPushersList)

        if (pushersList.isEmpty()) {
            preferenceScreen.removePreference(mPushersSettingsCategory)
            preferenceScreen.removePreference(mPushersSettingsDivider)
            return
        }

        // check first if there is an update
        var isNewList = true
        if (pushersList.size == mDisplayedPushers.size) {
            isNewList = !mDisplayedPushers.containsAll(pushersList)
        }

        if (isNewList) {
            // remove the displayed one
            mPushersSettingsCategory.removeAll()

            // add new emails list
            mDisplayedPushers = pushersList

            var index = 0

            for (pusher in mDisplayedPushers) {
                if (null != pusher.lang) {
                    val isThisDeviceTarget = TextUtils.equals(gcmRegistrationManager.currentRegistrationToken, pusher.pushkey)

                    val preference = VectorCustomActionEditTextPreference(activity, if (isThisDeviceTarget) Typeface.BOLD else Typeface.NORMAL)
                    preference.title = pusher.deviceDisplayName
                    preference.summary = pusher.appDisplayName
                    preference.key = PUSHER_PREFERENCE_KEY_BASE + index
                    index++
                    mPushersSettingsCategory.addPreference(preference)

                    // the user cannot remove the self device target
                    if (!isThisDeviceTarget) {
                        preference.setOnPreferenceLongClickListener {
                            AlertDialog.Builder(activity)
                                    .setTitle(R.string.dialog_title_confirmation)
                                    .setMessage(R.string.settings_delete_notification_targets_confirmation)
                                    .setPositiveButton(R.string.remove) { dialog, which ->
                                        dialog.dismiss()

                                        displayLoadingView()
                                        gcmRegistrationManager.unregister(mSession, pusher, object : ApiCallback<Void> {
                                            override fun onSuccess(info: Void?) {
                                                refreshPushersList()
                                                onCommonDone(null)
                                            }

                                            override fun onNetworkError(e: Exception) {
                                                onCommonDone(e.localizedMessage)
                                            }

                                            override fun onMatrixError(e: MatrixError) {
                                                onCommonDone(e.localizedMessage)
                                            }

                                            override fun onUnexpectedError(e: Exception) {
                                                onCommonDone(e.localizedMessage)
                                            }
                                        })
                                    }
                                    .setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }
                                    .show()
                            true
                        }
                    }
                }
            }
        }
    }

//==============================================================================================================
// Email management
//==============================================================================================================

    /**
     * Refresh the emails list
     */
    private fun refreshEmailsList() {
        val currentEmail3PID = ArrayList(mSession.myUser.getlinkedEmails())

        val newEmailsList = ArrayList<String>()
        for (identifier in currentEmail3PID) {
            newEmailsList.add(identifier.address)
        }

        // check first if there is an update
        var isNewList = true
        if (newEmailsList.size == mDisplayedEmails.size) {
            isNewList = !mDisplayedEmails.containsAll(newEmailsList)
        }

        if (isNewList) {
            // remove the displayed one
            run {
                var index = 0
                while (true) {
                    val preference = mUserSettingsCategory.findPreference(EMAIL_PREFERENCE_KEY_BASE + index)

                    if (null != preference) {
                        mUserSettingsCategory.removePreference(preference)
                    } else {
                        break
                    }
                    index++
                }
            }

            // add new emails list
            mDisplayedEmails = newEmailsList

            var index = 0
            val addEmailBtn = mUserSettingsCategory.findPreference(ADD_EMAIL_PREFERENCE_KEY)
                    ?: return

            // reported by GA

            var order = addEmailBtn.order

            for (email3PID in currentEmail3PID) {
                val preference = VectorCustomActionEditTextPreference(activity)

                preference.title = getString(R.string.settings_email_address)
                preference.summary = email3PID.address
                preference.key = EMAIL_PREFERENCE_KEY_BASE + index
                preference.order = order

                preference.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference ->
                    displayDelete3PIDConfirmationDialog(email3PID, preference.summary)
                    true
                }

                preference.setOnPreferenceLongClickListener {
                    VectorUtils.copyToClipboard(activity, email3PID.address)
                    true
                }

                mUserSettingsCategory.addPreference(preference)

                index++
                order++
            }

            addEmailBtn.order = order
        }
    }

    /**
     * A request has been processed.
     * Display a toast if there is a an error message
     *
     * @param errorMessage the error message
     */
    private fun onCommonDone(errorMessage: String?) {
        if (null != activity) {
            activity.runOnUiThread {
                if (!TextUtils.isEmpty(errorMessage)) {
                    Toast.makeText(VectorApp.getInstance(), errorMessage, Toast.LENGTH_SHORT).show()
                }
                hideLoadingView()
            }
        }
    }

    /**
     * Attempt to add a new email to the account
     *
     * @param email the email to add.
     */
    private fun addEmail(email: String?) {
        // check first if the email syntax is valid
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email!!).matches()) {
            Toast.makeText(activity, getString(R.string.auth_invalid_email), Toast.LENGTH_SHORT).show()
            return
        }

        // check first if the email syntax is valid
        if (mDisplayedEmails.indexOf(email) >= 0) {
            Toast.makeText(activity, getString(R.string.auth_email_already_defined), Toast.LENGTH_SHORT).show()
            return
        }

        val pid = ThreePid(email, ThreePid.MEDIUM_EMAIL)

        displayLoadingView()

        mSession.myUser.requestEmailValidationToken(pid, object : ApiCallback<Void> {
            override fun onSuccess(info: Void?) {
                if (null != activity) {
                    activity.runOnUiThread { showEmailValidationDialog(pid) }
                }
            }

            override fun onNetworkError(e: Exception) {
                onCommonDone(e.localizedMessage)
            }

            override fun onMatrixError(e: MatrixError) {
                if (TextUtils.equals(MatrixError.THREEPID_IN_USE, e.errcode)) {
                    onCommonDone(getString(R.string.account_email_already_used_error))
                } else {
                    onCommonDone(e.localizedMessage)
                }
            }

            override fun onUnexpectedError(e: Exception) {
                onCommonDone(e.localizedMessage)
            }
        })
    }

    /**
     * Show an email validation dialog to warn the user tho valid his email link.
     *
     * @param pid the used pid.
     */
    private fun showEmailValidationDialog(pid: ThreePid) {
        AlertDialog.Builder(activity)
                .setTitle(R.string.account_email_validation_title)
                .setMessage(R.string.account_email_validation_message)
                .setPositiveButton(R.string._continue) { dialog, which ->
                    dialog.dismiss()
                    mSession.myUser.add3Pid(pid, true, object : ApiCallback<Void> {
                        override fun onSuccess(info: Void?) {
                            if (null != activity) {
                                activity.runOnUiThread {
                                    hideLoadingView()
                                    refreshEmailsList()
                                }
                            }
                        }

                        override fun onNetworkError(e: Exception) {
                            onCommonDone(e.localizedMessage)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            if (TextUtils.equals(e.errcode, MatrixError.THREEPID_AUTH_FAILED)) {
                                if (null != activity) {
                                    activity.runOnUiThread {
                                        hideLoadingView()
                                        Toast.makeText(activity, getString(R.string.account_email_validation_error), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                onCommonDone(e.localizedMessage)
                            }
                        }

                        override fun onUnexpectedError(e: Exception) {
                            onCommonDone(e.localizedMessage)
                        }
                    })
                }
                .setNegativeButton(R.string.cancel) { dialog, which ->
                    dialog.dismiss()
                    hideLoadingView()
                }
                .show()
    }

//==============================================================================================================
// Phone number management
//==============================================================================================================

    /**
     * Refresh phone number list
     */
    private fun refreshPhoneNumbersList() {
        val currentPhoneNumber3PID = ArrayList(mSession.myUser.getlinkedPhoneNumbers())

        val phoneNumberList = ArrayList<String>()
        for (identifier in currentPhoneNumber3PID) {
            phoneNumberList.add(identifier.address)
        }

        // check first if there is an update
        var isNewList = true
        if (phoneNumberList.size == mDisplayedPhoneNumber.size) {
            isNewList = !mDisplayedPhoneNumber.containsAll(phoneNumberList)
        }

        if (isNewList) {
            // remove the displayed one
            run {
                var index = 0
                while (true) {
                    val preference = mUserSettingsCategory.findPreference(PHONE_NUMBER_PREFERENCE_KEY_BASE + index)

                    if (null != preference) {
                        mUserSettingsCategory.removePreference(preference)
                    } else {
                        break
                    }
                    index++
                }
            }

            // add new phone number list
            mDisplayedPhoneNumber = phoneNumberList

            var index = 0
            val addPhoneBtn = mUserSettingsCategory.findPreference(ADD_PHONE_NUMBER_PREFERENCE_KEY)
                    ?: return
            var order = addPhoneBtn.order

            for (phoneNumber3PID in currentPhoneNumber3PID) {
                val preference = VectorCustomActionEditTextPreference(activity)

                preference.title = getString(R.string.settings_phone_number)
                var phoneNumberFormatted = phoneNumber3PID.address
                try {
                    // Attempt to format phone number
                    val phoneNumber = PhoneNumberUtil.getInstance().parse("+$phoneNumberFormatted", null)
                    phoneNumberFormatted = PhoneNumberUtil.getInstance().format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
                } catch (e: NumberParseException) {
                    // Do nothing, we will display raw version
                }

                preference.summary = phoneNumberFormatted
                preference.key = PHONE_NUMBER_PREFERENCE_KEY_BASE + index
                preference.order = order

                preference.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference ->
                    displayDelete3PIDConfirmationDialog(phoneNumber3PID, preference.summary)
                    true
                }

                preference.setOnPreferenceLongClickListener {
                    VectorUtils.copyToClipboard(activity, phoneNumber3PID.address)
                    true
                }

                index++
                order++
                mUserSettingsCategory.addPreference(preference)
            }

            addPhoneBtn.order = order
        }

    }

//==============================================================================================================
// contacts management
//==============================================================================================================

    private fun setContactsPreferences() {
        // Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // on Android >= 23, use the system one
            mContactSettingsCategory.removePreference(findPreference(ContactsManager.CONTACTS_BOOK_ACCESS_KEY))
        }
        // Phonebook country
        mContactPhonebookCountryPreference.summary = PhoneNumberUtils.getHumanCountryCode(PhoneNumberUtils.getCountryCode(activity))

        mContactPhonebookCountryPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = CountryPickerActivity.getIntent(activity, true)
            startActivityForResult(intent, REQUEST_PHONEBOOK_COUNTRY)
            true
        }
    }

    private fun onPhonebookCountryUpdate(data: Intent?) {
        if (data != null && data.hasExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_NAME)
                && data.hasExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE)) {
            val countryCode = data.getStringExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE)
            if (!TextUtils.equals(countryCode, PhoneNumberUtils.getCountryCode(activity))) {
                PhoneNumberUtils.setCountryCode(activity, countryCode)
                mContactPhonebookCountryPreference.summary = data.getStringExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_NAME)
            }
        }
    }

//==============================================================================================================
// user interface management
//==============================================================================================================

    private fun setUserInterfacePreferences() {
        // Selected language
        selectedLanguagePreference.summary = VectorApp.localeToLocalisedString(VectorApp.getApplicationLocale())

        selectedLanguagePreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivityForResult(LanguagePickerActivity.getIntent(activity), REQUEST_LOCALE)
            true
        }

        // Text size
        textSizePreference.summary = FontScale.getFontScaleDescription()

        textSizePreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            displayTextSizeSelection(activity)
            true
        }
    }

    private fun displayTextSizeSelection(activity: Activity) {
        val inflater = activity.layoutInflater

        val layout = inflater.inflate(R.layout.text_size_selection, null)

        val dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.font_size)
                .setView(layout)
                .setPositiveButton(R.string.ok) { dialog, id -> }
                .setNegativeButton(R.string.cancel) { dialog, id -> }
                .show()

        val linearLayout = layout.findViewById<LinearLayout>(R.id.text_selection_group_view)

        val childCount = linearLayout.childCount

        val scaleText = FontScale.getFontScalePrefValue()

        for (i in 0 until childCount) {
            val v = linearLayout.getChildAt(i)

            if (v is CheckedTextView) {
                v.isChecked = TextUtils.equals(v.text, scaleText)

                v.setOnClickListener {
                    dialog.dismiss()
                    FontScale.updateFontScale(v.text.toString())
                    activity.startActivity(activity.intent)
                    activity.finish()
                }
            }
        }
    }

//==============================================================================================================
// background sync management
//==============================================================================================================

    /**
     * Convert a delay in seconds to string
     *
     * @param seconds the delay in seconds
     * @return the text
     */
    private fun secondsToText(seconds: Int): String {
        return if (seconds > 1) {
            seconds.toString() + " " + getString(R.string.settings_seconds)
        } else {
            seconds.toString() + " " + getString(R.string.settings_second)
        }
    }

    /**
     * Refresh the background sync preference
     */
    private fun refreshBackgroundSyncPrefs() {
        // sanity check
        if (null == activity) {
            return
        }

        val gcmmgr = Matrix.getInstance(activity)!!.sharedGCMRegistrationManager

        val timeout = gcmmgr.backgroundSyncTimeOut / 1000
        val delay = gcmmgr.backgroundSyncDelay / 1000

        // update the settings
        PreferenceManager.getDefaultSharedPreferences(activity).edit {
            putString(PreferencesManager.SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY, timeout.toString() + "")
            putString(PreferencesManager.SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY, delay.toString() + "")
        }

        mSyncRequestTimeoutPreference?.let {
            it.summary = secondsToText(timeout)
            it.text = timeout.toString() + ""

            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                var newTimeOut = timeout

                try {
                    newTimeOut = Integer.parseInt(newValue as String)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "## refreshBackgroundSyncPrefs : parseInt failed " + e.message)
                }

                if (newTimeOut != timeout) {
                    gcmmgr.backgroundSyncTimeOut = newTimeOut * 1000

                    activity.runOnUiThread { refreshBackgroundSyncPrefs() }
                }

                false
            }
        }


        mSyncRequestDelayPreference?.let {
            it.summary = secondsToText(delay)
            it.text = delay.toString() + ""

            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                var newDelay = delay

                try {
                    newDelay = Integer.parseInt(newValue as String)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "## refreshBackgroundSyncPrefs : parseInt failed " + e.message)
                }

                if (newDelay != delay) {
                    gcmmgr.backgroundSyncDelay = newDelay * 1000

                    activity.runOnUiThread { refreshBackgroundSyncPrefs() }
                }

                false
            }
        }
    }


//==============================================================================================================
// Cryptography
//==============================================================================================================

    private fun removeCryptographyPreference() {
        if (null != preferenceScreen) {
            preferenceScreen.removePreference(mCryptographyCategory)
            preferenceScreen.removePreference(mCryptographyCategoryDivider)
        }
    }

    /**
     * Build the cryptography preference section.
     *
     * @param aMyDeviceInfo the device info
     */
    private fun refreshCryptographyPreference(aMyDeviceInfo: DeviceInfo?) {
        val userId = mSession.myUserId
        val deviceId = mSession.credentials.deviceId

        // device name
        if (null != aMyDeviceInfo && !TextUtils.isEmpty(aMyDeviceInfo.display_name)) {
            cryptoInfoDeviceNamePreference.summary = aMyDeviceInfo.display_name

            cryptoInfoDeviceNamePreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                displayDeviceRenameDialog(aMyDeviceInfo)
                true
            }

            cryptoInfoDeviceNamePreference.setOnPreferenceLongClickListener {
                VectorUtils.copyToClipboard(activity, aMyDeviceInfo.display_name)
                true
            }
        }

        // crypto section: device ID
        if (!TextUtils.isEmpty(deviceId)) {
            cryptoInfoDeviceIdPreference.summary = deviceId

            cryptoInfoDeviceIdPreference.setOnPreferenceLongClickListener {
                VectorUtils.copyToClipboard(activity, deviceId)
                true
            }

            exportPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                exportKeys()
                true
            }

            importPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                importKeys()
                true
            }
        }

        // crypto section: device key (fingerprint)
        if (!TextUtils.isEmpty(deviceId) && !TextUtils.isEmpty(userId)) {
            mSession.crypto.getDeviceInfo(userId, deviceId, object : SimpleApiCallback<MXDeviceInfo>() {
                override fun onSuccess(deviceInfo: MXDeviceInfo?) {
                    if (null != deviceInfo && !TextUtils.isEmpty(deviceInfo.fingerprint()) && null != activity) {
                        cryptoInfoTextPreference.summary = deviceInfo.getFingerprintHumanReadable()

                        cryptoInfoTextPreference.setOnPreferenceLongClickListener {
                            VectorUtils.copyToClipboard(activity, deviceInfo.fingerprint())
                            true
                        }
                    }
                }
            })
        }

        sendToUnverifiedDevicesPref.isChecked = false

        mSession.crypto.getGlobalBlacklistUnverifiedDevices(object : SimpleApiCallback<Boolean>() {
            override fun onSuccess(status: Boolean) {
                sendToUnverifiedDevicesPref.isChecked = status
            }
        })

        sendToUnverifiedDevicesPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            mSession.crypto.getGlobalBlacklistUnverifiedDevices(object : SimpleApiCallback<Boolean>() {
                override fun onSuccess(status: Boolean) {
                    if (sendToUnverifiedDevicesPref.isChecked != status) {
                        mSession.crypto
                                .setGlobalBlacklistUnverifiedDevices(sendToUnverifiedDevicesPref.isChecked, object : SimpleApiCallback<Void>() {
                                    override fun onSuccess(info: Void?) {

                                    }
                                })
                    }
                }
            })

            true
        }
    }

//==============================================================================================================
// devices list
//==============================================================================================================

    private fun removeDevicesPreference() {
        if (null != preferenceScreen) {
            preferenceScreen.removePreference(mDevicesListSettingsCategory)
            preferenceScreen.removePreference(mDevicesListSettingsCategoryDivider)
        }
    }

    /**
     * Force the refresh of the devices list.<br></br>
     * The devices list is the list of the devices where the user as looged in.
     * It can be any mobile device, as any browser.
     */
    private fun refreshDevicesList() {
        if (mSession.isCryptoEnabled && !TextUtils.isEmpty(mSession.credentials.deviceId)) {
            // display a spinner while loading the devices list
            if (0 == mDevicesListSettingsCategory.preferenceCount) {
                val preference = ProgressBarPreference(activity)
                mDevicesListSettingsCategory.addPreference(preference)
            }

            mSession.getDevicesList(object : ApiCallback<DevicesListResponse> {
                override fun onSuccess(info: DevicesListResponse) {
                    if (0 == info.devices.size) {
                        removeDevicesPreference()
                    } else {
                        buildDevicesSettings(info.devices)
                    }
                }

                override fun onNetworkError(e: Exception) {
                    removeDevicesPreference()
                    onCommonDone(e.message)
                }

                override fun onMatrixError(e: MatrixError) {
                    removeDevicesPreference()
                    onCommonDone(e.message)
                }

                override fun onUnexpectedError(e: Exception) {
                    removeDevicesPreference()
                    onCommonDone(e.message)
                }
            })
        } else {
            removeDevicesPreference()
            removeCryptographyPreference()
        }
    }

    /**
     * Build the devices portion of the settings.<br></br>
     * Each row correspond to a device ID and its corresponding device name. Clicking on the row
     * display a dialog containing: the device ID, the device name and the "last seen" information.
     *
     * @param aDeviceInfoList the list of the devices
     */
    private fun buildDevicesSettings(aDeviceInfoList: List<DeviceInfo>) {
        var preference: VectorCustomActionEditTextPreference
        var typeFaceHighlight: Int
        var isNewList = true
        val myDeviceId = mSession.credentials.deviceId

        if (aDeviceInfoList.size == mDevicesNameList.size) {
            isNewList = !mDevicesNameList.containsAll(aDeviceInfoList)
        }

        if (isNewList) {
            var prefIndex = 0
            mDevicesNameList = aDeviceInfoList

            // sort before display: most recent first
            DeviceInfo.sortByLastSeen(mDevicesNameList)

            // start from scratch: remove the displayed ones
            mDevicesListSettingsCategory.removeAll()

            for (deviceInfo in mDevicesNameList) {
                // set bold to distinguish current device ID
                if (null != myDeviceId && myDeviceId == deviceInfo.device_id) {
                    mMyDeviceInfo = deviceInfo
                    typeFaceHighlight = Typeface.BOLD
                } else {
                    typeFaceHighlight = Typeface.NORMAL
                }

                // add the edit text preference
                preference = VectorCustomActionEditTextPreference(activity, typeFaceHighlight)

                if (null == deviceInfo.device_id && null == deviceInfo.display_name) {
                    continue
                } else {
                    if (null != deviceInfo.device_id) {
                        preference.title = deviceInfo.device_id
                    }

                    // display name parameter can be null (new JSON API)
                    if (null != deviceInfo.display_name) {
                        preference.summary = deviceInfo.display_name
                    }
                }

                preference.key = DEVICES_PREFERENCE_KEY_BASE + prefIndex
                prefIndex++

                // onClick handler: display device details dialog
                preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    displayDeviceDetailsDialog(deviceInfo)
                    true
                }

                mDevicesListSettingsCategory.addPreference(preference)
            }

            refreshCryptographyPreference(mMyDeviceInfo)
        }
    }

    /**
     * Display a dialog containing the device ID, the device name and the "last seen" information.<>
     * This dialog allow to delete the corresponding device (see [.displayDeviceDeletionDialog])
     *
     * @param aDeviceInfo the device information
     */
    private fun displayDeviceDetailsDialog(aDeviceInfo: DeviceInfo?) {
        val builder = AlertDialog.Builder(activity)
        val inflater = activity.layoutInflater
        val layout = inflater.inflate(R.layout.devices_details_settings, null)

        if (null != aDeviceInfo) {
            //device ID
            var textView = layout.findViewById<TextView>(R.id.device_id)
            textView.text = aDeviceInfo.device_id

            // device name
            textView = layout.findViewById(R.id.device_name)
            val displayName = if (TextUtils.isEmpty(aDeviceInfo.display_name)) LABEL_UNAVAILABLE_DATA else aDeviceInfo.display_name
            textView.text = displayName

            // last seen info
            textView = layout.findViewById(R.id.device_last_seen)
            if (!TextUtils.isEmpty(aDeviceInfo.last_seen_ip)) {
                val lastSeenIp = aDeviceInfo.last_seen_ip
                var lastSeenTime = LABEL_UNAVAILABLE_DATA

                if (null != activity) {
                    val dateFormatTime = SimpleDateFormat(getString(R.string.devices_details_time_format))
                    val time = dateFormatTime.format(Date(aDeviceInfo.last_seen_ts))

                    val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
                    lastSeenTime = dateFormat.format(Date(aDeviceInfo.last_seen_ts)) + ", " + time
                }
                val lastSeenInfo = getString(R.string.devices_details_last_seen_format, lastSeenIp, lastSeenTime)
                textView.text = lastSeenInfo
            } else {
                // hide last time seen section
                layout.findViewById<View>(R.id.device_last_seen_title).visibility = View.GONE
                textView.visibility = View.GONE
            }

            // title & icon
            builder.setTitle(R.string.devices_details_dialog_title)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setView(layout)
                    .setPositiveButton(R.string.rename) { dialog, which -> displayDeviceRenameDialog(aDeviceInfo) }

            // disable the deletion for our own device
            if (!TextUtils.equals(mSession.crypto.myDevice.deviceId, aDeviceInfo.device_id)) {
                builder.setNegativeButton(R.string.delete) { dialog, which -> displayDeviceDeletionDialog(aDeviceInfo) }
            }

            builder.setNeutralButton(R.string.cancel) { dialog, which -> dialog.dismiss() }
                    .setOnKeyListener(DialogInterface.OnKeyListener { dialog, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                            dialog.cancel()
                            return@OnKeyListener true
                        }
                        false
                    })
                    .show()
        } else {
            Log.e(LOG_TAG, "## displayDeviceDetailsDialog(): sanity check failure")
            // FIXME Hardcoded string
            activity?.applicationContext?.toast("DeviceDetailsDialog cannot be displayed.\nBad input parameters.")
        }
    }

    /**
     * Display an alert dialog to rename a device
     *
     * @param aDeviceInfoToRename device info
     */
    private fun displayDeviceRenameDialog(aDeviceInfoToRename: DeviceInfo) {
        val input = EditText(activity)
        input.setText(aDeviceInfoToRename.display_name)

        AlertDialog.Builder(activity)
                .setTitle(R.string.devices_details_device_name)
                .setView(input)
                .setPositiveButton(R.string.ok) { dialog, which ->
                    displayLoadingView()

                    mSession.setDeviceName(aDeviceInfoToRename.device_id, input.text.toString(), object : ApiCallback<Void> {
                        override fun onSuccess(info: Void?) {
                            // search which preference is updated
                            val count = mDevicesListSettingsCategory.preferenceCount

                            for (i in 0 until count) {
                                val pref = mDevicesListSettingsCategory.getPreference(i) as VectorCustomActionEditTextPreference

                                if (TextUtils.equals(aDeviceInfoToRename.device_id, pref.title)) {
                                    pref.summary = input.text
                                }
                            }

                            // detect if the updated device is the current account one
                            val pref = findPreference(PreferencesManager.SETTINGS_ENCRYPTION_INFORMATION_DEVICE_ID_PREFERENCE_KEY)
                            if (TextUtils.equals(pref.summary, aDeviceInfoToRename.device_id)) {
                                findPreference(PreferencesManager.SETTINGS_ENCRYPTION_INFORMATION_DEVICE_ID_PREFERENCE_KEY).summary = input.text
                            }

                            hideLoadingView()
                        }

                        override fun onNetworkError(e: Exception) {
                            onCommonDone(e.localizedMessage)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            onCommonDone(e.localizedMessage)
                        }

                        override fun onUnexpectedError(e: Exception) {
                            onCommonDone(e.localizedMessage)
                        }
                    })
                }
                .setNegativeButton(R.string.cancel) { dialog, which -> dialog.cancel() }
                .show()
    }

    /**
     * Try to delete a device.
     *
     * @param deviceId the device id
     */
    private fun deleteDevice(deviceId: String) {
        displayLoadingView()
        mSession.deleteDevice(deviceId, mAccountPassword, object : ApiCallback<Void> {
            override fun onSuccess(info: Void?) {
                hideLoadingView()
                refreshDevicesList() // force settings update
            }

            private fun onError(message: String) {
                mAccountPassword = null
                onCommonDone(message)
            }

            override fun onNetworkError(e: Exception) {
                onError(e.localizedMessage)
            }

            override fun onMatrixError(e: MatrixError) {
                onError(e.localizedMessage)
            }

            override fun onUnexpectedError(e: Exception) {
                onError(e.localizedMessage)
            }
        })
    }

    /**
     * Display a delete confirmation dialog to remove a device.<br></br>
     * The user is invited to enter his password to confirm the deletion.
     *
     * @param aDeviceInfoToDelete device info
     */
    private fun displayDeviceDeletionDialog(aDeviceInfoToDelete: DeviceInfo?) {
        if (null != aDeviceInfoToDelete && null != aDeviceInfoToDelete.device_id) {
            if (!TextUtils.isEmpty(mAccountPassword)) {
                deleteDevice(aDeviceInfoToDelete.device_id)
            } else {
                val inflater = activity.layoutInflater
                val layout = inflater.inflate(R.layout.devices_settings_delete, null)
                val passwordEditText = layout.findViewById<EditText>(R.id.delete_password)

                AlertDialog.Builder(activity)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.devices_delete_dialog_title)
                        .setView(layout)

                        .setPositiveButton(R.string.devices_delete_submit_button_label, DialogInterface.OnClickListener { dialog, which ->
                            if (TextUtils.isEmpty(passwordEditText.toString())) {
                                // FIXME Hardcoded string
                                activity.applicationContext.toast("Password missing..")
                                return@OnClickListener
                            }
                            mAccountPassword = passwordEditText.text.toString()
                            deleteDevice(aDeviceInfoToDelete.device_id)
                        })
                        .setNegativeButton(R.string.cancel) { dialog, which ->
                            dialog.dismiss()
                            hideLoadingView()
                        }
                        .setOnKeyListener(DialogInterface.OnKeyListener { dialog, keyCode, event ->
                            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                                dialog.cancel()
                                hideLoadingView()
                                return@OnKeyListener true
                            }
                            false
                        })
                        .show()
            }
        } else {
            Log.e(LOG_TAG, "## displayDeviceDeletionDialog(): sanity check failure")
        }
    }

    /**
     * Manage the e2e keys export.
     */
    private fun exportKeys() {
        val dialogLayout = activity.layoutInflater.inflate(R.layout.dialog_export_e2e_keys, null)
        val builder = AlertDialog.Builder(activity)
                .setTitle(R.string.encryption_export_room_keys)
                .setView(dialogLayout)

        val passPhrase1EditText = dialogLayout.findViewById<TextInputEditText>(R.id.dialog_e2e_keys_passphrase_edit_text)
        val passPhrase2EditText = dialogLayout.findViewById<TextInputEditText>(R.id.dialog_e2e_keys_confirm_passphrase_edit_text)
        val exportButton = dialogLayout.findViewById<Button>(R.id.dialog_e2e_keys_export_button)
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                exportButton.isEnabled = !TextUtils.isEmpty(passPhrase1EditText.text) && TextUtils.equals(passPhrase1EditText.text, passPhrase2EditText.text)
            }

            override fun afterTextChanged(s: Editable) {

            }
        }

        passPhrase1EditText.addTextChangedListener(textWatcher)
        passPhrase2EditText.addTextChangedListener(textWatcher)

        exportButton.isEnabled = false

        val exportDialog = builder.show()

        exportButton.setOnClickListener {
            displayLoadingView()

            CommonActivityUtils.exportKeys(mSession, passPhrase1EditText.text.toString(), object : ApiCallback<String> {
                override fun onSuccess(filename: String) {
                    Toast.makeText(VectorApp.getInstance().applicationContext, filename, Toast.LENGTH_SHORT).show()
                    hideLoadingView()
                }

                override fun onNetworkError(e: Exception) {
                    hideLoadingView()
                }

                override fun onMatrixError(e: MatrixError) {
                    hideLoadingView()
                }

                override fun onUnexpectedError(e: Exception) {
                    hideLoadingView()
                }
            })

            exportDialog.dismiss()
        }
    }

    /**
     * Manage the e2e keys import.
     */
    @SuppressLint("NewApi")
    private fun importKeys() {
        val fileIntent = Intent(Intent.ACTION_GET_CONTENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        fileIntent.type = "*/*"
        startActivityForResult(fileIntent, REQUEST_E2E_FILE_REQUEST_CODE)
    }

    /**
     * Manage the e2e keys import.
     *
     * @param intent the intent result
     */
    private fun importKeys(intent: Intent?) {
        // sanity check
        if (null == intent) {
            return
        }

        val sharedDataItems = ArrayList(RoomMediaMessage.listRoomMediaMessages(intent))

        if (sharedDataItems.size > 0) {
            val sharedDataItem = sharedDataItems[0]
            val dialogLayout = activity.layoutInflater.inflate(R.layout.dialog_import_e2e_keys, null)
            val builder = AlertDialog.Builder(activity)
                    .setTitle(R.string.encryption_import_room_keys)
                    .setView(dialogLayout)

            val passPhraseEditText = dialogLayout.findViewById<TextInputEditText>(R.id.dialog_e2e_keys_passphrase_edit_text)
            val importButton = dialogLayout.findViewById<Button>(R.id.dialog_e2e_keys_import_button)

            passPhraseEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    importButton.isEnabled = !TextUtils.isEmpty(passPhraseEditText.text)
                }

                override fun afterTextChanged(s: Editable) {

                }
            })

            importButton.isEnabled = false

            val importDialog = builder.show()
            val appContext = activity.applicationContext

            importButton.setOnClickListener(View.OnClickListener {
                val password = passPhraseEditText.text.toString()
                val resource = ResourceUtils.openResource(appContext, sharedDataItem.uri, sharedDataItem.getMimeType(appContext))

                val data: ByteArray

                try {
                    data = ByteArray(resource.mContentStream.available())
                    resource.mContentStream.read(data)
                    resource.mContentStream.close()
                } catch (e: Exception) {
                    try {
                        resource.mContentStream.close()
                    } catch (e2: Exception) {
                        Log.e(LOG_TAG, "## importKeys() : " + e2.message)
                    }

                    Toast.makeText(appContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    return@OnClickListener
                }

                displayLoadingView()

                mSession.crypto.importRoomKeys(data, password, object : ApiCallback<Void> {
                    override fun onSuccess(info: Void?) {
                        hideLoadingView()
                    }

                    override fun onNetworkError(e: Exception) {
                        Toast.makeText(appContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
                        hideLoadingView()
                    }

                    override fun onMatrixError(e: MatrixError) {
                        Toast.makeText(appContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
                        hideLoadingView()
                    }

                    override fun onUnexpectedError(e: Exception) {
                        Toast.makeText(appContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
                        hideLoadingView()
                    }
                })

                importDialog.dismiss()
            })
        }
    }

//==============================================================================================================
// Group flairs management
//==============================================================================================================

    /**
     * Force the refresh of the devices list.<br></br>
     * The devices list is the list of the devices where the user as looged in.
     * It can be any mobile device, as any browser.
     */
    private fun refreshGroupFlairsList() {
        // display a spinner while refreshing
        if (0 == mGroupsFlairCategory.preferenceCount) {
            val preference = ProgressBarPreference(activity)
            mGroupsFlairCategory.addPreference(preference)
        }

        mSession.groupsManager.getUserPublicisedGroups(mSession.myUserId, true, object : ApiCallback<Set<String>> {
            override fun onSuccess(publicisedGroups: Set<String>) {
                buildGroupsList(publicisedGroups)
            }

            override fun onNetworkError(e: Exception) {
                // NOP
            }

            override fun onMatrixError(e: MatrixError) {
                // NOP
            }

            override fun onUnexpectedError(e: Exception) {
                // NOP
            }
        })
    }

    /**
     * Build the groups list.
     *
     * @param publicisedGroups the publicised groups list.
     */
    private fun buildGroupsList(publicisedGroups: Set<String>) {
        var isNewList = true

        if (null != mPublicisedGroups && mPublicisedGroups!!.size == publicisedGroups.size) {
            isNewList = !mPublicisedGroups!!.containsAll(publicisedGroups)
        }

        if (isNewList) {
            val joinedGroups = ArrayList(mSession.groupsManager.joinedGroups)
            Collections.sort(joinedGroups, Group.mGroupsComparator)

            var prefIndex = 0
            mPublicisedGroups = publicisedGroups.toMutableSet()

            // clear everything
            mGroupsFlairCategory.removeAll()

            for (group in joinedGroups) {
                val vectorGroupPreference = VectorGroupPreference(activity)
                vectorGroupPreference.key = DEVICES_PREFERENCE_KEY_BASE + prefIndex
                prefIndex++

                vectorGroupPreference.setGroup(group, mSession)
                vectorGroupPreference.title = group.displayName
                vectorGroupPreference.summary = group.groupId

                vectorGroupPreference.isChecked = publicisedGroups.contains(group.groupId)
                mGroupsFlairCategory.addPreference(vectorGroupPreference)

                vectorGroupPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    if (newValue is Boolean) {
                        val isFlaired = mPublicisedGroups!!.contains(group.groupId)

                        if (newValue != isFlaired) {
                            displayLoadingView()
                            mSession.groupsManager.updateGroupPublicity(group.groupId, newValue, object : ApiCallback<Void> {
                                override fun onSuccess(info: Void?) {
                                    hideLoadingView()
                                    if (newValue) {
                                        mPublicisedGroups!!.add(group.groupId)
                                    } else {
                                        mPublicisedGroups!!.remove(group.groupId)
                                    }
                                }

                                private fun onError() {
                                    hideLoadingView()
                                    // restore default value
                                    vectorGroupPreference.isChecked = publicisedGroups.contains(group.groupId)
                                }

                                override fun onNetworkError(e: Exception) {
                                    onError()
                                }

                                override fun onMatrixError(e: MatrixError) {
                                    onError()
                                }

                                override fun onUnexpectedError(e: Exception) {
                                    onError()
                                }
                            })
                        }
                    }
                    true
                }

            }

            refreshCryptographyPreference(mMyDeviceInfo)
        }
    }

/* ==========================================================================================
 * Companion
 * ========================================================================================== */

    companion object {
        private val LOG_TAG = VectorSettingsPreferencesFragment::class.java.simpleName

        // arguments indexes
        private const val ARG_MATRIX_ID = "VectorSettingsPreferencesFragment.ARG_MATRIX_ID"

        private const val EMAIL_PREFERENCE_KEY_BASE = "EMAIL_PREFERENCE_KEY_BASE"
        private const val PHONE_NUMBER_PREFERENCE_KEY_BASE = "PHONE_NUMBER_PREFERENCE_KEY_BASE"
        private const val PUSHER_PREFERENCE_KEY_BASE = "PUSHER_PREFERENCE_KEY_BASE"
        private const val DEVICES_PREFERENCE_KEY_BASE = "DEVICES_PREFERENCE_KEY_BASE"
        private const val IGNORED_USER_KEY_BASE = "IGNORED_USER_KEY_BASE"
        private const val ADD_EMAIL_PREFERENCE_KEY = "ADD_EMAIL_PREFERENCE_KEY"
        private const val ADD_PHONE_NUMBER_PREFERENCE_KEY = "ADD_PHONE_NUMBER_PREFERENCE_KEY"
        private const val APP_INFO_LINK_PREFERENCE_KEY = "application_info_link"

        private const val DUMMY_RULE = "DUMMY_RULE"
        private const val LABEL_UNAVAILABLE_DATA = "none"

        private const val REQUEST_E2E_FILE_REQUEST_CODE = 123
        private const val REQUEST_NEW_PHONE_NUMBER = 456
        private const val REQUEST_PHONEBOOK_COUNTRY = 789
        private const val REQUEST_LOCALE = 777
        private const val REQUEST_NOTIFICATION_RINGTONE = 888

        // rule Id <-> preference name
        private var mPushesRuleByResourceId = mapOf(
                PreferencesManager.SETTINGS_ENABLE_ALL_NOTIF_PREFERENCE_KEY to BingRule.RULE_ID_DISABLE_ALL,
                PreferencesManager.SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY to DUMMY_RULE,
                PreferencesManager.SETTINGS_TURN_SCREEN_ON_PREFERENCE_KEY to DUMMY_RULE,
                PreferencesManager.SETTINGS_CONTAINING_MY_DISPLAY_NAME_PREFERENCE_KEY to BingRule.RULE_ID_CONTAIN_DISPLAY_NAME,
                PreferencesManager.SETTINGS_CONTAINING_MY_USER_NAME_PREFERENCE_KEY to BingRule.RULE_ID_CONTAIN_USER_NAME,
                PreferencesManager.SETTINGS_MESSAGES_IN_ONE_TO_ONE_PREFERENCE_KEY to BingRule.RULE_ID_ONE_TO_ONE_ROOM,
                PreferencesManager.SETTINGS_MESSAGES_IN_GROUP_CHAT_PREFERENCE_KEY to BingRule.RULE_ID_ALL_OTHER_MESSAGES_ROOMS,
                PreferencesManager.SETTINGS_INVITED_TO_ROOM_PREFERENCE_KEY to BingRule.RULE_ID_INVITE_ME,
                PreferencesManager.SETTINGS_CALL_INVITATIONS_PREFERENCE_KEY to BingRule.RULE_ID_CALL,
                PreferencesManager.SETTINGS_MESSAGES_SENT_BY_BOT_PREFERENCE_KEY to BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS
        )

        // static constructor
        fun newInstance(matrixId: String) = VectorSettingsPreferencesFragment()
                .apply {
                    arguments = Bundle().apply { putString(ARG_MATRIX_ID, matrixId) }
                }
    }
}
