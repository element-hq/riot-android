/*
 * Copyright 2014 OpenMarket Ltd
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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;

import java.util.ArrayList;
import java.util.Collection;

import im.vector.Matrix;
import im.vector.R;
import im.vector.util.ResourceUtils;

public class VectorRoomCreationActivity extends MXCActionBarActivity {

    private static final int TAKE_IMAGE = 1;
    private static final int GET_MEMBERS = 2;

    // backup while rotating the screen
    private static final String BACKUP_IS_PRIVATE = "BACKUP_IS_PRIVATE";
    private static final String BACKUP_ACCOUNT_ID = "BACKUP_ACCOUNT_ID";
    private static final String BACKUP_THUMB_URL = "BACKUP_THUMB_URL";
    private static final String BACKUP_ROOM_NAME = "BACKUP_ROOM_NAME";

    // UI items
    Spinner mAccountsSpinner;
    EditText mRoomNameEditText;
    ImageView mAvatarImageView;
    TextView mPrivacyText;
    Button mPrivacyButton;
    TextView mSingleAccountText;

    // the next button should only be displayed if a room name has been entered
    MenuItem mNextMenuItem = null;

    ArrayList<MXSession> mSessions = null;
    Integer mSessionIndex = 0;

    // values
    Boolean mIsPrivate = true;

    Uri mThumbnailUri = null;
    Bitmap mThumbnail = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_vector_room_creation);

        mSingleAccountText = (TextView)findViewById(R.id.room_creation_single_account_text);
        mAccountsSpinner =  (Spinner)findViewById(R.id.room_creation_accounts_spinner);
        mRoomNameEditText = (EditText) findViewById(R.id.room_creation_account_name_edit);

        mRoomNameEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                refreshNextButtonStatus();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mAvatarImageView = (ImageView) findViewById(R.id.avatar_img);
        mPrivacyText = (TextView) findViewById(R.id.room_creation_account_privacy_state);
        mPrivacyButton = (Button) findViewById(R.id.room_creation_account_privacy_button);

        // restore saved values
        if (null != savedInstanceState) {
            if (savedInstanceState.containsKey(BACKUP_IS_PRIVATE)) {
                mIsPrivate = savedInstanceState.getBoolean(BACKUP_IS_PRIVATE);
            }

            if (savedInstanceState.containsKey(BACKUP_ACCOUNT_ID)) {
                String accountId = savedInstanceState.getString(BACKUP_ACCOUNT_ID);

                if (!TextUtils.isEmpty(accountId)) {
                    Collection<MXSession> sessions = Matrix.getInstance(this).getSessions();
                    int index = 0;

                    // search the session
                    for(MXSession session : sessions) {
                        if (session.getMyUser().userId.equals(accountId)) {
                            mSessionIndex = index;
                            break;
                        }
                        index++;
                    }
                }
            }

            if (savedInstanceState.containsKey(BACKUP_THUMB_URL)) {
                String url = savedInstanceState.getString(BACKUP_THUMB_URL);
                if (!TextUtils.isEmpty(url)) {
                    try {
                        mThumbnailUri = Uri.parse(url);
                    } catch (Exception e) {
                    }
                }
            }

            if (savedInstanceState.containsKey(BACKUP_ROOM_NAME)) {
                mRoomNameEditText.setText(savedInstanceState.getString(BACKUP_ROOM_NAME));
            }
        }

        refreshUIItems();
        refreshNextButtonStatus();

        mAccountsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                mSessionIndex = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }
        });

        mAvatarImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(VectorRoomCreationActivity.this, VectorMediasPickerActivity.class);
                intent.putExtra(VectorMediasPickerActivity.EXTRA_SINGLE_IMAGE_MODE, "");
                startActivityForResult(intent, TAKE_IMAGE);
            }
        });


        mPrivacyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsPrivate) {
                    VectorRoomCreationActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // The user is trying to leave with unsaved changes. Warn about that
                            new AlertDialog.Builder(VectorRoomCreationActivity.this)
                                    .setTitle(R.string.room_creation_make_public_prompt_title)
                                    .setMessage(R.string.room_creation_make_public_prompt_msg)
                                    .setPositiveButton(R.string.room_creation_make_public, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            mIsPrivate = false;
                                            refreshUIItems();
                                        }
                                    })
                                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .create()
                                    .show();
                        }
                    });
                } else {
                    mIsPrivate = true;
                    refreshUIItems();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.vector_room_creation, menu);
        mNextMenuItem = menu.findItem(R.id.action_next);
        refreshNextButtonStatus();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_next) {
            Intent intent = new Intent(VectorRoomCreationActivity.this, VectorAddParticipantsActivity.class);

            MXSession session = (null == mSessions) ? Matrix.getInstance(this).getDefaultSession() : mSessions.get(mSessionIndex);
            intent.putExtra(VectorAddParticipantsActivity.EXTRA_MATRIX_ID, session.getCredentials().userId);
            startActivityForResult(intent, GET_MEMBERS);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * The next menu button is only enabled when there is non empty room name.
     */
    private void refreshNextButtonStatus() {
        String roomName = mRoomNameEditText.getText().toString();
        Boolean isValidRoomName = false;

        if (null != roomName) {
            isValidRoomName = !TextUtils.isEmpty(roomName.trim());
        }

        if (null != mNextMenuItem) {
            mNextMenuItem.setEnabled(isValidRoomName);
        }
    }

    /**
     * Refresh the page UI items
     */
    private void refreshUIItems() {
        // session selection
        ArrayList<MXSession> sessions = Matrix.getInstance(this).getSessions();

        // one session -> no need to select a session
        if (sessions.size() == 1) {
            mAccountsSpinner.setVisibility(View.GONE);
            mSingleAccountText.setVisibility(View.VISIBLE);

            mSingleAccountText.setText(sessions.get(0).getMyUser().displayname + " (" + sessions.get(0).getMyUser().userId + ")");
        } else {
            mSingleAccountText.setVisibility(View.GONE);
            mAccountsSpinner.setVisibility(View.VISIBLE);

            // not yet initialized
            if (null == mSessions) {
                ArrayList<String> sessionsTextsList = new ArrayList<String>();

                mSessions = new ArrayList<MXSession>();
                for(MXSession session : sessions) {
                    mSessions.add(session);
                    sessionsTextsList.add(session.getMyUser().displayname + " (" + session.getMyUser().userId + ")");
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                        android.R.layout.simple_spinner_item, sessionsTextsList.toArray(new String[sessionsTextsList.size()]));


                mAccountsSpinner.setAdapter(adapter);
            }

            mAccountsSpinner.setSelection(mSessionIndex);
        }

        if (null != mThumbnailUri) {
            if (null == mThumbnail) {
                mThumbnail = getBitmap(mThumbnailUri);
            }
            mAvatarImageView.setImageBitmap(mThumbnail);
        }

        // privacy settings
        if (mIsPrivate) {
            mPrivacyText.setText(R.string.room_creation_private_room);
            mPrivacyButton.setText(R.string.room_creation_make_public);
        } else {
            mPrivacyText.setText(R.string.room_creation_public_room);
            mPrivacyButton.setText(R.string.room_creation_make_private);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUIItems();
        refreshNextButtonStatus();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean(BACKUP_IS_PRIVATE, mIsPrivate);

        MXSession session = (null == mSessions) ? Matrix.getInstance(this).getDefaultSession() : mSessions.get(mSessionIndex);
        savedInstanceState.putString(BACKUP_ACCOUNT_ID, session.getCredentials().userId);

        if (null != mThumbnailUri) {
            savedInstanceState.putString(BACKUP_THUMB_URL, mThumbnailUri.toString());
        }

        if (null != mRoomNameEditText.getText()) {
            savedInstanceState.putString(BACKUP_ROOM_NAME, mRoomNameEditText.getText().toString());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    /**
     * return the bitmap from a resource.
     * @param mediaUri the media URI.
     * @return the bitmap, null if it fails.
     */
    private Bitmap getBitmap(Uri mediaUri) {
        if (null != mediaUri) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            ResourceUtils.Resource resource = ResourceUtils.openResource(this, mediaUri);

            // sanity checks
            if ((null != resource) && (null != resource.contentStream)) {
                return BitmapFactory.decodeStream(resource.contentStream, null, options);
            }
        }

        return null;
    }

    @SuppressLint("NewApi")
    private void onPickerDone(final Intent data) {
        // sanity check
        if (null == data) {
            return;
        }

        mThumbnailUri = null;

        if (null != data) {
            ClipData clipData = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                clipData = data.getClipData();
            }

            // multiple data
            if (null != clipData) {
                if (clipData.getItemCount() > 0) {
                    mThumbnailUri = clipData.getItemAt(0).getUri();
                }
            } else if (null != data.getData()) {
                mThumbnailUri = data.getData();
            }
        }

        if (null != mThumbnailUri) {
            mThumbnail = getBitmap(mThumbnailUri);
            refreshUIItems();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == TAKE_IMAGE) {
                onPickerDone(data);
            } else if (requestCode == GET_MEMBERS) {
                final ArrayList<String> userIDsList = (ArrayList<String>)data.getExtras().get(VectorAddParticipantsActivity.RESULT_USERS_ID);
                final MXSession session = (null == mSessions) ? Matrix.getInstance(this).getDefaultSession() : mSessions.get(mSessionIndex);
                final Activity activity = VectorRoomCreationActivity.this;
                final String roomVisibility = !mIsPrivate ? RoomState.VISIBILITY_PUBLIC : RoomState.VISIBILITY_PRIVATE;

                session.createRoom(mRoomNameEditText.getText().toString(), null, roomVisibility, null, new SimpleApiCallback<String>(activity) {
                    @Override
                    public void onSuccess(String roomId) {
                        CommonActivityUtils.goToRoomPage(session, roomId, activity, null);

                        Room room = session.getDataHandler().getRoom(roomId);

                        if ((null != room) && (null != userIDsList)) {
                            room.invite(userIDsList, new SimpleApiCallback<Void>(activity) {
                                @Override
                                public void onSuccess(Void info) {
                                }
                            });
                        }
                    }
                });
            }
        }
    }
}
