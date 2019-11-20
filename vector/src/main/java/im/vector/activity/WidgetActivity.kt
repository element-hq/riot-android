/*
 * Copyright 2017 Vector Creations Ltd
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.airbnb.mvrx.viewModel
import im.vector.R
import im.vector.fragments.roomwidgets.*
import im.vector.ui.themes.ThemeUtils
import im.vector.widgets.Widget

/*
 * This class displays a widget
 */
class WidgetActivity : VectorAppCompatActivity() {

    val viewModel: RoomWidgetViewModel by viewModel()

    /* ==========================================================================================
     * LIFE CYCLE
     * ========================================================================================== */

    override fun getLayoutRes() = R.layout.activity_widget

    override fun getMenuRes() = R.menu.menu_room_widget

    override fun getTitleRes() = R.string.room_widget_activity_title

    @SuppressLint("NewApi")
    override fun initUiAndData() {
        configureToolbar()

        supportActionBar?.setHomeAsUpIndicator(ContextCompat.getDrawable(this, R.drawable.ic_material_leave)?.let {
            ThemeUtils.tintDrawableWithColor(it, Color.WHITE)
        })

        viewModel.selectSubscribe(this, RoomWidgetViewModelState::status) { ws ->
            when (ws) {
                WidgetState.UNKNOWN            -> {
                }
                WidgetState.WIDGET_NOT_ALLOWED -> {
                    val dFrag = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_PERMISSION) as? RoomWidgetPermissionBottomSheet
                    if (dFrag != null && dFrag.dialog?.isShowing == true && !dFrag.isRemoving) {
                        //already there
                    } else {
                        RoomWidgetPermissionBottomSheet
                                .newInstance(viewModel.session!!.myUserId, viewModel.widget).apply {
                                    onFinish = { accepted ->
                                        if (!accepted) finish()
                                    }
                                }
                                .show(supportFragmentManager, FRAGMENT_TAG_PERMISSION)
                    }
                }
                WidgetState.WIDGET_ALLOWED     -> {
                    //mount the webview fragment if needed
                    if (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_WEBVIEW) == null) {
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, RoomWidgetFragment(), FRAGMENT_TAG_WEBVIEW)
                                .commit()
                    }
                }
            }
        }

        viewModel.selectSubscribe(this, RoomWidgetViewModelState::widgetName) { name ->
            supportActionBar?.title = name
        }

        viewModel.selectSubscribe(this, RoomWidgetViewModelState::canManageWidgets) {
            invalidateOptionsMenu()
        }

        viewModel.navigateEvent.observe(this, Observer { uxStateEvent ->
            when (uxStateEvent?.getContentIfNotHandled()) {
                RoomWidgetViewModel.NAVIGATE_FINISH -> {
                    finish()
                }
            }
        })

        viewModel.toastMessageEvent.observe(this, Observer {
            it?.getContentIfNotHandled()?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        })
    }

    /* ==========================================================================================
     * companion
     * ========================================================================================== */

    companion object {

        private const val FRAGMENT_TAG_PERMISSION = "FRAGMENT_TAG_PERMISSION"
        private const val FRAGMENT_TAG_WEBVIEW = "WebView"
        /**
         * The linked widget
         */
        const val EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID"

        fun getIntent(context: Context, widget: Widget): Intent {
            return Intent(context, WidgetActivity::class.java)
                    .apply {
                        putExtra(EXTRA_WIDGET_ID, widget)
                    }
        }
    }
}