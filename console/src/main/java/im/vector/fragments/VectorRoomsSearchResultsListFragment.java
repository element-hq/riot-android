package im.vector.fragments;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.VectorUnifiedSearchActivity;
import im.vector.adapters.VectorRoomSummaryAdapter;


public class VectorRoomsSearchResultsListFragment extends Fragment implements VectorRoomSummaryAdapter.RoomEventListener {
    public static final String ARG_ROOM_ID = "org.matrix.androidsdk.fragments.MatrixMessageListFragment.ARG_ROOM_ID";
    public static final String ARG_MATRIX_ID = "org.matrix.androidsdk.fragments.MatrixMessageListFragment.ARG_MATRIX_ID";
    public static final String ARG_LAYOUT_ID = "org.matrix.androidsdk.fragments.MatrixMessageListFragment.ARG_LAYOUT_ID";

    private ExpandableListView mSearchResultExpandableListView;
    private String mMatrixId;
    private MXSession mSession;
    private VectorRoomSummaryAdapter mAdapter;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public VectorRoomsSearchResultsListFragment() {
    }

    public static VectorRoomsSearchResultsListFragment newInstance(String aMatrixId) {
        VectorRoomsSearchResultsListFragment fragment = new VectorRoomsSearchResultsListFragment();
        Bundle args = new Bundle();

        //args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, aMatrixId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("VectorRoomsSearchResultsListFragment", "## onCreate()");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d("VectorRoomsSearchResultsListFragment", "## onCreateView()");
        // get fragment parameters
        Bundle fragArgs = getArguments();

        if(null != fragArgs) {
            mMatrixId = fragArgs.getString(ARG_MATRIX_ID);
            mSession = getSession(mMatrixId);
        }
        else {
            Log.e("VectorRoomsSearchResultsListFragment","## onCreateView(): can't create list view");
            return null;
        }

        // inflate the expandable view & create the adapter
        View view = inflater.inflate(R.layout.fragment_vector_search_rooms_list_fragment, container, false);
        mAdapter = new VectorRoomSummaryAdapter(getActivity(), mSession, R.layout.adapter_item_vector_recent_room, R.layout.adapter_item_vector_recent_header, this);

        // setup the expandable view with its adapter
        mSearchResultExpandableListView = (ExpandableListView) view.findViewById(R.id.search_result_exp_list_view);
        mSearchResultExpandableListView.setGroupIndicator(null);
        mSearchResultExpandableListView.setAdapter(mAdapter);

        return view;
    }


