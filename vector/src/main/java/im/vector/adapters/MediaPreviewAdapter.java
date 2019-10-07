package im.vector.adapters;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import org.matrix.androidsdk.data.RoomMediaMessage;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;

/**
 * Adapter for previews of media files.
 */
public class MediaPreviewAdapter extends RecyclerView.Adapter<MediaPreviewAdapter.MediaItemViewHolder> {


    private final List<RoomMediaMessage> mImagePreviewList;
    private final EventListener mEventListener;

    /**
     * Initialises the adapter and sets member fields.
     *
     * @param imagePreviewList list with RoomMediaMessages to be displayed.
     * @param eventListener    The event listener attached to the adapter
     */
    public MediaPreviewAdapter(@NonNull final List<RoomMediaMessage> imagePreviewList,
                               @NonNull final EventListener eventListener) {
        mImagePreviewList = imagePreviewList;
        mEventListener = eventListener;
    }

    @Override
    public int getItemCount() {
        return mImagePreviewList.size();
    }

    @NonNull
    @Override
    public MediaItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_item_media_preview, parent, false);
        return new MediaItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaItemViewHolder holder, int position) {
        final Context context = holder.mImagePreview.getContext();
        final RoomMediaMessage roomMediaMessage = mImagePreviewList.get(position);
        final String mimeType = roomMediaMessage.getMimeType(context);
        final Uri uri = roomMediaMessage.getUri();
        if (mimeType != null) {
            if (mimeType.startsWith("image") || mimeType.startsWith("video")) {
                Glide.with(context)
                        .asBitmap()
                        .load(uri)
                        .apply(new RequestOptions().frame(0))
                        .into(holder.mImagePreview);
            } else {
                holder.mImagePreview.setImageResource(R.drawable.filetype_attachment);
                holder.mImagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mEventListener.onMediaMessagePreviewClicked(roomMediaMessage);
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return position;
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

    public interface EventListener {
        void onMediaMessagePreviewClicked(@NonNull final RoomMediaMessage roomMediaMessage);
    }

}


