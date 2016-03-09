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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.ImageView;

import org.matrix.androidsdk.rest.model.Event;

import im.vector.adapters.VectorRoomSummaryAdapter;

/**
 * Display a circular image.
 */
public class RecentsExpandableListView extends ExpandableListView {

    public interface DragAndDropEventsListener {
        /**
         */
        void onCellMove(int y, int groupPosition, int childPosition);

        void onDragEnd();
    }

    private static final String LOG_TAG = "VectExpandableListView";

    public RecentsExpandableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public int mSelectedGroupPosition = -1;
    public int mSelectedChildPosition = -1;

    private int mLastY = -1;

    public DragAndDropEventsListener mDragAndDropEventsListener = null;

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        final int action = event.getAction();
        final int x = (int) event.getX();
        final int y = (int) event.getY();

        mLastY = y;

        int flatPosition = pointToPosition(x, y);
        long packagedPosition = getExpandableListPosition(flatPosition);

        int groupPosition = ExpandableListView.getPackedPositionGroup(packagedPosition);
        int childPosition = ExpandableListView.getPackedPositionChild(packagedPosition);

        mSelectedGroupPosition = groupPosition;
        mSelectedChildPosition = Math.max(childPosition, 0);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:

                if (null != mDragAndDropEventsListener) {
                    mDragAndDropEventsListener.onCellMove(mLastY, mSelectedGroupPosition, mSelectedChildPosition);
                }

                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:

                if (null != mDragAndDropEventsListener) {
                    mDragAndDropEventsListener.onDragEnd();
                }

            default:
                break;
        }

        return super.onTouchEvent(event);
    }

    public int getCellY() {
        return mLastY;
    }
}
