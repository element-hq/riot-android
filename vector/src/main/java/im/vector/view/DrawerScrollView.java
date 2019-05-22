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

package im.vector.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import org.matrix.androidsdk.core.Log;

import java.util.ArrayList;
import java.util.List;

public class DrawerScrollView extends ScrollView {

    private static final String LOG_TAG = DrawerScrollView.class.getSimpleName();

    //Tag for views that should stick, must be with at least one flag among FLAG_HEADER, FLAG_FOOTER
    private static final String STICKY_TAG = "sticky";

    //Tag for views that should stick as header
    private static final String FLAG_HEADER = "-header";
    //Tag for views that should stick as footer
    private static final String FLAG_FOOTER = "-footer";

    // List of sticky headers
    private List<View> mHeaders;
    // List of sticky footers
    private List<View> mFooters;
    // The currently sticky header
    private View mCurrentHeader;
    // The currently sticky footer
    private View mCurrentFooter;

    private float stickyViewTopOffset;
    private int stickyViewLeftOffset;
    private boolean clippingToPadding;
    private boolean clipToPaddingHasBeenSet;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public DrawerScrollView(Context context) {
        this(context, null);
        setup();
    }

    public DrawerScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.scrollViewStyle);
        setup();
    }

    public DrawerScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setup();
    }

    /*
     * *********************************************************************************************
     * Overridden methods
     * *********************************************************************************************
     */

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {
        super.onSizeChanged(xNew, yNew, xOld, yOld);
        Log.d(LOG_TAG, "onSizeChanged y " + yNew + " " + yOld);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        Log.d(LOG_TAG, "onLayout changed " + changed + " t:" + t + " b:" + b);

        if (!clipToPaddingHasBeenSet) {
            clippingToPadding = true;
        }
        if (changed) {
            notifyHierarchyChanged();
        }
    }

    @Override
    public void setClipToPadding(boolean clipToPadding) {
        super.setClipToPadding(clipToPadding);
        clippingToPadding = clipToPadding;
        clipToPaddingHasBeenSet = true;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mCurrentHeader != null) {
            canvas.save();
            canvas.translate(getPaddingLeft() + stickyViewLeftOffset,
                    getScrollY() + stickyViewTopOffset + (clippingToPadding ? getPaddingTop() : 0));

            canvas.clipRect(0,
                    (clippingToPadding ? -stickyViewTopOffset : 0),
                    getWidth() - stickyViewLeftOffset,
                    mCurrentHeader.getHeight() + 1);

            canvas.clipRect(0,
                    (clippingToPadding ? -stickyViewTopOffset : 0),
                    getWidth(),
                    mCurrentHeader.getHeight());
            mCurrentHeader.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        Log.d(LOG_TAG, "onScrollChanged new y:" + t + " old y:" + oldt);

        if (mCurrentFooter != null) {
            mCurrentFooter.setTranslationY(0);
        }

        doTheStickyThing();
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    private void setup() {
        mHeaders = new ArrayList<>();
        mFooters = new ArrayList<>();
    }

    private void doTheStickyThing() {
        View headerThatShouldStick = null;
        View footerThatShouldStick = null;
        View approachingView = null;
        for (View v : mHeaders) {
            int viewTop = getTopForViewRelativeOnlyChild(v) - getScrollY() + (clippingToPadding ? 0 : getPaddingTop());
            if (viewTop <= 0) {
                if (headerThatShouldStick == null
                        || viewTop > (getTopForViewRelativeOnlyChild(headerThatShouldStick) - getScrollY() + (clippingToPadding ? 0 : getPaddingTop()))) {
                    headerThatShouldStick = v;
                }
            } else {
                if (approachingView == null
                        || viewTop < (getTopForViewRelativeOnlyChild(approachingView) - getScrollY() + (clippingToPadding ? 0 : getPaddingTop()))) {
                    approachingView = v;
                }
            }
        }

        stopStickingCurrentlyStickingViews();

        if (headerThatShouldStick != null) {
            stickyViewTopOffset = approachingView == null ? 0
                    : Math.min(0,
                    getTopForViewRelativeOnlyChild(approachingView)
                            - getScrollY()
                            + (clippingToPadding ? 0 : getPaddingTop()) - headerThatShouldStick.getHeight());
            if (headerThatShouldStick != mCurrentHeader) {
                // only compute the left offset when we start sticking.
                stickyViewLeftOffset = getLeftForViewRelativeOnlyChild(headerThatShouldStick);
                mCurrentHeader = headerThatShouldStick;
            }
        } else if (mCurrentHeader != null) {
            mCurrentHeader = null;
        }

        int footerIndex = 0;
        if (mCurrentHeader != null) {
            footerIndex = mHeaders.indexOf(mCurrentHeader);
        }

        if (mFooters != null && footerIndex < mFooters.size()) {
            footerThatShouldStick = mFooters.get(footerIndex);
        }
        if (footerThatShouldStick != null) {
            if (footerThatShouldStick != mCurrentFooter) {
                mCurrentFooter = footerThatShouldStick;
                mCurrentFooter.setTranslationY(-mCurrentFooter.getTop() + getHeight() - mCurrentFooter.getHeight());
            }
            mCurrentFooter = footerThatShouldStick;
            final int newTransY = -mCurrentFooter.getTop() + getHeight() - mCurrentFooter.getHeight() + getScrollY();
            if (newTransY >= 0) {
                mCurrentFooter.setTranslationY(0);
                mCurrentFooter = null;
            } else {
                mCurrentFooter.setTranslationY(newTransY);
            }
        } else if (mCurrentFooter != null) {
            mCurrentFooter.setTranslationY(0);
            mCurrentFooter = null;
        }
    }

    private void stopStickingCurrentlyStickingViews() {
        if (mCurrentHeader != null) {
            mCurrentHeader = null;
        }
        if (mCurrentFooter != null) {
            mCurrentFooter.setTranslationY(0);
            mCurrentFooter = null;
        }
    }

    private void notifyHierarchyChanged() {
        Log.e(LOG_TAG, "notifyHierarchyChanged");
        stopStickingCurrentlyStickingViews();

        mHeaders.clear();
        mFooters.clear();
        findStickyViews(this);
        Log.d(LOG_TAG, "headers " + mHeaders.size());
        Log.d(LOG_TAG, "footers " + mFooters.size());

        doTheStickyThing();
        invalidate();
    }

    private void findStickyViews(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                String tag = getStringTagForView(vg.getChildAt(i));
                if (tag != null && tag.contains(STICKY_TAG)) {
                    if (tag.contains(FLAG_HEADER)) {
                        mHeaders.add(vg.getChildAt(i));
                    }
                    if (tag.contains(FLAG_FOOTER)) {
                        mFooters.add(vg.getChildAt(i));
                    }

                    vg.getChildAt(i).setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            smoothScrollTo(0, v.getTop());
                        }
                    });
                }
                if (vg.getChildAt(i) instanceof ViewGroup) {
                    findStickyViews(vg.getChildAt(i));
                }
            }
        } else {
            String tag = (String) v.getTag();
            if (tag != null && tag.contains(STICKY_TAG)) {
                if (tag.contains(FLAG_HEADER)) {
                    mHeaders.add(v);
                }
                if (tag.contains(FLAG_FOOTER)) {
                    mFooters.add(v);
                }

                v.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        smoothScrollTo(0, v.getTop());
                    }
                });
            }
        }
    }

    private String getStringTagForView(View v) {
        Object tagObject = v.getTag();
        return String.valueOf(tagObject);
    }

    private int getLeftForViewRelativeOnlyChild(View v) {
        int left = v.getLeft();
        while (v.getParent() != getChildAt(0)) {
            v = (View) v.getParent();
            left += v.getLeft();
        }
        return left;
    }

    private int getTopForViewRelativeOnlyChild(View v) {
        int top = v.getTop();
        while (v.getParent() != getChildAt(0)) {
            v = (View) v.getParent();
            top += v.getTop();
        }
        return top;
    }

}