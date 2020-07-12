package im.vector.directory.people

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import im.vector.R
import im.vector.directory.people.detail.PeopleDetailActivity
import im.vector.directory.people.model.DirectoryPeople
import kotlinx.android.synthetic.main.fragment_directory_people.*

class DirectoryPeopleFragment : Fragment(), PeopleClickListener {
    private lateinit var peopleDirectoryAdapter: PeopleDirectoryAdapter

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_directory_people, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        peopleDirectoryAdapter = PeopleDirectoryAdapter(requireContext(), this)
        peopleRecyclerview.layoutManager = LinearLayoutManager(requireContext())
        peopleRecyclerview.adapter = peopleDirectoryAdapter

        //test data
        val testPeopleData = mutableListOf<DirectoryPeople>()
        for (i in 1..10) {
            testPeopleData.add(DirectoryPeople(i.toString(), "Official Name $i", "job title $i", null, arrayListOf("Role $i"), arrayListOf("Category $i")))
        }
        peopleDirectoryAdapter.setData(testPeopleData)
    }

    override fun onPeopleClick(directoryPeople: DirectoryPeople) {
        startActivity(Intent(activity, PeopleDetailActivity::class.java))
    }

    override fun onPeopleFavorite(directoryPeople: DirectoryPeople) {

    }
}