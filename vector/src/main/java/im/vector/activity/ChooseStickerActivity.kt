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

import android.content.Context
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.core.widget.toast
import im.vector.R
import im.vector.types.ScalarEventData
import im.vector.util.ThemeUtils
import org.matrix.androidsdk.util.Log

class ChooseStickerActivity : IntegrationManagerActivity() {

    /* ==========================================================================================
     * DATA
     * ========================================================================================== */

    private lateinit var mWidgetUrl: String

    /* ==========================================================================================
     * IMPLEMENT METHODS
     * ========================================================================================== */

    override fun getLayoutRes() = R.layout.activity_choose_sticker

    override fun getTitleRes() = R.string.title_activity_choose_sticker

    override fun initUiAndData() {
        mWidgetUrl = intent.getStringExtra(EXTRA_WIDGET_URL)

        super.initUiAndData()

        configureToolbar()
    }

    override fun displayInFullscreen() = false

    override fun getBaseUrl() = mWidgetUrl

    /**
     * Manage the modular requests
     *
     * @param JSData the js data request
     */
    override fun onScalarMessage(JSData: ScalarEventData?) {
        if (null == JSData) {
            Log.e(LOG_TAG, "## onScalarMessage() : invalid JSData")
            return
        }

        val eventData = JSData["event.data"]

        if (null == eventData) {
            Log.e(LOG_TAG, "## onScalarMessage() : invalid JSData")
            return
        }

        // TODO BMA New Scalar event?
        super.onScalarMessage(JSData)
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
            R.id.menu_settings -> toast("Settings TODO").also { return true }
        }

        return super.onOptionsItemSelected(item)
    }

    /* ==========================================================================================
     * companion
     * ========================================================================================== */

    companion object {
        private val LOG_TAG = ChooseStickerActivity::class.java.simpleName

        /**
         * the parameters
         */
        private const val EXTRA_WIDGET_URL = "EXTRA_WIDGET_URL"

        fun getIntent(context: Context, matrixId: String, roomId: String, widgetUrl: String): Intent {
            return Intent(context, ChooseStickerActivity::class.java)
                    .apply {
                        putExtra(EXTRA_MATRIX_ID, matrixId)
                        putExtra(EXTRA_ROOM_ID, roomId)
                        putExtra(EXTRA_WIDGET_URL, widgetUrl)
                    }
        }
    }
}