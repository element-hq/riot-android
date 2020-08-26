package im.vector.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import im.vector.R
import im.vector.adapters.HomeRoomAdapter
import im.vector.fragments.AbsHomeFragment
import im.vector.ui.themes.ThemeUtils.getColor
import im.vector.ui.themes.ThemeUtils.setTabLayoutTheme
import im.vector.util.HomeRoomsViewModel
import im.vector.util.PreferencesManager
import im.vector.util.RoomUtils
import kotlinx.android.synthetic.main.fragment_view_pager_tab.*
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.data.RoomTag

class NewHomeFragment : AbsHomeFragment(), HomeRoomAdapter.OnSelectRoomListener, RegisterListener {
    val dataUpdateListeners = ArrayList<UpDateListener>()
    var result: HomeRoomsViewModel.Result? = null

    override fun getLayoutResId(): Int {
        return R.layout.fragment_view_pager_tab
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutResId(), container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        pager.offscreenPageLimit = 3
        pager.adapter = HomePagerAdapter(childFragmentManager, resources.getStringArray(R.array.home_tabs))
        tabLayout.setupWithViewPager(pager)
        activity?.let { activity ->
            setTabLayoutTheme(activity, tabLayout)
            mPrimaryColor = getColor(activity, R.attr.vctr_tab_home)
            mSecondaryColor = getColor(activity, R.attr.vctr_tab_home_secondary)
            mFabColor = ContextCompat.getColor(activity, R.color.tab_rooms)
            mFabPressedColor = ContextCompat.getColor(activity, R.color.tab_rooms_secondary)
        }
    }

    override fun getRooms(): MutableList<Room> {
        return ArrayList(mSession.dataHandler.store?.rooms)
    }

    override fun onFilter(pattern: String?, listener: OnFilterListener?) {
        dataUpdateListeners.forEach {
            it.onFilter(pattern, listener)
        }
    }

    override fun onResetFilter() {
        dataUpdateListeners.forEach {
            it.onFilter("", null)
        }
    }

    override fun onRoomResultUpdated(result: HomeRoomsViewModel.Result?) {
        if (isResumed) {
            refreshData(result)
        }
    }

    /*
     * *********************************************************************************************
     * Data management
     * *********************************************************************************************
     */
    /**
     * Init the rooms data
     */
    private fun refreshData(result: HomeRoomsViewModel.Result?) {
        this.result = result
        val pinMissedNotifications = PreferencesManager.pinMissedNotifications(activity)
        val pinUnreadMessages = PreferencesManager.pinUnreadMessages(activity)
        val notificationComparator = RoomUtils.getNotifCountRoomsComparator(mSession, pinMissedNotifications, pinUnreadMessages)
        dataUpdateListeners.forEachIndexed { index, listener ->
            when (index) {
                ROOM_FRAGMENTS.INVITE.ordinal -> {
                    listener.onUpdate(mActivity.roomInvitations, notificationComparator)
                }
                ROOM_FRAGMENTS.FAVORITE.ordinal -> {
                    listener.onUpdate(result?.favourites, notificationComparator)
                }
                ROOM_FRAGMENTS.NORMAL.ordinal -> {
                    listener.onUpdate(result?.otherRooms?.plus(result.directChats), notificationComparator)
                }
                ROOM_FRAGMENTS.LOW_PRIORITY.ordinal -> {
                    listener.onUpdate(result?.lowPriorities, notificationComparator)
                }
            }
        }
        mActivity.hideWaitingView()
    }

    override fun onLongClickRoom(v: View?, room: Room?, position: Int) {
        // User clicked on the "more actions" area
        val tags = room!!.accountData.keys
        val isFavorite = tags != null && tags.contains(RoomTag.ROOM_TAG_FAVOURITE)
        val isLowPriority = tags != null && tags.contains(RoomTag.ROOM_TAG_LOW_PRIORITY)
        RoomUtils.displayPopupMenu(activity, mSession, room, v, isFavorite, isLowPriority, this)
    }

    override fun onSelectRoom(room: Room?, position: Int) {
        openRoom(room)
    }

    override fun onRegister(listener: UpDateListener) {
        dataUpdateListeners.add(listener)
    }

    override fun onUnregister(listener: UpDateListener) {
        dataUpdateListeners.remove(listener)
    }

    inner class HomePagerAdapter(fm: FragmentManager, val titles: Array<String>) : FragmentPagerAdapter(fm) {
        private val pinMissedNotifications = PreferencesManager.pinMissedNotifications(activity)
        private val pinUnreadMessages = PreferencesManager.pinUnreadMessages(activity)
        private val notificationComparator = RoomUtils.getNotifCountRoomsComparator(mSession, pinMissedNotifications, pinUnreadMessages)

        override fun getItem(position: Int): Fragment {
            val fragment = when (position) {
                ROOM_FRAGMENTS.INVITE.ordinal -> {
                    val fragment = InviteRoomFragment()
                    fragment.onUpdate(mActivity.roomInvitations, notificationComparator)
                    fragment.onSelectRoomListener = this@NewHomeFragment
                    fragment.invitationListener = this@NewHomeFragment
                    fragment.moreActionListener = null
                    fragment
                }
                ROOM_FRAGMENTS.FAVORITE.ordinal -> {
                    val fragment = FavoriteRoomFragment()
                    fragment.onUpdate(result?.favourites, notificationComparator)
                    fragment.onSelectRoomListener = this@NewHomeFragment
                    fragment.invitationListener = null
                    fragment.moreActionListener = this@NewHomeFragment
                    fragment
                }
                ROOM_FRAGMENTS.NORMAL.ordinal -> {
                    val fragment = NormalRoomFragment()
                    fragment.onUpdate(result?.otherRooms, notificationComparator)
                    fragment.onSelectRoomListener = this@NewHomeFragment
                    fragment.invitationListener = null
                    fragment.moreActionListener = this@NewHomeFragment
                    fragment
                }
                ROOM_FRAGMENTS.LOW_PRIORITY.ordinal -> {
                    val fragment = LowPriorityRoomFragment()
                    fragment.onUpdate(result?.lowPriorities, notificationComparator)
                    fragment.onSelectRoomListener = this@NewHomeFragment
                    fragment.invitationListener = null
                    fragment.moreActionListener = this@NewHomeFragment
                    fragment
                }
                else -> InviteRoomFragment()
            }
            fragment.registerListener = this@NewHomeFragment
            return fragment
        }

        override fun getCount(): Int = titles.size

        override fun getPageTitle(position: Int): CharSequence {
            return titles[position]
        }
    }

    enum class ROOM_FRAGMENTS {
        INVITE,
        FAVORITE,
        NORMAL,
        LOW_PRIORITY
    }
}

