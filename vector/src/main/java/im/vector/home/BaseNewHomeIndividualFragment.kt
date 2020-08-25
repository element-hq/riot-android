package im.vector.home

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import im.vector.view.HomeSectionView
import org.matrix.androidsdk.data.Room
import java.util.*


open class BaseNewHomeIndividualFragment : Fragment(), UpDateListener{
    private val LOG_TAG = BaseNewHomeIndividualFragment::class.java.simpleName

    var registerListener: RegisterListener? = null

    override fun onUpdate(rooms: List<Room>?) {
        Log.d("zzzz", "called" + rooms?.size)
    }

    /**
     * Sort the given room list with the given comparator then attach it to the given adapter
     *
     * @param rooms
     * @param comparator
     * @param section
     */
    private fun sortAndDisplay(rooms: List<Room>, comparator: Comparator<Room>, section: HomeSectionView) {
        try {
            Collections.sort(rooms, comparator)
        } catch (e: Exception) {
            org.matrix.androidsdk.core.Log.e(LOG_TAG, "## sortAndDisplay() failed " + e.message, e)
        }
        section.setRooms(rooms)
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
    fun onUpdate(rooms: List<Room>?)
}

interface RegisterListener{
    fun onRegister(listener: UpDateListener)
    fun onUnregister(listener: UpDateListener)
}