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

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import im.vector.Matrix;
import im.vector.R;

public class LockScreenActivity extends Activity { // do NOT extend from UC*Activity, we do not want to login on this screen!
    public static final String EXTRA_SENDER_NAME = "extra_sender_name";
    public static final String EXTRA_MESSAGE_BODY = "extra_chat_body";
    public static final String EXTRA_ROOM_ID = "extra_room_id";
    public static final String EXTRA_MATRIX_ID = "extra_matrix_id";

    private static LockScreenActivity mLockScreenActivity = null;

    public static boolean isDisplayingALockScreenActivity() {
        return (null != mLockScreenActivity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // kill any running alert
        if (null != mLockScreenActivity) {
            mLockScreenActivity.finish();
        }

        mLockScreenActivity = this;
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // this will turn the screen on whilst honouring the screen timeout setting, so it will
        // dim/turn off depending on user configured values.
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_lock_screen);

        // remove any pending notifications
        NotificationManager notificationsManager= (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationsManager.cancelAll();

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

        ((TextView)findViewById(R.id.lock_screen_sender)).setText(intent.getStringExtra(EXTRA_SENDER_NAME) + " : ");
        ((TextView)findViewById(R.id.lock_screen_body)).setText( intent.getStringExtra(EXTRA_MESSAGE_BODY));
        ((TextView)findViewById(R.id.lock_screen_room_name)).setText(room.getName(session.getCredentials().userId));

        findViewById(R.id.lock_screen_sendbutton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                EditText editText = (EditText) findViewById(R.id.lock_screen_edittext);
                String body = editText.getText().toString();

                Message message = new Message();
                message.msgtype = Message.MSGTYPE_TEXT;
                message.body = body;

                Event event = new Event(message, session.getCredentials().userId, roomId);
                room.storeOutgoingEvent(event);

                if (null != room) {
                    room.sendEvent(event, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                        }
                    });
                }

                LockScreenActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
        });
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
