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

package im.vector.adapters;

import android.content.Context;

import org.matrix.androidsdk.util.Log;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.crypto.data.MXDeviceInfo;

import java.util.ArrayList;
import java.util.List;

import im.vector.R;

/**
 * This class displays a list of unknowns e2e devices.
 */
public class VectorUnknownDevicesAdapter extends BaseExpandableListAdapter {

    private static final String LOG_TAG = "VUnknownDevicesAdapter";

    // devices verification listener
    public interface IVerificationAdapterListener {
        /**
         * Verify device button handler
         *
         * @param aDeviceInfo device info
         */
        void OnVerifyDeviceClick(MXDeviceInfo aDeviceInfo);

        /**
         * Block device button handler
         *
         * @param aDeviceInfo device info
         */
        void OnBlockDeviceClick(MXDeviceInfo aDeviceInfo);
    }

    // context
    private final Context mContext;
    // layout inflater
    private final LayoutInflater mLayoutInflater;
    // devices list
    private final List<Pair<String, List<MXDeviceInfo>>> mUnknownDevicesList;
    // listener
    private IVerificationAdapterListener mListener;

    /**
     * Constructor
     *
     * @param aContext       the context
     * @param unknownDevices the unknown devices list
     */
    public VectorUnknownDevicesAdapter(Context aContext, List<Pair<String, List<MXDeviceInfo>>> unknownDevices) {
        // init internal fields
        mContext = aContext;
        mLayoutInflater = LayoutInflater.from(mContext);
        mUnknownDevicesList = (null == unknownDevices) ? new ArrayList<Pair<String, List<MXDeviceInfo>>>() : unknownDevices;
    }

    /**
     * Update the listener
     *
     * @param listener the listener
     */
    public void setListener(IVerificationAdapterListener listener) {
        mListener = listener;
    }

    /**
     * Compute the name of the group according to its position.
     *
     * @param groupPosition index of the section
     * @return group title corresponding to the index
     */
    private String getGroupTitle(int groupPosition) {
        // should not happen
        if (groupPosition >= mUnknownDevicesList.size()) {
            return "???";
        }

        return mUnknownDevicesList.get(groupPosition).first;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public int getGroupCount() {
        return mUnknownDevicesList.size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return getGroupTitle(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return getGroupTitle(groupPosition).hashCode();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return mUnknownDevicesList.get(groupPosition).second.size();
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return null;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return 0L;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (null == convertView) {
            convertView = this.mLayoutInflater.inflate(R.layout.adapter_item_vector_unknown_devices_header, null);
        }

        TextView sectionNameTxtView = (TextView) convertView.findViewById(R.id.heading);

        if (null != sectionNameTxtView) {
            sectionNameTxtView.setText(getGroupTitle(groupPosition));
        }

        ImageView imageView = (ImageView) convertView.findViewById(R.id.heading_image);

        if (isExpanded) {
            imageView.setImageResource(R.drawable.ic_material_expand_less_black);
        } else {
            imageView.setImageResource(R.drawable.ic_material_expand_more_black);
        }
        return convertView;
    }


    /**
     * Compute the View that should be used to render the child,
     * given its position and its group’s position
     */
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.adapter_item_member_details_devices, parent, false);
        }

        final MXDeviceInfo deviceItem = mUnknownDevicesList.get(groupPosition).second.get(childPosition);

        // retrieve the ui items
        final Button buttonVerify = (Button) convertView.findViewById(R.id.button_verify);
        final Button buttonBlock = (Button) convertView.findViewById(R.id.button_block);
        final TextView deviceNameTextView = (TextView) convertView.findViewById(R.id.device_name);
        final TextView deviceIdTextView = (TextView) convertView.findViewById(R.id.device_id);
        final ImageView e2eIconView = (ImageView) convertView.findViewById(R.id.device_e2e_icon);

        buttonVerify.setTransformationMethod(null);
        buttonBlock.setTransformationMethod(null);

        // set devices text names
        deviceNameTextView.setText(deviceItem.displayName());
        deviceIdTextView.setText(deviceItem.deviceId);

        // display e2e icon status
        switch (deviceItem.mVerified) {
            case MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED:
                e2eIconView.setImageResource(R.drawable.e2e_verified);
                break;

            case MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED:
                e2eIconView.setImageResource(R.drawable.e2e_blocked);
                break;

            default:
                e2eIconView.setImageResource(R.drawable.e2e_warning);
                break;
        }

        // display buttons label according to verification status
        switch (deviceItem.mVerified) {
            case MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED:
                buttonVerify.setText(R.string.encryption_information_verify);
                buttonBlock.setText(R.string.encryption_information_block);
                break;

            case MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED:
                buttonVerify.setText(R.string.encryption_information_unverify);
                buttonBlock.setText(R.string.encryption_information_block);
                break;

            case MXDeviceInfo.DEVICE_VERIFICATION_UNKNOWN:
                buttonVerify.setText(R.string.encryption_information_verify);
                buttonBlock.setText(R.string.encryption_information_block);
                break;

            default: // Blocked
                buttonVerify.setText(R.string.encryption_information_verify);
                buttonBlock.setText(R.string.encryption_information_unblock);
                break;
        }

        buttonVerify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    try {
                        mListener.OnVerifyDeviceClick(deviceItem);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## getChildView() : OnVerifyDeviceClick fails " + e.getMessage());
                    }
                }
            }
        });

        buttonBlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    try {
                        mListener.OnBlockDeviceClick(deviceItem);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## getChildView() : OnBlockDeviceClick fails " + e.getMessage());
                    }
                }
            }
        });

        return convertView;
    }
}
