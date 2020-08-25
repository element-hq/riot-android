package im.vector.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import im.vector.R
import im.vector.adapters.AbsAdapter
import im.vector.adapters.HomeRoomAdapter
import im.vector.fragments.AbsHomeFragment
import im.vector.ui.themes.ThemeUtils
import im.vector.util.PreferencesManager
import im.vector.util.RoomUtils
import im.vector.view.HomeSectionView
import kotlinx.android.synthetic.main.fragment_home_individual.*
import kotlinx.android.synthetic.main.fragment_view_pager_tab.*
import org.matrix.androidsdk.data.Room
import java.util.*
import kotlin.collections.ArrayList


open abstract class BaseNewHomeIndividualFragment : AbsHomeFragment(), UpDateListener{
    private val LOG_TAG = BaseNewHomeIndividualFragment::class.java.simpleName

    var registerListener: RegisterListener? = null
    var onSelectRoomListener: HomeRoomAdapter.OnSelectRoomListener? = null
    var invitationListener: AbsAdapter.RoomInvitationListener? = null
    var moreActionListener: AbsAdapter.MoreRoomActionListener? = null

    val localRooms = ArrayList<Room>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.let { activity ->
            mPrimaryColor = ThemeUtils.getColor(activity, R.attr.vctr_tab_home)
            mSecondaryColor = ThemeUtils.getColor(activity, R.attr.vctr_tab_home_secondary)
            mFabColor = ContextCompat.getColor(activity, R.color.tab_rooms)
            mFabPressedColor = ContextCompat.getColor(activity, R.color.tab_rooms_secondary)
        }
        sectionView.mHeader.visibility = GONE
        sectionView.mBadge.visibility = GONE
        sectionView.setHideIfEmpty(true)
    }

    override fun onUpdate(rooms: List<Room>?, comparator: Comparator<Room>) {
        Log.d("zzzz", "called" + rooms?.size)
        localRooms.clear()
        try {
            Collections.sort(rooms, comparator)
        } catch (e: Exception) {
            org.matrix.androidsdk.core.Log.e(LOG_TAG, "## sortAndDisplay() failed " + e.message, e)
        }
        rooms?.let {
            localRooms.addAll(it)
            if(sectionView!=null){
                sectionView.setRooms(localRooms)
            }
        }
    }

    override fun getRooms(): MutableList<Room> {
        return ArrayList(mSession.dataHandler.store?.rooms)
    }

    override fun onFilter(pattern: String?, listener: OnFilterListener?) {
        sectionView.onFilter(pattern, listener)
    }

    override fun onResetFilter() {
        sectionView.onFilter("", null)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        registerListener?.onRegister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        registerListener?.onUnregister(this)
    }
}

interface UpDateListener{
    fun onUpdate(rooms: List<Room>?, comparator: Comparator<Room>)
}

interface RegisterListener{
    fun onRegister(listener: UpDateListener)
    fun onUnregister(listener: UpDateListener)
}