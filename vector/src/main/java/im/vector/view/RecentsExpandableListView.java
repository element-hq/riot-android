/*
 * Copyright 2016 OpenMarket Ltd
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
import android.view.MotionEvent;
import android.widget.ExpandableListView;

/**
 * Defines a custom Expandable listview.
 * It only tracks the touch move and up.
 */
public class RecentsExpandableListView extends ExpandableListView {

    public interface DragAndDropEventsListener {
        /**
         * Provides the new y touch position and the related child view.
         *
         * @param y             the touch y position.
         * @param groupPosition the child view group position
         * @param childPosition the child view child position
         */
        void onTouchMove(int y, int groupPosition, int childPosition);

        /**
         * The user ends the move
         */
        void onDrop();

        /**
         * Called the list view is over scrolled
         *
         * @param isTop set to true when the list is top over scrolled.
         */
        void onOverScrolled(boolean isTop);
    }

    // the touched child view
    private int mTouchedGroupPosition = -1;
    private int mTouchedChildPosition = -1;

    // the current Y position
    private int mCurrentY = -1;

    // the drag events listener
    public DragAndDropEventsListener mDragAndDropEventsListener = null;

    // default constructor
    public RecentsExpandableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        if (clampedY && (null != mDragAndDropEventsListener)) {
            mDragAndDropEventsListener.onOverScrolled(0 == getFirstVisiblePosition());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        final int x = (int) event.getX();
        final int y = (int) event.getY();

        mCurrentY = y;

        long packagedPosition = getExpandableListPosition(pointToPosition(x, y));

        int groupPosition = ExpandableListView.getPackedPositionGroup(packagedPosition);
        int childPosition = ExpandableListView.getPackedPositionChild(packagedPosition);

        // store values
        mTouchedGroupPosition = Math.max(groupPosition, 0);
        // childPosition can be negative if the touch is done on the header
        mTouchedChildPosition = Math.max(childPosition, 0);

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (null != mDragAndDropEventsListener) {
                    mDragAndDropEventsListener.onTouchMove(mCurrentY, mTouchedGroupPosition, mTouchedChildPosition);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (null != mDragAndDropEventsListener) {
                    mDragAndDropEventsListener.onDrop();
                }
            default:
                break;
        }

        return super.onTouchEvent(event);
    }

    /**
     * @return the touch Y position
     */
    public int getTouchedY() {
        return mCurrentY;
    }

    /**
     * return the touched cell group position
     */
    public int getTouchedGroupPosition() {
        return mTouchedGroupPosition;
    }

    /**
     * @return the touched cell child group position.
     */
    public int getTouchedChildPosition() {
        return mTouchedChildPosition;
    }
}
