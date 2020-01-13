/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.message.Message;

import im.vector.Matrix;
import im.vector.R;
import im.vector.notifications.NotificationUtils;
import im.vector.ui.themes.ActivityOtherThemes;
import im.vector.util.ViewUtilKt;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * LockScreenActivity is displayed within the notification to send a message without opening the application.
 */
public class LockScreenActivity extends VectorAppCompatActivity { // do NOT extend from UC*Activity, we do not want to login on this screen!
    private static final String LOG_TAG = LockScreenActivity.class.getSimpleName();

    public static final String EXTRA_SENDER_NAME = "extra_sender_name";
    public static final String EXTRA_MESSAGE_BODY = "extra_chat_body";
    public static final String EXTRA_ROOM_ID = "extra_room_id";
    private static final String EXTRA_MATRIX_ID = "extra_matrix_id";

    private static LockScreenActivity mLockScreenActivity = null;

    private MXSession session;
    private Room room;

    public static boolean isDisplayingALockScreenActivity() {
        return (null != mLockScreenActivity);
    }

    private LinearLayout mMainLayout;

    private EditText mEditText;

    @NotNull

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public ActivityOtherThemes getOtherThemes() {
        return ActivityOtherThemes.Lock.INSTANCE;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_lock_screen;
    }

    @Override
    public void doBeforeSetContentView() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // this will turn the screen on whilst honouring the screen timeout setting, so it will
        // dim/turn off depending on user configured values.
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    @Override
    public void initUiAndData() {
        // keep theme ?

        // kill any running alert
        if (null != mLockScreenActivity) {
            mLockScreenActivity.finish();
        }

        mLockScreenActivity = this;

        // remove any pending notifications
        NotificationUtils.INSTANCE.cancelAllNotifications(this);

        Intent intent = getIntent();

        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            finish();
            return;
        }

        if (!intent.hasExtra(EXTRA_SENDER_NAME)) {
            finish();
            return;
        }

        final String roomId = intent.getStringExtra(EXTRA_ROOM_ID);
        String matrixId = null;

        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            matrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        session = Matrix.getInstance(getApplicationContext()).getSession(matrixId);
        room = session.getDataHandler().getRoom(roomId);

        // display the room name as title
        String roomName = room.getRoomDisplayName(this);
        setTitle(roomName);

        ((TextView) findViewById(R.id.lock_screen_sender)).setText(getString(R.string.generic_label, intent.getStringExtra(EXTRA_SENDER_NAME)));
        ((TextView) findViewById(R.id.lock_screen_body)).setText(intent.getStringExtra(EXTRA_MESSAGE_BODY));
        ((TextView) findViewById(R.id.lock_screen_room_name)).setText(roomName);
        final View sendButton = findViewById(R.id.lock_screen_sendbutton);
        mEditText = findViewById(R.id.lock_screen_edittext);

        // disable send button
        sendButton.setEnabled(false);
        sendButton.setAlpha(ViewUtilKt.UTILS_OPACITY_HALF);

        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // disable/enable send button according to input text content
                if (TextUtils.isEmpty(s)) {
                    sendButton.setEnabled(false);
                    sendButton.setAlpha(ViewUtilKt.UTILS_OPACITY_HALF);
                } else {
                    sendButton.setEnabled(true);
                    sendButton.setAlpha(ViewUtilKt.UTILS_OPACITY_FULL);
                }
            }
        });

        mEditText.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                        int imeActionId = actionId & EditorInfo.IME_MASK_ACTION;

                        if (EditorInfo.IME_ACTION_DONE == imeActionId || EditorInfo.IME_ACTION_SEND == imeActionId) {
                            if (!TextUtils.isEmpty(mEditText.getText().toString())) {
                                sendMessage();
                                return true;
                            }
                        }

                        return false;
                    }
                }
        );

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        mMainLayout = findViewById(R.id.lock_main_layout);
    }

    private void sendMessage() {
        Log.d(LOG_TAG, "Send a message ...");

        String body = mEditText.getText().toString();

        Message message = new Message();
        message.msgtype = Message.MSGTYPE_TEXT;
        message.body = body;

        final Event event = new Event(message, session.getCredentials().userId, room.getRoomId());
        room.storeOutgoingEvent(event);
        room.sendEvent(event, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "Send message : onSuccess ");
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.d(LOG_TAG, "Send message : onNetworkError " + e.getMessage(), e);
                Toast.makeText(LockScreenActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.d(LOG_TAG, "Send message : onMatrixError " + e.getMessage());

                if (e instanceof MXCryptoError) {
                    Toast.makeText(LockScreenActivity.this, ((MXCryptoError) e).getDetailedErrorDescription(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(LockScreenActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.d(LOG_TAG, "Send message : onUnexpectedError " + e.getMessage(), e);
                Toast.makeText(LockScreenActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        finish();
    }

    private void refreshMainLayout() {
        if (null != mMainLayout) {
            // adjust the width to match to 80 % of the screen width
            ViewGroup.LayoutParams params = mMainLayout.getLayoutParams();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.8f);
            mMainLayout.setLayoutParams(params);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshMainLayout();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshMainLayout();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // only one instance of LockScreenActivity must be active
        if (this == mLockScreenActivity) {
            mLockScreenActivity = null;
        }
    }

}
