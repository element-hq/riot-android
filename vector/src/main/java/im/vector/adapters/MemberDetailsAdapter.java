/*
 * Copyright 2016 OpenMarket Ltd
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
import im.vector.activity.VectorMemberDetailsActivity;

/**
 * An adapter which can display room information.
 */
public class MemberDetailsAdapter extends ArrayAdapter<MemberDetailsAdapter.AdapterMemberActionItems> {

    private LayoutInflater mLayoutInflater;
    private int mRowItemLayoutResourceId;
    private Context mContext;
    private IEnablingActions mActionListener;

    /**
     * Interface proxy to get the power rights from the host activity.
     */
    public interface IEnablingActions {
        /**
         * @param aActionType the action type
         */
        void performItemAction(int aActionType);
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

    /**
     * Set the action listener
     * @param aActionListener the action listenet
     */
    public void setActionListener(IEnablingActions aActionListener){
        mActionListener = aActionListener;
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
            // recycle previous view..
            viewHolder = (MemberDetailsViewHolder)convertView.getTag();
        }

        // get current item
        final AdapterMemberActionItems currentItem = getItem(position);

        if (null != currentItem) {
            // update the icon and the action text
            viewHolder.mActionDescTextView.setText(currentItem.mActionDescText);
            viewHolder.mActionImageView.setImageResource(currentItem.mIconResourceId);

            // update the text colour: specific colour is required for the remove action
            int colourTxt = mContext.getResources().getColor(R.color.material_grey_900);

            if (VectorMemberDetailsActivity.ITEM_ACTION_KICK == currentItem.mActionType) {
                colourTxt = mContext.getResources().getColor(R.color.vector_fuchsia_color);
            }

            viewHolder.mActionDescTextView.setTextColor(colourTxt);

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

        return convertView;
    }
}
