/*
 * Copyright 2015 OpenMarket Ltd
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import im.vector.R;

/**
 * An adapter which can display room information.
 */
public class MemberDetailsAdapter extends ArrayAdapter<MemberDetailsAdapter.AdapterMemberActionItems> {

    private static final int NUM_ROW_TYPES = 2;

    private static final int ROW_TYPE_HEADER = 0;
    private static final int ROW_TYPE_ENTRY = 1;

    private LayoutInflater mLayoutInflater;
    private int mRowItemLayoutResourceId;
    private Context mContext;
    private IEnablingActions mActionListener;

    /**
     * Interface proxy to get the power rights from the host activity.
     */
    public interface IEnablingActions {
        /**
         * Indicate if the row associated with the aActionType must be enabled.
         * @param aActionType the action type
         * @return true if the corresponding row is enabled, false otherwise
         */
        public boolean isActionEnabled(int aActionType);

        /**
         *
         * @param aActionType the action type
         */
        public void performAction(int aActionType);
    }

    static public class AdapterMemberActionItems {
        final public int mIconResourceId;
        final public String mActionDescText;
        final public int mActionType; // ban, kick..

        public AdapterMemberActionItems(int aIconResourceId, String aText, int aActionType) {
            mIconResourceId = aIconResourceId;
            mActionDescText = aText;
            mActionType = aActionType;
        }
    }


    /**
     * Construct an adapter to display a drawer adapter
     * @param context the context
     * @param aRowItemLayoutResourceId the entry layout id
     */
    public MemberDetailsAdapter(Context context, int aRowItemLayoutResourceId, List<AdapterMemberActionItems> aListItems) {
        super(context, aRowItemLayoutResourceId, aListItems);
        mContext = context;
        mActionListener = (IEnablingActions) context;
        mRowItemLayoutResourceId = aRowItemLayoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);

        //populateItemsList();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowItemLayoutResourceId, parent, false);
        }

        // UI widget binding
        TextView textView = (TextView) convertView.findViewById(R.id.adapter_member_details_action_text);
        ImageView imageView = (ImageView) convertView.findViewById(R.id.adapter_member_details_icon);

        // set the icon and the action text
        final AdapterMemberActionItems entry = getItem(position);
        textView.setText(entry.mActionDescText);
        imageView.setImageResource(entry.mIconResourceId);

        // set action listener and enable the view if it's allowed
        final boolean isActionEnabled = mActionListener.isActionEnabled(entry.mActionType);
        convertView.setEnabled(isActionEnabled);
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ((null != mActionListener) && (isActionEnabled)) {
                    mActionListener.performAction(entry.mActionType);
                }
            }
        });

        return convertView;
    }
}
