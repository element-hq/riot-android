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

package im.vector.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.util.Log;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import im.vector.R;
import im.vector.util.RoomDirectoryData;

public class RoomDirectoryAdapter extends RecyclerView.Adapter<RoomDirectoryAdapter.RoomDirectoryViewHolder> implements Filterable {

    private static final String LOG_TAG = "RoomDirectoryAdapter";

    private List<RoomDirectoryData> mList;
    private List<RoomDirectoryData> mFilteredList;

    private final OnSelectRoomDirectoryListener mListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public RoomDirectoryAdapter(final List<RoomDirectoryData> serversList, final OnSelectRoomDirectoryListener listener) {
        mList = (null == serversList) ? new ArrayList<RoomDirectoryData>() : new ArrayList<>(serversList);
        mFilteredList = new ArrayList<>(mList);
        mListener = listener;
    }

    /**
     * Update the servers list
     * @param serversList the new servers list
     */
    public void updateDirectoryServersList(List<RoomDirectoryData> serversList) {
        mList.clear();
        mList.addAll(serversList);

        mFilteredList.clear();
        mFilteredList.addAll(serversList);
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
        viewHolder.populateViews(mFilteredList.get(position));
    }

    @Override
    public int getItemCount() {
        return mFilteredList.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                mFilteredList.clear();
                final FilterResults results = new FilterResults();

                if (TextUtils.isEmpty(constraint)) {
                    mFilteredList.addAll(mList);
                } else {
                    final String filterPattern = constraint.toString().trim();
                    mFilteredList.add(new RoomDirectoryData(filterPattern, filterPattern, null, null, false));

                    Pattern pattern = Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE);

                    for (final RoomDirectoryData serverData : mList) {
                        if (serverData.isMatched(pattern)) {
                            mFilteredList.add(serverData);
                        }
                    }
                }
                results.values = mFilteredList;
                results.count = mFilteredList.size();

                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };
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

        private RoomDirectoryViewHolder(final View itemView) {
            super(itemView);
            vMainView = itemView;
            vAvatarView = (ImageView) itemView.findViewById(R.id.room_directory_avatar);
            vServerTextView = (TextView) itemView.findViewById(R.id.room_directory_display_name);
        }

        private void populateViews(final RoomDirectoryData server) {
            vServerTextView.setText(server.getDisplayName());
            setAvatar(vAvatarView, server.getAvatarUrl(), server.isIncludedAllNetworks() ? null : vServerTextView.getContext().getResources().getDrawable(R.drawable.network_matrix));

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

        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... params) {
                Bitmap bitmap = null;
                try {
                    URL url = new URL(avatarURL);
                    bitmap = BitmapFactory.decodeStream((InputStream) url.getContent());
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## downloadAvatar() : cannot load the avatar " + avatarURL);
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

                    for(WeakReference<ImageView> weakImageView : weakImageViews) {
                        ImageView imageViewToUpdate = weakImageView.get();

                        if ((null != imageViewToUpdate) && TextUtils.equals((String) imageView.getTag(), avatarURL)) {
                            imageViewToUpdate.setImageBitmap(bitmap);
                        }
                    }
                }
            }
        }.execute();
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