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

    init {
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
                R.string.room_widget_permission_ip_address,
                R.string.room_widget_permission_useragent,
                R.string.room_widget_permission_widget_id,
                R.string.room_widget_permission_room_id,
                R.string.room_widget_permission_matrix_profile
        )
        setState {
            copy(
                    authorName = creator?.displayname,
                    authorId = widget.widgetEvent.sender,
                    authorAvatarUrl = creator?.getAvatarUrl(),
                    widgetDomain = domain,
                    permissionsList = infoShared
            )
        }
    }

    fun allowWidget(onFinished: (() -> Unit)) {
        session.integrationManager.setWidgetAllowed(widget.widgetEvent?.eventId
                ?: "", true, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                onFinished()
            }

            override fun onUnexpectedError(e: Exception?) {
                //TODO.. make the button with a loading state?
            }

            override fun onNetworkError(e: Exception?) {
                //TODO.. make the button with a loading state?
            }

            override fun onMatrixError(e: MatrixError?) {
                //TODO.. make the button with a loading state?
            }

        })
    }

    fun blockWidget(onFinished: (() -> Unit)) {
        session.integrationManager.setWidgetAllowed(widget.widgetEvent?.eventId
                ?: "", false, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                onFinished()
            }

            override fun onUnexpectedError(e: Exception?) {
                //TODO.. make the button with a loading state?
            }

            override fun onNetworkError(e: Exception?) {
                //TODO.. make the button with a loading state?
            }

            override fun onMatrixError(e: MatrixError?) {
                //TODO.. make the button with a loading state?
            }

        })
    }

    companion object : MvRxViewModelFactory<RoomWidgetPermissionViewModel, RoomWidgetPermissionViewState> {

        override fun create(viewModelContext: ViewModelContext, state: RoomWidgetPermissionViewState): RoomWidgetPermissionViewModel? {
            val args = viewModelContext.args<RoomWidgetPermissionBottomSheet.FragArgs>()
            val session = Matrix.getMXSession(viewModelContext.activity, args.mxId)
            return RoomWidgetPermissionViewModel(session, args.widget, state)
        }

    }
}