/* 
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.console.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * The listView automatically scrolls down when its height is updated.
 * It is used to scroll the list when the keyboard is displayed
 */
public class AutoScrollDownListView extends ListView {

    public AutoScrollDownListView (Context context) {
        super(context);
    }

    public AutoScrollDownListView (Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoScrollDownListView (Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {
        super.onSizeChanged(xNew, yNew, xOld, yOld);

        // check if the keyboard is displayed
        // we don't want that the list scrolls to the bottom when the keyboard is hidden.
        if (yNew < yOld) {
            this.post(new Runnable() {
                @Override
                public void run() {
                    setSelection(getCount() - 1);
                }
            });
        }
    }
}
