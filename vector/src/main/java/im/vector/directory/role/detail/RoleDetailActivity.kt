package im.vector.directory.role.detail

import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import im.vector.R
import im.vector.activity.AbstractWidgetActivity
import im.vector.activity.MXCActionBarActivity
import im.vector.activity.VectorAppCompatActivity
import im.vector.directory.role.RolesDirectoryAdapter
import kotlinx.android.synthetic.main.activity_role_detail.*

class RoleDetailActivity: MXCActionBarActivity(), FragmentManager.OnBackStackChangedListener {
    private lateinit var roleAdapter: RolesDetailAdapter

    override fun getLayoutRes(): Int = R.layout.activity_role_detail
    override fun getTitleRes() = R.string.title_activity_role_detail

    override fun initUiAndData() {
        configureToolbar()
        roleAdapter = RolesDetailAdapter( this)
        roleRecyclerview.layoutManager = LinearLayoutManager(this)
        roleRecyclerview.adapter = roleAdapter

        callIcon.setOnClickListener {  }
        chatIcon.setOnClickListener {  }
        videoCallIcon.setOnClickListener {  }
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