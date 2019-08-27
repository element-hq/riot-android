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

package im.vector.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.matrix.androidsdk.core.Log;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.vector.R;
import im.vector.util.RoomDirectoryData;

public class RoomDirectoryAdapter extends RecyclerView.Adapter<RoomDirectoryAdapter.RoomDirectoryViewHolder> {

    private static final String LOG_TAG = RoomDirectoryAdapter.class.getSimpleName();

    private final List<RoomDirectoryData> mList;

    private final OnSelectRoomDirectoryListener mListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public RoomDirectoryAdapter(final List<RoomDirectoryData> serversList, final OnSelectRoomDirectoryListener listener) {
        mList = (null == serversList) ? new ArrayList<RoomDirectoryData>() : new ArrayList<>(serversList);
        mListener = listener;
    }

    /**
     * Update the servers list
     *
     * @param serversList the new servers list
     */
    public void updateDirectoryServersList(List<RoomDirectoryData> serversList) {
        mList.clear();
        mList.addAll(serversList);
        notifyDataSetChanged();
    }

    /*
     * *********************************************************************************************
     * RecyclerView.Adapter methods
     * *********************************************************************************************
     */

    @Override
    public RoomDirectoryViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(viewGroup.getContext());
        final View itemView = layoutInflater.inflate(R.layout.item_room_directory, viewGroup, false);
        return new RoomDirectoryAdapter.RoomDirectoryViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(RoomDirectoryAdapter.RoomDirectoryViewHolder viewHolder, int position) {
        if (position < mList.size()) {
            viewHolder.populateViews(mList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    /*
     * *********************************************************************************************
     * View holders
     * *********************************************************************************************
     */

    class RoomDirectoryViewHolder extends RecyclerView.ViewHolder {
        final View vMainView;
        final ImageView vAvatarView;
        final TextView vServerTextView;
        final TextView vDescriptionTextView;

        private RoomDirectoryViewHolder(final View itemView) {
            super(itemView);
            vMainView = itemView;
            vAvatarView = itemView.findViewById(R.id.room_directory_avatar);
            vServerTextView = itemView.findViewById(R.id.room_directory_display_name);
            vDescriptionTextView = itemView.findViewById(R.id.room_directory_description);
        }

        private void populateViews(final RoomDirectoryData server) {
            vServerTextView.setText(server.getDisplayName());

            String description = null;

            if (server.isIncludedAllNetworks()) {
                description = vServerTextView.getContext().getString(R.string.directory_server_all_rooms_on_server, server.getDisplayName());
            } else if (TextUtils.equals("Matrix", server.getDisplayName())) {
                description = vServerTextView.getContext().getString(R.string.directory_server_native_rooms, server.getDisplayName());
            }

            vDescriptionTextView.setText(description);
            vDescriptionTextView.setVisibility(!TextUtils.isEmpty(description) ? View.VISIBLE : View.GONE);

            setAvatar(vAvatarView,
                    server.getAvatarUrl(),
                    server.isIncludedAllNetworks() ? null : vServerTextView.getContext().getResources().getDrawable(R.drawable.network_matrix));

            vMainView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onSelectRoomDirectory(server);
                }
            });
        }
    }

    /*
     * *********************************************************************************************
     * avatar downloader
     * *********************************************************************************************
     */
    private static final Map<String, Bitmap> mAvatarByUrl = new HashMap<>();
    private static final Map<String, List<WeakReference<ImageView>>> mPendingDownloadByUrl = new HashMap<>();

    /**
     * Load the avatar bitmap in the provided imageView
     *
     * @param imageView the image view
     * @param avatarURL the avatar URL
     */
    private void setAvatar(final ImageView imageView, final String avatarURL, Drawable defaultAvatar) {
        imageView.setImageDrawable(defaultAvatar);
        imageView.setTag(null);

        if (null != avatarURL) {
            Bitmap bitmap = mAvatarByUrl.get(avatarURL);
            // if the image is not cached, download it
            if (null == bitmap) {
                downloadAvatar(imageView, avatarURL);
            } else {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    /**
     * Download the room avatar.
     *
     * @param imageView the image view
     * @param avatarURL the avatar url
     */
    private void downloadAvatar(final ImageView imageView, final String avatarURL) {
        // sanity check
        if ((null == imageView) || (null == avatarURL)) {
            return;
        }

        // set the ImageView tag
        imageView.setTag(avatarURL);

        WeakReference<ImageView> weakImageView = new WeakReference<>(imageView);

        // test if there is already a pending download
        if (mPendingDownloadByUrl.containsKey(avatarURL)) {
            mPendingDownloadByUrl.get(avatarURL).add(weakImageView);
            return;
        }

        mPendingDownloadByUrl.put(avatarURL, new ArrayList<>(Arrays.asList(weakImageView)));

        AsyncTask<Void, Void, Bitmap> task = new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... params) {
                Bitmap bitmap = null;
                try {
                    URL url = new URL(avatarURL);
                    bitmap = BitmapFactory.decodeStream((InputStream) url.getContent());
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## downloadAvatar() : cannot load the avatar " + avatarURL, e);
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if ((null != bitmap) && !mAvatarByUrl.containsKey(avatarURL)) {
                    mAvatarByUrl.put(avatarURL, bitmap);
                }

                if (mPendingDownloadByUrl.containsKey(avatarURL)) {
                    List<WeakReference<ImageView>> weakImageViews = mPendingDownloadByUrl.get(avatarURL);
                    mPendingDownloadByUrl.remove(avatarURL);

                    for (WeakReference<ImageView> weakImageView : weakImageViews) {
                        ImageView imageViewToUpdate = weakImageView.get();

                        if ((null != imageViewToUpdate) && TextUtils.equals((String) imageView.getTag(), avatarURL)) {
                            imageViewToUpdate.setImageBitmap(bitmap);
                        }
                    }
                }
            }
        };

        try {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## downloadAvatar() failed " + e.getMessage(), e);
            task.cancel(true);
        }
    }

    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface OnSelectRoomDirectoryListener {
        void onSelectRoomDirectory(RoomDirectoryData roomDirectory);
    }
}