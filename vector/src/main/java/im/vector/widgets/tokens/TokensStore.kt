/*
 * Copyright 2019 New Vector Ltd
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

package im.vector.widgets.tokens

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.matrix.androidsdk.core.JsonUtils

class TokensStore(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson = JsonUtils.getBasicGson()

    private data class TokensStore(
            // Keys are user Id
            @JvmField
            val userToServerTokens: MutableMap<String, ServerTokens> = mutableMapOf()
    )

    private data class ServerTokens(
            // Keys are server Url, values are token
            @JvmField
            val serverTokens: MutableMap<String, String> = mutableMapOf()
    )

    fun getToken(userId: String, serverUrl: String): String? {
        handleMigration(userId)

        return readStore()
                .userToServerTokens[userId]
                ?.serverTokens
                ?.get(serverUrl)
    }

    private fun handleMigration(userId: String) {
        val prefKey = SCALAR_TOKEN_LEGACY_PREFERENCE_KEY + userId

        val previousStoredToken = prefs.getString(prefKey, null)

        if (!previousStoredToken.isNullOrBlank()) {
            // It was maybe a token for scalar.vector.im. If it is not the case, it will be invalid and will be replaced.
            setToken(userId, "https://scalar.vector.im/api", previousStoredToken)

            prefs.edit {
                remove(prefKey)
            }
        }
    }

    fun setToken(userId: String, serverUrl: String, token: String) {
        readStore()
                .apply {
                    userToServerTokens.getOrPut(userId) { ServerTokens() }
                            .serverTokens[serverUrl] = token
                }
                .commit()
    }

    private fun readStore(): TokensStore {
        return prefs.getString(SCALAR_TOKENS_PREFERENCE_KEY, null)
                ?.toModel()
                ?: TokensStore()
    }

    private fun TokensStore.commit() {
        prefs.edit {
            putString(SCALAR_TOKENS_PREFERENCE_KEY, this@commit.fromModel())
        }
    }

    fun clear() {
        prefs.edit {
            remove(SCALAR_TOKENS_PREFERENCE_KEY)
        }
    }

    private fun String.toModel(): TokensStore? {
        return gson.fromJson<TokensStore>(this, TokensStore::class.java)
    }

    private fun TokensStore.fromModel(): String? {
        return gson.toJson(this)
    }

    companion object {
        private const val SCALAR_TOKEN_LEGACY_PREFERENCE_KEY = "SCALAR_TOKEN_PREFERENCE_KEY"

        private const val SCALAR_TOKENS_PREFERENCE_KEY = "SCALAR_TOKENS_PREFERENCE_KEY"
    }
}