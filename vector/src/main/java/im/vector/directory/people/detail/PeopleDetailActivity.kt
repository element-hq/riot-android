package im.vector.directory.people.detail

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import im.vector.Matrix
import im.vector.R
import im.vector.activity.MXCActionBarActivity
import im.vector.directory.people.model.DirectoryPeople
import im.vector.directory.role.detail.RoleDetailActivity
import im.vector.directory.role.model.DummyRole
import im.vector.util.VectorUtils
import kotlinx.android.synthetic.main.activity_people_detail.*

class PeopleDetailActivity : MXCActionBarActivity(), FragmentManager.OnBackStackChangedListener {
    private lateinit var peopleDetailAdapter: PeopleDetailAdapter

    override fun getLayoutRes(): Int = R.layout.activity_people_detail
    override fun getTitleRes() = R.string.title_activity_people_detail

    override fun initUiAndData() {
        configureToolbar()
        mSession = Matrix.getInstance(this).defaultSession

        val people = intent.getParcelableExtra<DirectoryPeople>(PEOPLE_EXTRA)
        VectorUtils.loadRoomAvatar(this, session, avatar, people)

        displayName.text = people.officialName
        jobTitle.text = people.jobTitle
        organisation.text = people.organisations
        businessUnit.text = people.businessUnits

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

    companion object {
        private const val PEOPLE_EXTRA = "PEOPLE_EXTRA"
        fun intent(context: Context, directoryPeople: DirectoryPeople): Intent {
            return Intent(context, PeopleDetailActivity::class.java).also {
                it.putExtra(PEOPLE_EXTRA, directoryPeople)
            }
        }
    }
}