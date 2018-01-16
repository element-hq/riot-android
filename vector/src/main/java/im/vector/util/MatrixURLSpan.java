/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
package im.vector.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Browser;
import android.text.ParcelableSpan;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ClickableSpan;
import android.view.View;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import im.vector.listeners.IMessagesAdapterActionsListener;

// Class to track some matrix items click}
public class MatrixURLSpan extends ClickableSpan implements ParcelableSpan {
    private static final String LOG_TAG = MatrixURLSpan.class.getSimpleName();

    public static final Parcelable.Creator<MatrixURLSpan> CREATOR = new Parcelable.Creator<MatrixURLSpan>() {
        @Override
        public MatrixURLSpan createFromParcel(Parcel source) {
            return new MatrixURLSpan(source);
        }

        @Override
        public MatrixURLSpan[] newArray(int size) {
            return new MatrixURLSpan[size];
        }
    };

    // the URL to track
    private final String mURL;

    // URL regex
    private final Pattern mPattern;

    // listener
    private final IMessagesAdapterActionsListener mActionsListener;

    private MatrixURLSpan(String url, Pattern pattern, IMessagesAdapterActionsListener actionsListener) {
        mURL = url;
        mPattern = pattern;
        mActionsListener = actionsListener;
    }

    private MatrixURLSpan(Parcel src) {
        mURL = src.readString();
        mPattern = null;
        mActionsListener = null;
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public int getSpanTypeId() {
        return getSpanTypeIdInternal();
    }

    /*
     * *********************************************************************************************
     *  Inherited from ParcelableSpan
     * *********************************************************************************************
     */

    public int getSpanTypeIdInternal() {
        return getClass().hashCode();
    }

    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }

    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeString(mURL);
    }

    /*
     * *********************************************************************************************
     *  Custom methods
     * *********************************************************************************************
     */
    private String getURL() {
        return mURL;
    }

    @Override
    public void onClick(View widget) {
        try {
            if (mPattern == MXSession.PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER) {
                if (null != mActionsListener) {
                    mActionsListener.onMatrixUserIdClick(mURL);
                }
            } else if (mPattern == MXSession.PATTERN_CONTAIN_MATRIX_ALIAS) {
                if (null != mActionsListener) {
                    mActionsListener.onRoomAliasClick(mURL);
                }
            } else if (mPattern == MXSession.PATTERN_CONTAIN_MATRIX_ROOM_IDENTIFIER) {
                if (null != mActionsListener) {
                    mActionsListener.onRoomIdClick(mURL);
                }
            } else if (mPattern == MXSession.PATTERN_CONTAIN_MATRIX_MESSAGE_IDENTIFIER) {
                if (null != mActionsListener) {
                    mActionsListener.onMessageIdClick(mURL);
                }
            } else if (mPattern == MXSession.PATTERN_CONTAIN_MATRIX_GROUP_IDENTIFIER) {
                if (null != mActionsListener) {
                    mActionsListener.onGroupIdClick(mURL);
                }
            } else {
                Uri uri = Uri.parse(getURL());

                if (null != mActionsListener) {
                    mActionsListener.onURLClick(uri);
                } else {
                    Context context = widget.getContext();
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
                    context.startActivity(intent);
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "MatrixURLSpan : on click failed " + e.getLocalizedMessage());
        }
    }

    // list of patterns to find some matrix item.
    private static final List<Pattern> mMatrixItemPatterns = Arrays.asList(
            MXSession.PATTERN_CONTAIN_MATRIX_TO_PERMALINK_ROOM_ID,
            MXSession.PATTERN_CONTAIN_MATRIX_TO_PERMALINK_ROOM_ALIAS,
            MXSession.PATTERN_CONTAIN_APP_LINK_PERMALINK_ROOM_ID,
            MXSession.PATTERN_CONTAIN_APP_LINK_PERMALINK_ROOM_ALIAS,
            MXSession.PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER,
            MXSession.PATTERN_CONTAIN_MATRIX_ALIAS,
            MXSession.PATTERN_CONTAIN_MATRIX_ROOM_IDENTIFIER,
            MXSession.PATTERN_CONTAIN_MATRIX_MESSAGE_IDENTIFIER,
            MXSession.PATTERN_CONTAIN_MATRIX_GROUP_IDENTIFIER
    );

    /**
     * Find the matrix spans i.e matrix id , user id ... to display them as URL.
     *
     * @param stringBuilder the text in which the matrix items has to be clickable.
     */
    public static void refreshMatrixSpans(SpannableStringBuilder stringBuilder, IMessagesAdapterActionsListener mActionsListener) {
        // sanity checks
        if ((null == stringBuilder) || (0 == stringBuilder.length())) {
            return;
        }

        String text = stringBuilder.toString();

        for (int index = 0; index < mMatrixItemPatterns.size(); index++) {
            Pattern pattern = mMatrixItemPatterns.get(index);

            // room id.
            Matcher matcher = pattern.matcher(stringBuilder);
            while (matcher.find()) {

                try {
                    int startPos = matcher.start(0);

                    if ((startPos == 0) || (text.charAt(startPos - 1) != '/')) {
                        int endPos = matcher.end(0);
                        String url = text.substring(matcher.start(0), matcher.end(0));
                        stringBuilder.setSpan(new MatrixURLSpan(url, pattern, mActionsListener), startPos, endPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "refreshMatrixSpans " + e.getLocalizedMessage());
                }
            }
        }
    }
}
