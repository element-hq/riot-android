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
import android.support.v4.app.DialogFragment;

import android.content.DialogInterface;
import android.os.Bundle;

import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;

import android.view.ViewGroup;
import android.widget.ExpandableListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.adapters.VectorUnknownDevicesAdapter;

public class VectorUnknownDevicesFragment extends DialogFragment {
    private static final String LOG_TAG = "VUnknownFrgt";

    private static final String ARG_SESSION_ID = "VectorUnknownDevicesFragment.ARG_SESSION_ID";

    private static MXUsersDevicesMap<MXDeviceInfo> mUnknownDevicesMap;

    public static VectorUnknownDevicesFragment newInstance(String sessionId, MXUsersDevicesMap<MXDeviceInfo> unknownDevicesMap) {
        VectorUnknownDevicesFragment f = new VectorUnknownDevicesFragment();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        // cannot serialize unknownDevicesMap if it is too large
        mUnknownDevicesMap = unknownDevicesMap;
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSession = Matrix.getMXSession(getActivity(), getArguments().getString(ARG_SESSION_ID));
    }

    private MXSession mSession;
    private ExpandableListView mExpandableListView;

    /**
     * Convert a MXUsersDevicesMap to a list of List
     *
     * @return the list of list
     */
    private static List<Pair<String, List<MXDeviceInfo>>> getDevicesList() {
        List<Pair<String, List<MXDeviceInfo>>> res = new ArrayList<>();

        List<String> userIds = mUnknownDevicesMap.getUserIds();

        for (String userId : userIds) {
            List<MXDeviceInfo> deviceInfos = new ArrayList<>();
            List<String> deviceIds = mUnknownDevicesMap.getUserDeviceIds(userId);

            for (String deviceId : deviceIds) {
                deviceInfos.add(mUnknownDevicesMap.getObject(deviceId, userId));
            }
            res.add(new Pair<>(userId, deviceInfos));
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

        final List<Pair<String, List<MXDeviceInfo>>> devicesList = getDevicesList();
        final VectorUnknownDevicesAdapter adapter = new VectorUnknownDevicesAdapter(getContext(), devicesList);

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

        // put the text in the header to make it scrollable
        final View headerView = v.findViewById(R.id.unknown_devices_header_view);
        if (headerView.getParent() instanceof ViewGroup) {
            ((ViewGroup)headerView.getParent()).removeView(headerView);
            mExpandableListView.addHeaderView(headerView);
        }

        builder.setView(v)
                .setTitle(R.string.unknown_devices_alert_title)
                // Add action buttons
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        List<MXDeviceInfo> dis = new ArrayList<>();

                        for (Pair<String, List<MXDeviceInfo>> item : devicesList) {
                            dis.addAll(item.second);
                        }

                        mSession.getCrypto().setDevicesKnown(dis, null);
                        mUnknownDevicesMap = null;
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mUnknownDevicesMap = null;
                    }
                });
        return builder.create();
    }
}
