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

import android.content.Intent;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.util.Log;

import im.vector.Matrix;
import im.vector.R;
import im.vector.notifications.NotificationUtils;
import im.vector.util.ViewUtilKt;
import kotlin.Pair;

/**
 * LockScreenActivity is displayed within the notification to send a message without opening the application.
 */
public class LockScreenActivity extends RiotAppCompatActivity { // do NOT extend from UC*Activity, we do not want to login on this screen!
    private static final String LOG_TAG = LockScreenActivity.class.getSimpleName();

    public static final String EXTRA_SENDER_NAME = "extra_sender_name";
    public static final String EXTRA_MESSAGE_BODY = "extra_chat_body";
    public static final String EXTRA_ROOM_ID = "extra_room_id";
    private static final String EXTRA_MATRIX_ID = "extra_matrix_id";

    private static LockScreenActivity mLockScreenActivity = null;

    public static boolean isDisplayingALockScreenActivity() {
        return (null != mLockScreenActivity);
    }

    private LinearLayout mMainLayout;

    @NotNull
    @Override
    public Pair getOtherThemes() {
        return new Pair(R.style.Vector_Lock_Dark, R.style.Vector_Lock_Light);
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

        final MXSession session = Matrix.getInstance(getApplicationContext()).getSession(matrixId);
        final Room room = session.getDataHandler().getRoom(roomId);

        // display the room name as title
        setTitle(room.getName(session.getCredentials().userId));

        ((TextView) findViewById(R.id.lock_screen_sender)).setText(intent.getStringExtra(EXTRA_SENDER_NAME) + " : ");
        ((TextView) findViewById(R.id.lock_screen_body)).setText(intent.getStringExtra(EXTRA_MESSAGE_BODY));
        ((TextView) findViewById(R.id.lock_screen_room_name)).setText(room.getName(session.getCredentials().userId));
        final ImageButton sendButton = findViewById(R.id.lock_screen_sendbutton);
        final EditText editText = findViewById(R.id.lock_screen_edittext);

        // disable send button
        sendButton.setEnabled(false);
        sendButton.setAlpha(ViewUtilKt.UTILS_OPACITY_HALF);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // disable/enable send button according to input text content
                String inputText = editText.getText().toString();
                if (TextUtils.isEmpty(inputText)) {
                    sendButton.setEnabled(false);
                    sendButton.setAlpha(ViewUtilKt.UTILS_OPACITY_HALF);
                } else {
                    sendButton.setEnabled(true);
                    sendButton.setAlpha(ViewUtilKt.UTILS_OPACITY_FULL);
                }
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "Send a message ...");

                String body = editText.getText().toString();

                Message message = new Message();
                message.msgtype = Message.MSGTYPE_TEXT;
                message.body = body;

                final Event event = new Event(message, session.getCredentials().userId, roomId);
                room.storeOutgoingEvent(event);
                room.sendEvent(event, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        Log.d(LOG_TAG, "Send message : onSuccess ");
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.d(LOG_TAG, "Send message : onNetworkError " + e.getMessage());
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
                        Log.d(LOG_TAG, "Send message : onUnexpectedError " + e.getMessage());
                        Toast.makeText(LockScreenActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
        });

        mMainLayout = findViewById(R.id.lock_main_layout);
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
