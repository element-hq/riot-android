package im.vector.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.PublicRoom;

import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.VectorRoomSummaryAdapter;


public class VectorRoomsSearchResultsListFragment extends Fragment implements VectorRoomSummaryAdapter.RoomEventListener {
    // session identifier.
    public static final String ARG_MATRIX_ID = "VectorRoomsSearchResultsListFragment.ARG_MATRIX_ID";

    // log tag
    private static String LOG_TAG = "V_RoomsSearchResultsListFragment";

    // the session
    private MXSession mSession;

    // the result list
    private ExpandableListView mSearchResultExpandableListView;
    private VectorRoomSummaryAdapter mAdapter;

    // current public Rooms List
    private List<PublicRoom> mPublicRoomsList;

    /**
     * Static constructor
     * @param aMatrixId the matrix id
     * @return a VectorRoomsSearchResultsListFragment instance
     */
    public static VectorRoomsSearchResultsListFragment newInstance(String aMatrixId) {
        VectorRoomsSearchResultsListFragment fragment = new VectorRoomsSearchResultsListFragment();
        Bundle args = new Bundle();

        //args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, aMatrixId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "## onCreateView()");
        // get fragment parameters
        Bundle fragArgs = getArguments();

        if(null != fragArgs) {
            String matrixId = fragArgs.getString(ARG_MATRIX_ID);
            mSession = getSession(matrixId);
        } else {
            Log.e(LOG_TAG,"## onCreateView(): can't create list view");
            return null;
        }

        // inflate the expandable view & create the adapter
        View view = inflater.inflate(R.layout.fragment_vector_search_rooms_list_fragment, container, false);
        mAdapter = new VectorRoomSummaryAdapter(getActivity(), mSession, true, R.layout.adapter_item_vector_recent_room, R.layout.adapter_item_vector_recent_header, this);

        // setup the expandable view with its adapter
        mSearchResultExpandableListView = (ExpandableListView) view.findViewById(R.id.search_result_exp_list_view);
        mSearchResultExpandableListView.setGroupIndicator(null);
        mSearchResultExpandableListView.setAdapter(mAdapter);

        return view;
    }

    private void collapseAllSections() {
        final int groupCount = mAdapter.getGroupCount();

        for(int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            mSearchResultExpandableListView.collapseGroup(groupIndex);
        }
    }

    private void expandsAllSections() {
        final int groupCount = mAdapter.getGroupCount();

        for(int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            mSearchResultExpandableListView.expandGroup(groupIndex);
        }
    }

    public void searchPattern(final String pattern,  final MatrixMessageListFragment.OnSearchResultListener onSearchResultListener) {
        if (TextUtils.isEmpty(pattern)) {

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onSearchResultListener.onSearchSucceed(0);
                }
            });
        } else {

            collapseAllSections();
            mAdapter.setPublicRoomsList(mPublicRoomsList);
            mAdapter.setSearchPattern(pattern);

            mSearchResultExpandableListView.post(new Runnable() {
                @Override
                public void run() {
                    expandsAllSections();
                    onSearchResultListener.onSearchSucceed(1);
                }
            });

            // the public rooms have not yet been retrieved
            if (null == mPublicRoomsList) {
                // use any session to get the public rooms list
                mSession.getEventsApiClient().loadPublicRooms(new SimpleApiCallback<List<PublicRoom>>(getActivity()) {
                    @Override
                    public void onSuccess(List<PublicRoom> publicRooms) {
                        if (null != publicRooms) {
                            mPublicRoomsList = publicRooms;
                            mAdapter.setPublicRoomsList(mPublicRoomsList);
                            mAdapter.notifyDataSetChanged();
                            mSearchResultExpandableListView.invalidateViews();
                        }
                    }
                });
            }
        }
    }

    public MXSession getSession(String matrixId) {
        return Matrix.getMXSession(getActivity(), matrixId);
    }

    // =============================================================================================
    // RoomEventListener implementation
    @Override
    public void onJoinRoom(MXSession session, String roomId) {
        //CommonActivityUtils.goToRoomPage(session, roomId, VectorHomeActivity.this, null);
        Log.d(LOG_TAG,"## onJoinRoom()");
    }

    @Override
    public void onRejectInvitation(MXSession session, String roomId) {
        Log.d(LOG_TAG,"## onRejectInvitation()");
    }

    @Override
    public void onToggleRoomNotifications(MXSession session, String roomId) {
        Log.d(LOG_TAG,"## onToggleRoomNotifications()");
    }

    @Override
    public void moveToFavorites(MXSession session, String roomId) {
        Log.d(LOG_TAG,"## moveToFavorites()");
    }

    @Override
    public void moveToConversations(MXSession session, String roomId) {
        Log.d(LOG_TAG,"## moveToConversations()");
    }

    @Override
    public void moveToLowPriority(MXSession session, String roomId) {
        Log.d(LOG_TAG,"## moveToLowPriority()");
    }

    @Override
    public void onLeaveRoom(MXSession session, String roomId) {
        Log.d(LOG_TAG,"## onLeaveRoom()");
    }

    @Override
    public void onPause() {
        super.onPause();
        mPublicRoomsList = null;
        Log.d(LOG_TAG, "## onPause()");
    }
}
