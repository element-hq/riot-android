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

import java.util.Comparator;
import java.util.List;

public class GroupAdapterSection<T> extends AdapterSection<T> {

    public GroupAdapterSection(Context context , String title, int headerSubViewResId, int contentResId, int headerViewType,
                          int contentViewType, List<T> items, Comparator<T> comparator) {
        super(context, title, headerSubViewResId, contentResId, headerViewType, contentViewType, items, comparator);
    }

    /**
     * Update the title depending on the number of items
     */
    void updateTitle() {
        String newTitle;

        // the group members / rooms lists are estimated
        // it seems safer to display the count only for the filtered lists
        if ((getItems().size() != getFilteredItems().size()) && (getNbItems() > 0)) {
            newTitle = mTitle.concat("   " + getNbItems());
        } else {
            newTitle = mTitle;
        }

        formatTitle(newTitle);
    }
}
