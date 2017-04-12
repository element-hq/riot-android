package im.vector.adapters;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AdapterSection<T> {
    private final String mTitle;

    private String mTitleFormatted;
    // Place holder if no item for the section
    private String mNoItemPlaceholder;
    // Place holder if no result after search
    private String mNoResultPlaceholder;

    private int mHeaderSubView;
    private int mContentView;
    private int mHeaderViewType;
    private int mContentViewType;

    private List<T> mItems;

    private List<T> mFilteredItems;

    private Comparator<T> mComparator;

    private CharSequence mCurrentFilterPattern;

    private boolean mIsHiddenWhenEmpty;

    public AdapterSection(String title, int headerSubViewResId, int contentResId, int headerViewType,
                          int contentViewType, List<T> items, Comparator<T> comparator) {
        mTitle = title;
        mItems = items;
        mFilteredItems = new ArrayList<>(items);
        mHeaderSubView = headerSubViewResId;
        mContentView = contentResId;

        mHeaderViewType = headerViewType;
        mContentViewType = contentViewType;
        mComparator = comparator;

        updateTitle();
    }

    public void setItems(List<T> items, CharSequence currentFilterPattern) {
        if (mComparator != null) {
            Collections.sort(items, mComparator);
        }
        mItems.clear();
        mItems.addAll(items);

        setFilteredItems(items, currentFilterPattern);
    }

    private void updateTitle() {
        if (getNbItems() > 0) {
            mTitleFormatted = mTitle.concat(" (" + getNbItems() + ")");
        } else {
            mTitleFormatted = mTitle;
        }
    }

    public void setFilteredItems(List<T> items, CharSequence currentFilterPattern) {
        mFilteredItems.clear();
        mFilteredItems.addAll(items);

        mCurrentFilterPattern = currentFilterPattern;
        updateTitle();
    }

    public String getTitle() {
        return mTitleFormatted;
    }

    public String getEmptyViewPlaceholder() {
        return TextUtils.isEmpty(mCurrentFilterPattern) ? mNoItemPlaceholder : mNoResultPlaceholder;
    }

    public int getHeaderSubView() {
        return mHeaderSubView;
    }


    public void setEmptyViewPlaceholder(final String noItemPlaceholder) {
        mNoItemPlaceholder = noItemPlaceholder;
        mNoResultPlaceholder = noItemPlaceholder;
    }

    public void setEmptyViewPlaceholder(final String noItemPlaceholder, final String noResultPlaceholder) {
        mNoItemPlaceholder = noItemPlaceholder;
        mNoResultPlaceholder = noResultPlaceholder;
    }

    public int getHeaderViewType() {
        return mHeaderViewType;
    }

    public int getContentViewType() {
        return mContentViewType;
    }

    public List<T> getItems() {
        return mItems;
    }

    public List<T> getFilteredItems() {
        return mFilteredItems;
    }

    public int getNbItems() {
        return mFilteredItems.size();
    }

    public void resetFilter() {
        mFilteredItems.clear();
        mFilteredItems.addAll(mItems);

        mCurrentFilterPattern = null;
        updateTitle();
    }

    public void setIsHiddenWhenEmpty(final boolean isHiddenWhenEmpty) {
        mIsHiddenWhenEmpty = isHiddenWhenEmpty;
    }

    public boolean hideWhenEmpty() {
        return mIsHiddenWhenEmpty;
    }
}
