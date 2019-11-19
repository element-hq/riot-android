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
package im.vector.fragments.roomwidgets

import com.airbnb.mvrx.BaseMvRxViewModel
import com.airbnb.mvrx.MvRxState
import com.airbnb.mvrx.MvRxViewModelFactory
import com.airbnb.mvrx.ViewModelContext
import im.vector.Matrix
import im.vector.R
import im.vector.widgets.Widget
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import java.net.URL

data class RoomWidgetPermissionViewState(
        val authorId: String = "",
        val authorAvatarUrl: String? = null,
        val authorName: String? = null,
        val widgetDomain: String? = null,
        //List of @StringRes
        val permissionsList: List<Int>? = null
) : MvRxState

class RoomWidgetPermissionViewModel(val session: MXSession, val widget: Widget, initialState: RoomWidgetPermissionViewState)
    : BaseMvRxViewModel<RoomWidgetPermissionViewState>(initialState, false) {

    fun allowWidget(onFinished: (() -> Unit)? = null) {
        session.integrationManager.setWidgetAllowed(widget.widgetEvent?.eventId
                ?: "", true, object : ApiCallback<Void?> {

            override fun onSuccess(info: Void?) {
                onFinished?.invoke()
            }

            override fun onUnexpectedError(e: Exception) {
                Log.e(LOG_TAG, e.message)
            }

            override fun onNetworkError(e: Exception) {
                Log.e(LOG_TAG, e.message)
            }

            override fun onMatrixError(e: MatrixError) {
                Log.e(LOG_TAG, e.message)
            }

        })
    }

    fun blockWidget(onFinished: (() -> Unit)? = null) {
        session.integrationManager.setWidgetAllowed(widget.widgetEvent?.eventId
                ?: "", false, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                onFinished?.invoke()
            }

            override fun onUnexpectedError(e: Exception) {
                Log.e(LOG_TAG, e.message)
            }

            override fun onNetworkError(e: Exception) {
                Log.e(LOG_TAG, e.message)
            }

            override fun onMatrixError(e: MatrixError) {
                Log.e(LOG_TAG, e.message)
            }

        })
    }

    companion object : MvRxViewModelFactory<RoomWidgetPermissionViewModel, RoomWidgetPermissionViewState> {

        val LOG_TAG = RoomWidgetPermissionViewModel::class.simpleName

        override fun create(viewModelContext: ViewModelContext, state: RoomWidgetPermissionViewState): RoomWidgetPermissionViewModel? {
            val args = viewModelContext.args<RoomWidgetPermissionBottomSheet.FragArgs>()
            val session = Matrix.getMXSession(viewModelContext.activity, args.mxId)
            return RoomWidgetPermissionViewModel(session, args.widget, state)
        }

        override fun initialState(viewModelContext: ViewModelContext): RoomWidgetPermissionViewState? {
            val args = viewModelContext.args<RoomWidgetPermissionBottomSheet.FragArgs>()
            val session = Matrix.getMXSession(viewModelContext.activity, args.mxId)
            val widget = args.widget

            val room = session.dataHandler.getRoom(widget.roomId)
            val creator = room.getMember(widget.widgetEvent.sender)

            var domain: String?
            try {
                domain = URL(widget.url).host
            } catch (e: Throwable) {
                domain = null
            }

            //TODO check from widget urls the perms that should be shown?
            //For now put all
            val infoShared = listOf<Int>(
                    R.string.room_widget_permission_display_name,
                    R.string.room_widget_permission_avatar_url,
                    R.string.room_widget_permission_user_id,
                    R.string.room_widget_permission_theme,
                    R.string.room_widget_permission_widget_id,
                    R.string.room_widget_permission_room_id
            )
            return RoomWidgetPermissionViewState(
                    authorName = creator?.displayname,
                    authorId = widget.widgetEvent.sender,
                    authorAvatarUrl = creator?.getAvatarUrl(),
                    widgetDomain = domain,
                    permissionsList = infoShared
            )
        }
    }
}