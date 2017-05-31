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

package im.vector.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Filter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.matrix.androidsdk.data.Room;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.adapters.AbsAdapter;
import im.vector.adapters.HomeRoomAdapter;
import im.vector.fragments.AbsHomeFragment;

public class HomeSectionView extends RelativeLayout {

    @BindView(R.id.section_header)
    TextView mHeader;

    @BindView(R.id.section_badge)
    TextView mBadge;

    @BindView(R.id.section_recycler_view)
    RecyclerView mRecyclerView;

    @BindView(R.id.section_placeholder)
    TextView mPlaceHolder;

    private HomeRoomAdapter mAdapter;

    private boolean mHideIfEmpty;
    private String mNoItemPlaceholder;
    private String mNoResultPlaceholder;
    private String mCurrentFilter;

    public HomeSectionView(Context context) {
        super(context);
        setup();
    }

    public HomeSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public HomeSectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public HomeSectionView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setup();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAdapter = null; // might be necessary to avoid memory leak?
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Setup the view
     */
    private void setup() {
        inflate(getContext(), R.layout.home_section_view, this);
        ButterKnife.bind(this);

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(100);
        shape.setColor(ContextCompat.getColor(getContext(), R.color.vector_white_alpha_50));
        mBadge.setBackground(shape);

        mHeader.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRecyclerView != null) {
                    mRecyclerView.stopScroll();
                    mRecyclerView.scrollToPosition(0);
                }
            }
        });
    }

    /**
     * Update the views to reflect the new number of items
     */
    private void onDataUpdated() {
        setVisibility(mHideIfEmpty && mAdapter.isEmpty() ? GONE : VISIBLE);
        final int badgeCount = mAdapter.getBadgeCount();
        mBadge.setText(String.valueOf(badgeCount));
        mBadge.setVisibility(badgeCount == 0 ? GONE : VISIBLE);
        mRecyclerView.setVisibility(mAdapter.hasNoResult() ? GONE : VISIBLE);
        mPlaceHolder.setVisibility(mAdapter.hasNoResult() ? VISIBLE : GONE);
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Set the title of the section
     *
     * @param title new title
     */
    public void setTitle(@StringRes final int title) {
        mHeader.setText(title);
    }

    /**
     * Set the placeholders to display when there are no items/results
     *
     * @param noItemPlaceholder   placeholder when no items
     * @param noResultPlaceholder placeholder when no results after a filter had been applied
     */
    public void setPlaceholders(final String noItemPlaceholder, final String noResultPlaceholder) {
        mNoItemPlaceholder = noItemPlaceholder;
        mNoResultPlaceholder = noResultPlaceholder;
        mPlaceHolder.setText(TextUtils.isEmpty(mCurrentFilter) ? mNoItemPlaceholder : mNoResultPlaceholder);
    }

    /**
     * Set whether the section should be hidden when there are no items
     *
     * @param hideIfEmpty
     */
    public void setHideIfEmpty(final boolean hideIfEmpty) {
        mHideIfEmpty = hideIfEmpty;
        setVisibility(mHideIfEmpty && (mAdapter == null || mAdapter.isEmpty()) ? GONE : VISIBLE);
    }

    /**
     * Setup the recycler and its adapter with the given params
     *
     * @param layoutManager        layout manager
     * @param itemResId            cell layout
     * @param nestedScrollEnabled  whether nested scroll should be enabled
     * @param onSelectRoomListener listener for room click
     * @param invitationListener   listener for invite buttons
     * @param moreActionListener   listener for room menu
     */
    public void setupRecyclerView(final RecyclerView.LayoutManager layoutManager, @LayoutRes final int itemResId,
                                  final boolean nestedScrollEnabled, final HomeRoomAdapter.OnSelectRoomListener onSelectRoomListener,
                                  final AbsAdapter.InvitationListener invitationListener,
                                  final AbsAdapter.MoreRoomActionListener moreActionListener) {
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setNestedScrollingEnabled(nestedScrollEnabled);

        mAdapter = new HomeRoomAdapter(getContext(), itemResId, onSelectRoomListener, invitationListener, moreActionListener);
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                onDataUpdated();
            }
        });
    }

    /**
     * Filter section items with the given filter
     *
     * @param pattern
     * @param listener
     */
    public void onFilter(final String pattern, final AbsHomeFragment.OnFilterListener listener) {
        mAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                if (listener != null) {
                    listener.onFilterDone(count);
                }
                setCurrentFilter(pattern);
                mRecyclerView.getLayoutManager().scrollToPosition(0);
                onDataUpdated();
            }
        });
    }

    /**
     * Set the current filter
     *
     * @param filter
     */
    public void setCurrentFilter(final String filter) {
        mCurrentFilter = filter;
        mAdapter.onFilterDone(mCurrentFilter);
        mPlaceHolder.setText(TextUtils.isEmpty(mCurrentFilter) ? mNoItemPlaceholder : mNoResultPlaceholder);
    }

    /**
     * Set rooms of the section
     *
     * @param rooms
     */
    public void setRooms(final List<Room> rooms) {
        if (mAdapter != null) {
            mAdapter.setRooms(rooms);
        }
    }
}
