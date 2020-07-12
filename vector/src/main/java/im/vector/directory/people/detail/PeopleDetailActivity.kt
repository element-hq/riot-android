package im.vector.directory.people.detail

import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import im.vector.R
import im.vector.activity.MXCActionBarActivity
import kotlinx.android.synthetic.main.activity_people_detail.*
import kotlinx.android.synthetic.main.activity_role_detail.callIcon
import kotlinx.android.synthetic.main.activity_role_detail.chatIcon
import kotlinx.android.synthetic.main.activity_role_detail.videoCallIcon

class PeopleDetailActivity : MXCActionBarActivity(), FragmentManager.OnBackStackChangedListener {
    private lateinit var peopleDetailAdapter: PeopleDetailAdapter

    override fun getLayoutRes(): Int = R.layout.activity_people_detail
    override fun getTitleRes() = R.string.title_activity_people_detail

    override fun initUiAndData() {
        configureToolbar()
        peopleDetailAdapter = PeopleDetailAdapter(this)
        peopleRecyclerview.layoutManager = LinearLayoutManager(this)
        peopleRecyclerview.adapter = peopleDetailAdapter

        callIcon.setOnClickListener { }
        chatIcon.setOnClickListener { }
        videoCallIcon.setOnClickListener { }
    }

    override fun onDestroy() {
        supportFragmentManager.removeOnBackStackChangedListener(this)
        super.onDestroy()
    }

    override fun onBackStackChanged() {
        if (0 == supportFragmentManager.backStackEntryCount) {
            supportActionBar?.title = getString(getTitleRes())
        }
    }
}