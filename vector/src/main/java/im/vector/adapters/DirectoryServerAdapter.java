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
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import im.vector.util.DirectoryServerData;

import im.vector.R;

public class DirectoryServerAdapter extends RecyclerView.Adapter<DirectoryServerAdapter.DirectoryServerViewHolder> implements Filterable {

    private static final String LOG_TAG = "DirServerAdapter";

    private List<DirectoryServerData> mList;
    private List<DirectoryServerData> mFilteredList;

    private final OnSelectDirectoryServerListener mListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public DirectoryServerAdapter(final List<DirectoryServerData> serversList, final OnSelectDirectoryServerListener listener) {
        mList = serversList;
        mFilteredList = new ArrayList<>(serversList);
        mListener = listener;
    }

    /**
     * Update the servers list
     * @param serversList the new servers list
     */
    public void updateDirectoryServersList(List<DirectoryServerData> serversList) {
        mList = serversList;
        mFilteredList = new ArrayList<>(serversList);
        notifyDataSetChanged();
    }

    /*
     * *********************************************************************************************
     * RecyclerView.Adapter methods
     * *********************************************************************************************
     */

    @Override
    public DirectoryServerViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(viewGroup.getContext());
        final View itemView = layoutInflater.inflate(R.layout.item_directory_server, viewGroup, false);
        return new DirectoryServerAdapter.DirectoryServerViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(DirectoryServerAdapter.DirectoryServerViewHolder viewHolder, int position) {
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
                    mFilteredList.add(new DirectoryServerData(filterPattern, filterPattern, null, null, false));

                    Pattern pattern = Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE);

                    for (final DirectoryServerData serverData : mList) {
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

    class DirectoryServerViewHolder extends RecyclerView.ViewHolder {
        ImageView vAvatarView;
        TextView vServerTextView;

        private DirectoryServerViewHolder(final View itemView) {
            super(itemView);
            vAvatarView = (ImageView) itemView.findViewById(R.id.directory_server_avatar);
            vServerTextView = (TextView) itemView.findViewById(R.id.directory_server_display_name);
        }

        private void populateViews(final DirectoryServerData server) {
            vServerTextView.setText(server.getDisplayName());
            setAvatar(vAvatarView, server.getAvatarUrl(), server.isIncludedAllNetworks() ? null : vServerTextView.getContext().getDrawable(R.drawable.network_matrix));

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onSelectDirectoryServer(server);
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
            imageView.setTag(avatarURL);

            // if the image is not cached, download it
            if (null == bitmap) {
                new AsyncTask<Void, Void, Bitmap>() {
                    @Override
                    protected Bitmap doInBackground(Void... params) {
                        Bitmap bitmap = null;
                        try {
                            URL url = new URL(avatarURL);
                            bitmap = BitmapFactory.decodeStream((InputStream) url.getContent());
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## setAvatar() : cannot load the avatar " + avatarURL);
                        }
                        return bitmap;
                    }

                    @Override
                    protected void onPostExecute(Bitmap bitmap) {
                        if ((null != bitmap) && !mAvatarByUrl.containsKey(avatarURL)) {
                            mAvatarByUrl.put(avatarURL, bitmap);
                        }

                        if (TextUtils.equals((String) imageView.getTag(), avatarURL)) {
                            imageView.setImageBitmap(bitmap);
                        }
                    }
                }.execute();
            } else {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface OnSelectDirectoryServerListener {
        void onSelectDirectoryServer(DirectoryServerData directoryServer);
    }
}