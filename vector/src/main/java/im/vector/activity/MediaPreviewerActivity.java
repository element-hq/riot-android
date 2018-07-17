package im.vector.activity;

import android.net.Uri;
import android.os.Build;
import android.app.Activity;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import im.vector.R;
import im.vector.adapters.MediaPreviewAdapter;
import im.vector.listeners.ItemPositionChangedListener;
import kotlin.Pair;

/**
 * Previews media selected to be send.
 */
public class MediaPreviewerActivity extends MXCActionBarActivity implements ItemPositionChangedListener {

    /**
     * the picture uri if a picture is taken with the camera
     */
    public static final String EXTRA_CAMERA_PICTURE_URI = "EXTRA_CAMERA_PICTURE_URI";
    /**
     * the room title (string)
     */
    public static final String EXTRA_ROOM_TITLE = "EXTRA_ROOM_TITLE";

    private static final String LOG_TAG = MediaPreviewerActivity.class.getSimpleName();

    @BindView(R.id.images_preview)
    RecyclerView mImagesPreview;

    @BindView(R.id.image_previewer)
    ImageView mImagePreview;

    @BindView(R.id.web_previewer)
    WebView mWebPreview;

    @BindView(R.id.video_previewer)
    VideoView mVideoPreview;

    @BindView(R.id.file_previewer)
    ImageView mFilePreview;

    @BindView(R.id.file_name)
    TextView mFileNameView;

    private RoomMediaMessage mCurrentRoomMediaMessage;

    List<RoomMediaMessage> mSharedDataItems;

    @NotNull
    @Override
    public Pair getOtherThemes() {
        return new Pair(R.style.AppTheme_NoActionBar_Dark, R.style.AppTheme_NoActionBar_Black);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_media_previewer;
    }

    @Override
    public void onItemPositionChangedListener(int position) {
        setPreview(mSharedDataItems.get(position));
    }

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

        configureToolbar();

        String roomTitle = (String) getIntent().getExtras().get(EXTRA_ROOM_TITLE);
        if (!TextUtils.isEmpty(roomTitle)) {
            getSupportActionBar().setTitle(roomTitle);
        }

        setStatusBarColor(findViewById(R.id.status_bar_background),
                ContextCompat.getColor(this, R.color.transparent_dark));

        // Resize web content to prevent scrollbars.
        mWebPreview.getSettings().setUseWideViewPort(true);
        mWebPreview.getSettings().setLoadWithOverviewMode(true);

        mSharedDataItems = new ArrayList<>(RoomMediaMessage.listRoomMediaMessages(getIntent(), RoomMediaMessage.class.getClassLoader()));

        if (mSharedDataItems.isEmpty()) {
            mSharedDataItems.add(new RoomMediaMessage(Uri.parse(getIntent().getStringExtra(EXTRA_CAMERA_PICTURE_URI))));
        }

        if (!mSharedDataItems.isEmpty()) {
            LinearLayoutManager imagesPreviewLinearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            mImagesPreview.setLayoutManager(imagesPreviewLinearLayoutManager);

            MediaPreviewAdapter mediaPreviewAdapter = new MediaPreviewAdapter(mSharedDataItems, this);
            mImagesPreview.setAdapter(mediaPreviewAdapter);

            setPreview(mSharedDataItems.get(0));
        }
    }

    @OnClick(R.id.send_floating_action_button)
    public void onClick() {
        setResult(Activity.RESULT_OK, getIntent());
        finish();
    }

    private void setPreview(RoomMediaMessage roomMediaMessage) {

        // Prevent blinking when tapping on the same item multiple times.
        if (roomMediaMessage != mCurrentRoomMediaMessage) {
            mCurrentRoomMediaMessage = roomMediaMessage;

            mWebPreview.setVisibility(View.GONE);
            mImagePreview.setVisibility(View.GONE);
            mVideoPreview.setVisibility(View.GONE);
            mFilePreview.setVisibility(View.GONE);
            mFileNameView.setVisibility(View.GONE);

            String mimeType = roomMediaMessage.getMimeType(this);
            Uri uri = roomMediaMessage.getUri();

            if (mimeType != null) {
                if (mimeType.startsWith("image")) {
                    if (mimeType.endsWith("gif")) {
                        mWebPreview.loadUrl(uri.toString());
                        mWebPreview.setVisibility(View.VISIBLE);
                    } else {
                        mImagePreview.setImageURI(uri);
                        mImagePreview.setVisibility(View.VISIBLE);
                    }
                } else if (mimeType.startsWith("video")) {
                    mVideoPreview.setVideoURI(uri);
                    mVideoPreview.seekTo(100);
                    mVideoPreview.setVisibility(View.VISIBLE);

                    // Pause/play video on click.
                    mVideoPreview.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View view, MotionEvent motionEvent) {
                            if (!mVideoPreview.isPlaying()) {
                                mVideoPreview.start();
                            } else {
                                mVideoPreview.pause();
                            }
                            return false;
                        }
                    });
                } else {
                    // As other files can't be previewed, show a generic file image.
                    mFilePreview.setVisibility(View.VISIBLE);
                }

                mFileNameView.setText(roomMediaMessage.getFileName(this));
                mFileNameView.setVisibility(View.VISIBLE);
            }
        }
    }


    private void setStatusBarColor(View statusBar, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window window = getWindow();
            window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

            int statusBarHeight = getStatusBarHeight();

            statusBar.getLayoutParams().height = statusBarHeight;
            statusBar.setBackgroundColor(color);
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }

        return result;
    }
}
