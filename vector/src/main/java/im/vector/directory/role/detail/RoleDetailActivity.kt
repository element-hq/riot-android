package im.vector.directory.role.detail

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import im.vector.R
import im.vector.activity.MXCActionBarActivity
import im.vector.activity.ReviewTermsActivity
import im.vector.directory.role.model.DummyRole
import kotlinx.android.synthetic.main.activity_role_detail.*

class RoleDetailActivity : MXCActionBarActivity(), FragmentManager.OnBackStackChangedListener {
    private lateinit var roleAdapter: RolesDetailAdapter

    override fun getLayoutRes(): Int = R.layout.activity_role_detail
    override fun getTitleRes() = R.string.title_activity_role_detail

    override fun initUiAndData() {
        configureToolbar()

        val role = intent.getParcelableExtra<DummyRole>(ROLE_EXTRA)

        roleAdapter = RolesDetailAdapter(this)
        roleRecyclerview.layoutManager = LinearLayoutManager(this)
        roleRecyclerview.adapter = roleAdapter

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
        private const val ROLE_EXTRA = "ROLE_EXTRA"
        fun intent(context: Context, dummyRole: DummyRole): Intent {
            return Intent(context, RoleDetailActivity::class.java).also {
                it.putExtra(ROLE_EXTRA, dummyRole)
            }
        }
    }
}