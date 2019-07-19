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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.matrix.androidsdk.core.Log;

import im.vector.R;
import im.vector.adapters.AdapterSection;
import im.vector.ui.themes.ThemeUtils;

public class SectionView extends RelativeLayout {

    private final String LOG_TAG = SectionView.class.getSimpleName();

    private AdapterSection mSection;

    private int mHeaderTop;
    private int mFooterTop;

    // header subview
    private View mSubView;

    // header items
    private TextView mTitleView;
    private ProgressBar mLoadingView;

    public SectionView(Context context) {
        super(context);
    }

    public SectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Init the section header
     *
     * @param section the section description
     */
    public void setup(final AdapterSection section) {
        mSection = section;

        setBackgroundColor(ThemeUtils.INSTANCE.getColor(getContext(), R.attr.vctr_list_header_background_color));
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // inflate the layout
        View headerView = inflate(getContext(), R.layout.adapter_sticky_header, null);

        // extract the UI items
        mTitleView = headerView.findViewById(R.id.section_title);
        mTitleView.setText(section.getTitle());

        mLoadingView = headerView.findViewById(R.id.section_loading);
        mLoadingView.setVisibility(View.INVISIBLE);

        // custom subview ?
        if (section.getHeaderSubView() != -1) {
            // Inflate subview
            mSubView = inflate(getContext(), section.getHeaderSubView(), null);
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.BELOW, headerView.getId());
            mSubView.setLayoutParams(params);
            // Add to parent before title so when we fold it, it goes behind the title
            addView(mSubView);
        }

        addView(headerView);
    }

    /**
     * Refresh the title
     */
    public void updateTitle() {
        mTitleView.setText(mSection.getTitle());
    }

    /**
     * Display the loading wheel
     */
    public void showLoadingView() {
        mLoadingView.setVisibility(View.VISIBLE);
        mLoadingView.animate();
    }

    /**
     * Hide the loading wheel
     */
    public void hideLoadingView() {
        mLoadingView.setVisibility(View.INVISIBLE);
    }

    public boolean isStickyHeader() {
        return getTranslationY() == mHeaderTop;
    }

    /**
     * Fold or unfold the subview behind the title
     *
     * @param nextSection
     * @param dy
     */
    public void onFoldSubView(final SectionView nextSection, final int dy) {
        if (mSubView != null && getHeaderTop() == getTranslationY()) {
            final float nextSectionDistance = nextSection.getTranslationY() - getTranslationY() - getMeasuredHeight();
            final float translation = Math.min(0, nextSectionDistance);
            if (mSubView.getTranslationY() != translation) {
                Log.d(LOG_TAG, "onFoldSubView new translation " + translation);
                mSubView.setTranslationY(translation);
            }
        }
    }

    /**
     * Get the height of the view when it is sticky, ie height of the title alone
     * If mSubView is null then the title is at position 0
     * If mSubView is not null then the title is at position 1
     *
     * @return height of the title
     */
    public int getStickyHeaderHeight() {
        return getChildAt(mSubView != null ? 1 : 0).getMeasuredHeight();
    }

    /**
     * Get the top (coordinate Y) when the view is sticky at the top
     *
     * @return
     */
    public int getHeaderTop() {
        return mHeaderTop;
    }

    /**
     * Set the top (coordinate Y) when the view is sticky at the top
     *
     * @param headerTop
     */
    public void setHeaderTop(int headerTop) {
        Log.d(LOG_TAG, "sectionview " + mSection.getTitle() + " setHeaderTop " + headerTop);
        mHeaderTop = headerTop;
    }

    /**
     * Set the bottom (coordinate Y) when the view is sticky at the top
     *
     * @param headerBottom
     */
    public void setHeaderBottom(int headerBottom) {
        Log.d(LOG_TAG, "sectionview " + mSection.getTitle() + " setHeaderBottom " + headerBottom);
        //mHeaderBottom = headerBottom;
        setBottom(headerBottom);
    }

    /**
     * Get the top (coordinate Y) when the view is sticky at the bottom
     *
     * @return
     */
    public int getFooterTop() {
        return mFooterTop;
    }

    /**
     * Set the top (coordinate Y) when the view is sticky at the bottom
     *
     * @param footerTop
     */
    public void setFooterTop(int footerTop) {
        Log.d(LOG_TAG, "sectionview " + mSection.getTitle() + " setFooterTop " + footerTop);
        mFooterTop = footerTop;
    }

    /**
     * Set the bottom (coordinate Y) when the view is sticky at the bottom
     *
     * @param footerBottom
     */
    public void setFooterBottom(int footerBottom) {
        Log.d(LOG_TAG, "sectionview " + mSection.getTitle() + " setFooterBottom " + footerBottom);
        //mFooterBottom = footerBottom;
    }

    /**
     * Get the section corresponding to this section view
     *
     * @return section
     */
    public AdapterSection getSection() {
        return mSection;
    }

    /**
     * Update the view position by applying a Y translation
     *
     * @param topMargin
     */
    public void updatePosition(int topMargin) {
        int newTranslationY = Math.max(mHeaderTop, topMargin);
        newTranslationY = Math.min(newTranslationY, mFooterTop);

        if (getTranslationY() != newTranslationY) {
            Log.d(LOG_TAG, "sectionview " + mSection.getTitle() + " updatePosition translation y " + newTranslationY);

            setTranslationY(newTranslationY);
            requestLayout();
            invalidate();
        }
    }
}

