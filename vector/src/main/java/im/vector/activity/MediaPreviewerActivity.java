package im.vector.activity;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.ColorRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.bumptech.glide.Glide;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import im.vector.R;
import im.vector.adapters.MediaPreviewAdapter;
import kotlin.Pair;

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
    @BindView(R.id.media_previewer_list)
    RecyclerView mPreviewerRecyclerView;
    @BindView(R.id.media_previewer_file_name)
    TextView mFileNameView;


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
        configureToolbar();
        final String roomTitle = getIntent().getExtras().getString(EXTRA_ROOM_TITLE);
        getSupportActionBar().setTitle(roomTitle);
        // Resize web content to prevent scrollbars.
        final List<RoomMediaMessage> sharedDataItems = new ArrayList<>(RoomMediaMessage.listRoomMediaMessages(getIntent(), RoomMediaMessage.class.getClassLoader()));
        if (sharedDataItems.isEmpty()) {
            sharedDataItems.add(new RoomMediaMessage(Uri.parse(getIntent().getStringExtra(EXTRA_CAMERA_PICTURE_URI))));
        } else {
            final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            mPreviewerRecyclerView.setLayoutManager(linearLayoutManager);
            final MediaPreviewAdapter mediaPreviewAdapter = new MediaPreviewAdapter(sharedDataItems, this);
            mPreviewerRecyclerView.setAdapter(mediaPreviewAdapter);
            updatePreview(sharedDataItems.get(0));
        }
    }

    @Override
    public void onMediaMessagePreviewClicked(final RoomMediaMessage roomMediaMessage) {
        if (roomMediaMessage != mCurrentRoomMediaMessage) {
            mCurrentRoomMediaMessage = roomMediaMessage;
            updatePreview(roomMediaMessage);
        }
    }

    void onVideoPreviewClicked() {
        if (!mPreviewerVideoView.isPlaying()) {
            mPreviewerVideoView.start();
        } else {
            mPreviewerVideoView.pause();
        }
    }

    @OnClick(R.id.media_previewer_send_button)
    public void onClick() {
        setResult(Activity.RESULT_OK, getIntent());
        finish();
    }

    private void updatePreview(final RoomMediaMessage roomMediaMessage) {
        mPreviewerVideoView.pause();
        mFileNameView.setText(roomMediaMessage.getFileName(this));
        
        final String mimeType = roomMediaMessage.getMimeType(this);
        final Uri uri = roomMediaMessage.getUri();
        if (mimeType != null) {
            if (mimeType.startsWith("image")) {
                mPreviewerImageView.setVisibility(View.VISIBLE);
                mPreviewerVideoView.setVisibility(View.GONE);
                Glide.with(this)
                        .asBitmap()
                        .load(uri)
                        .into(mPreviewerImageView);
            } else if (mimeType.startsWith("video")) {
                mPreviewerImageView.setVisibility(View.GONE);
                mPreviewerVideoView.setVisibility(View.VISIBLE);
                mPreviewerVideoView.setVideoURI(uri);
                mPreviewerVideoView.seekTo(100);
            } else {
                mPreviewerImageView.setVisibility(View.VISIBLE);
                mPreviewerVideoView.setVisibility(View.GONE);
                mPreviewerImageView.setImageResource(R.drawable.filetype_attachment);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setStatusBarColor(@ColorRes int statusBarColor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return;
        //getWindow().setStatusBarColor(ContextCompat.getColor(this, statusBarColor));
    }

}
