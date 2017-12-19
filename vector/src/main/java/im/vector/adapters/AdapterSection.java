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
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.util.ThemeUtils;

public class AdapterSection<T> {

    final String mTitle;
    private SpannableString mTitleFormatted;
    // Place holder if no item for the section
    private String mNoItemPlaceholder;
    // Place holder if no result after search
    private String mNoResultPlaceholder;

    private final int mHeaderSubView;
    private final int mHeaderViewType;
    private final int mContentViewType;

    private final List<T> mItems;

    private final List<T> mFilteredItems;

    private final Comparator<T> mComparator;

    CharSequence mCurrentFilterPattern;

    private boolean mIsHiddenWhenEmpty;
    private boolean mIsHiddenWhenNoFilter;

    private Context mContext;

    public AdapterSection(Context context , String title, int headerSubViewResId, int contentResId, int headerViewType,
                          int contentViewType, List<T> items, Comparator<T> comparator) {
        mContext = context;
        mTitle = title;
        mItems = items;
        mFilteredItems = new ArrayList<>(items);
        mHeaderSubView = headerSubViewResId;

        mHeaderViewType = headerViewType;
        mContentViewType = contentViewType;
        mComparator = comparator;

        updateTitle();
    }

    /**
     * Update items list
     *
     * @param items
     * @param currentFilterPattern
     */
    public void setItems(List<T> items, CharSequence currentFilterPattern) {
        if (mComparator != null) {
            Collections.sort(items, mComparator);
        }
        mItems.clear();
        mItems.addAll(items);

        setFilteredItems(items, currentFilterPattern);
    }

    /**
     * Update the filtered list of items using the given items and pattern
     *
     * @param items
     * @param currentFilterPattern
     */
    public void setFilteredItems(List<T> items, CharSequence currentFilterPattern) {
        mFilteredItems.clear();
        mFilteredItems.addAll(items);

        mCurrentFilterPattern = currentFilterPattern;
        updateTitle();
    }

    /**
     * Update the title depending on the number of items
     */
    void updateTitle() {
        String newTitle;
        if (getNbItems() > 0) {
            newTitle = mTitle.concat("   " + getNbItems());
        } else {
            newTitle = mTitle;
        }

        formatTitle(newTitle);
    }

    /**
     * Format the given title
     *
     * @param titleToFormat
     */
    void formatTitle(final String titleToFormat) {
        SpannableString spannableString = new SpannableString(titleToFormat.toUpperCase(VectorApp.getApplicationLocale()));
        spannableString.setSpan(new ForegroundColorSpan(ThemeUtils.getColor(mContext, R.attr.list_header_subtext_color)),
                mTitle.length(), titleToFormat.length(), 0);
        mTitleFormatted = spannableString;
    }

    /**
     * Get title
     *
     * @return
     */
    public SpannableString getTitle() {
        return mTitleFormatted;
    }

    /**
     * Get the layout resId of the custom view that should be added to the header
     *
     * @return
     */
    public int getHeaderSubView() {
        return mHeaderSubView;
    }

    /**
     * Get the text to display when there is no item for the section
     *
     * @return
     */
    public String getEmptyViewPlaceholder() {
        return TextUtils.isEmpty(mCurrentFilterPattern) ? mNoItemPlaceholder : mNoResultPlaceholder;
    }

    /**
     * Set the text to display when there is no item/no result
     *
     * @param noItemPlaceholder
     */
    public void setEmptyViewPlaceholder(final String noItemPlaceholder) {
        mNoItemPlaceholder = noItemPlaceholder;
        mNoResultPlaceholder = noItemPlaceholder;
    }

    /**
     * Set the texts to display when there is no item or no result
     *
     * @param noItemPlaceholder
     */
    public void setEmptyViewPlaceholder(final String noItemPlaceholder, final String noResultPlaceholder) {
        mNoItemPlaceholder = noItemPlaceholder;
        mNoResultPlaceholder = noResultPlaceholder;
    }

    /**
     * Get the header view type for the header (used by adapter)
     *
     * @return
     */
    public int getHeaderViewType() {
        return mHeaderViewType;
    }

    /**
     * Get the content view type for the header (used by adapter)
     *
     * @return
     */
    public int getContentViewType() {
        return mContentViewType;
    }

    /**
     * Get the list of items
     *
     * @return
     */
    public List<T> getItems() {
        return mItems;
    }

    /**
     * Get the list of items matching the current filter
     *
     * @return
     */
    public List<T> getFilteredItems() {
        return mFilteredItems;
    }

    /**
     * Get the number of items matching the current filter
     *
     * @return
     */
    public int getNbItems() {
        return mFilteredItems.size();
    }

    /**
     * Update the filtered list by removing the current filter
     */
    public void resetFilter() {
        mFilteredItems.clear();
        mFilteredItems.addAll(mItems);

        mCurrentFilterPattern = null;
        updateTitle();
    }

    /**
     * Set whether the section should be hidden when it has no item
     *
     * @return
     */
    public void setIsHiddenWhenEmpty(final boolean isHiddenWhenEmpty) {
        mIsHiddenWhenEmpty = isHiddenWhenEmpty;
    }

    /**
     * Set whether the section should be hidden when there is no filter
     *
     * @return
     */
    public void setIsHiddenWhenNoFilter(final boolean isHiddenWhenNoFilter) {
        mIsHiddenWhenNoFilter = isHiddenWhenNoFilter;
    }

    /**
     * Get whether the section should be hidden depending on its internal state
     *
     * @return true if should be hidden
     */
    public boolean shouldBeHidden() {
        return (mIsHiddenWhenEmpty && getItems().isEmpty()) || (mIsHiddenWhenNoFilter && TextUtils.isEmpty(mCurrentFilterPattern));
    }

    /**
     * Remove an item from the section
     *
     * @param object
     * @return
     */
    public boolean removeItem(final T object) {
        if (mFilteredItems.contains(object)) {
            mFilteredItems.remove(object);
            updateTitle();
            return true;
        }
        mItems.remove(object);
        return false;
    }
}
