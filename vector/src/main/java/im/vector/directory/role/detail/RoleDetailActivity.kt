package im.vector.directory.role.detail

import androidx.fragment.app.FragmentManager
import im.vector.R
import im.vector.activity.AbstractWidgetActivity
import im.vector.activity.MXCActionBarActivity
import im.vector.activity.VectorAppCompatActivity

class RoleDetailActivity: MXCActionBarActivity(), FragmentManager.OnBackStackChangedListener {
    override fun getLayoutRes(): Int = R.layout.activity_role_detail
    override fun getTitleRes() = R.string.title_activity_role_detail

    override fun initUiAndData() {
        configureToolbar()
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