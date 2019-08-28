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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import im.vector.R;
import im.vector.ui.themes.ThemeUtils;

public class SimpleDividerItemDecoration extends DividerItemDecoration {
    private final Drawable mDivider;
    private final int mOrientation;
    private final int mLeftMargin;

    // No divider will be drawn for children with this tag
    private static final String NO_DIVIDER_TAG = "without-divider";

    public SimpleDividerItemDecoration(final Context context, final int orientation, final int leftMargin) {
        super(context, orientation);
        mDivider = ContextCompat.getDrawable(context, ThemeUtils.INSTANCE.getResourceId(context, R.drawable.line_divider_dark));
        mLeftMargin = leftMargin;
        mOrientation = orientation;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        outRect.set(0, 0, 0, 0);
    }

    @Override
    public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        if (parent.getLayoutManager() == null) {
            return;
        }
        if (mOrientation == HORIZONTAL || mLeftMargin <= 0) {
            super.onDraw(canvas, parent, state);
        } else {
            canvas.save();
            int right = parent.getWidth() - parent.getPaddingRight();

            int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                View nextChild = parent.getChildAt(i + 1);
                if (!String.valueOf(child.getTag()).contains(NO_DIVIDER_TAG)
                        && (nextChild != null && !String.valueOf(nextChild.getTag()).contains(NO_DIVIDER_TAG))) {
                    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

                    int top = child.getBottom() + params.bottomMargin;
                    int bottom = top + mDivider.getIntrinsicHeight();

                    mDivider.setBounds(mLeftMargin, top, right, bottom);
                    mDivider.draw(canvas);
                }
            }
            canvas.restore();
        }
    }
}
