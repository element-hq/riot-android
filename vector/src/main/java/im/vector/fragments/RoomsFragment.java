package im.vector.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import im.vector.R;

public class RoomsFragment extends AbsHomeFragment {

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static RoomsFragment newInstance() {
        return new RoomsFragment();
    }

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rooms, container, false);
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
        Toast.makeText(getActivity(), "room onFilter "+pattern, Toast.LENGTH_SHORT).show();
        //TODO adapter getFilter().filter(pattern, listener)
        //TODO call listener.onFilterDone(); when complete
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
        // TODO
    }
}
