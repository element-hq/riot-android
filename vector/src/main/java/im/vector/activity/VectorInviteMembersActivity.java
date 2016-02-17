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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.VectorAddParticipantsAdapter;

/**
 * This class provides a way to search other user to invite them in a dedicated room
 */
public class VectorInviteMembersActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = "VectorInviteMembersAct";

    // search in the room
    public static final String EXTRA_ROOM_ID = "VectorInviteMembersActivity.EXTRA_ROOM_ID";
    public static final String EXTRA_SELECTED_USER_ID =  "VectorInviteMembersActivity.EXTRA_SELECTED_USER_ID";

    private static final int SPEECH_REQUEST_CODE = 1234;

    // account data
    private String mRoomId;
    private String mMatrixId;

    // main UI items
    private ListView mListView;
    private ImageView mBackgroundImageView;

    private VectorAddParticipantsAdapter mAdapter;

    private ActionBar mActionBar;
    private EditText mPatternToSearchEditText;

    private MenuItem mMicroMenuItem;
    private MenuItem mClearEditTextMenuItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            mMatrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        // get current session
        mSession = Matrix.getInstance(getApplicationContext()).getSession(mMatrixId);
        if (null == mSession) {
            finish();
            return;
        }

        mRoomId = intent.getStringExtra(EXTRA_ROOM_ID);

        setContentView(R.layout.activity_vector_invite_members);

        mBackgroundImageView = (ImageView)findViewById(R.id.search_background_imageview);

        mListView = (ListView) findViewById(R.id.room_details_members_list);
        mAdapter = new VectorAddParticipantsAdapter(this, R.layout.adapter_item_vector_add_participants, mSession, mRoomId, false, mSession.getMediasCache());
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // returns the selected user
                Intent intent = new Intent();
                intent.putExtra(EXTRA_SELECTED_USER_ID, mAdapter.getItem(position).mUserId);
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        mActionBar = getSupportActionBar();
        View actionBarView = customizeActionBar();

        // add the search logic based on the text search input listener
        mPatternToSearchEditText = (EditText) actionBarView.findViewById(R.id.room_action_bar_edit_text);
        actionBarView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mPatternToSearchEditText.requestFocus();
                InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                im.showSoftInput(mPatternToSearchEditText, 0);
            }
        }, 100);

        mPatternToSearchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    onPatternUpdate();
                    return true;
                }
                return false;
            }
        });

        manageBackground();
    }

    /**
     * The search pattern has been updated
     */
    private void onPatternUpdate() {
        manageBackground();
        mAdapter.setSearchedPattern(mPatternToSearchEditText.getText().toString());

        mListView.post(new Runnable() {
            @Override
            public void run() {
                int i = 0;
                i++;
            }
        });
    }

    /**
     * Add a custom action bar with a view
     * @return the action bar inflated view
     */
    private View customizeActionBar() {
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_HOME_AS_UP);

        // add a custom action bar view containing an EditText to input the search text
        ActionBar.LayoutParams layout = new ActionBar.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT);
        View actionBarLayout =  getLayoutInflater().inflate(R.layout.vector_search_action_bar, null);
        mActionBar.setCustomView(actionBarLayout, layout);

        return actionBarLayout;
    }

    /**
     * Hide/show background/listview according to the text length
     */
    private void manageBackground() {
        boolean emptyText = TextUtils.isEmpty(mPatternToSearchEditText.getText().toString());

        mBackgroundImageView.setVisibility(emptyText ? View.VISIBLE : View.GONE);
        mListView.setVisibility(emptyText ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.vector_searches, menu);

        mMicroMenuItem = menu.findItem(R.id.ic_action_speak_to_search);
        mClearEditTextMenuItem = menu.findItem(R.id.ic_action_clear_search);

        refreshMenuEntries();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_speak_to_search) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            startActivityForResult(intent, SPEECH_REQUEST_CODE);

        } else if (id ==  R.id.ic_action_clear_search) {
            mPatternToSearchEditText.setText("");
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Handle the results from the voice recognition activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if ((requestCode == SPEECH_REQUEST_CODE) && (resultCode == RESULT_OK)) {
            final ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            // one matched items
            if (matches.size() == 1) {
                // use it
                mPatternToSearchEditText.setText(matches.get(0));
                onPatternUpdate();
            } else if (matches.size() > 1) {
                // if they are several matches, let the user chooses the right one.
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                String[] mes = matches.toArray(new String[matches.size()]);

                builder.setItems(mes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        mPatternToSearchEditText.setText(matches.get(item));
                        VectorInviteMembersActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onPatternUpdate();
                            }
                        });
                    }
                });

                AlertDialog alert = builder.create();
                alert.show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * @return true of the device supports speech recognizer.
     */
    private boolean supportSpeechRecognizer() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);

        return (null != activities) && (activities.size() > 0);
    }

    /**
     * Refresh the menu entries
     */
    private void refreshMenuEntries() {
        boolean hasText = !TextUtils.isEmpty(mPatternToSearchEditText.getText());

        if (null != mMicroMenuItem) {
            mMicroMenuItem.setVisible(!hasText && supportSpeechRecognizer());
        }

        if (null != mClearEditTextMenuItem) {
            mClearEditTextMenuItem.setVisible(hasText);
        }
    }

}