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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v4.content.res.ResourcesCompat;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.Log;

import android.text.Html;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import im.vector.R;
import im.vector.VectorApp;

public class VectorImageGetter implements Html.ImageGetter {
    private final String LOG_TAG = VectorImageGetter.class.getSimpleName();

    // application image placeholder
    private static Drawable mPlaceHolder = null;

    // source to image map
    private Map<String, Drawable> mBitmapCache = new HashMap<>();

    // pending source downloads
    private Set<String> mPendingDownloads = new HashSet<>();

    /**
     * Image download listener
     */
    public interface OnImageDownloadListener {
        /**
         * An image has been downloaded.
         *
         * @param source the image URL
         */
        void onImageDownloaded(String source);
    }

    //
    private MXSession mSession;

    // listener
    private OnImageDownloadListener mListener;

    /**
     * Constructor
     *
     * @param session the session
     */
    public VectorImageGetter(MXSession session) {
        mSession = session;
    }

    /**
     * Set the listener
     *
     * @param listener the listener
     */
    public void setListener(OnImageDownloadListener listener) {
        mListener = listener;
    }

    @Override
    public Drawable getDrawable(String source) {

        // allow only url which starts with mxc://
        if ((null != source) && source.toLowerCase().startsWith(ContentManager.MATRIX_CONTENT_URI_SCHEME)) {
            if (mBitmapCache.containsKey(source)) {
                Log.d(LOG_TAG, "## getDrawable() : " + source + " already cached");
                return mBitmapCache.get(source);
            }

            if (!mPendingDownloads.contains(source)) {
                Log.d(LOG_TAG, "## getDrawable() : starts a task to download " + source);
                try {
                    new ImageDownloaderTask().execute(source);
                    mPendingDownloads.add(source);
                } catch (Throwable t) {
                    Log.e(LOG_TAG, "## getDrawable() failed " + t.getMessage());
                }
            } else {
                Log.d(LOG_TAG, "## getDrawable() : " + source + " is downloading");
            }


        }

        if (null == mPlaceHolder) {
            mPlaceHolder = ResourcesCompat.getDrawable(VectorApp.getInstance().getResources(), R.drawable.filetype_image, null);
            mPlaceHolder.setBounds(0, 0, mPlaceHolder.getIntrinsicWidth(), mPlaceHolder.getIntrinsicHeight());
        }

        return mPlaceHolder;
    }


    private class ImageDownloaderTask extends AsyncTask<Object, Void, Bitmap> {
        private String mSource;

        @Override
        protected Bitmap doInBackground(Object... params) {
            mSource = (String) params[0];
            Log.d(LOG_TAG, "## doInBackground() : " + mSource);
            try {
                return BitmapFactory.decodeStream(new URL(mSession.getContentManager().getDownloadableUrl(mSource)).openConnection().getInputStream());
            } catch (Throwable t) {
                Log.e(LOG_TAG, "## ImageDownloader() failed " + t.getMessage());
            }

            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            Log.d(LOG_TAG, "## doInBackground() : bitmap " + bitmap);

            mPendingDownloads.remove(mSource);

            if (null != bitmap) {
                Drawable drawable = new BitmapDrawable(VectorApp.getInstance().getResources(), bitmap);
                drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());

                mBitmapCache.put(mSource, drawable);

                try {
                    if (null != mListener) {
                        mListener.onImageDownloaded(mSource);
                    }
                } catch (Throwable t) {
                    Log.e(LOG_TAG, "## ImageDownloader() failed " + t.getMessage());
                }
            }
        }
    }
}
