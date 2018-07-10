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
import android.text.TextUtils;

import java.util.Comparator;
import java.util.List;

class KnownContactsAdapterSection extends AdapterSection<ParticipantAdapterItem> {

    // Tells if the search result is limited
    private boolean mIsLimited;
    private String mCustomHeaderExtra;

    public KnownContactsAdapterSection(Context context, String title, int headerSubViewResId, int contentResId, int headerViewType,
                                       int contentViewType, List<ParticipantAdapterItem> items, Comparator<ParticipantAdapterItem> comparator) {
        super(context, title, headerSubViewResId, contentResId, headerViewType, contentViewType, items, comparator);
    }

    /**
     * Tells that the search result is limited
     *
     * @param isLimited true if limited
     */
    public void setIsLimited(boolean isLimited) {
        mIsLimited = isLimited;
    }

    /**
     * Defines a custom extra string
     *
     * @param extraHeader the extra header string
     */
    public void setCustomHeaderExtra(String extraHeader) {
        mCustomHeaderExtra = extraHeader;
    }

    @Override
    protected void updateTitle() {
        String newTitle;

        if (getNbItems() > 0) {
            if (!TextUtils.isEmpty(mCustomHeaderExtra)) {
                newTitle = mTitle.concat("   " + mCustomHeaderExtra + ", " + getNbItems());
            } else if (!mIsLimited) {
                newTitle = mTitle.concat("   " + getNbItems());
            } else {
                newTitle = mTitle.concat("   >" + getNbItems());
            }
        } else {
            newTitle = mTitle;
        }

        formatTitle(newTitle);
    }
}
