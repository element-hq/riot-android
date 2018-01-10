/*
 * Copyright 2014 OpenMarket Ltd
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;

import org.matrix.androidsdk.util.Log;

import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Markdown parser.
 * This class uses a webview.
 */
public class VectorMarkdownParser extends WebView {
    private static final String LOG_TAG = VectorMarkdownParser.class.getSimpleName();

    // tell if the parser is properly initialised
    private boolean mIsInitialised = false;

    public interface IVectorMarkdownParserListener {
        /**
         * A markdown text has been parsed.
         *
         * @param text     the text to parse.
         * @param HTMLText the parsed text
         */
        void onMarkdownParsed(String text, String HTMLText);
    }

    /**
     * Java <-> JS interface
     **/
    private final MarkDownWebAppInterface mMarkDownWebAppInterface = new MarkDownWebAppInterface();

    public VectorMarkdownParser(Context context) {
        this(context, null);
    }

    public VectorMarkdownParser(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VectorMarkdownParser(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initialize() {
        try {
            loadUrl("file:///android_asset/html/markdown.html");

            // allow java script
            getSettings().setJavaScriptEnabled(true);

            // java <-> web interface
            addJavascriptInterface(mMarkDownWebAppInterface, "Android");

            getSettings().setAllowUniversalAccessFromFileURLs(true);

            mIsInitialised = true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## initialize() failed " + e.getMessage());
        }
    }

    /**
     * Parse the MarkDown text.
     *
     * @param markdownText the text to parse
     * @param listener     the parser listener
     */
    public void markdownToHtml(final String markdownText, final IVectorMarkdownParserListener listener) {
        // sanity check
        if (null == listener) {
            return;
        }

        String text = markdownText;

        if (null != markdownText) {
            text = markdownText.trim();
        }

        // empty text or disabled
        if (!mIsInitialised || TextUtils.isEmpty(text) || !PreferencesManager.isMarkdownEnabled(getContext())) {
            // nothing to do
            listener.onMarkdownParsed(markdownText, text);
            return;
        }

        mMarkDownWebAppInterface.initParams(markdownText, listener);

        try {
            // the conversion starts
            mMarkDownWebAppInterface.start();

            // call the javascript method
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                loadUrl(String.format("javascript:convertToHtml('%s')", escapeText(markdownText)));
            } else {
                evaluateJavascript(String.format("convertToHtml('%s')", escapeText(markdownText)), null);
            }
        } catch (Exception e) {
            mMarkDownWebAppInterface.cancel();
            Log.e(LOG_TAG, "## markdownToHtml() : failed " + e.getMessage());
            listener.onMarkdownParsed(markdownText, text);
        }
    }

    /**
     * Escape text before converting it.
     *
     * @param text the text to escape
     * @return the escaped text
     */
    private static String escapeText(String text) {
        text = text.replace("\\", "\\\\");
        text = text.replace("\n", "\\\\n");
        text = text.replace("'", "\\\'");
        text = text.replace("\r", "");
        return text;
    }

    // private class
    private class MarkDownWebAppInterface {
        /**
         * The text to parse
         */
        private String mTextToParse;

        /**
         * The parser listener
         */
        private IVectorMarkdownParserListener mListener;

        /**
         * Defines watchdog timer
         */
        private Timer mWatchdogTimer;

        /**
         * Init the search params.
         *
         * @param textToParse the text to parse
         * @param listener    the listener.
         */
        public void initParams(String textToParse, IVectorMarkdownParserListener listener) {
            mTextToParse = textToParse;
            mListener = listener;
        }

        /**
         * The parsing starts.
         */
        public void start() {
            Log.d(LOG_TAG, "## start() : Markdown starts");

            try {
                // monitor the parsing as there is no way to detect if there was an error in the JS.
                mWatchdogTimer = new Timer();
                mWatchdogTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (null != mListener) {
                            Log.d(LOG_TAG, "## start() : delay expires");

                            try {
                                mListener.onMarkdownParsed(mTextToParse, mTextToParse);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## wOnParse() " + e.getMessage());
                            }
                        }
                        done();
                    }
                }, 300);
            } catch (Throwable e) {
                Log.e(LOG_TAG, "## start() : failed to starts " + e.getMessage());
            }
        }

        /**
         * Cancel the markdown parser
         */
        public void cancel() {
            Log.e(LOG_TAG, "## cancel()");
            done();
        }

        /**
         * The parsing is done
         */
        private void done() {
            if (null != mWatchdogTimer) {
                mWatchdogTimer.cancel();
                mWatchdogTimer = null;
            }
            mListener = null;
        }

        @JavascriptInterface
        public void wOnParse(String HTMLText) {
            if (!TextUtils.isEmpty(HTMLText)) {
                HTMLText = HTMLText.trim();

                if (HTMLText.startsWith("<p>")) {
                    HTMLText = HTMLText.substring("<p>".length());
                }

                if (HTMLText.endsWith("</p>\n")) {
                    HTMLText = HTMLText.substring(0, HTMLText.length() - "</p>\n".length());
                } else if (HTMLText.endsWith("</p>")) {
                    HTMLText = HTMLText.substring(0, HTMLText.length() - "</p>".length());
                }
            }

            if (null != mListener) {
                Log.d(LOG_TAG, "## wOnParse() : parse done");

                try {
                    mListener.onMarkdownParsed(mTextToParse, HTMLText);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## wOnParse() " + e.getMessage());
                }

                done();
            } else {
                Log.d(LOG_TAG, "## wOnParse() : parse required too much time");
            }
        }
    }
}
