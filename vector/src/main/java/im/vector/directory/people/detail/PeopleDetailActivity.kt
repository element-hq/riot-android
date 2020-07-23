package im.vector.directory.people.detail

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import im.vector.Matrix
import im.vector.R
import im.vector.activity.MXCActionBarActivity
import im.vector.directory.people.model.DirectoryPeople
import im.vector.directory.role.model.*
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
        peopleDetailAdapter.setData(people)

        val testRoleData = mutableListOf<DummyRole>()
        testRoleData.add(DummyRole("1", "ED Acute SRMO", "Emergency Department  Acute Senior Resident Medical Officer Medical Officer", null, "ED {Emergency Department}", arrayListOf(Role("1", "Senior Resident Medical Officer", "Doctor")),
                arrayListOf(Speciality("1", "Emergency")), arrayListOf(DummyLocation("1", "CH {Canberra Hospital}")), arrayListOf(Team("1", "Emergency Department Acute"))))

        testRoleData.add(DummyRole("1", "ED Acute RMO", "Emergency Department  Acute Resident Medical Officer", null, "ED {Emergency Department}", arrayListOf(Role("1", "Resident", "Doctor")),
                arrayListOf(Speciality("1", "Emergency")), arrayListOf(DummyLocation("1", "CH {Canberra Hospital}")), arrayListOf(Team("1", "Emergency Department Acute"))))

        testRoleData.add(DummyRole("1", "ED Acute Intern", "Emergency Department  Acute Intern", null, "ED {Emergency Department}", arrayListOf(Role("1", "Intern", "Doctor")),
                arrayListOf(Speciality("1", "Emergency")), arrayListOf(DummyLocation("1", "CH {Canberra Hospital}")), arrayListOf(Team("1", "Emergency Department Acute"))))

        testRoleData.add(DummyRole("1", "ED Acute Consultant", "Emergency Department  Acute Consultant", null, "ED {Emergency Department}", arrayListOf(Role("1", "Consultant", "Doctor")),
                arrayListOf(Speciality("1", "Emergency")), arrayListOf(DummyLocation("1", "CH {Canberra Hospital}")), arrayListOf(Team("1", "Emergency Department Acute"))))

        testRoleData.add(DummyRole("1", "ED Acute East Nurse", "Emergency Department  Acute East Nurse", null, "ED {Emergency Department}", arrayListOf(Role("1", "Emergency Department Nurse", "Nursing and Midwifery")),
                arrayListOf(Speciality("1", "Emergency")), arrayListOf(DummyLocation("1", "CH {Canberra Hospital}")), arrayListOf(Team("1", "Emergency Department Acute"))))

        peopleDetailAdapter.setData(testRoleData)


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