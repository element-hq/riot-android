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

package org.matrix.matrixandroidsdk.fragments;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.matrix.matrixandroidsdk.R;

/**
 * A dialog fragment showing the JSON content of a message
 */
public class MessageDetailsFragment extends DialogFragment {
    private static final String LOG_TAG = "MessageDetailsFragment";
    public static final String ARG_TEXT = "org.matrix.matrixandroidsdk.fragments.MessageDetailsFragment.ARG_TEXT";

    private String mBody = "";

    public static MessageDetailsFragment newInstance(String text) {
        MessageDetailsFragment f = new MessageDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TEXT, text);
        f.setArguments(args);

        f.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBody = getArguments().getString(ARG_TEXT);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_message_details, container, false);

        TextView textView = ((TextView)v.findViewById(R.id.message_details_text));
        textView.setText(mBody);

        return v;
    }
}
