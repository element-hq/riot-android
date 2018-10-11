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

package im.vector.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.Pair
import im.vector.R
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import org.matrix.androidsdk.util.Log
import java.util.*

/**
 * Object to manage the Locale choice of the user
 */
object VectorLocale {
    private val LOG_TAG = VectorLocale.javaClass.simpleName

    // the supported application languages
    private val mApplicationLocales = HashSet<Locale>()

    private const val APPLICATION_LOCALE_COUNTRY_KEY = "APPLICATION_LOCALE_COUNTRY_KEY"
    private const val APPLICATION_LOCALE_VARIANT_KEY = "APPLICATION_LOCALE_VARIANT_KEY"
    private const val APPLICATION_LOCALE_LANGUAGE_KEY = "APPLICATION_LOCALE_LANGUAGE_KEY"

    private val mApplicationDefaultLanguage = Locale("en", "US")

    private lateinit var applicationLocal: Locale

    /**
     * Provides the current application locale
     *
     * @return the application locale
     */
    fun getApplicationLocale() = applicationLocal

    /**
     * Init this object
     */
    fun init(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        if (!preferences.contains(APPLICATION_LOCALE_LANGUAGE_KEY)) {
            applicationLocal = Locale.getDefault()

            // detect if the default language is used
            val defaultStringValue = getString(context, mApplicationDefaultLanguage, R.string.resources_country_code)
            if (TextUtils.equals(defaultStringValue, getString(context, applicationLocal, R.string.resources_country_code))) {
                applicationLocal = mApplicationDefaultLanguage
            }

            saveApplicationLocale(context, applicationLocal)
        } else {
            applicationLocal = Locale(preferences.getString(APPLICATION_LOCALE_LANGUAGE_KEY, ""),
                    preferences.getString(APPLICATION_LOCALE_COUNTRY_KEY, ""),
                    preferences.getString(APPLICATION_LOCALE_VARIANT_KEY, "")
            )
        }

        // init the known locales in background, using kotlin coroutines
        GlobalScope.launch {
            getApplicationLocales(context)
        }
    }

    /**
     * Save the new application locale.
     */
    fun saveApplicationLocale(context: Context, locale: Locale) {
        applicationLocal = locale

        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()

        val language = locale.language
        if (!TextUtils.isEmpty(language)) {
            editor.putString(APPLICATION_LOCALE_LANGUAGE_KEY, language)
        } else {
            editor.remove(APPLICATION_LOCALE_LANGUAGE_KEY)
        }

        val country = locale.country
        if (!TextUtils.isEmpty(country)) {
            editor.putString(APPLICATION_LOCALE_COUNTRY_KEY, country)
        } else {
            editor.remove(APPLICATION_LOCALE_COUNTRY_KEY)
        }

        val variant = locale.variant
        if (!TextUtils.isEmpty(variant)) {
            editor.putString(APPLICATION_LOCALE_VARIANT_KEY, variant)
        } else {
            editor.remove(APPLICATION_LOCALE_VARIANT_KEY)
        }

        editor.apply()
    }

    /**
     * Get String from a locale
     *
     * @param context    the context
     * @param locale     the locale
     * @param resourceId the string resource id
     * @return the localized string
     */
    private fun getString(context: Context, locale: Locale, resourceId: Int): String {
        var result: String

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            try {
                result = context.createConfigurationContext(config).getText(resourceId).toString()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "## getString() failed : " + e.message, e)
                // use the default one
                result = context.getString(resourceId)
            }

        } else {
            val resources = context.resources
            val conf = resources.configuration
            val savedLocale = conf.locale
            conf.locale = locale
            resources.updateConfiguration(conf, null)

            // retrieve resources from desired locale
            result = resources.getString(resourceId)

            // restore original locale
            conf.locale = savedLocale
            resources.updateConfiguration(conf, null)
        }

        return result
    }

    /**
     * Provides the supported application locales list
     *
     * @param context the context
     * @return the supported application locales list
     */
    @Synchronized
    fun getApplicationLocales(context: Context): List<Locale> {
        if (mApplicationLocales.isEmpty()) {
            val knownLocalesSet = HashSet<Pair<String, String>>()

            try {
                val availableLocales = Locale.getAvailableLocales()

                for (locale in availableLocales) {
                    knownLocalesSet.add(Pair(getString(context, locale, R.string.resources_language),
                            getString(context, locale, R.string.resources_country_code)))
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "## getApplicationLocales() : failed " + e.message, e)
                knownLocalesSet.add(Pair(context.getString(R.string.resources_language), context.getString(R.string.resources_country_code)))
            }

            for (knownLocale in knownLocalesSet) {
                mApplicationLocales.add(Locale(knownLocale.first, knownLocale.second))
            }
        }

        val sortedLocalesList = ArrayList(mApplicationLocales)

        // sort by human display names
        sortedLocalesList.sortWith(Comparator { lhs, rhs -> localeToLocalisedString(lhs).compareTo(localeToLocalisedString(rhs)) })

        return sortedLocalesList
    }

    /**
     * Convert a locale to a string
     *
     * @param locale the locale to convert
     * @return the string
     */
    fun localeToLocalisedString(locale: Locale): String {
        var res = locale.getDisplayLanguage(locale)

        if (!TextUtils.isEmpty(locale.getDisplayCountry(locale))) {
            res += " (" + locale.getDisplayCountry(locale) + ")"
        }

        return res
    }
}

