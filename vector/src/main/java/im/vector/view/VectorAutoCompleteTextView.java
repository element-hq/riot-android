/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.MultiAutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import im.vector.R;
import im.vector.adapters.AutoCompletedCommandLineAdapter;
import im.vector.adapters.AutoCompletedUserAdapter;
import im.vector.util.AutoCompletionMode;
import im.vector.util.SlashCommandsParser;

/**
 * Custom AppCompatMultiAutoCompleteTextView to display matrix id / displayname
 */
public class VectorAutoCompleteTextView extends AppCompatMultiAutoCompleteTextView {

    private static final String LOG_TAG = VectorAutoCompleteTextView.class.getSimpleName();

    // results adapter
    public AutoCompletedUserAdapter mAdapterUser;
    public AutoCompletedCommandLineAdapter mAdapterCommand;

    // the pending patter,
    private String mPendingFilter;

    // trick to customize the popup
    private Field mPopupCanBeUpdatedField;

    // trick to fix the list width
    private android.widget.ListPopupWindow mListPopupWindow;

    // add a colon when the inserted text is the first item of the string
    private boolean mAddColonOnFirstItem;

    private AutoCompletionMode mAutoCompletionMode;

    public VectorAutoCompleteTextView(Context context) {
        super(context, null);
    }

