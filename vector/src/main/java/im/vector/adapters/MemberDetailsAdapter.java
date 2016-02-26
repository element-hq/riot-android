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

import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorMemberDetailsActivity;

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
        public boolean isItemActionEnabled(int aActionType);

        /**
         *
         * @param aActionType the action type
         */
        public void performItemAction(int aActionType);
    }

    /**
     * Recycle view holder class.
     */
    private static class MemberDetailsViewHolder {
        final ImageView mActionImageView;
        final TextView mActionDescTextView;

        MemberDetailsViewHolder(View aParentView){
            mActionImageView = (ImageView)aParentView.findViewById(R.id.adapter_member_details_icon);
            mActionDescTextView = (TextView) aParentView.findViewById(R.id.adapter_member_details_action_text);
        }
    }

    /**
     * List view data model class.
     * The model consists of an icon, a text and the associated internal action type.
     */
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
     * Construct an adapter where the items layout and the data model collection is provided
     *
     * @param aContext Android App context
     * @param aRowItemLayoutResourceId the layout of the list view item (row)
     */
    public MemberDetailsAdapter(Context aContext, int aRowItemLayoutResourceId) {
        super(aContext, aRowItemLayoutResourceId);
        mContext = aContext;
        mRowItemLayoutResourceId = aRowItemLayoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
    }

    public void setActionListener(IEnablingActions aActionListener){
        try {
            mActionListener = (IEnablingActions) aActionListener;
        }
        catch(ClassCastException e) {
            throw new ClassCastException(aActionListener.toString() + " must implement IEnablingActions");
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MemberDetailsViewHolder viewHolder;

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowItemLayoutResourceId, parent, false);
            viewHolder = new MemberDetailsViewHolder(convertView);
            convertView.setTag(viewHolder);
        }
        else {
            // recylce previous view..
            viewHolder = (MemberDetailsViewHolder)convertView.getTag();
        }

        // get current item
        final AdapterMemberActionItems currentItem = getItem(position);
        if(null != currentItem) {
            // update the icon and the action text
            viewHolder.mActionDescTextView.setText(currentItem.mActionDescText);
            viewHolder.mActionImageView.setImageResource(currentItem.mIconResourceId);

            // update the text colour: specific colour is required for the remove action
            int colourTxt = mContext.getResources().getColor(R.color.material_grey_900);
            if(VectorMemberDetailsActivity.ITEM_ACTION_REMOVE_FROM_ROOM == currentItem.mActionType) {
                colourTxt = mContext.getResources().getColor(R.color.vector_fuchsia_color);
            }
            viewHolder.mActionDescTextView.setTextColor(colourTxt);

            // set the listener
            if (null != mActionListener) {
                // is the action allowed according to the power levels?
                //final boolean isActionEnabled = mActionListener.isItemActionEnabled(currentItem.mActionType);
                //setEnabledItem(viewHolder.mActionDescTextView, isActionEnabled);

                // set the action associated to the item
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (null != mActionListener) {
                            mActionListener.performItemAction(currentItem.mActionType);
                        }
                    }
                });
            }
        }

        return convertView;
    }

    /**
     * Enable a view according to the aIsViewEnabled value. If aIsViewEnabled
     * is set to false the view is disabled and its opacity is half transparent.
     * If set to true, the view is enabled and its opacity is not set (full tranparent).
     *
     * @param aView the view to disa/enable
     * @param aIsViewEnabled enabling state value
     */
    private void setEnabledItem(View aView, boolean aIsViewEnabled) {
        if(null != aView){
            aView.setEnabled(aIsViewEnabled);
            float opacity = aIsViewEnabled?CommonActivityUtils.UTILS_OPACITY_NO_OPACITY :CommonActivityUtils.UTILS_OPACITY_HALPH_OPACITY;
            aView.setAlpha(opacity);
        }
    }
}
