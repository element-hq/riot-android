package im.vector.directory.role

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import im.vector.R
import kotlinx.android.synthetic.main.fragment_directory_role.*

class DirectoryRoleFragment : Fragment(){
    private lateinit var viewModel: DirectoryRoleViewModel

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_directory_role, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(DirectoryRoleViewModel::class.java)
        subscribeUI()
        advancedSearchButton.setOnClickListener {
            viewModel.toggleSearchView()
        }
    }

    private fun subscribeUI() {
        viewModel.advancedSearchVisibility.observe(viewLifecycleOwner, Observer {
            if(it){
                advancedSearchViewGroup.visibility = View.VISIBLE
            }else{
                advancedSearchViewGroup.visibility = View.GONE
            }
        })
    }
}