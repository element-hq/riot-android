package im.vector.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import im.vector.R
import im.vector.adapters.HomeRoomAdapter
import im.vector.directory.role.DirectoryRoleFragment
import im.vector.fragments.AbsHomeFragment
import im.vector.ui.themes.ThemeUtils.getColor
import im.vector.ui.themes.ThemeUtils.setTabLayoutTheme
import im.vector.util.RoomUtils
import kotlinx.android.synthetic.main.fragment_view_pager_tab.*
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.data.RoomTag
import java.util.*

class NewHomeFragment : AbsHomeFragment(), HomeRoomAdapter.OnSelectRoomListener {
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
        TODO("Not yet implemented")
    }

    override fun onResetFilter() {
        TODO("Not yet implemented")
    }

    override fun onSelectRoom(room: Room?, position: Int) {
        openRoom(room)
    }

    override fun onLongClickRoom(v: View?, room: Room?, position: Int) {
        // User clicked on the "more actions" area
        val tags = room!!.accountData.keys
        val isFavorite = tags != null && tags.contains(RoomTag.ROOM_TAG_FAVOURITE)
        val isLowPriority = tags != null && tags.contains(RoomTag.ROOM_TAG_LOW_PRIORITY)
        RoomUtils.displayPopupMenu(activity, mSession, room, v, isFavorite, isLowPriority, this)
    }

    class HomePagerAdapter(fm: FragmentManager, val titles: Array<String>) : FragmentStatePagerAdapter(fm) {
        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> InviteRoomFragment()
                1 -> FavoriteRoomFragment()
                2 -> NormalRoomFragment()
                3-> LowPriorityRoomFragment()
                else -> DirectoryRoleFragment()
            }
        }

        override fun getCount(): Int = titles.size

        override fun getPageTitle(position: Int): CharSequence {
            return titles[position]
        }
    }
}