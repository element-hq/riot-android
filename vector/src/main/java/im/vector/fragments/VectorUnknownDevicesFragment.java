/*
 * Copyright 2017 Vector Creations Ltd
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ExpandableListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.adapters.VectorUnknownDevicesAdapter;

public class VectorUnknownDevicesFragment extends DialogFragment {
    private static final String ARG_SESSION_ID = "VectorUnknownDevicesFragment.ARG_SESSION_ID";

    /**
     * Define the SendAnyway button listener
     */
    public interface IUnknownDevicesSendAnywayListener {
        // the "Send Anyway" button has been tapped
        void onSendAnyway();
    }

    private static MXUsersDevicesMap<MXDeviceInfo> mUnknownDevicesMap = null;

    private static IUnknownDevicesSendAnywayListener mListener = null;

    public static VectorUnknownDevicesFragment newInstance(String sessionId, MXUsersDevicesMap<MXDeviceInfo> unknownDevicesMap, IUnknownDevicesSendAnywayListener listener) {
        VectorUnknownDevicesFragment f = new VectorUnknownDevicesFragment();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
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
    }

    // current session
    private MXSession mSession;
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
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        View v = inflater.inflate(R.layout.dialog_unknown_devices, null);
        mExpandableListView = (ExpandableListView) v.findViewById(R.id.unknown_devices_list_view);

        mDevicesList = getDevicesList();
        final VectorUnknownDevicesAdapter adapter = new VectorUnknownDevicesAdapter(getContext(), mDevicesList);

        adapter.setListener(new VectorUnknownDevicesAdapter.IVerificationAdapterListener() {
            /**
             * Refresh the adapter
             */
            private void refresh() {
                adapter.notifyDataSetChanged();
            }

            /**
             * Common callback
             */
            final ApiCallback<Void> mCallback = new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    refresh();
                }

                @Override
                public void onNetworkError(Exception e) {
                    refresh();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    refresh();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    refresh();
                }
            };

            @Override
            public void OnVerifyDeviceClick(MXDeviceInfo aDeviceInfo) {
                switch (aDeviceInfo.mVerified) {
                    case MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED:
                        mSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, aDeviceInfo.deviceId, aDeviceInfo.userId, mCallback);
                        break;

                    case MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED:
                    default: // Blocked
                        CommonActivityUtils.displayDeviceVerificationDialog(aDeviceInfo, aDeviceInfo.userId, mSession, getActivity(), mCallback);
                        break;
                }
            }

            @Override
            public void OnBlockDeviceClick(MXDeviceInfo aDeviceInfo) {
                if (aDeviceInfo.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED) {
                    mSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, aDeviceInfo.deviceId, aDeviceInfo.userId, mCallback);
                } else {
                    mSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, aDeviceInfo.deviceId, aDeviceInfo.userId, mCallback);
                }
                refresh();
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

        builder.setView(v).setTitle(R.string.unknown_devices_alert_title);

        if (null != mListener) {
            // Add action buttons
            builder.setPositiveButton(R.string.send_anyway, new DialogInterface.OnClickListener() {
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
            // Add action buttons
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    // nothing : everything will be done on onDismiss()
                }
            });
        }

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
