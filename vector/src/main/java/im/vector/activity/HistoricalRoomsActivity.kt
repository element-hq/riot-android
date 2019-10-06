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

import android.app.SearchManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import im.vector.Matrix
import im.vector.R
import im.vector.adapters.AbsAdapter
import im.vector.adapters.HomeRoomAdapter
import im.vector.extensions.withoutLeftMargin
import im.vector.ui.themes.ActivityOtherThemes
import im.vector.util.RoomUtils
import im.vector.view.EmptyViewItemDecoration
import im.vector.view.SimpleDividerItemDecoration
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.data.Room
import java.util.*

/**
 * Displays the historical rooms list
 */
class HistoricalRoomsActivity : VectorAppCompatActivity(),
        SearchView.OnQueryTextListener,
        HomeRoomAdapter.OnSelectRoomListener,
        AbsAdapter.MoreRoomActionListener,
        RoomUtils.HistoricalRoomActionListener {

    @BindView(R.id.historical_search_view)
    internal lateinit var mSearchView: SearchView

    @BindView(R.id.historical_recycler_view)
    internal lateinit var mHistoricalRecyclerView: androidx.recyclerview.widget.RecyclerView

    @BindView(R.id.historical_no_results)
    internal lateinit var mHistoricalPlaceHolder: TextView

    // historical adapter
    private lateinit var mHistoricalAdapter: HomeRoomAdapter

    // sessions
    private var mSession: MXSession? = null

    /*
     * *********************************************************************************************
     * Activity lifecycle
     * *********************************************************************************************
     */

    override fun getOtherThemes() = ActivityOtherThemes.Home

    override fun getLayoutRes() = R.layout.activity_historical

    override fun getTitleRes() = R.string.title_activity_historical

    override fun initUiAndData() {
        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.")
            CommonActivityUtils.restartApp(this)
            return
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen")
            return
        }

        initViews()
    }

    override fun onResume() {
        super.onResume()
        refreshHistorical()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        CommonActivityUtils.onLowMemory(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CommonActivityUtils.onTrimMemory(this, level)
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private fun initViews() {
        // Waiting View
        waitingView = findViewById(R.id.historical_waiting_view)

        // Toolbar
        configureToolbar()

        mSession = Matrix.getInstance(this).defaultSession

        val margin = resources.getDimension(R.dimen.item_decoration_left_margin).toInt()

        mHistoricalAdapter = HomeRoomAdapter(this, R.layout.adapter_item_room_view, this, null, this)

        mHistoricalRecyclerView.let {
            it.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.VERTICAL, false)
            it.setHasFixedSize(true)
            it.isNestedScrollingEnabled = false
            it.addItemDecoration(SimpleDividerItemDecoration(this, DividerItemDecoration.VERTICAL, margin))
            it.addItemDecoration(EmptyViewItemDecoration(this, DividerItemDecoration.VERTICAL, 40, 16, 14))

            it.adapter = mHistoricalAdapter
        }

        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager

        // Remove unwanted left margin
        mSearchView.withoutLeftMargin()

        toolbar.contentInsetStartWithNavigation = 0

        mSearchView.let {
            it.maxWidth = Integer.MAX_VALUE
            it.isSubmitButtonEnabled = false
            it.setSearchableInfo(searchManager.getSearchableInfo(componentName))
            it.setOnQueryTextListener(this)
        }
    }

    /*
     * *********************************************************************************************
     * historical management
     * *********************************************************************************************
     */

    private fun refreshHistorical() {
        val dataHandler = mSession!!.dataHandler

        if (!dataHandler.areLeftRoomsSynced()) {
            mHistoricalAdapter.setRooms(ArrayList())
            showWaitingView()
            dataHandler.retrieveLeftRooms(object : HistoricalRoomApiCallback() {
                override fun onSuccess(info: Void?) {
                    runOnUiThread { initHistoricalRoomsData() }
                }
            })
        } else {
            initHistoricalRoomsData()
        }
    }

    /**
     * Init history rooms data
     */
    private fun initHistoricalRoomsData() {
        hideWaitingView()
        val historicalRooms = ArrayList(mSession!!.dataHandler.leftRooms)
        val iterator = historicalRooms.iterator()
        while (iterator.hasNext()) {
            val room = iterator.next()
            if (room.isConferenceUserRoom) {
                iterator.remove()
            }
        }

        doAsync {
            Collections.sort(historicalRooms, RoomUtils.getHistoricalRoomsComparator(mSession, false))

            uiThread {
                mHistoricalAdapter.setRooms(historicalRooms)
            }
        }
    }

    /*
     * *********************************************************************************************
     * User action management
     * *********************************************************************************************
     */
    override fun onQueryTextChange(newText: String): Boolean {
        // compute an unique pattern

        if (mSession!!.dataHandler.areLeftRoomsSynced()) {
            // wait before really triggering the search
            // else a search is triggered for each new character
            // eg "matt" triggers
            // 1 - search for m
            // 2 - search for ma
            // 3 - search for mat
            // 4 - search for matt
            // whereas only one search should have been triggered
            // else it might trigger some lags evenif the search is done in a background thread
            Handler(Looper.getMainLooper()).postDelayed({
                val queryText = mSearchView.query.toString()

                // display if the pattern matched
                if (TextUtils.equals(queryText, newText)) {
                    mHistoricalAdapter.filter.filter(newText) { count ->
                        mHistoricalRecyclerView.scrollToPosition(0)
                        mHistoricalPlaceHolder.isVisible = count == 0
                    }
                }
            }, 500)
        }
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return true
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    private abstract inner class HistoricalRoomApiCallback : ApiCallback<Void> {
        /**
         * Handle the end of any request : hide loading wheel and display error message if there is any
         *
         * @param errorMessage the localized error message
         */
        protected fun onRequestDone(errorMessage: String?) {
            if (!isFinishing) {
                runOnUiThread {
                    hideWaitingView()
                    if (!TextUtils.isEmpty(errorMessage)) {
                        Toast.makeText(this@HistoricalRoomsActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onNetworkError(e: Exception) {
            onRequestDone(e.localizedMessage)
        }

        override fun onMatrixError(e: MatrixError) {
            onRequestDone(e.localizedMessage)
        }

        override fun onUnexpectedError(e: Exception) {
            onRequestDone(e.localizedMessage)
        }
    }

    override fun onSelectRoom(room: Room, position: Int) {
        showWaitingView()
        CommonActivityUtils.previewRoom(this, mSession, room.roomId, "", object : HistoricalRoomApiCallback() {
            override fun onSuccess(info: Void?) {
                onRequestDone(null)
            }
        })
    }

    override fun onLongClickRoom(v: View, room: Room, position: Int) {
        RoomUtils.displayHistoricalRoomMenu(this, mSession, room, v, this)
    }

    override fun onMoreActionClick(itemView: View, room: Room) {
        RoomUtils.displayHistoricalRoomMenu(this, mSession, room, itemView, this)
    }

    override fun onForgotRoom(room: Room) {
        showWaitingView()

        room.forget(object : HistoricalRoomApiCallback() {
            override fun onSuccess(info: Void?) {
                initHistoricalRoomsData()
            }
        })
    }

    companion object {
        private val LOG_TAG = HistoricalRoomsActivity::class.java.simpleName
    }
}
