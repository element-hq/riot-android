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

import android.content.Context
import android.text.TextUtils
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.MutableLiveData
import com.airbnb.mvrx.*
import im.vector.Matrix
import im.vector.R
import im.vector.VectorApp
import im.vector.activity.WidgetActivity
import im.vector.ui.arch.LiveEvent
import im.vector.widgets.Widget
import im.vector.widgets.WidgetsManager
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.features.integrationmanager.IntegrationManager
import org.matrix.androidsdk.features.terms.TermsNotSignedException

enum class WidgetState {
    UNKNOWN,
    WIDGET_NOT_ALLOWED,
    WIDGET_ALLOWED
}

data class RoomWidgetViewModelState(
        val status: WidgetState = WidgetState.UNKNOWN,
        val formattedURL: Async<String> = Uninitialized,
        val webviewLoadedUrl: Async<String> = Uninitialized,
        val widgetName: String = "",
        val canManageWidgets: Boolean = false,
        val createdByMe: Boolean = false
) : MvRxState

class RoomWidgetViewModel(initialState: RoomWidgetViewModelState, val widget: Widget)
    : BaseMvRxViewModel<RoomWidgetViewModelState>(initialState, false) {

    companion object : MvRxViewModelFactory<RoomWidgetViewModel, RoomWidgetViewModelState> {
        const val NAVIGATE_FINISH = "NAVIGATE_FINISH"

        override fun create(viewModelContext: ViewModelContext, state: RoomWidgetViewModelState): RoomWidgetViewModel? {
            return (viewModelContext.activity.intent?.extras?.getSerializable(WidgetActivity.EXTRA_WIDGET_ID) as? Widget)?.let {
                RoomWidgetViewModel(state, it)
            } ?: super.create(viewModelContext, state)
        }

        override fun initialState(viewModelContext: ViewModelContext): RoomWidgetViewModelState? {
            val widget = viewModelContext.activity.intent?.extras?.getSerializable(WidgetActivity.EXTRA_WIDGET_ID) as? Widget
                    ?: return null
            val session = Matrix.getInstance(viewModelContext.activity).getSession(widget.sessionId)
            return RoomWidgetViewModelState(
                    widgetName = widget.humanName,
                    createdByMe = widget.widgetEvent.getSender() == session?.myUserId
            )
        }

    }

    var navigateEvent: MutableLiveData<LiveEvent<String>> = MutableLiveData()
    var termsNotSignedEvent: MutableLiveData<LiveEvent<TermsNotSignedException>> = MutableLiveData()
    var loadWebURLEvent: MutableLiveData<LiveEvent<String>> = MutableLiveData()
    var toastMessageEvent: MutableLiveData<LiveEvent<String>> = MutableLiveData()

    private var room: Room? = null

    var session: MXSession? = null

    var widgetsManager: WidgetsManager? = null

    /**
     * Widget events listener
     */
    private val mWidgetListener = WidgetsManager.onWidgetUpdateListener { widget ->
        if (TextUtils.equals(widget.widgetId, widget.widgetId)) {
            if (!widget.isActive) {
                doFinish()
            }
        }
    }

    init {
        configure()

        session?.integrationManager?.addListener(object : IntegrationManager.IntegrationManagerManagerListener {
            override fun onIntegrationManagerChange(managerConfig: IntegrationManager) {
                refreshPermissionStatus()
            }

        })
    }

    fun webviewStartedToLoad(url: String?) = withState {
        //Only do it for first load
        setState {
            copy(webviewLoadedUrl = Loading())
        }
    }

    fun webviewLoadingError(url: String?, reason: String?) = withState {
        setState {
            copy(webviewLoadedUrl = Fail(Throwable(reason)))
        }
    }

    fun webviewLoadSuccess(url: String?) = withState {
        setState {
            copy(webviewLoadedUrl = Success(url ?: ""))
        }
    }

    private fun configure() {

        val applicationContext = VectorApp.getInstance().applicationContext
        val matrix = Matrix.getInstance(applicationContext)

        session = matrix.getSession(widget.sessionId)

        if (session == null) {
            //defensive code
            doFinish()
            return
        }

        room = session?.dataHandler?.getRoom(widget.roomId)

        if (room == null) {
            //defensive code
            doFinish()
            return
        }

        widgetsManager = matrix
                .getWidgetManagerProvider(session)?.getWidgetManager(applicationContext)

        setState {
            copy(canManageWidgets = WidgetsManager.checkWidgetPermission(session, room) == null)
        }


        widgetsManager?.addListener(mWidgetListener)

        refreshPermissionStatus(applicationContext)
    }

    private fun refreshPermissionStatus(applicationContext: Context = VectorApp.getInstance().applicationContext) {

        //If it was added by me, consider it as allowed
        if (widget.widgetEvent.getSender() == session?.myUserId) {
            onWidgetAllowed(applicationContext)
            return
        }

        val isAllowed = session
                ?.integrationManager
                ?.isWidgetAllowed(widget.widgetEvent.eventId)
                ?: false

        if (!isAllowed) {
            setState {
                copy(status = WidgetState.WIDGET_NOT_ALLOWED)
            }
        } else {
            //we can start loading the widget then
            onWidgetAllowed(applicationContext)
        }
    }


    fun doCloseWidget(context: Context) {
        AlertDialog.Builder(context)
                .setMessage(R.string.widget_delete_message_confirmation)
                .setPositiveButton(R.string.remove) { _, _ ->

                    widgetsManager?.closeWidget(session, room, widget.widgetId, object : ApiCallback<Void> {
                        override fun onSuccess(info: Void?) {
                            doFinish()
                        }

                        private fun onError(errorMessage: String) {
                            toastMessageEvent.postValue(LiveEvent(errorMessage))
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
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    fun doFinish() {
        navigateEvent.postValue(LiveEvent(NAVIGATE_FINISH))
    }

    fun refreshAfterTermsAccepted() {
        onWidgetAllowed()
    }

    private fun onWidgetAllowed(applicationContext: Context = VectorApp.getInstance().applicationContext) {

        setState {
            copy(
                    status = WidgetState.WIDGET_ALLOWED,
                    formattedURL = Loading()
            )
        }
        if (widgetsManager != null) {
            widgetsManager!!.getFormattedWidgetUrl(applicationContext, widget, object : ApiCallback<String> {
                override fun onSuccess(url: String) {
                    loadWebURLEvent.postValue(LiveEvent(url))
                    setState {
                        //We use a live event to trigger the webview load
                        copy(
                                status = WidgetState.WIDGET_ALLOWED,
                                formattedURL = Success(url)
                        )
                    }
                }

                private fun onError(errorMessage: String) {
                    setState {
                        copy(
                                status = WidgetState.WIDGET_ALLOWED,
                                formattedURL = Fail(Throwable(errorMessage))
                        )
                    }
                }

                override fun onNetworkError(e: Exception) {
                    onError(e.localizedMessage)
                }

                override fun onMatrixError(e: MatrixError) {
                    onError(e.localizedMessage)
                }

                override fun onUnexpectedError(e: Exception) {
                    if (e is TermsNotSignedException) {
                        termsNotSignedEvent.postValue(LiveEvent(e))
                    } else {
                        onError(e.localizedMessage)
                    }
                }
            })
        } else {
            setState {
                copy(
                        status = WidgetState.WIDGET_ALLOWED,
                        formattedURL = Success(widget.url)
                )
            }
            loadWebURLEvent.postValue(LiveEvent(widget.url))
        }
    }

    fun revokeWidget(onFinished: (() -> Unit)? = null) {
        setState {
            copy(
                    status = WidgetState.UNKNOWN
            )
        }
        session?.integrationManager?.setWidgetAllowed(widget.widgetEvent?.eventId
                ?: "", false, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                onFinished?.invoke()
            }

            override fun onUnexpectedError(e: Exception) {
                Log.e(this::class.java.name, e.message)
            }

            override fun onNetworkError(e: Exception) {
                Log.e(this::class.java.name, e.message)
            }

            override fun onMatrixError(e: MatrixError) {
                Log.e(this::class.java.name, e.message)
            }

        })
    }

    override fun onCleared() {
        super.onCleared()
        widgetsManager?.removeListener(mWidgetListener)
    }


}