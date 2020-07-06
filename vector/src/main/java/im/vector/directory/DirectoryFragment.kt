package im.vector.directory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import im.vector.R
import im.vector.directory.group.DirectoryGroupFragment
import im.vector.directory.people.DirectoryPeopleFragment
import im.vector.directory.role.DirectoryRoleFragment
import im.vector.fragments.AbsHomeFragment
import im.vector.ui.themes.ThemeUtils.getColor
import im.vector.ui.themes.ThemeUtils.setTabLayoutTheme
import kotlinx.android.synthetic.main.fragment_directory.*
import org.matrix.androidsdk.data.Room

class DirectoryFragment : AbsHomeFragment() {
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutResId(), container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        pager.adapter = DemoCollectionPagerAdapter(childFragmentManager, resources.getStringArray(R.array.directory_tabs))
        tabLayout.setupWithViewPager(pager)
        activity?.let { activity ->
            setTabLayoutTheme(activity, tabLayout)
            mPrimaryColor = getColor(activity, R.attr.vctr_tab_home)
            mSecondaryColor = getColor(activity, R.attr.vctr_tab_home_secondary)

            mFabColor = ContextCompat.getColor(activity, R.color.tab_people)
            mFabPressedColor = ContextCompat.getColor(activity, R.color.tab_people_secondary)
        }
    }

    override fun getRooms(): MutableList<Room> {
        TODO("Not yet implemented")
    }

    override fun getLayoutResId(): Int {
        return R.layout.fragment_directory
    }

    override fun onFilter(pattern: String?, listener: OnFilterListener?) {
        TODO("Not yet implemented")
    }

    override fun onResetFilter() {
        TODO("Not yet implemented")
    }

    class DemoCollectionPagerAdapter(fm: FragmentManager, val titles: Array<String>) : FragmentStatePagerAdapter(fm) {
        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> DirectoryRoleFragment()
                1 -> DirectoryPeopleFragment()
                2 -> DirectoryGroupFragment()
                else -> DirectoryRoleFragment()
            }
        }

        override fun getCount(): Int = titles.size

        override fun getPageTitle(position: Int): CharSequence {
            return titles[position]
        }
    }
}