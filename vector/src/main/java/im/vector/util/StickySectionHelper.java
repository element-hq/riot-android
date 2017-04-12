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

package im.vector.util;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.ArrayList;
import java.util.List;

import im.vector.adapters.AdapterSection;
import im.vector.view.SectionView;

public class StickySectionHelper extends RecyclerView.OnScrollListener implements View.OnLayoutChangeListener {

    private final String LOG_TAG = StickySectionHelper.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;

    private List<Pair<Integer, SectionView>> mSectionViews = new ArrayList<>();

    private int headerTop = 0;
    private int headerBottom = 0;

    private int footerTop = 0;
    private int footerBottom = 0;

    public StickySectionHelper(RecyclerView recyclerView, List<Pair<Integer, AdapterSection>> sections) {
        mRecyclerView = recyclerView;
        mRecyclerView.addOnScrollListener(this);
        mRecyclerView.addOnLayoutChangeListener(this);
        mLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

        //Initialize the sticky views
        for (final Pair<Integer, AdapterSection> section : sections) {
            final SectionView sectionView = new SectionView(mRecyclerView.getContext(), section.second);
            sectionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int i = 0; i < mSectionViews.size(); i++) {
                        SectionView current = mSectionViews.get(i).second;
                        if (current == sectionView) {
                            SectionView next = i + 1 < mSectionViews.size() ? mSectionViews.get(i + 1).second : null;
                            if (!sectionView.isStickyHeader() || next != null && !next.isStickyFooter()) {
                                int pos = getPositionForSectionView((SectionView) v);
                                if (pos >= 0) {
                                    mLayoutManager.scrollToPositionWithOffset(pos, current.getHeaderTop());
                                }
                            } else {
                                SectionView previous = i > 0 ? mSectionViews.get(i - 1).second : null;
                                if (previous != null) {
                                    int pos = getPositionForSectionView(previous);
                                    if (pos >= 0) {
                                        mLayoutManager.scrollToPositionWithOffset(pos, previous.getHeaderTop());
                                    }
                                }
                            }
                        }
                    }
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
        Log.i(LOG_TAG, "onLayoutChange bottom " + bottom + " oldBottom " + oldBottom);

        if (bottom != oldBottom && bottom > footerBottom) {
            // Calculate the coordinates (header/footer) of section views
            computeSectionViewsCoordinates(v, mSectionViews);
        } else {
            updateStickySection(-1);
        }
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        Log.d(LOG_TAG, "onScrolled dy " + dy);

        if (dy != 0) {
            updateStickySection(dy);
        }
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    public void resetSticky(List<Pair<Integer, AdapterSection>> sections) {
        Log.e(LOG_TAG, "resetSticky");

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

            setBottom(footerBottom);
            headerBottom = 0;

            // Calculate the coordinates (header/footer) of section views
            computeSectionViewsCoordinates(mRecyclerView, mSectionViews);
        }
    }

    public void setBottom(int bottom) {
        footerTop = bottom;
        footerBottom = bottom;
    }

    public SectionView getSectionViewForSectionIndex(int index) {
        return mSectionViews.get(index).second;
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    private void computeSectionViewsCoordinates(View v, List<Pair<Integer, SectionView>> sectionViews) {
        setBottom(v.getBottom());
        headerBottom = 0;

        Log.i(LOG_TAG, "computeSectionViewsCoordinates");
        if (!sectionViews.isEmpty()) {

            // Compute header
            for (int i = 0; i < sectionViews.size(); i++) {
                SectionView previous = i > 0 ? sectionViews.get(i - 1).second : null;
                SectionView current = sectionViews.get(i).second;
                SectionView next = i + 1 < sectionViews.size() ? sectionViews.get(i + 1).second : null;

                current.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int sectionHeight = current.getStickyHeaderHeight();
                if (current.getSection().hideWhenEmpty() && current.getSection().getItems().isEmpty()) {
                    current.setHeaderTop(0 - current.getStickyHeaderHeight());
                    current.setHeaderBottom(0);
//                    current.setTranslationY(0 - current.getStickyHeaderHeight());
                } else {
                    current.setHeaderTop(headerBottom);
                    current.setHeaderBottom(headerBottom + current.getStickyHeaderHeight());
                    headerBottom += sectionHeight;
                }
            }

            // Compute footer
            for (int i = sectionViews.size() - 1; i >= 0; i--) {
                SectionView previous = i > 0 ? sectionViews.get(i - 1).second : null;
                SectionView current = sectionViews.get(i).second;
                SectionView next = i + 1 < sectionViews.size() ? sectionViews.get(i + 1).second : null;

                current.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int sectionheight = current.getStickyHeaderHeight();
                current.setFooterTop(footerTop - current.getStickyHeaderHeight());
                current.setFooterBottom(footerTop);
                footerTop -= sectionheight;
            }
        }

        for (Pair<Integer, SectionView> section : mSectionViews) {
            removeViewFromParent(section.second);
            ((ViewGroup) v.getParent()).addView(section.second);
        }
        updateStickySection(-1);
    }

    private void updateStickySection(int dy) {
        Log.e(LOG_TAG, "updateStickySection " + dy);
        for (int i = mSectionViews.size() - 1; i >= 0; i--) {
            SectionView previous = i > 0 ? mSectionViews.get(i - 1).second : null;
            int currentPos = mSectionViews.get(i).first;
            SectionView current = mSectionViews.get(i).second;
            SectionView next = i + 1 < mSectionViews.size() ? mSectionViews.get(i + 1).second : null;

            RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForLayoutPosition(currentPos);
            Log.e(LOG_TAG, "updateStickySection holder for " + current.getSection().getTitle() + " is " + holder);
            if (holder != null) {
                Log.e(LOG_TAG, "updateStickySection holder top " + holder.itemView.getTop() + " bottom " + holder.itemView.getBottom());
                current.updatePosition(holder.itemView.getTop());
                if (previous != null) {
                    previous.onFoldSubView(current, dy);
                }
            } else {
                // header is out of screen, check if header or footer
                int firstVisiPos = mLayoutManager.findFirstVisibleItemPosition();
                int lastVisiPos = mLayoutManager.findLastVisibleItemPosition();
                if (currentPos < firstVisiPos) {
                    current.updatePosition(current.getHeaderTop());
                } else if (currentPos > lastVisiPos) {
                    current.updatePosition(current.getFooterTop());
                }
            }
            current.requestLayout();
        }
    }

    private int getPositionForSectionView(SectionView sectionView) {

        for (Pair<Integer, SectionView> section : mSectionViews) {
            if (sectionView == section.second) {
                return section.first;
            }
        }
        return -1;
    }

    private static ViewGroup removeViewFromParent(final View view) {
        final ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
        return ((ViewGroup) parent);
    }
}
