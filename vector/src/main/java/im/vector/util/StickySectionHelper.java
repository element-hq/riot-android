/*
 * Copyright 2017 Vector Creations Ltd
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

package im.vector.util;

import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.matrix.androidsdk.core.Log;

import java.util.ArrayList;
import java.util.List;

import im.vector.adapters.AdapterSection;
import im.vector.view.SectionView;

public class StickySectionHelper extends RecyclerView.OnScrollListener implements View.OnLayoutChangeListener {

    private final String LOG_TAG = StickySectionHelper.class.getSimpleName();

    private final RecyclerView mRecyclerView;
    private final LinearLayoutManager mLayoutManager;

    private final List<Pair<Integer, SectionView>> mSectionViews = new ArrayList<>();

    private int mHeaderBottom = 0;

    private int mFooterTop = 0;
    private int mFooterBottom = 0;

    public StickySectionHelper(RecyclerView recyclerView, List<Pair<Integer, AdapterSection>> sections) {
        mRecyclerView = recyclerView;
        mRecyclerView.addOnScrollListener(this);
        mRecyclerView.addOnLayoutChangeListener(this);
        mLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

        //Initialize the sticky views
        for (final Pair<Integer, AdapterSection> section : sections) {
            final SectionView sectionView = new SectionView(mRecyclerView.getContext());
            sectionView.setup(section.second);
            sectionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = getPositionForSectionView(sectionView);
                    mRecyclerView.stopScroll();
                    mLayoutManager.scrollToPositionWithOffset(pos, sectionView.getHeaderTop());
                }
            });
            mSectionViews.add(new Pair<>(section.first, sectionView));
        }
    }

    /*
     * *********************************************************************************************
     * Overridden methods
     * *********************************************************************************************
     */

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        //Log.i(LOG_TAG, "onLayoutChange bottom " + bottom + " oldBottom " + oldBottom);

        if (bottom != oldBottom && bottom > mFooterBottom) {
            // Calculate the coordinates (header/footer) of section views
            computeSectionViewsCoordinates(v, mSectionViews);
        } else {
            updateStickySection(-1);
        }
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        //Log.d(LOG_TAG, "onScrolled dy " + dy);

        if (dy != 0) {
            updateStickySection(dy);
        }
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Reset the section views position
     *
     * @param sections updated list of sections
     */
    public void resetSticky(List<Pair<Integer, AdapterSection>> sections) {
        Log.d(LOG_TAG, "resetSticky");

        if (!mSectionViews.isEmpty()) {
            List<Pair<Integer, SectionView>> newList = new ArrayList<>();
            for (int i = 0; i < sections.size(); i++) {
                Pair<Integer, SectionView> pair = mSectionViews.get(i);
                if (pair != null) {
                    newList.add(new Pair<>(sections.get(i).first, pair.second));
                    mSectionViews.get(i).second.updateTitle();
                }
            }

            mSectionViews.clear();
            mSectionViews.addAll(newList);

            setBottom(mFooterBottom);
            mHeaderBottom = 0;

            // Calculate the coordinates (header/footer) of section views
            computeSectionViewsCoordinates(mRecyclerView, mSectionViews);
        }
    }

    /**
     * Init the footer top and bottom with a new bottom value
     * (footer top will be updated after section views have been processed)
     *
     * @param bottom new bottom
     */
    private void setBottom(int bottom) {
        mFooterTop = bottom;
        mFooterBottom = bottom;
    }

    /**
     * Get a section view by its index
     *
     * @param index of the section view we are looking for
     * @return section view
     */
    public SectionView getSectionViewForSectionIndex(int index) {
        return mSectionViews.get(index).second;
    }

    public View findSectionSubViewById(int viewId) {
        if (mSectionViews != null) {
            for (Pair<Integer, SectionView> sectionViewPair : mSectionViews) {
                final View viewFound = sectionViewPair.second.findViewById(viewId);
                if (viewFound != null) {
                    return viewFound;
                }
            }
        }
        return null;
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * For each section view, calculate its "Y" coordinates corresponding to:
     * - top coordinate when sticky on top
     * - bottom coordinate when sticky on top
     * - top coordinate when sticky on bottom
     * - bottom coordinate when sticky on bottom
     *
     * @param v            view used to retrieve the footer bottom
     * @param sectionViews list of section views
     */
    private void computeSectionViewsCoordinates(View v, List<Pair<Integer, SectionView>> sectionViews) {
        setBottom(v.getBottom());
        mHeaderBottom = 0;

        Log.i(LOG_TAG, "computeSectionViewsCoordinates");
        if (!sectionViews.isEmpty()) {
            // Compute header
            for (int i = 0; i < sectionViews.size(); i++) {
                //SectionView previous = i > 0 ? sectionViews.get(i - 1).second : null;
                SectionView current = sectionViews.get(i).second;
                //SectionView next = i + 1 < sectionViews.size() ? sectionViews.get(i + 1).second : null;

                current.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int sectionHeight = current.getStickyHeaderHeight();
                if (current.getSection().shouldBeHidden()) {
                    current.setVisibility(View.GONE);
                    current.setHeaderTop(0 - current.getStickyHeaderHeight());
                    current.setHeaderBottom(0);
//                    current.setTranslationY(0 - current.getStickyHeaderHeight());
                } else {
                    current.setVisibility(View.VISIBLE);
                    current.setHeaderTop(mHeaderBottom);
                    current.setHeaderBottom(mHeaderBottom + current.getStickyHeaderHeight());
                    mHeaderBottom += sectionHeight;
                }
            }

            // Compute footer
            for (int i = sectionViews.size() - 1; i >= 0; i--) {
                //SectionView previous = i > 0 ? sectionViews.get(i - 1).second : null;
                SectionView current = sectionViews.get(i).second;
                //SectionView next = i + 1 < sectionViews.size() ? sectionViews.get(i + 1).second : null;

                current.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int sectionHeight = current.getStickyHeaderHeight();
                current.setFooterTop(mFooterTop - current.getStickyHeaderHeight());
                current.setFooterBottom(mFooterTop);
                if (!current.getSection().shouldBeHidden()) {
                    mFooterTop -= sectionHeight;
                }
            }
        }

        for (Pair<Integer, SectionView> section : mSectionViews) {
            removeViewFromParent(section.second);
            ((ViewGroup) v.getParent()).addView(section.second);
        }
        updateStickySection(-1);
    }

    /**
     * Update the section views position to take into account the scroll of the given value
     *
     * @param dy scroll value
     */
    private void updateStickySection(int dy) {
        //Log.d(LOG_TAG, "updateStickySection " + dy);

        // header is out of screen, check if header or footer
        int firstVisiblePos = mLayoutManager.findFirstVisibleItemPosition();
        int lastVisiblePos = mLayoutManager.findLastVisibleItemPosition();

        for (int i = mSectionViews.size() - 1; i >= 0; i--) {
            SectionView previous = i > 0 ? mSectionViews.get(i - 1).second : null;
            int currentPos = mSectionViews.get(i).first;
            SectionView current = mSectionViews.get(i).second;

            RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForLayoutPosition(currentPos);
            //Log.d(LOG_TAG, "updateStickySection holder for " + current.getSection().getTitle() + " is " + holder);
            if (holder != null) {
                Log.d(LOG_TAG, "updateStickySection holder top " + holder.itemView.getTop() + " bottom " + holder.itemView.getBottom());
                current.updatePosition(holder.itemView.getTop());
                if (previous != null) {
                    previous.onFoldSubView(current, dy);
                }
            } else {
                if (currentPos < firstVisiblePos) {
                    current.updatePosition(current.getHeaderTop());
                } else if (currentPos > lastVisiblePos) {
                    current.updatePosition(current.getFooterTop());
                }
            }
        }
    }

    /**
     * Get the adapter position of the header corresponding to the given section view
     *
     * @param sectionView section view
     * @return adapter position
     */
    private int getPositionForSectionView(SectionView sectionView) {

        for (Pair<Integer, SectionView> section : mSectionViews) {
            if (sectionView == section.second) {
                return section.first;
            }
        }
        return -1;
    }

    /**
     * Remove the given view from its parent
     *
     * @param view to remove from its parent
     */
    private static void removeViewFromParent(final View view) {
        final ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
    }
}
