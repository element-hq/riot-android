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

package im.vector.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.preference.PreferenceManager
import android.text.SpannableString
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.transition.TransitionManager
import butterknife.BindView
import butterknife.ButterKnife
import com.binaryfork.spanny.Spanny
import im.vector.R
import im.vector.features.hhs.ResourceLimitErrorFormatter
import im.vector.listeners.IMessagesAdapterActionsListener
import im.vector.ui.animation.VectorTransitionSet
import im.vector.ui.themes.ThemeUtils
import im.vector.util.MatrixURLSpan
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.rest.model.RoomTombstoneContent

private const val LOG_TAG = "NotificationAreaView"

/**
 * The view used in VectorRoomActivity to show some information
 * It does have a unique render method
 */
class NotificationAreaView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    @BindView(R.id.room_notification_icon)
    lateinit var imageView: ImageView
    @BindView(R.id.room_notification_message)
    lateinit var messageView: TextView

    var delegate: Delegate? = null
    private var state: State = State.Initial

    var scrollState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE
        set(value) {
            field = value

            val pendingV = pendingVisibility

            if (pendingV != null) {
                pendingVisibility = null
                visibility = pendingV
            }
        }

    private var pendingVisibility: Int? = null

    /**
     * Visibility when the info area is empty.
     * [View.VISIBLE] only when preference is set to [SHOW_INFO_AREA_VALUE_ALWAYS].
     */
    private val visibilityForEmptyContent: Int

    /**
     * Visibility when the info area has a non-error message or icon (for example scrolling icon).
     * [View.VISIBLE] only when preference is set to [SHOW_INFO_AREA_VALUE_ALWAYS] or [SHOW_INFO_AREA_VALUE_MESSAGES_AND_ERRORS].
     */
    private val visibilityForMessages: Int

    init {
        setupView()

        when (PreferenceManager.getDefaultSharedPreferences(context).getString(SHOW_INFO_AREA_KEY, SHOW_INFO_AREA_VALUE_ALWAYS)) {
            SHOW_INFO_AREA_VALUE_ALWAYS -> {
                visibilityForEmptyContent = View.VISIBLE
                visibilityForMessages = View.VISIBLE
            }
            SHOW_INFO_AREA_VALUE_MESSAGES_AND_ERRORS -> {
                visibilityForEmptyContent = View.GONE
                visibilityForMessages = View.VISIBLE
            }
            else /* SHOW_INFO_AREA_VALUE_ONLY_ERRORS */ -> {
                visibilityForEmptyContent = View.GONE
                visibilityForMessages = View.GONE
            }
        }
    }

    /**
     * This methods is responsible for rendering the view according to the newState
     *
     * @param newState the newState representing the view
     */
    fun render(newState: State) {
        if (newState == state) {
            Log.d(LOG_TAG, "State unchanged")
            return
        }
        Log.d(LOG_TAG, "Rendering $newState")
        cleanUp()
        state = newState
        when (newState) {
            is State.Default -> renderDefault()
            is State.Hidden -> renderHidden()
            is State.Tombstone -> renderTombstone(newState)
            is State.ResourceLimitExceededError -> renderResourceLimitExceededError(newState)
            is State.ConnectionError -> renderConnectionError()
            is State.Typing -> renderTyping(newState)
            is State.UnreadPreview -> renderUnreadPreview()
            is State.ScrollToBottom -> renderScrollToBottom(newState)
            is State.UnsentEvents -> renderUnsent(newState)
        }
    }

    override fun setVisibility(visibility: Int) {
        if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
            // Wait for scroll state to be idle
            pendingVisibility = visibility
            return
        }

        if (visibility != getVisibility()) {
            // Schedule animation
            val parent = parent as ViewGroup
            TransitionManager.beginDelayedTransition(parent, VectorTransitionSet().apply {
                appearFromBottom(this@NotificationAreaView)
                appearWithAlpha(this@NotificationAreaView)
            })
        }

        super.setVisibility(visibility)
    }

    // PRIVATE METHODS *****************************************************************************************************************************************

    private fun setupView() {
        inflate(context, R.layout.view_notification_area, this)
        ButterKnife.bind(this)
    }

    private fun cleanUp() {
        messageView.setOnClickListener(null)
        imageView.setOnClickListener(null)
        setBackgroundColor(Color.TRANSPARENT)
        messageView.text = null
        imageView.setImageResource(0)
    }

    private fun renderTombstone(state: State.Tombstone) {
        visibility = View.VISIBLE
        imageView.setImageResource(R.drawable.error)
        val roomTombstoneContent = state.tombstoneContent
        val urlSpan = MatrixURLSpan(roomTombstoneContent.replacementRoom, state.tombstoneEventSenderId, delegate?.providesMessagesActionListener())
        val textColorInt = ThemeUtils.getColor(context, R.attr.vctr_message_text_color)
        val message = Spanny(resources.getString(R.string.room_tombstone_versioned_description),
                StyleSpan(Typeface.BOLD),
                ForegroundColorSpan(textColorInt))
                .append("\n")
                .append(resources.getString(R.string.room_tombstone_continuation_link), urlSpan, ForegroundColorSpan(textColorInt))
        messageView.movementMethod = LinkMovementMethod.getInstance()
        messageView.text = message
    }

    private fun renderResourceLimitExceededError(state: State.ResourceLimitExceededError) {
        visibility = View.VISIBLE
        val resourceLimitErrorFormatter = ResourceLimitErrorFormatter(context)
        val formatterMode: ResourceLimitErrorFormatter.Mode
        val backgroundColor: Int
        if (state.isSoft) {
            backgroundColor = R.color.soft_resource_limit_exceeded
            formatterMode = ResourceLimitErrorFormatter.Mode.Soft
        } else {
            backgroundColor = R.color.hard_resource_limit_exceeded
            formatterMode = ResourceLimitErrorFormatter.Mode.Hard
        }
        val message = resourceLimitErrorFormatter.format(state.matrixError, formatterMode, clickable = true)
        messageView.setTextColor(Color.WHITE)
        messageView.text = message
        messageView.movementMethod = LinkMovementMethod.getInstance()
        messageView.setLinkTextColor(Color.WHITE)
        setBackgroundColor(ContextCompat.getColor(context, backgroundColor))
    }

    private fun renderConnectionError() {
        visibility = View.VISIBLE
        imageView.setImageResource(R.drawable.error)
        messageView.setTextColor(ContextCompat.getColor(context, R.color.vector_fuchsia_color))
        messageView.text = SpannableString(resources.getString(R.string.room_offline_notification))
    }

    private fun renderTyping(state: State.Typing) {
        visibility = visibilityForMessages
        imageView.setImageResource(R.drawable.vector_typing)
        messageView.text = SpannableString(state.message)
        messageView.setTextColor(ThemeUtils.getColor(context, R.attr.vctr_room_notification_text_color))
    }

    private fun renderUnreadPreview() {
        visibility = visibilityForMessages
        imageView.setImageResource(R.drawable.scrolldown)
        messageView.setTextColor(ThemeUtils.getColor(context, R.attr.vctr_room_notification_text_color))
        imageView.setOnClickListener { delegate?.closeScreen() }
    }

    private fun renderScrollToBottom(state: State.ScrollToBottom) {
        visibility = visibilityForMessages
        if (state.unreadCount > 0) {
            imageView.setImageResource(R.drawable.newmessages)
            messageView.setTextColor(ContextCompat.getColor(context, R.color.vector_fuchsia_color))
            messageView.text = SpannableString(resources.getQuantityString(R.plurals.room_new_messages_notification, state.unreadCount, state.unreadCount))
        } else {
            imageView.setImageResource(R.drawable.scrolldown)
            messageView.setTextColor(ThemeUtils.getColor(context, R.attr.vctr_room_notification_text_color))
            if (!TextUtils.isEmpty(state.message)) {
                messageView.text = SpannableString(state.message)
            }
        }
        messageView.setOnClickListener { delegate?.jumpToBottom() }
        imageView.setOnClickListener { delegate?.jumpToBottom() }
    }

    private fun renderUnsent(state: State.UnsentEvents) {
        visibility = View.VISIBLE
        imageView.setImageResource(R.drawable.error)
        val cancelAll = resources.getString(R.string.room_prompt_cancel)
        val resendAll = resources.getString(R.string.room_prompt_resend)
        val messageRes = if (state.hasUnknownDeviceEvents) R.string.room_unknown_devices_messages_notification else R.string.room_unsent_messages_notification
        val message = context.getString(messageRes, resendAll, cancelAll)
        val cancelAllPos = message.indexOf(cancelAll)
        val resendAllPos = message.indexOf(resendAll)
        val spannableString = SpannableString(message)
        // cancelAllPos should always be > 0 but a GA crash reported here
        if (cancelAllPos >= 0) {
            spannableString.setSpan(CancelAllClickableSpan(), cancelAllPos, cancelAllPos + cancelAll.length, 0)
        }

        // resendAllPos should always be > 0 but a GA crash reported here
        if (resendAllPos >= 0) {
            spannableString.setSpan(ResendAllClickableSpan(), resendAllPos, resendAllPos + resendAll.length, 0)
        }
        messageView.movementMethod = LinkMovementMethod.getInstance()
        messageView.setTextColor(ContextCompat.getColor(context, R.color.vector_fuchsia_color))
        messageView.text = spannableString
    }

    private fun renderDefault() {
        visibility = visibilityForEmptyContent
    }

    private fun renderHidden() {
        visibility = View.GONE
    }

    /**
     * Track the cancel all click.
     */
    private inner class CancelAllClickableSpan : ClickableSpan() {
        override fun onClick(widget: View) {
            delegate?.deleteUnsentEvents()
            render(state)
        }

        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.color = ContextCompat.getColor(context, R.color.vector_fuchsia_color)
            ds.bgColor = 0
            ds.isUnderlineText = true
        }
    }

    /**
     * Track the resend all click.
     */
    private inner class ResendAllClickableSpan : ClickableSpan() {
        override fun onClick(widget: View) {
            delegate?.resendUnsentEvents()
            render(state)
        }

        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.color = ContextCompat.getColor(context, R.color.vector_fuchsia_color)
            ds.bgColor = 0
            ds.isUnderlineText = true
        }
    }

    /**
     * The state representing the view
     * It can take one state at a time
     * Priority of state is managed in {@link VectorRoomActivity.refreshNotificationsArea() }
     */
    sealed class State {

        // Not yet rendered
        object Initial : State()

        // View will be Invisible
        object Default : State()

        // View will be Gone
        object Hidden : State()

        // Resource limit exceeded error will be displayed (only hard for the moment)
        data class ResourceLimitExceededError(val isSoft: Boolean, val matrixError: MatrixError) : State()

        // Server connection is lost
        object ConnectionError : State()

        // The room is dead
        data class Tombstone(val tombstoneContent: RoomTombstoneContent, val tombstoneEventSenderId: String) : State()

        // Somebody is typing
        data class Typing(val message: String) : State()

        // Some new messages are unread in preview
        object UnreadPreview : State()

        // Some new messages are unread (grey or red)
        data class ScrollToBottom(val unreadCount: Int, val message: String? = null) : State()

        // Some event has been unsent
        data class UnsentEvents(val hasUndeliverableEvents: Boolean, val hasUnknownDeviceEvents: Boolean) : State()
    }

    /**
     * An interface to delegate some actions to another object
     */
    interface Delegate {
        fun providesMessagesActionListener(): IMessagesAdapterActionsListener
        fun resendUnsentEvents()
        fun deleteUnsentEvents()
        fun closeScreen()
        fun jumpToBottom()
    }

    companion object {
        /**
         * Preference key.
         */
        private const val SHOW_INFO_AREA_KEY = "SETTINGS_SHOW_INFO_AREA_KEY"

        /**
         * Always show the info area.
         */
        private const val SHOW_INFO_AREA_VALUE_ALWAYS = "always"

        /**
         * Show the info area when it has messages or errors.
         */
        private const val SHOW_INFO_AREA_VALUE_MESSAGES_AND_ERRORS = "messages_and_errors"

        /**
         * Show the info area only when it has errors.
         */
        private const val SHOW_INFO_AREA_VALUE_ONLY_ERRORS = "only_errors"
    }
}

