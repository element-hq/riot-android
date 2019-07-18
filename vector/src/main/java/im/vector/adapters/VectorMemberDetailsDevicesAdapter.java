/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;

/**
 * This class displays a list of members to create a room.
 */
public class VectorMemberDetailsDevicesAdapter extends ArrayAdapter<MXDeviceInfo> {

    private static final String LOG_TAG = VectorMemberDetailsDevicesAdapter.class.getSimpleName();

    // remove participants listener
    public interface IDevicesAdapterListener {
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

    // layout info
    private final LayoutInflater mLayoutInflater;

    // used layouts
    private final int mItemLayoutResourceId;

    // the events listener
    private IDevicesAdapterListener mActivityListener;

    // the oneself device Id
    final private String myDeviceId;

    /**
     * Constructor
     *
     * @param aContext              app context
     * @param aItemLayoutResourceId layout id to be displayed on each row
     * @param aSession              session
     */
    public VectorMemberDetailsDevicesAdapter(Context aContext, int aItemLayoutResourceId, MXSession aSession) {
        super(aContext, aItemLayoutResourceId);

        mLayoutInflater = LayoutInflater.from(aContext);
        mItemLayoutResourceId = aItemLayoutResourceId;

        if (null != aSession.getCredentials()) {
            myDeviceId = aSession.getCredentials().deviceId;
        } else {
            myDeviceId = null;
        }
    }

    /**
     * Defines a listener.
     *
     * @param aListener the new listener.
     */
    public void setDevicesAdapterListener(IDevicesAdapterListener aListener) {
        mActivityListener = aListener;
    }

    @Override
    public void notifyDataSetChanged() {
        // always display the oneself device on top
        if (null != myDeviceId) {
            setNotifyOnChange(false);

            MXDeviceInfo deviceInfo = null;

            for (int i = 0; i < getCount(); i++) {
                if (TextUtils.equals(myDeviceId, getItem(i).deviceId)) {
                    deviceInfo = getItem(i);
                    break;
                }
            }

            if (null != deviceInfo) {
                remove(deviceInfo);
                insert(deviceInfo, 0);
            }

            setNotifyOnChange(true);
        }

        super.notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MemberDetailsDevicesViewHolder holder;

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mItemLayoutResourceId, parent, false);

            holder = new MemberDetailsDevicesViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (MemberDetailsDevicesViewHolder) convertView.getTag();
        }

        final MXDeviceInfo deviceItem = getItem(position);

        // set devices text names
        holder.deviceNameTextView.setText(deviceItem.displayName());
        holder.deviceIdTextView.setText(deviceItem.deviceId);

        // display e2e icon status
        switch (deviceItem.mVerified) {
            case MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED:
                holder.e2eIconView.setImageResource(R.drawable.e2e_verified);
                break;

            case MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED:
                holder.e2eIconView.setImageResource(R.drawable.e2e_blocked);
                break;

            default:
                holder.e2eIconView.setImageResource(R.drawable.e2e_warning);
                break;
        }

        // display buttons label according to verification status
        switch (deviceItem.mVerified) {
            case MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED:
                holder.buttonVerify.setText(R.string.encryption_information_verify);
                holder.buttonBlock.setText(R.string.encryption_information_block);
                break;

            case MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED:
                holder.buttonVerify.setText(R.string.encryption_information_unverify);
                holder.buttonBlock.setText(R.string.encryption_information_block);
                break;

            case MXDeviceInfo.DEVICE_VERIFICATION_UNKNOWN:
                holder.buttonVerify.setText(R.string.encryption_information_verify);
                holder.buttonBlock.setText(R.string.encryption_information_block);
                break;

            default: // Blocked
                holder.buttonVerify.setText(R.string.encryption_information_verify);
                holder.buttonBlock.setText(R.string.encryption_information_unblock);
                break;
        }

        holder.buttonVerify.setVisibility(TextUtils.equals(myDeviceId, deviceItem.deviceId) ? View.INVISIBLE : View.VISIBLE);
        holder.buttonBlock.setVisibility(TextUtils.equals(myDeviceId, deviceItem.deviceId) ? View.INVISIBLE : View.VISIBLE);

        holder.buttonVerify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mActivityListener) {
                    try {
                        mActivityListener.OnVerifyDeviceClick(deviceItem);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## getView() : OnVerifyDeviceClick fails " + e.getMessage(), e);
                    }
                }
            }
        });

        holder.buttonBlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mActivityListener) {
                    try {
                        mActivityListener.OnBlockDeviceClick(deviceItem);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## getView() : OnBlockDeviceClick fails " + e.getMessage(), e);
                    }
                }
            }
        });

        return convertView;
    }

    class MemberDetailsDevicesViewHolder {
        @BindView(R.id.button_verify)
        Button buttonVerify;

        @BindView(R.id.button_block)
        Button buttonBlock;

        @BindView(R.id.device_name)
        TextView deviceNameTextView;

        @BindView(R.id.device_id)
        TextView deviceIdTextView;

        @BindView(R.id.device_e2e_icon)
        ImageView e2eIconView;

        MemberDetailsDevicesViewHolder(View view) {
            ButterKnife.bind(this, view);

            buttonVerify.setTransformationMethod(null);
            buttonBlock.setTransformationMethod(null);
        }
    }
}
