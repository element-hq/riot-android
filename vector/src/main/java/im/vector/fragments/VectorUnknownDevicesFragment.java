/*
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;

import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.SASVerificationActivity;
import im.vector.adapters.VectorUnknownDevicesAdapter;

public class VectorUnknownDevicesFragment extends DialogFragment {
    private static final String ARG_SESSION_ID = "VectorUnknownDevicesFragment.ARG_SESSION_ID";
    private static final String ARG_IS_FOR_CALLING = "VectorUnknownDevicesFragment.ARG_IS_FOR_CALLING";


    private static final int DEVICE_VERIF_REQ_CODE = 12;

    /**
     * Define the SendAnyway button listener
     */
    public interface IUnknownDevicesSendAnywayListener {
        // the "Send Anyway" button has been tapped
        void onSendAnyway();
    }

    private static MXUsersDevicesMap<MXDeviceInfo> mUnknownDevicesMap = null;

    private static IUnknownDevicesSendAnywayListener mListener = null;

    /**
     * @param sessionId
     * @param unknownDevicesMap
     * @param isForCalling      true when the user wants to start a call
     * @return
     */
    public static VectorUnknownDevicesFragment newInstance(String sessionId,
                                                           MXUsersDevicesMap<MXDeviceInfo> unknownDevicesMap,
                                                           boolean isForCalling,
                                                           IUnknownDevicesSendAnywayListener listener) {
        VectorUnknownDevicesFragment f = new VectorUnknownDevicesFragment();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        args.putBoolean(ARG_IS_FOR_CALLING, isForCalling);
        // cannot serialize unknownDevicesMap if it is too large
        mUnknownDevicesMap = unknownDevicesMap;
        // idem for the listener
        mListener = listener;
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSession = Matrix.getMXSession(getActivity(), getArguments().getString(ARG_SESSION_ID));
        mIsForCalling = getArguments().getBoolean(ARG_IS_FOR_CALLING);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == DEVICE_VERIF_REQ_CODE) {
            // Update the status
            String otherUserId = SASVerificationActivity.Companion.getOtherUserId(data);
            String otherDeviceId = SASVerificationActivity.Companion.getOtherDeviceId(data);

            if (mDevicesList != null && otherUserId != null && otherDeviceId != null) {
                for (Pair<String, List<MXDeviceInfo>> pair : mDevicesList) {
                    if (pair.first.equals(otherUserId)) {
                        for (MXDeviceInfo mxDeviceInfo : pair.second) {
                            if (mxDeviceInfo.deviceId.equals(otherDeviceId)) {
                                mxDeviceInfo.mVerified = MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED;
                            }
                        }
                    }
                }
            }

            ExpandableListAdapter adapter = mExpandableListView.getExpandableListAdapter();
            if (adapter instanceof VectorUnknownDevicesAdapter) {
                ((VectorUnknownDevicesAdapter) adapter).notifyDataSetChanged();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // current session
    private MXSession mSession;
    // true when the user want to start a call
    private boolean mIsForCalling;
    // list view
    private ExpandableListView mExpandableListView;
    // Devices list
    private List<Pair<String, List<MXDeviceInfo>>> mDevicesList;
    // Tells if the dialog has been closed by tapping on the "Send anyway" button
    private boolean mIsSendAnywayTapped = false;

    /**
     * Convert a MXUsersDevicesMap to a list of List
     *
     * @return the list of list
     */
    private static List<Pair<String, List<MXDeviceInfo>>> getDevicesList() {
        List<Pair<String, List<MXDeviceInfo>>> res = new ArrayList<>();

        // sanity check
        if (null != mUnknownDevicesMap) {
            List<String> userIds = mUnknownDevicesMap.getUserIds();

            for (String userId : userIds) {
                List<MXDeviceInfo> deviceInfos = new ArrayList<>();
                List<String> deviceIds = mUnknownDevicesMap.getUserDeviceIds(userId);

                for (String deviceId : deviceIds) {
                    deviceInfos.add(mUnknownDevicesMap.getObject(deviceId, userId));
                }
                res.add(new Pair<>(userId, deviceInfos));
            }
        }

        return res;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        View v = inflater.inflate(R.layout.dialog_unknown_devices, null);
        mExpandableListView = v.findViewById(R.id.unknown_devices_list_view);

        mDevicesList = getDevicesList();
        final VectorUnknownDevicesAdapter adapter = new VectorUnknownDevicesAdapter(getContext(), mDevicesList);

        adapter.setListener(new VectorUnknownDevicesAdapter.IVerificationAdapterListener() {
            /**
             * Refresh the adapter
             */
            private void refresh() {
                adapter.notifyDataSetChanged();
            }

            @Override
            public void OnVerifyDeviceClick(MXDeviceInfo aDeviceInfo) {
                switch (aDeviceInfo.mVerified) {
                    case MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED:
                        mSession.getCrypto()
                                .setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED,
                                        aDeviceInfo.deviceId,
                                        aDeviceInfo.userId,
                                        new SimpleApiCallback<Void>() {
                                            @Override
                                            public void onSuccess(Void info) {
                                                aDeviceInfo.mVerified = MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED;
                                                refresh();
                                            }
                                        });
                        break;

                    case MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED:
                    default: // Blocked
                        CommonActivityUtils.displayDeviceVerificationDialog(aDeviceInfo,
                                aDeviceInfo.userId,
                                mSession,
                                getActivity(),
                                VectorUnknownDevicesFragment.this,
                                DEVICE_VERIF_REQ_CODE
                        );
                        break;
                }
            }

            @Override
            public void OnBlockDeviceClick(MXDeviceInfo aDeviceInfo) {
                if (aDeviceInfo.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED) {
                    mSession.getCrypto()
                            .setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED,
                                    aDeviceInfo.deviceId,
                                    aDeviceInfo.userId,
                                    new SimpleApiCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void info) {
                                            aDeviceInfo.mVerified = MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED;
                                            refresh();
                                        }
                                    });
                } else {
                    mSession.getCrypto()
                            .setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED,
                                    aDeviceInfo.deviceId,
                                    aDeviceInfo.userId,
                                    new SimpleApiCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void info) {
                                            aDeviceInfo.mVerified = MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED;
                                            refresh();
                                        }
                                    });
                }
            }
        });

        mExpandableListView.addHeaderView(inflater.inflate(R.layout.dialog_unknown_devices_header, null));
        mExpandableListView.setGroupIndicator(null);
        mExpandableListView.setAdapter(adapter);
        // expand each group by default
        mExpandableListView.post(new Runnable() {
            @Override
            public void run() {
                int count = adapter.getGroupCount();

                for (int i = 0; i < count; i++) {
                    mExpandableListView.expandGroup(i);
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setView(v)
                .setTitle(R.string.unknown_devices_alert_title);

        mIsSendAnywayTapped = false;

        if (null != mListener) {
             //Add action buttons
            int messageResId = mIsForCalling ? R.string.call_anyway : R.string.send_anyway;
            builder.setPositiveButton(messageResId, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    mIsSendAnywayTapped = true;
                }
            });
            builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    // nothing : everything will be done on onDismiss()
                }
            });

        } else {
             //Add action buttons
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                     //nothing : everything will be done on onDismiss()
                }
            });
        }

        builder.create().dismiss();

        return builder.create();

    }
    @Override
    public void dismissAllowingStateLoss() {
        // reported by GA
        if (null != getFragmentManager()) {
            super.dismissAllowingStateLoss();
        }
        // Ensure that the map is released when the fragment is dismissed.
        mUnknownDevicesMap = null;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        // whatever the user clicks
        // we assume that the devices are now known because they have been displayed.
        setDevicesKnown(mDevicesList);
    }

    /**
     * Update the devices verifications status.
     *
     * @param devicesList the devices list.
     */
    private void setDevicesKnown(List<Pair<String, List<MXDeviceInfo>>> devicesList) {
        if (null != mUnknownDevicesMap) {
            // release the static members list
            mUnknownDevicesMap = null;

            List<MXDeviceInfo> dis = new ArrayList<>();

            for (Pair<String, List<MXDeviceInfo>> item : devicesList) {
                dis.addAll(item.second);
            }

            mSession.getCrypto().setDevicesKnown(dis, new ApiCallback<Void>() {
                // common method
                private void onDone() {
                    if (mIsSendAnywayTapped && (null != mListener)) {
                        mListener.onSendAnyway();
                    }
                    mListener = null;
                    // ensure that the fragment won't be displayed anymore
                    if (isAdded() && isResumed()) {
                        dismissAllowingStateLoss();
                    }
                }

                @Override
                public void onSuccess(Void info) {
                    onDone();
                }

                @Override
                public void onNetworkError(Exception e) {
                    onDone();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onDone();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onDone();
                }
            });
        }
    }
}
