/*
 * Copyright 2016 OpenMarket Ltd
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
package im.vector.util;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.ParcelableSpan;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ClickableSpan;
import android.view.View;

import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.MXPatterns;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import im.vector.listeners.IMessagesAdapterActionsListener;

// Class to track some matrix items click
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

    // is a tombstone link
    private final boolean isTombstone;

    // SenderId for the tombstone link
    private final String senderId;

    // listener
    private final IMessagesAdapterActionsListener mActionsListener;

    public MatrixURLSpan(String url, Pattern pattern, IMessagesAdapterActionsListener actionsListener) {
        mURL = url;
        mPattern = pattern;
        isTombstone = false;
        senderId = null;
        mActionsListener = actionsListener;
    }

    /**
     * Create a URL Span for tombstone
     *
     * @param roomId
     * @param senderId
     * @param actionsListener
     */
    public MatrixURLSpan(String roomId, String senderId, IMessagesAdapterActionsListener actionsListener) {
        mURL = roomId;
        mPattern = null;
        isTombstone = true;
        this.senderId = senderId;
        mActionsListener = actionsListener;
    }

    private MatrixURLSpan(Parcel src) {
        mURL = src.readString();
        mPattern = null;
        isTombstone = false;
        senderId = null;
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
            if (isTombstone) {
                if (null != mActionsListener) {
                    mActionsListener.onTombstoneLinkClicked(mURL, senderId);
                }
            } else {
                if (mPattern == MXPatterns.PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER) {
                    if (null != mActionsListener) {
                        mActionsListener.onMatrixUserIdClick(mURL);
                    }
                } else if (mPattern == MXPatterns.PATTERN_CONTAIN_MATRIX_ALIAS) {
                    if (null != mActionsListener) {
                        mActionsListener.onRoomAliasClick(mURL);
                    }
                } else if (mPattern == MXPatterns.PATTERN_CONTAIN_MATRIX_ROOM_IDENTIFIER) {
                    if (null != mActionsListener) {
                        mActionsListener.onRoomIdClick(mURL);
                    }
                } else if (mPattern == MXPatterns.PATTERN_CONTAIN_MATRIX_EVENT_IDENTIFIER) {
                    if (null != mActionsListener) {
                        mActionsListener.onEventIdClick(mURL);
                    }
                } else if (mPattern == MXPatterns.PATTERN_CONTAIN_MATRIX_GROUP_IDENTIFIER) {
                    if (null != mActionsListener) {
                        mActionsListener.onGroupIdClick(mURL);
                    }
                } else {
                    Uri uri = Uri.parse(getURL());

                    if (null != mActionsListener) {
                        mActionsListener.onURLClick(uri);
                    } else {
                        ExternalApplicationsUtilKt.openUrlInExternalBrowser(widget.getContext(), uri);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "MatrixURLSpan : on click failed " + e.getLocalizedMessage(), e);
        }
    }

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

        for (int index = 0; index < MXPatterns.MATRIX_PATTERNS.size(); index++) {
            Pattern pattern = MXPatterns.MATRIX_PATTERNS.get(index);

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
                    Log.e(LOG_TAG, "refreshMatrixSpans " + e.getLocalizedMessage(), e);
                }
            }
        }
    }
}
