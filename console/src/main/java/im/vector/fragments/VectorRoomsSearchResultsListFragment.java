package im.vector.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.PublicRoom;

import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorPublicRoomsActivity;
import im.vector.activity.VectorUnifiedSearchActivity;
import im.vector.adapters.VectorRoomSummaryAdapter;


public class VectorRoomsSearchResultsListFragment extends VectorRecentsListFragment {
    // log tag
    private static String LOG_TAG = "V_RoomsSearchResultsListFragment";

    // the session
    private MXSession mSession;

    // current public Rooms List
    private List<PublicRoom> mPublicRoomsList;

    /**
     * Static constructor
     * @param matrixId the matrix id
     * @return a VectorRoomsSearchResultsListFragment instance
     */
    public static VectorRoomsSearchResultsListFragment newInstance(String matrixId, int layoutResId) {
        VectorRoomsSearchResultsListFragment f = new VectorRoomsSearchResultsListFragment();
        Bundle args = new Bundle();
        args.putInt(VectorRecentsListFragment.ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Bundle args = getArguments();

        mMatrixId = args.getString(ARG_MATRIX_ID);
        mSession = Matrix.getInstance(getActivity()).getSession(mMatrixId);

        if (null == mSession) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mRecentsListView = (ExpandableListView)v.findViewById(R.id.fragment_recents_list);
        // the chevron is managed in the header view
        mRecentsListView.setGroupIndicator(null);
        // create the adapter
        mAdapter = new VectorRoomSummaryAdapter(getActivity(), mSession, true, R.layout.adapter_item_vector_recent_room, R.layout.adapter_item_vector_recent_header, this);
        mRecentsListView.setAdapter(mAdapter);

        // hide it by default
        mRecentsListView.setVisibility(View.GONE);

        // Set rooms click listener:
        // - reset the unread count
        // - start the corresponding room activity
        mRecentsListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {

                if (mAdapter.isDirectoryGroupPosition(groupPosition)) {
                    List<PublicRoom> matchedPublicRooms = mAdapter.getMatchedPublicRooms();

                    if ((null != matchedPublicRooms) && (matchedPublicRooms.size() > 0)) {
                        Intent intent = new Intent(getActivity(), VectorPublicRoomsActivity.class);
                        intent.putExtra(VectorPublicRoomsActivity.EXTRA_MATRIX_ID, mSession.getMyUser().userId);
                        intent.putExtra(VectorPublicRoomsActivity.EXTRA_PUBLIC_ROOMS_LIST_ID, new ArrayList<PublicRoom>(matchedPublicRooms));

                        getActivity().startActivity(intent);
                    }

                } else {
                    RoomSummary roomSummary = mAdapter.getRoomSummaryAt(groupPosition, childPosition);
                    MXSession session = Matrix.getInstance(getActivity()).getSession(roomSummary.getMatrixId());

                    String roomId = roomSummary.getRoomId();
                    Room room = session.getDataHandler().getRoom(roomId);
                    // cannot join a leaving room
                    if ((null == room) || room.isLeaving()) {
                        roomId = null;
                    }

                    // update the unread messages count
                    if (mAdapter.resetUnreadCount(groupPosition, childPosition)) {
                        session.getDataHandler().getStore().flushSummary(roomSummary);
                    }

                    // launch corresponding room activity
                    if (null != roomId) {
                        CommonActivityUtils.goToRoomPage(session, roomId, getActivity(), null);
                    }
                }

                // click is handled
                return true;
            }
        });

        return v;
    }

    /**
     * Expands all existing sections.
     */
    private void expandsAllSections() {
        final int groupCount = mAdapter.getGroupCount();

        for(int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            mRecentsListView.expandGroup(groupIndex);
        }
    }

    /**
     * Search a pattern in the room
     * @param pattern
     * @param onSearchResultListener
     */
    public void searchPattern(final String pattern,  final MatrixMessageListFragment.OnSearchResultListener onSearchResultListener) {
        if (TextUtils.isEmpty(pattern)) {
            mRecentsListView.setVisibility(View.GONE);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onSearchResultListener.onSearchSucceed(0);
                }
            });
        } else {
            mAdapter.setPublicRoomsList(mPublicRoomsList);
            mAdapter.setSearchPattern(pattern);

            mRecentsListView.post(new Runnable() {
                @Override
                public void run() {
                    mRecentsListView.setVisibility(View.VISIBLE);
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
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mPublicRoomsList = null;
        Log.d(LOG_TAG, "## onPause()");
    }

    @Override
    public void onResume() {
        super.onResume();

        // warn the activity that the current fragment is ready
        if (getActivity() instanceof VectorUnifiedSearchActivity) {
            ((VectorUnifiedSearchActivity)getActivity()).onSearchFragmentResume();
        }
    }
}
