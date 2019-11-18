/*
 * Copyright 2015 OpenMarket Ltd
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
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.*
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.Unbinder
import com.airbnb.mvrx.BaseMvRxActivity
import im.vector.BuildConfig
import im.vector.R
import im.vector.VectorApp
import im.vector.activity.interfaces.Restorable
import im.vector.dialogs.ConsentNotGivenHelper
import im.vector.fragments.VectorBaseFragment
import im.vector.fragments.VectorBaseMvRxFragment
import im.vector.receiver.DebugReceiver
import im.vector.ui.themes.ActivityOtherThemes
import im.vector.ui.themes.ThemeUtils
import im.vector.util.AssetReader
import im.vector.util.BugReporter
import org.matrix.androidsdk.core.Log

/**
 * Parent class for all Activities in Vector application
 */
abstract class VectorAppCompatActivity : BaseMvRxActivity() {

    private var LOG_TAG = VectorAppCompatActivity::class.java.simpleName

    /* ==========================================================================================
     * DATA
     * ========================================================================================== */

    private var unBinder: Unbinder? = null

    private var savedInstanceState: Bundle? = null

    // For debug only
    private var debugReceiver: DebugReceiver? = null

    /* ==========================================================================================
     * UI
     * ========================================================================================== */

    @Nullable
    @BindView(R.id.toolbar)
    protected lateinit var toolbar: Toolbar

    /* ==========================================================================================
     * LIFE CYCLE
     * ========================================================================================== */

    @CallSuper
    override fun onLowMemory() {
        super.onLowMemory()

        AssetReader.clearCache()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(VectorApp.getLocalisedContext(base))
    }

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeUtils.setActivityTheme(this, getOtherThemes())

        doBeforeSetContentView()

        setContentView(getLayoutRes())

        unBinder = ButterKnife.bind(this)

        this.savedInstanceState = savedInstanceState

        initUiAndData()

        val titleRes = getTitleRes()
        if (titleRes != -1) {
            supportActionBar?.let {
                it.setTitle(titleRes)
            } ?: run {
                setTitle(titleRes)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unBinder?.unbind()
        unBinder = null
    }

    @CallSuper
    override fun onResume() {
        super.onResume()

        if (displayInFullscreen()) {
            setFullScreen()
        }

        Log.event(Log.EventTag.NAVIGATION, "onResume Activity " + this.javaClass.simpleName)

        DebugReceiver
                .getIntentFilter()
                .takeIf { BuildConfig.DEBUG }
                ?.let {
                    debugReceiver = DebugReceiver()
                    registerReceiver(debugReceiver, it)
                }
    }

    override fun onPause() {
        super.onPause()

        debugReceiver?.let {
            unregisterReceiver(debugReceiver)
            debugReceiver = null
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && displayInFullscreen()) {
            setFullScreen()
        }
    }

    override fun onBackPressed() {
        val handled = recursivelyDispatchOnBackPressed(supportFragmentManager)
        if (!handled) {
            super.onBackPressed()
        }
    }

    private fun recursivelyDispatchOnBackPressed(fm: FragmentManager): Boolean {
        val reverseOrder = fm.fragments.filter { it is VectorBaseFragment || it is VectorBaseMvRxFragment }.reversed()
        for (f in reverseOrder) {
            val handledByChildFragments = recursivelyDispatchOnBackPressed(f.childFragmentManager)
            if (handledByChildFragments) {
                return true
            }
            if (f is HandleBackParticipant && f.onBackPressed()) {
                return true
            }
        }
        return false
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration?) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)

        Log.w(LOG_TAG, "onMultiWindowModeChanged. isInMultiWindowMode: $isInMultiWindowMode")
        BugReporter.setMultiWindowMode(isInMultiWindowMode)
    }

    /* ==========================================================================================
     * MENU MANAGEMENT
     * ========================================================================================== */

    final override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val menuRes = getMenuRes()

        if (menuRes != -1) {
            menuInflater.inflate(menuRes, menu)
            ThemeUtils.tintMenuIcons(menu, ThemeUtils.getColor(this, getMenuTint()))
            return true
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_CANCELED)
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    /* ==========================================================================================
     * ABSTRACT METHODS
     * ========================================================================================== */

    @LayoutRes
    abstract fun getLayoutRes(): Int

    /* ==========================================================================================
     * OPEN METHODS
     * ========================================================================================== */

    open fun displayInFullscreen() = false

    open fun doBeforeSetContentView() = Unit

    open fun initUiAndData() = Unit

    @StringRes
    open fun getTitleRes() = -1

    @MenuRes
    open fun getMenuRes() = -1

    @AttrRes
    open fun getMenuTint() = R.attr.vctr_icon_tint_on_dark_action_bar_color

    /**
     * Return a object containing other themes for this activity
     */
    open fun getOtherThemes(): ActivityOtherThemes = ActivityOtherThemes.Default

    //==============================================================================================
    // Handle loading view (also called waiting view or spinner view)
    //==============================================================================================

    var waitingView: View? = null
        set(value) {
            field = value

            // Ensure this view is clickable to catch UI events
            value?.isClickable = true
        }

    /**
     * Tells if the waiting view is currently displayed
     *
     * @return true if the waiting view is displayed
     */
    fun isWaitingViewVisible() = waitingView?.isVisible == true

    /**
     * Show the waiting view
     */
    open fun showWaitingView() {
        waitingView?.isVisible = true
    }

    /**
     * Hide the waiting view
     */
    open fun hideWaitingView() {
        waitingView?.isVisible = false
    }

    /* ==========================================================================================
     * PROTECTED METHODS
     * ========================================================================================== */

    /**
     * Get the saved instance state.
     * Ensure {@link isFirstCreation()} returns false before calling this
     *
     * @return
     */
    protected fun getSavedInstanceState(): Bundle {
        return savedInstanceState!!
    }

    /**
     * Is first creation
     *
     * @return true if Activity is created for the first time (and not restored by the system)
     */
    protected fun isFirstCreation() = savedInstanceState == null

    /**
     * Configure the Toolbar. It MUST be present in your layout with id "toolbar"
     */
    protected fun configureToolbar() {
        setSupportActionBar(toolbar)

        supportActionBar?.let {
            it.setDisplayShowHomeEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
        }
    }

    /* ==========================================================================================
     * PRIVATE METHODS
     * ========================================================================================== */

    /**
     * Force to render the activity in fullscreen
     */
    private fun setFullScreen() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    /* ==========================================================================================
     * Save state management
     * ========================================================================================== */

    // Set of restorable object
    private val restorables = HashSet<Restorable>()

    @CallSuper
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        restorables.forEach {
            it.saveState(outState)
        }
    }

    protected fun addToRestorables(restorable: Restorable) = restorables.add(restorable)

    /* ==========================================================================================
     * User Consent
     * ========================================================================================== */

    val consentNotGivenHelper by lazy {
        ConsentNotGivenHelper(this, savedInstanceState)
                .apply { addToRestorables(this) }
    }
}
