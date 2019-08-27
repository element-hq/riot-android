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
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import im.vector.R;
import im.vector.adapters.AbsAdapter;
import im.vector.adapters.AdapterSection;
import im.vector.ui.themes.ThemeUtils;

public class EmptyViewItemDecoration extends DividerItemDecoration {
    private final int mOrientation;
    private final float mTextSize;
    private final float mEmptyViewHeight;
    private final float mEmptyViewLeftMargin;
    private final int mTextColor;

    public EmptyViewItemDecoration(final Context context, final int orientation,
                                   final int emptyViewHeight, final int emptyViewLeftMargin,
                                   final int textSize) {
        super(context, orientation);
        final float density = context.getResources().getDisplayMetrics().density;

        mOrientation = orientation;
        mTextSize = textSize * density;
        mEmptyViewHeight = emptyViewHeight * density;
        mEmptyViewLeftMargin = emptyViewLeftMargin * density;
        mTextColor = ThemeUtils.INSTANCE.getColor(context, R.attr.vctr_list_divider_color);
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        if (mOrientation == VERTICAL && isDecorated(view, parent)) {
            outRect.set(0, 0, 0, (int) mEmptyViewHeight);
        } else {
            outRect.set(0, 0, 0, 0);
        }
    }

    @Override
    public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        if (parent.getLayoutManager() == null) {
            return;
        }

        if (mOrientation == HORIZONTAL) {
            super.onDraw(canvas, parent, state);
        } else {
            canvas.save();

            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
                RecyclerView.ViewHolder holder = parent.getChildViewHolder(child);
                if (!lp.isItemRemoved()
                        && !lp.isViewInvalid()
                        && isDecorated(child, parent)) {

                    final String emptyViewPlaceholder = ((AbsAdapter.HeaderViewHolder) holder).getSection().getEmptyViewPlaceholder();
                    if (!TextUtils.isEmpty(emptyViewPlaceholder)) {
                        TextPaint textPaint = new TextPaint();
                        textPaint.setAntiAlias(true);
                        textPaint.setTextSize(mTextSize);
                        textPaint.setColor(mTextColor);
                        textPaint.setTextAlign(Paint.Align.LEFT);
                        int width = (int) textPaint.measureText(emptyViewPlaceholder);
                        StaticLayout layout = new StaticLayout(emptyViewPlaceholder, textPaint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                        layout.getOffsetToLeftOf(10);
                        float bottom = holder.itemView.getBottom() + mEmptyViewHeight - ((mEmptyViewHeight - layout.getHeight()) / 1.5f);
                        canvas.drawText(emptyViewPlaceholder, mEmptyViewLeftMargin, bottom, textPaint);
                    }
                }
            }
            canvas.restore();
        }
    }

    /**
     * Check if an empty view must be displayed i.e. when there is no item for the section and
     * a placeholder is provided
     *
     * @param view
     * @param parent
     * @return true if must be decorated
     */
    private boolean isDecorated(View view, RecyclerView parent) {
        RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
        if (holder instanceof AbsAdapter.HeaderViewHolder) {
            final AdapterSection section = ((AbsAdapter.HeaderViewHolder) holder).getSection();
            return section != null && !TextUtils.isEmpty(section.getEmptyViewPlaceholder()) && section.getNbItems() == 0;
        } else {
            return false;
        }
    }
}
