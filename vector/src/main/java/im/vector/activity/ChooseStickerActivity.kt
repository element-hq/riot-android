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

package im.vector.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import com.google.gson.Gson
import im.vector.R
import im.vector.types.JsonDict
import im.vector.util.ThemeUtils
import org.matrix.androidsdk.util.Log
import java.net.URLEncoder

class ChooseStickerActivity : AbstractWidgetActivity() {

    /* ==========================================================================================
     * DATA
     * ========================================================================================== */

    private lateinit var mWidgetUrl: String
    private lateinit var mWidgetId: String

    /* ==========================================================================================
     * IMPLEMENT METHODS
     * ========================================================================================== */

    override fun getLayoutRes() = R.layout.activity_choose_sticker

    override fun getTitleRes() = R.string.title_activity_choose_sticker

    override fun initUiAndData() {
        mWidgetUrl = intent.getStringExtra(EXTRA_WIDGET_URL)
        mWidgetId = intent.getStringExtra(EXTRA_WIDGET_ID)

        configureToolbar()

        super.initUiAndData()
    }

    /**
     * Compute the URL
     *
     * @return the URL
     */
    override fun buildInterfaceUrl(scalarToken: String): String? {
        try {
            return mWidgetUrl + "?" +
                    "scalar_token=" + URLEncoder.encode(scalarToken, "utf-8") +
                    "&room_id=" + URLEncoder.encode(mRoom!!.roomId, "utf-8") +
                    "&widgetId=" + URLEncoder.encode(mWidgetId, "utf-8")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## buildInterfaceUrl() failed " + e.message)
        }

        return null
    }

    /**
     * A Widget message has been received, deals with it and send the response
     */
    override fun dealsWithWidgetRequest(eventData: JsonDict<Any>): Boolean {
        val action = eventData["action"] as String?

        when (action) {
            "m.sticker" -> sendSticker(eventData)
                    .also { return true }
        }

        return false
    }

    /* ==========================================================================================
     * MENU
     * ========================================================================================== */

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.vector_choose_sticker, menu)

        // TODO Maintenance: this should be done in the parent Activity
        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(this, R.attr.icon_tint_on_dark_action_bar_color))

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_settings -> {
                val intent = IntegrationManagerActivity.getIntent(context = this,
                        matrixId = mSession!!.myUserId,
                        roomId = mRoom!!.roomId,
                        widgetId = mWidgetId,
                        screenId = "type_$WIDGET_NAME")

                startActivity(intent)

                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    /* ==========================================================================================
     * Private methods
     * ========================================================================================== */

    private fun sendSticker(eventData: JsonDict<Any>) {
        Log.d(LOG_TAG, "Received request send sticker")

        val data = eventData["data"]

        if (data == null) {
            sendError(getString(R.string.widget_integration_missing_parameter), eventData)
            return
        }

        val content = (data as JsonDict<Any>)["content"]

        if (content == null) {
            sendError(getString(R.string.widget_integration_missing_parameter), eventData)
            return
        }

        val json = Gson().toJson(content)

        // Send the response to be polite (since the Activity will be finished)
        sendSuccess(eventData)

        // Ok send the result back to the calling Activity
        val intent = Intent().apply {
            // Serialize the JSON object
            putExtra(EXTRA_OUT_CONTENT, json)
        }

        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    /* ==========================================================================================
     * companion
     * ========================================================================================== */

    companion object {
        private val LOG_TAG = ChooseStickerActivity::class.java.simpleName

        /* ==========================================================================================
         * Const
         * ========================================================================================== */

        const val WIDGET_NAME = "m.stickerpicker"

        /* ==========================================================================================
         * Parameters
         * ========================================================================================== */

        private const val EXTRA_WIDGET_URL = "EXTRA_WIDGET_URL"
        private const val EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID"

        fun getIntent(context: Context, matrixId: String, roomId: String, widgetUrl: String, widgetId: String): Intent {
            return Intent(context, ChooseStickerActivity::class.java)
                    .apply {
                        putExtra(EXTRA_MATRIX_ID, matrixId)
                        putExtra(EXTRA_ROOM_ID, roomId)
                        putExtra(EXTRA_WIDGET_URL, widgetUrl)
                        putExtra(EXTRA_WIDGET_ID, widgetId)
                    }
        }

        /* ==========================================================================================
         * Result
         * ========================================================================================== */

        private const val EXTRA_OUT_CONTENT = "EXTRA_OUT_CONTENT"

        fun getResultContent(intent: Intent): String {
            return intent.getStringExtra(EXTRA_OUT_CONTENT)
        }
    }
}