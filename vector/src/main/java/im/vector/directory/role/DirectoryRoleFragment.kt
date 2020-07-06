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

class DirectoryRoleFragment : Fragment() {
    private lateinit var viewModel: DirectoryRoleViewModel
    private lateinit var categoryAdapter: DropDownAdapter
    private lateinit var organisationUnitAdapter: DropDownAdapter
    private lateinit var specialityAdapter: DropDownAdapter
    private lateinit var locationAdapter: DropDownAdapter

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

        categoryAdapter = DropDownAdapter(requireContext(), R.layout.drop_down_item)
        categoryEditText.threshold = 1
        categoryEditText.setAdapter(categoryAdapter)

        organisationUnitAdapter = DropDownAdapter(requireContext(), R.layout.drop_down_item)
        organisationEditText.threshold = 1
        organisationEditText.setAdapter(organisationUnitAdapter)

        specialityAdapter = DropDownAdapter(requireContext(), R.layout.drop_down_item)
        specialityEditText.threshold = 1
        specialityEditText.setAdapter(specialityAdapter)

        locationAdapter = DropDownAdapter(requireContext(), R.layout.drop_down_item)
        locationEditText.threshold = 1
        locationEditText.setAdapter(locationAdapter)

        //test data
        val testDropDownData = mutableListOf<DropDownItem>()
        for (i in 1..5) {
            testDropDownData.add(DropDownItem(i, "Item $i"))
        }
        categoryAdapter.addData(testDropDownData)
        organisationUnitAdapter.addData(testDropDownData)
        specialityAdapter.addData(testDropDownData)
        locationAdapter.addData(testDropDownData)
    }

    private fun subscribeUI() {
        viewModel.advancedSearchVisibility.observe(viewLifecycleOwner, Observer {
            if (it) {
                advancedSearchViewGroup.visibility = View.VISIBLE
            } else {
                advancedSearchViewGroup.visibility = View.GONE
            }
        })
    }
}