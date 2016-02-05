/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.activity;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.JsonUtils;
import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.ViewedRoomTracker;
import im.vector.adapters.ImageCompressionDescription;
import im.vector.fragments.ConsoleMessageListFragment;
import im.vector.fragments.ImageSizeSelectionDialogFragment;
import im.vector.fragments.VectorMessagesSearchResultsListFragment;
import im.vector.services.EventStreamService;
import im.vector.util.NotificationUtils;
import im.vector.util.ResourceUtils;
import im.vector.util.VectorUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a generic activity search method
 */
public class VectorSearchesActivity extends MXCActionBarActivity {

    private static final String TAG_FRAGMENT_ROOMS_SEARCH_RESULT_LIST = "im.vector.activity.TAG_FRAGMENT_ROOMS_SEARCH_RESULT_LIST";
    private static final String LOG_TAG = "VectorSearchesActivity";

    private VectorMessagesSearchResultsListFragment mVectorMessagesSearchResultsListFragment;
    private MXSession mSession;

    // UI items
    private ImageView mBackgroundImageview;
    private TextView mNoResultsTextView;
    private View mInitialSearchInProgressView;
    private EditText mEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_searches);

        // the session should be passed in paramater
        // but the current design does not describe how the multi accounts will be managed.
        mSession = Matrix.getInstance(this).getDefaultSession();

        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        mBackgroundImageview = (ImageView)findViewById(R.id.search_background_imageview);
        mNoResultsTextView = (TextView)findViewById(R.id.search_no_result_textview);
        mInitialSearchInProgressView =  findViewById(R.id.search_in_progress_view);

        FragmentManager fm = getSupportFragmentManager();
        mVectorMessagesSearchResultsListFragment = (VectorMessagesSearchResultsListFragment) fm.findFragmentByTag(TAG_FRAGMENT_ROOMS_SEARCH_RESULT_LIST);

        if (null == mVectorMessagesSearchResultsListFragment) {
            mVectorMessagesSearchResultsListFragment = VectorMessagesSearchResultsListFragment.newInstance(mSession.getMyUser().userId, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            fm.beginTransaction().add(R.id.search_fragment_messages, mVectorMessagesSearchResultsListFragment, TAG_FRAGMENT_ROOMS_SEARCH_RESULT_LIST).commit();
        }

        // replace the action bar
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayOptions(android.support.v7.app.ActionBar.DISPLAY_SHOW_CUSTOM | android.support.v7.app.ActionBar.DISPLAY_SHOW_HOME | android.support.v7.app.ActionBar.DISPLAY_HOME_AS_UP);

        android.support.v7.app.ActionBar.LayoutParams layout = new android.support.v7.app.ActionBar.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT);
        View actionBarLayout =  getLayoutInflater().inflate(R.layout.vector_search_action_bar, null);
        actionBar.setCustomView(actionBarLayout, layout);

        // add text listener
        mEditText = (EditText) actionBarLayout.findViewById(R.id.room_action_bar_edit_text);

        actionBarLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                mEditText.requestFocus();

                InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                im.showSoftInput(mEditText, 0);
            }
        }, 100);

        mBackgroundImageview.setVisibility(View.VISIBLE);

        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    mInitialSearchInProgressView.setVisibility(View.VISIBLE);

                    // the search command should only be passed to the active fragment
                    // by now, there is only one active fragment
                    // it is planned to have 3 (search by room names, by messages, by member name)
                    mVectorMessagesSearchResultsListFragment.searchPattern(mEditText.getText().toString(), new MatrixMessageListFragment.OnSearchResultListener() {

                        @Override
                        public void onSearchSucceed(int nbrMessages) {
                            onSearchEnd(nbrMessages);
                        }

                        @Override
                        public void onSearchFailed() {
                            onSearchEnd(0);
                        }
                    });
                    return true;
                }
                return false;
            }
        });

    }

    private void onSearchEnd(int nbrMessages) {
        mInitialSearchInProgressView.setVisibility(View.GONE);
        mBackgroundImageview.setVisibility((0 == nbrMessages) ? View.VISIBLE : View.GONE);
        // display the "no result" text only if there is dedicated pattern
        mNoResultsTextView.setVisibility(((0 == nbrMessages) && !TextUtils.isEmpty(mEditText.getText().toString())) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}


