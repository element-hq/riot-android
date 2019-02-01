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
package im.vector.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * View that displays a disc representing a percentage.
 */
public class VideoRecordView extends RelativeLayout {

    private VideoRecordProgressView mVideoRecordProgressView;

    /**
     * constructors
     **/
    public VideoRecordView(Context context) {
        super(context);
        initView();
    }

    public VideoRecordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public VideoRecordView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    /**
     * Common initialisation method.
     */
    private void initView() {
        View.inflate(getContext(), im.vector.R.layout.video_record_view, this);

        // retrieve the UI items
        mVideoRecordProgressView = findViewById(im.vector.R.id.video_record_progress_view);
    }

    /**
     * Start the video recording animation
     */
    public void startAnimation() {
        mVideoRecordProgressView.startAnimation();
    }

    /**
     * Stop the animation
     */
    private void stopAnimation() {
        mVideoRecordProgressView.stopAnimation();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        // if the view is hidden
        if ((View.GONE == visibility) || (View.INVISIBLE == visibility)) {
            stopAnimation();
        }
    }
}
