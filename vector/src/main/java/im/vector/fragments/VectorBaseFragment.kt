package im.vector.fragments

import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.v4.app.Fragment
import android.view.View
import butterknife.ButterKnife
import butterknife.Unbinder
import org.matrix.androidsdk.util.Log


/**
 * Parent class for all Fragment in Vector application
 */
open class VectorBaseFragment : Fragment() {

    // Butterknife unbinder
    private var mUnBinder: Unbinder? = null

    @CallSuper
    override fun onResume() {
        super.onResume()

        Log.event(Log.EventTag.NAVIGATION, "onResume Fragment " + this.javaClass.simpleName)
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mUnBinder = ButterKnife.bind(this, view)
    }

    @CallSuper
    override fun onDestroyView() {
        super.onDestroyView()
        mUnBinder?.unbind()
        mUnBinder = null
    }
}
