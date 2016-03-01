package im.vector.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ListView;

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
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.activity.VectorPublicRoomsActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.activity.VectorUnifiedSearchActivity;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorAddParticipantsAdapter;
import im.vector.adapters.VectorRoomSummaryAdapter;


public class VectorSearchPeopleListFragment extends Fragment {
    // log tag
    private static String LOG_TAG = "VectorSearchPeopleListFragment";

    public static final String ARG_MATRIX_ID = "VectorSearchPeopleListFragment.ARG_MATRIX_ID";
    public static final String ARG_LAYOUT_ID = "VectorSearchPeopleListFragment.ARG_LAYOUT_ID";

    // the session
    private MXSession mSession;

    private ListView mPeopleListView;

    private VectorAddParticipantsAdapter mAdapter;


    /**
     * Static constructor
     * @param matrixId the matrix id
     * @return a VectorSearchPeopleListFragment instance
     */
    public static VectorSearchPeopleListFragment newInstance(String matrixId, int layoutResId) {
        VectorSearchPeopleListFragment f = new VectorSearchPeopleListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Bundle args = getArguments();


        String matrixId = args.getString(ARG_MATRIX_ID);
        mSession = Matrix.getInstance(getActivity()).getSession(matrixId);

        if (null == mSession) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mPeopleListView = (ListView)v.findViewById(R.id.search_people_list);
        mAdapter = new VectorAddParticipantsAdapter(getActivity(), R.layout.adapter_item_vector_add_participants, mSession, null, false, mSession.getMediasCache());
        mPeopleListView.setAdapter(mAdapter);
        
        mAdapter.setOnParticipantsListener(new VectorAddParticipantsAdapter.OnParticipantsListener() {
            @Override
            public void onRemoveClick(ParticipantAdapterItem participant) {
            }

            @Override
            public void onLeaveClick() {
            }

            @Override
            public void onSelectUserId(String userId) {

            }

            @Override
            public void onClick(int position) {
                ParticipantAdapterItem item = mAdapter.getItem(position);

                Intent startRoomInfoIntent = new Intent(getActivity(), VectorMemberDetailsActivity.class);
                startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, item.mUserId);
                startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                startActivity(startRoomInfoIntent);
            }
        });

        return v;
    }

    /**
     * Search a pattern in the room
     * @param pattern
     * @param onSearchResultListener
     */
    public void searchPattern(final String pattern, final MatrixMessageListFragment.OnSearchResultListener onSearchResultListener) {
        if (TextUtils.isEmpty(pattern)) {
            mPeopleListView.setVisibility(View.GONE);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onSearchResultListener.onSearchSucceed(0);
                }
            });
        } else {
            mAdapter.setSearchedPattern(pattern);

            mPeopleListView.post(new Runnable() {
                @Override
                public void run() {
                    mPeopleListView.setVisibility(View.VISIBLE);
                    onSearchResultListener.onSearchSucceed(1);
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
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
