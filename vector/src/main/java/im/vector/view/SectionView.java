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
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import im.vector.R;
import im.vector.adapters.AdapterSection;

public class SectionView extends RelativeLayout {

    private final String LOG_TAG = SectionView.class.getSimpleName();

    private AdapterSection mSection;

    private int mHeaderTop;
    private int mHeaderBottom;
    private int mFooterTop;
    private int mFooterBottom;

    private View mSubView;

    public SectionView(Context context, AdapterSection section) {
        super(context);
        setup(section);
    }

    public SectionView(Context context, AttributeSet attrs, AdapterSection section) {
        super(context, attrs);
        setup(section);
    }

    public SectionView(Context context, AttributeSet attrs, int defStyleAttr, AdapterSection section) {
        super(context, attrs, defStyleAttr);
        setup(section);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SectionView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes, AdapterSection section) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setup(section);
    }

    private void setup(final AdapterSection section) {
        mSection = section;

        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.list_header_background));
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        View headerView = inflate(getContext(), R.layout.adapter_sticky_header, null);
        TextView titleView = (TextView) headerView.findViewById(R.id.section_title);
        titleView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        titleView.setText(section.getTitle());
        if (section.getHeaderSubView() != -1) {
            // Inflate subview
            mSubView = inflate(getContext(), section.getHeaderSubView(), null);
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.BELOW, titleView.getId());
            mSubView.setLayoutParams(params);
            // Add to parent before title so when we fold it, it goes behind the title
            addView(mSubView);
        }
        addView(headerView);
    }

    public void updateTitle() {
        TextView titleView = (TextView) findViewById(R.id.section_title);
        titleView.setText(mSection.getTitle());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Log.d(LOG_TAG, "onMeasure parent height" + MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {
        super.onSizeChanged(xNew, yNew, xOld, yOld);
        Log.d(LOG_TAG, "onSizeChanged yOld " + yOld + " yNew" + yNew);
    }

    public boolean isStickyHeader() {
        return getTranslationY() == mHeaderTop;
    }

    public boolean isStickyFooter() {
        return getTranslationY() == mFooterTop;
    }

    /**
     * Fold or unfold the subview behind the title
     *
     * @param nextSection
     * @param dy
     */
    public void onFoldSubView(final SectionView nextSection, int dy) {
        Log.d(LOG_TAG, "onFoldSubView dy " + dy);
        if (mSubView != null) {
            if (nextSection.getTop() <= getBottom()) {
                int translation;
                if (dy > 0) {
                    translation = Math.max(nextSection.getTop() - getBottom(), 0);
                    mSubView.setTranslationY(translation);
                } else if (dy < 0) {
                    translation = Math.min(nextSection.getTop() - getBottom(), 0);
                    mSubView.setTranslationY(translation);
                }

                if (-(nextSection.getTop() - getBottom()) > mSubView.getMeasuredHeight()) {
                    mSubView.setTranslationY(0);
                }
            } else if (mSubView.getTranslationY() < 0) {
                mSubView.setTranslationY(0);
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
        mHeaderBottom = headerBottom;
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
        mFooterBottom = footerBottom;
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

        Log.e(LOG_TAG, "sectionview " + mSection.getTitle() + " updatePosition translation y " + newTranslationY);

        setTranslationY(newTranslationY);
        requestLayout();
        invalidate();
    }
}

