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
package org.matrix.console.view;

import android.content.ClipData;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

public class ConsoleEditText extends EditText {

    Context mConsoleContext = null;

    public ConsoleEditText(Context context) {
        super(context);
        mConsoleContext = context;
    }

    public ConsoleEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        mConsoleContext = context;
    }

    public ConsoleEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mConsoleContext = context;
    }

    @Override
    public boolean onTextContextMenuItem(int id) {

        if (android.R.id.paste == id) {
            android.content.ClipboardManager clipboardManager = (android.content.ClipboardManager) mConsoleContext.getSystemService(Context.CLIPBOARD_SERVICE);

            ClipData aClipData = clipboardManager.getPrimaryClip();

            aClipData = aClipData;
                //android.content.ClipData clip = android.content.ClipData.newPlainText("text label","text to clip");
                //clipboard.setPrimaryClip(clip);




        }


        return super.onTextContextMenuItem(id);
    }
}
