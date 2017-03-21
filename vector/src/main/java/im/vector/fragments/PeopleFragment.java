package im.vector.fragments;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.AbsListAdapter;
import im.vector.adapters.RoomAdapter;
import im.vector.contacts.Contact;

public class PeopleFragment extends AbsHomeFragment implements AbsListAdapter.OnSelectItemListener<Room> {

    @BindView(R.id.direct_chats_recyclerview)
    RecyclerView mDirectChatsRecyclerView;

    @BindView(R.id.local_contact_recyclerview)
    RecyclerView mLocalContactsRecyclerView;

    @BindView(R.id.known_contact_recyclerview)
    RecyclerView mKnownContactsRecyclerView;

    private RoomAdapter mDirectChatAdapter;

    private List<Contact> mDirectChats;
    private List<Contact> mLocalContacts;
    private List<Contact> mKnownContacts;

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static PeopleFragment newInstance() {
        return new PeopleFragment();
    }

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_people, container, false);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initViews();

        if (savedInstanceState != null) {
            // Restore adapter items
        }
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected void onMarkAllAsRead() {

    }

    @Override
    protected void onFilter(String pattern, OnFilterListener listener) {
        Toast.makeText(getActivity(), "people onFilter " + pattern, Toast.LENGTH_SHORT).show();
        //TODO adapter getFilter().filter(pattern, listener)
        //TODO call listener.onFilterDone(); when complete
        mDirectChatAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                //mCountryEmptyView.setVisibility(count > 0 ? View.GONE : View.VISIBLE);
            }
        });
    }

    @Override
    protected void onResetFilter() {

    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mDirectChatsRecyclerView.setLayoutManager(layoutManager);
        mDirectChatAdapter = new RoomAdapter(getActivity(), this);
        mDirectChatsRecyclerView.setAdapter(mDirectChatAdapter);


        MXSession session = Matrix.getInstance(getActivity()).getDefaultSession();
        final List<String> directChatIds = session.getDirectChatRoomIdsList();
        final List<Room> directChatRooms = new ArrayList<>();

        for (String roomId : directChatIds) {
            directChatRooms.add(session.getDataHandler().getRoom(roomId));
        }

        mDirectChatAdapter.setItems(directChatRooms);

    }

    @Override
    public void onSelectItem(Room item) {

    }
}
