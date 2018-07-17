package im.vector.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.matrix.androidsdk.data.RoomMediaMessage;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.listeners.ItemPositionChangedListener;

/**
 * Adapter for previews of media files.
 */
public class MediaPreviewAdapter extends RecyclerView.Adapter<MediaPreviewAdapter.MediaItemViewHolder> {

    List<RoomMediaMessage> mImagePreviewList;

    ItemPositionChangedListener mItemPositionChangedListener;

    /**
     * Initialises the adapter and sets member fields.
     * @param imagePreviewList list with RoomMediaMessages to be displayed.
     * @param itemPositionChangedListener listener that listens for clicks on the items.
     */
    public MediaPreviewAdapter(List<RoomMediaMessage> imagePreviewList, ItemPositionChangedListener itemPositionChangedListener) {
        mImagePreviewList = imagePreviewList;
        mItemPositionChangedListener = itemPositionChangedListener;
    }

    /**
     * ViewHolder for all items in the adapter.
     */
    public static class MediaItemViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.image_preview)
        ImageView mImagePreview;

        MediaItemViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }

    @Override
    public int getItemCount() {
        // Return the amount of items in the list.
        return mImagePreviewList.size();
    }

    @Override
    public MediaItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Create a new CardItem when needed.
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.media_preview, parent, false);
        MediaItemViewHolder imageItemViewHolder = new MediaItemViewHolder(view);
        return imageItemViewHolder;
    }

    @Override
    public void onBindViewHolder(MediaItemViewHolder holder, int position) {
        final Context context = holder.mImagePreview.getContext();
        String mimeType = mImagePreviewList.get(position).getMimeType(context);
        Uri uri = mImagePreviewList.get(position).getUri();

        if (mimeType != null) {
            if (mimeType.startsWith("image")) {
                holder.mImagePreview.setImageURI(uri);
            } else if (mimeType.startsWith("video")) {
                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                mediaMetadataRetriever.setDataSource(context, uri);
                Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime();
                holder.mImagePreview.setImageBitmap(bitmap);
            } else {
                holder.mImagePreview.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.filetype_attachment));
                holder.mImagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        }

        final int itemPosition = position;

        // Call the listener (with the item position) when an item is clicked on.
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mItemPositionChangedListener.onItemPositionChangedListener(itemPosition);
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
}