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

package im.vector.view

import android.content.Context
import android.support.annotation.StringRes
import android.support.constraint.ConstraintLayout
import android.support.transition.TransitionManager
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.isVisible
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.binaryfork.spanny.Spanny
import im.vector.R
import im.vector.ui.themes.ThemeUtils
import org.jetbrains.anko.defaultSharedPreferences
import org.matrix.androidsdk.util.Log

/**
 * The view used in VectorHomeActivity to show some information about the keys backup state
 * It does have a unique render method
 */
class KeysBackupBanner @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), View.OnClickListener {

    @BindView(R.id.view_keys_backup_banner_text)
    lateinit var textView: TextView

    var delegate: Delegate? = null
    private var state: State = State.Initial

    private var scrollState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE
        set(value) {
            field = value

            val pendingV = pendingVisibility

            if (pendingV != null) {
                pendingVisibility = null
                visibility = pendingV
            }
        }

    private var pendingVisibility: Int? = null

    init {
        setupView()
    }

    /**
     * This methods is responsible for rendering the view according to the newState
     *
     * @param newState the newState representing the view
     */
    fun render(newState: State, force: Boolean = false) {
        if (newState == state && !force) {
            Log.d(LOG_TAG, "State unchanged")
            return
        }
        Log.d(LOG_TAG, "Rendering $newState")

        state = newState
        when (newState) {
            State.Initial -> renderInitial()
            State.Hidden -> renderHidden()
            is State.Setup -> renderSetup(newState.numberOfKeys)
            is State.Recover -> renderRecover(newState.version)
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
            TransitionManager.beginDelayedTransition(parent)
        }

        super.setVisibility(visibility)
    }

    override fun onClick(v: View?) {
        when (state) {
            is State.Setup -> {
                delegate?.setupKeysBackup()
            }
            is State.Recover -> {
                delegate?.recoverKeysBackup()
            }
        }
    }

    @OnClick(R.id.view_keys_backup_banner_close)
    internal fun onCloseClicked() {
        state.let {
            when (it) {
                is State.Setup -> {
                    context.defaultSharedPreferences.edit {
                        putBoolean(BANNER_SETUP_DO_NOT_SHOW_AGAIN, true)
                    }
                }
                is State.Recover -> {
                    context.defaultSharedPreferences.edit {
                        putString(BANNER_RECOVER_DO_NOT_SHOW_FOR_VERSION, it.version)
                    }
                }
            }
        }

        // Force refresh
        render(state, true)
    }

    // PRIVATE METHODS *****************************************************************************************************************************************

    private fun setupView() {
        inflate(context, R.layout.view_keys_backup_banner, this)
        ButterKnife.bind(this)

        setOnClickListener(this)
    }

    private fun renderInitial() {
        isVisible = false
    }

    private fun renderHidden() {
        isVisible = false
    }

    private fun renderSetup(nbOfKeys: Int) {
        if (nbOfKeys == 0
                || context.defaultSharedPreferences.getBoolean(BANNER_SETUP_DO_NOT_SHOW_AGAIN, false)) {
            // Do not display the setup banner if there is no keys to backup, or if the user has already closed it
            isVisible = false
        } else {
            isVisible = true

            setText(R.string.keys_backup_banner_setup, R.string.keys_backup_banner_setup_colored_part)
        }
    }

    private fun renderRecover(version: String) {
        if (version == context.defaultSharedPreferences.getString(BANNER_RECOVER_DO_NOT_SHOW_FOR_VERSION, null)) {
            isVisible = false
        } else {
            isVisible = true

            setText(R.string.keys_backup_banner_recover, R.string.keys_backup_banner_recover_colored_part)
        }
    }

    private fun setText(@StringRes fullTextRes: Int, @StringRes colorTextRes: Int) {
        val coloredPart = resources.getString(colorTextRes)
        val fullText = resources.getString(fullTextRes, coloredPart)

        val accentColor = ThemeUtils.getColor(context, R.attr.colorAccent)

        // Color colored part
        textView.text = Spanny(fullText).apply { findAndSpan(coloredPart) { ForegroundColorSpan(accentColor) } }
    }

    /**
     * The state representing the view
     * It can take one state at a time
     */
    sealed class State {
        // Not yet rendered
        object Initial : State()

        // View will be Gone
        object Hidden : State()

        // Keys backup is not setup
        data class Setup(val numberOfKeys: Int) : State()

        // Keys backup can be recovered
        data class Recover(val version: String) : State()
    }

    /**
     * An interface to delegate some actions to another object
     */
    interface Delegate {
        fun setupKeysBackup()
        fun recoverKeysBackup()
    }

    companion object {
        private const val LOG_TAG = "KeysBackupBanner"

        /**
         * Preference key for setup. Value is a boolean.
         */
        private const val BANNER_SETUP_DO_NOT_SHOW_AGAIN = "BANNER_SETUP_DO_NOT_SHOW_AGAIN"

        /**
         * Preference key for recover. Value is a backup version (String).
         */
        private const val BANNER_RECOVER_DO_NOT_SHOW_FOR_VERSION = "BANNER_RECOVER_DO_NOT_SHOW_FOR_VERSION"

        /**
         * Inform the banner that a Recover has been done for this version, so do not show the Recover banner for this version
         */
        fun onRecoverDoneForVersion(context: Context, version: String) {
            context.defaultSharedPreferences.edit {
                putString(BANNER_RECOVER_DO_NOT_SHOW_FOR_VERSION, version)
            }
        }
    }
}

