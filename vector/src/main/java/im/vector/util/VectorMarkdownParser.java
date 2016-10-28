package im.vector.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * Markdown parser.
 * This class uses a webview.
 */
public class VectorMarkdownParser extends WebView {
    private static String LOG_TAG = "VMarkdownParser";

    public interface IVectorMarkdownParserListener {
        /**
         * A markdown text has been parsed.
         * @param text the text to parse.
         * @param HTMLText the parsed text
         */
        void onMarkdownParsed(String text, String HTMLText);
    }

    /** Java <-> JS interface **/
    private MarkDownWebAppInterface mMarkDownWebAppInterface = new MarkDownWebAppInterface();

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
        loadUrl("file:///android_asset/html/markdown.html");

        // allow java script
        getSettings().setJavaScriptEnabled(true);

        // java <-> web interface
        addJavascriptInterface(mMarkDownWebAppInterface, "Android");

        getSettings().setAllowUniversalAccessFromFileURLs(true);
    }

    /**
     * Parse the MarkDown text.
     * @param markdownText the text to parse
     * @param listener the parser listener
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

        // empty text ?
        if (TextUtils.isEmpty(text)) {
            // nothing to do
            listener.onMarkdownParsed(markdownText, text);
            return;
        }

        mMarkDownWebAppInterface.initParams(markdownText, listener);

        // call the javascript method
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            loadUrl(String.format("javascript:convertToHtml('%s')", escapeText(markdownText)));
        } else {
            evaluateJavascript(String.format("convertToHtml('%s')", escapeText(markdownText)), null);
        }
    }

    /**
     * Escape text before converting it.
     * @param text the text to escape
     * @return the escaped text
     */
    private  static String escapeText(String text) {
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
         * Init the search params.
         * @param textToParse the text to parse
         * @param listener the listener.
         */
        public void initParams(String textToParse, IVectorMarkdownParserListener listener) {
            mTextToParse = textToParse;
            mListener = listener;
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
                try {
                    mListener.onMarkdownParsed(mTextToParse, HTMLText);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## wOnParse() " + e.getMessage());
                }
            }
        }
    }
}