    public VectorAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setInputType(getInputType() & (getInputType() ^ InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE));
    }

    public VectorAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setInputType(getInputType() & (getInputType() ^ InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE));
    }

    /**
     * Update the auto completion mode according to the first character of the message.
     *
     * @param forceUpdate set to true to force to load the adapter
     */
    public void updateAutoCompletionMode(boolean forceUpdate) {
        final AutoCompletionMode newMode = AutoCompletionMode.Companion.getWithText(getText().toString());
        if (newMode == mAutoCompletionMode && !forceUpdate) {
            return;
        }
        mAutoCompletionMode = newMode;
        switch (newMode) {
            case USER_MODE:
                setAdapter(mAdapterUser);
                setThreshold(3);
                break;
            case COMMAND_MODE:
                setAdapter(mAdapterCommand);
                setThreshold(1);
                break;
        }
    }

    /**
     * Build the auto completions list for a session.
     *
     * @param session the session
     */
    public void initAutoCompletion(MXSession session) {
        initAutoCompletion();
        buildAdapter(session, session.getDataHandler().getStore().getUsers(), null);
    }

    /**
     * Build the auto completions list of users for a room
     *
     * @param session the session
     * @param room    the room
     */
    public void initAutoCompletions(@NonNull final MXSession session, @Nullable Room room) {
        initAutoCompletion();

        final List<SlashCommandsParser.SlashCommand> slashCommandList = getSlashCommandList();

        // First build adapter with empty list of users
        buildAdapter(session, new ArrayList<User>(), slashCommandList);

        getUsersList(session, room, new SimpleApiCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> users) {
                buildAdapter(session, users, slashCommandList);
            }

            // ignore any errors
        });
    }

    private void getUsersList(@NonNull final MXSession session,
                              @Nullable Room room,
                              final ApiCallback<List<User>> callback) {

        if (null != room) {
            room.getMembersAsync(new SimpleApiCallback<List<RoomMember>>(callback) {
                @Override
                public void onSuccess(List<RoomMember> members) {
                    List<User> users = new ArrayList<>();

                    for (RoomMember member : members) {
                        User user = session.getDataHandler().getUser(member.getUserId());

                        if (null != user) {
                            users.add(user);
                        }
                    }

                    callback.onSuccess(users);
                }
            });
        } else {
            // Empty list
            callback.onSuccess(new ArrayList<User>());
        }
    }

    private List<SlashCommandsParser.SlashCommand> getSlashCommandList() {
        SlashCommandsParser.SlashCommand[] commandLines = SlashCommandsParser.SlashCommand.values();
        List<SlashCommandsParser.SlashCommand> commands = new ArrayList<SlashCommandsParser.SlashCommand>(Arrays.asList(commandLines));
        return commands;
    }

    private void initAutoCompletion() {
        // define the parser
        setTokenizer(new VectorAutoCompleteTokenizer());

        // retrieve 2 private members
        if (null == mPopupCanBeUpdatedField) {
            try {
                // FIXME This does not work anymore with Android API 28 -> Popup are displayed full width
                mPopupCanBeUpdatedField = AutoCompleteTextView.class.getDeclaredField("mPopupCanBeUpdated");
                mPopupCanBeUpdatedField.setAccessible(true);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## initAutoCompletion() : failed to retrieve mPopupCanBeUpdated " + e.getMessage());
            }
        }

        if (null == mListPopupWindow) {
            try {
                Field popup = AutoCompleteTextView.class.getDeclaredField("mPopup");
                popup.setAccessible(true);
                mListPopupWindow = (android.widget.ListPopupWindow) popup.get(this);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## initAutoCompletion() : failed to retrieve mListPopupWindow " + e.getMessage(), e);
            }
        }
    }

    /**
     * Internal method to build the auto completions list of users or slash commands.
     *
     * @param session      the session
     * @param users        the users list
     * @param commandLines the commands list
     */
    private void buildAdapter(@NonNull MXSession session,
                              Collection<User> users,
                              Collection<SlashCommandsParser.SlashCommand> commandLines) {
        // build the adapters
        if (null != commandLines) {
            mAdapterCommand = new AutoCompletedCommandLineAdapter(getContext(), R.layout.item_command_auto_complete, session, commandLines);
        }

        if (null != users) {
            mAdapterUser = new AutoCompletedUserAdapter(getContext(), R.layout.item_user_auto_complete, session, users);
        }

        updateAutoCompletionMode(true);
    }

    /**
     * Tells if the pasted text is always the user matrix id
     * even if the matched pattern is a display name.
     *
     * @param provideMatrixIdOnly true to always paste an user Id.
     */
    public void setProvideMatrixIdOnly(boolean provideMatrixIdOnly) {
        mAdapterUser.setProvideMatrixIdOnly(provideMatrixIdOnly);
    }

    /**
     * Compute the popup size for list of users or commands.
     */
    private void adjustPopupSize(@Nullable AutoCompletedUserAdapter userAdapter,
                                 @Nullable AutoCompletedCommandLineAdapter commandLineAdapter) {
        if (null != mListPopupWindow) {
            int maxWidth = 0;
            int count = 0;

            ViewGroup mMeasureParent = new FrameLayout(getContext());
            View itemView = null;

            if (null != userAdapter && null == commandLineAdapter) {
                count = userAdapter.getCount();
            } else if (null != commandLineAdapter && null == userAdapter) {
                count = commandLineAdapter.getCount();
            }

            for (int i = 0; i < count; i++) {
                if (null != userAdapter) {
                    itemView = userAdapter.getView(i, itemView, mMeasureParent, false);
                }
                if (null != commandLineAdapter) {
                    itemView = commandLineAdapter.getView(i, itemView, mMeasureParent);
                }
                itemView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                maxWidth = Math.max(maxWidth, itemView.getMeasuredWidth());
            }

            mListPopupWindow.setContentWidth(maxWidth);

            // setDropDownWidth(maxWidth) does not work on some devices
            // it seems working on android >= 5.1
            // but it does not on older android platforms
        }
    }

    /**
     * Set if a colon must be appended to the inserted text
     * if it is the first item of the string
     *
     * @param addColonOnFirstItem true to insert colon
     */
    public void setAddColonOnFirstItem(boolean addColonOnFirstItem) {
        mAddColonOnFirstItem = addColonOnFirstItem;
    }

    @Override
    protected void replaceText(CharSequence text) {
        String before = getText().toString();
        super.replaceText(text);

        if (mAddColonOnFirstItem) {
            try {
                Editable editableAfter = getText();

                // check if the inserted becomes was the new first item
                if ((null != before)
                        && !before.startsWith(text.toString())
                        && editableAfter.toString().startsWith(text.toString())) {

                    if (text.toString().startsWith("@")) {
                        editableAfter.replace(0, text.length(), text + ":");
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## replaceText() : failed " + e.getMessage(), e);
            }
        }

        // fix a samsung keyboard issue
        // "Joh" -> user selects "John" -> "John :" -> the user taps h -> "John : Johh"
        // by this way, the predictive texts list seems being deleted
        setInputType(getInputType() & (getInputType() & (~InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)));
        setInputType(getInputType() & (getInputType() ^ InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE));
    }

    @Override
    protected void performFiltering(final CharSequence text, final int start, final int end, int keyCode) {
        // cannot retrieve mPopupCanBeUpdated
        // use the default implementation
        if (null == mPopupCanBeUpdatedField) {
            super.performFiltering(text, start, end, keyCode);
        } else {
            String currentFilter = ((null == text) ? "" : text.toString()) + start + "-" + end;

            // check if there is a text update to force the drop down list dismiss
            if (!TextUtils.equals(currentFilter, mPendingFilter)) {
                dismissDropDown();
            }

            // allow to display the popup once again
            try {
                mPopupCanBeUpdatedField.setBoolean(this, true);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## performFiltering() : mPopupCanBeUpdatedField.setBoolean failed " + e.getMessage(), e);
            }

            // save the current written pattern
            mPendingFilter = currentFilter;

            if (mAutoCompletionMode == AutoCompletionMode.USER_MODE) {
                // wait 0.7s before displaying the popup
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String currentFilter = ((null == getText()) ? "" : getText().toString()) + start + "-" + end;

                        // display the popup only the user did not update the edited text
                        if (TextUtils.equals(currentFilter, mPendingFilter)) {
                            CharSequence subText = "";

                            try {
                                subText = text.subSequence(start, end);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## performFiltering() failed " + e.getMessage(), e);
                            }

                            mAdapterUser.getFilter().filter(subText, new Filter.FilterListener() {
                                @Override
                                public void onFilterComplete(int count) {
                                    adjustPopupSize(mAdapterUser, null);
                                    VectorAutoCompleteTextView.this.onFilterComplete(count);
                                }
                            });
                        }
                    }
                }, 700);
            } else {
                // Filter right now
                CharSequence subText = "";

                try {
                    subText = text.subSequence(start, end);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## performFiltering() failed " + e.getMessage(), e);
                }

                mAdapterCommand.getFilter().filter(subText, new Filter.FilterListener() {
                    @Override
                    public void onFilterComplete(int count) {
                        adjustPopupSize(null, mAdapterCommand);
                        VectorAutoCompleteTextView.this.onFilterComplete(count);
                    }
                });
            }
        }
    }

    /**
     * Custom tokenizer
     */
    private static class VectorAutoCompleteTokenizer implements MultiAutoCompleteTextView.Tokenizer {
        final static List<Character> mAllowedTokens = Arrays.asList(',', ';', '.', ' ', '\n', '\t');

        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;

            while (i > 0 && !mAllowedTokens.contains(text.charAt(i - 1))) {
                i--;
            }

            while (i < cursor && text.charAt(i) == ' ') {
                i++;
            }

            return i;
        }

        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();

            while (i < len) {
                if (mAllowedTokens.contains(text.charAt(i))) {
                    return i;
                } else {
                    i++;
                }
            }

            return len;
        }

        public CharSequence terminateToken(CharSequence text) {
            int i = text.length();

            while (i > 0 && text.charAt(i - 1) == ' ') {
                i--;
            }

            return text + " ";
        }
    }
}