    public void searchPattern(final String pattern,  final MatrixMessageListFragment.OnSearchResultListener onSearchResultListener) {
        if (TextUtils.isEmpty(pattern)) {
            //mPattern = null;
            //mAdapter.clear();

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onSearchResultListener.onSearchSucceed(0);
                }
            });
        }
        else {
            // TODO add the right implementation
            mAdapter.notifyDataSetChanged();
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Toast.makeText(getActivity(), VectorUnifiedSearchActivity.NOT_IMPLEMENTED, Toast.LENGTH_SHORT).show();
                    onSearchResultListener.onSearchSucceed(10);
                }
            });
            // expand all groups
            mSearchResultExpandableListView.post(new Runnable() {
                @Override
                public void run() {
                    // expand all
                    int groupCount = mSearchResultExpandableListView.getExpandableListAdapter().getGroupCount();
                    for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
                        mSearchResultExpandableListView.expandGroup(groupIndex);
                    }
                }
            });
            /*super.searchPattern(pattern, new MatrixMessageListFragment.OnSearchResultListener() {
                @Override
                public void onSearchSucceed(int nbrMessages) {
                    // scroll to the bottom
                    scrollToBottom();

                    if (null != onSearchResultListener) {
                        onSearchResultListener.onSearchSucceed(nbrMessages);
                    }
                }

                @Override
                public void onSearchFailed() {
                    // clear the results list if teh search fails
                    mAdapter.clear();

                    if (null != onSearchResultListener) {
                        onSearchResultListener.onSearchFailed();
                    }
                }
            });*/
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
        Log.d("VectorRoomsSearchResultsListFragment","## onJoinRoom()");
    }

    @Override
    public void onRejectInvitation(MXSession session, String roomId) {
        Log.d("VectorRoomsSearchResultsListFragment","## onRejectInvitation()");
    }

    @Override
    public void onToggleRoomNotifications(MXSession session, String roomId) {
        Log.d("VectorRoomsSearchResultsListFragment","## onToggleRoomNotifications()");
    }

    @Override
    public void moveToFavorites(MXSession session, String roomId) {
        Log.d("VectorRoomsSearchResultsListFragment","## moveToFavorites()");
    }

    @Override
    public void moveToConversations(MXSession session, String roomId) {
        Log.d("VectorRoomsSearchResultsListFragment","## moveToConversations()");
    }

    @Override
    public void moveToLowPriority(MXSession session, String roomId) {
        Log.d("VectorRoomsSearchResultsListFragment","## moveToLowPriority()");
    }

    @Override
    public void onLeaveRoom(MXSession session, String roomId) {
        Log.d("VectorRoomsSearchResultsListFragment","## onLeaveRoom()");
    }
    // =============================================================================================


    @Override
    public String toString() {
        return super.toString();
    }

    /**
     * Called immediately after {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}
     * has returned, but before any saved state has been restored in to the view.
     * This gives subclasses a chance to initialize themselves once
     * they know their view hierarchy has been completely created.  The fragment's
     * view hierarchy is not however attached to its parent at this point.
     *
     * @param view               The View returned by {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("VectorRoomsSearchResultsListFragment","## onViewCreated()");
    }

    /**
     * Called when the fragment's activity has been created and this
     * fragment's view hierarchy instantiated.  It can be used to do final
     * initialization once these pieces are in place, such as retrieving
     * views or restoring state.  It is also useful for fragments that use
     * {@link #setRetainInstance(boolean)} to retain their instance,
     * as this callback tells the fragment when it is fully associated with
     * the new activity instance.  This is called after {@link #onCreateView}
     * and before {@link #onViewStateRestored(Bundle)}.
     *
     * @param savedInstanceState If the fragment is being re-created from
     *                           a previous saved state, this is the state.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d("VectorRoomsSearchResultsListFragment", "## onActivityCreated()");
    }

    /**
     * Called to ask the fragment to save its current dynamic state, so it
     * can later be reconstructed in a new instance of its process is
     * restarted.  If a new instance of the fragment later needs to be
     * created, the data you place in the Bundle here will be available
     * in the Bundle given to {@link #onCreate(Bundle)},
     * {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}, and
     * {@link #onActivityCreated(Bundle)}.
     * <p/>
     * <p>This corresponds to {@link Activity#onSaveInstanceState(Bundle)
     * Activity.onSaveInstanceState(Bundle)} and most of the discussion there
     * applies here as well.  Note however: <em>this method may be called
     * at any time before {@link #onDestroy()}</em>.  There are many situations
     * where a fragment may be mostly torn down (such as when placed on the
     * back stack with no UI showing), but its state will not be saved until
     * its owning activity actually needs to save its state.
     *
     * @param outState Bundle in which to place your saved state.
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d("VectorRoomsSearchResultsListFragment", "## onSaveInstanceState()");
    }

    /**
     * Called when the Fragment is no longer resumed.  This is generally
     * tied to {@link Activity#onPause() Activity.onPause} of the containing
     * Activity's lifecycle.
     */
    @Override
    public void onPause() {
        super.onPause();
        Log.d("VectorRoomsSearchResultsListFragment", "## onPause()");
    }

    /**
     * Called when the Fragment is no longer started.  This is generally
     * tied to {@link Activity#onStop() Activity.onStop} of the containing
     * Activity's lifecycle.
     */
    @Override
    public void onStop() {
        super.onStop();
        Log.d("VectorRoomsSearchResultsListFragment", "## onStop()");
    }

    /**
     * Called when the view previously created by {@link #onCreateView} has
     * been detached from the fragment.  The next time the fragment needs
     * to be displayed, a new view will be created.  This is called
     * after {@link #onStop()} and before {@link #onDestroy()}.  It is called
     * <em>regardless</em> of whether {@link #onCreateView} returned a
     * non-null view.  Internally it is called after the view's state has
     * been saved but before it has been removed from its parent.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d("VectorRoomsSearchResultsListFragment", "## onDestroyView()");
    }

    /**
     * Called when the fragment is no longer attached to its activity.  This
     * is called after {@link #onDestroy()}.
     */
    @Override
    public void onDetach() {
        super.onDetach();
        Log.d("VectorRoomsSearchResultsListFragment", "## onDetach()");
    }

    /**
     * Called when a fragment is first attached to its activity.
     * {@link #onCreate(Bundle)} will be called after this.
     *
     * @param activity
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d("VectorRoomsSearchResultsListFragment", "## onAttach()");
    }

    /**
     * Called when the fragment is visible to the user and actively running.
     * This is generally
     * tied to {@link Activity#onResume() Activity.onResume} of the containing
     * Activity's lifecycle.
     */
    @Override
    public void onResume() {
        super.onResume();
        Log.d("VectorRoomsSearchResultsListFragment", "## onResume()");
    }

    /**
     * Called when the Fragment is visible to the user.  This is generally
     * tied to {@link Activity#onStart() Activity.onStart} of the containing
     * Activity's lifecycle.
     */
    @Override
    public void onStart() {
        super.onStart();
        Log.d("VectorRoomsSearchResultsListFragment", "## onStart()");
    }
}
