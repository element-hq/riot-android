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
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.R;
import im.vector.util.ThemeUtils;

/**
 * This class defines a base class to manage search in action bar
 */
public class VectorBaseSearchActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = VectorBaseSearchActivity.class.getSimpleName();

    public interface IVectorSearchActivity {
        void refreshSearch();
    }

    private static final int SPEECH_REQUEST_CODE = 1234;

    private ActionBar mActionBar;
    EditText mPatternToSearchEditText;

    private MenuItem mMicroMenuItem;
    private MenuItem mClearEditTextMenuItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActionBar = getSupportActionBar();
        View actionBarView = customizeActionBar();

        // add the search logic based on the text search input listener
        mPatternToSearchEditText = actionBarView.findViewById(R.id.room_action_bar_edit_text);
        actionBarView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mPatternToSearchEditText.requestFocus();
                InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                im.showSoftInput(mPatternToSearchEditText, 0);
            }
        }, 100);

        mPatternToSearchEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                VectorBaseSearchActivity.this.refreshMenuEntries();
                final String fPattern = mPatternToSearchEditText.getText().toString();

                Timer timer = new Timer();

                try {
                    // wait a little delay before refreshing the results.
                    // it avoid UI lags when the user is typing.
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            VectorBaseSearchActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (TextUtils.equals(mPatternToSearchEditText.getText().toString(), fPattern)) {
                                        VectorBaseSearchActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                onPatternUpdate(true);
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    }, 100);
                } catch (Throwable throwable) {
                    Log.e(LOG_TAG, "## failed to start the timer " + throwable.getMessage());

                    VectorBaseSearchActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (TextUtils.equals(mPatternToSearchEditText.getText().toString(), fPattern)) {
                                VectorBaseSearchActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        onPatternUpdate(true);
                                    }
                                });
                            }
                        }
                    });
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mPatternToSearchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((actionId == EditorInfo.IME_ACTION_SEARCH) ||
                        // hardware keyboard : detect the keydown event
                        ((null != event) && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                        ) {
                    onPatternUpdate(false);
                    return true;
                }
                return false;
            }
        });

        // required to avoid having the crash
        // focus search returned a view that wasn't able to take focus!
        mPatternToSearchEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mPatternToSearchEditText.getApplicationWindowToken(), 0);
    }

    @Override
    protected void onResume() {
        super.onResume();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onPatternUpdate(false);
            }
        });
    }

    /**
     * The search pattern has been updated.
     *
     * @param isTypingUpdate true when the pattern has been updated while typing.
     */
    void onPatternUpdate(boolean isTypingUpdate) {
        // do something here
    }

    /**
     * Add a custom action bar with a view
     *
     * @return the action bar inflated view
     */
    private View customizeActionBar() {
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_HOME_AS_UP);

        // add a custom action bar view containing an EditText to input the search text
        ActionBar.LayoutParams layout = new ActionBar.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT);
        View actionBarLayout = getLayoutInflater().inflate(R.layout.vector_search_action_bar, null);
        mActionBar.setCustomView(actionBarLayout, layout);

        return actionBarLayout;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // the application is in a weird state
        if (CommonActivityUtils.shouldRestartApp(this)) {
            return false;
        }

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.vector_searches, menu);
        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(this, R.attr.icon_tint_on_light_action_bar_color));

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

        } else if (id == R.id.ic_action_clear_search) {
            mPatternToSearchEditText.setText("");
            onPatternUpdate(false);
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Handle the results from the voice recognition activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == SPEECH_REQUEST_CODE) && (resultCode == RESULT_OK)) {
            final ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            // one matched items
            if (matches.size() == 1) {
                // use it
                mPatternToSearchEditText.setText(matches.get(0));
                onPatternUpdate(false);
            } else if (matches.size() > 1) {
                // if they are several matches, let the user chooses the right one.
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                String[] mes = matches.toArray(new String[matches.size()]);

                builder.setItems(mes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        mPatternToSearchEditText.setText(matches.get(item));
                        VectorBaseSearchActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onPatternUpdate(false);
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
