package im.vector.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.data.RoomMediaMessage;

import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import im.vector.R;
import im.vector.adapters.MediaPreviewAdapter;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Previews media selected to be send.
 */
public class MediaPreviewerActivity extends MXCActionBarActivity implements MediaPreviewAdapter.EventListener {

    private static final String LOG_TAG = MediaPreviewerActivity.class.getSimpleName();

    //the picture uri if a picture is taken with the camera
    public static final String EXTRA_CAMERA_PICTURE_URI = "EXTRA_CAMERA_PICTURE_URI";
    // the room title (string)
    public static final String EXTRA_ROOM_TITLE = "EXTRA_ROOM_TITLE";

    private RoomMediaMessage mCurrentRoomMediaMessage;

    @BindView(R.id.media_previewer_image_view)
    ImageView mPreviewerImageView;
    @BindView(R.id.media_previewer_video_view)
    VideoView mPreviewerVideoView;
    @BindView(R.id.media_previewer_video_thumbnail)
    ImageView mPreviewerVideoThumbnail;
    @BindView(R.id.media_previewer_list)
    RecyclerView mPreviewerRecyclerView;
    @BindView(R.id.media_previewer_file_name)
    TextView mFileNameView;
    @BindView(R.id.media_previewer_video_play)
    ImageView mPlayCircleView;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }


    @Override
    public int getLayoutRes() {
        return R.layout.activity_media_previewer;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void initUiAndData() {
        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.d(LOG_TAG, "onCreate : restart the application");
            CommonActivityUtils.restartApp(this);
            return;
        }
        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }
        mPreviewerVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onVideoPreviewClicked();
                return false;
            }
        });
        mPlayCircleView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onVideoPreviewClicked();
                return false;
            }
        });
        configureToolbar();
        final String roomTitle = getIntent().getExtras().getString(EXTRA_ROOM_TITLE);
        getSupportActionBar().setTitle(roomTitle);
        final List<RoomMediaMessage> sharedDataItems = getSharedItems();
        setupRecyclerView(sharedDataItems);
    }

    @OnClick(R.id.media_previewer_send_button)
    public void onClick() {
        setResult(Activity.RESULT_OK, getIntent());
        finish();
    }

    //region MediaPreviewAdapter.EventListener

    @Override
    public void onMediaMessagePreviewClicked(@NonNull final RoomMediaMessage roomMediaMessage) {
        if (roomMediaMessage != mCurrentRoomMediaMessage) {
            mCurrentRoomMediaMessage = roomMediaMessage;
            updatePreview(roomMediaMessage);
        }
    }

    //endregion

    //region Private methods

    private void updatePreview(final RoomMediaMessage roomMediaMessage) {
        mPreviewerVideoView.pause();
        mFileNameView.setText(roomMediaMessage.getFileName(this));
        final String mimeType = roomMediaMessage.getMimeType(this);
        final Uri uri = roomMediaMessage.getUri();

        if (mimeType != null) {
            if (mimeType.startsWith("image")) {
                mPreviewerImageView.setVisibility(View.VISIBLE);
                mPreviewerVideoView.setVisibility(View.GONE);
                mPreviewerVideoThumbnail.setVisibility(View.GONE);
                mPlayCircleView.setVisibility(View.GONE);
                Glide.with(this)
                        .load(uri)
                        .apply(new RequestOptions().fitCenter())
                        .into(mPreviewerImageView);
            } else if (mimeType.startsWith("video")) {
                mPreviewerImageView.setVisibility(View.GONE);
                mPreviewerVideoView.setVisibility(View.GONE);
                mPreviewerVideoThumbnail.setVisibility(View.VISIBLE);
                mPlayCircleView.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(uri)
                        .apply(new RequestOptions().fitCenter().frame(0))
                        .into(mPreviewerVideoThumbnail);
                mPreviewerVideoView.setVideoURI(uri);
                mPreviewerVideoView.seekTo(0);
            } else {
                mPreviewerImageView.setVisibility(View.VISIBLE);
                mPreviewerVideoView.setVisibility(View.GONE);
                mPreviewerVideoThumbnail.setVisibility(View.GONE);
                mPreviewerImageView.setImageResource(R.drawable.filetype_attachment);
            }
        }
    }

    private void setupRecyclerView(@NonNull final List<RoomMediaMessage> sharedDataItems) {
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);
        mPreviewerRecyclerView.setLayoutManager(linearLayoutManager);
        final MediaPreviewAdapter mediaPreviewAdapter = new MediaPreviewAdapter(sharedDataItems, this);
        mPreviewerRecyclerView.setAdapter(mediaPreviewAdapter);
        final RoomMediaMessage firstRoomMedia = sharedDataItems.get(0);
        updatePreview(firstRoomMedia);
    }

    private List<RoomMediaMessage> getSharedItems() {
        final List<RoomMediaMessage> sharedDataItems = RoomMediaMessage.listRoomMediaMessages(getIntent());
        if (sharedDataItems.isEmpty()) {
            final Uri roomMediaUri = Uri.parse(getIntent().getStringExtra(EXTRA_CAMERA_PICTURE_URI));
            final RoomMediaMessage roomMediaMessage = new RoomMediaMessage(roomMediaUri);
            sharedDataItems.add(roomMediaMessage);
        }
        return sharedDataItems;
    }

    private void onVideoPreviewClicked() {
        if (!mPreviewerVideoView.isPlaying()) {
            mPreviewerVideoView.setVisibility(View.VISIBLE);
            mPreviewerVideoThumbnail.setVisibility(View.GONE);
            mPlayCircleView.setVisibility(View.GONE);
            mPreviewerVideoView.start();
        } else {
            mPreviewerVideoThumbnail.setVisibility(View.VISIBLE);
            mPlayCircleView.setVisibility(View.VISIBLE);
            mPreviewerVideoView.setVisibility(View.GONE);
            mPreviewerVideoView.pause();
        }
    }

    //endregion
}
