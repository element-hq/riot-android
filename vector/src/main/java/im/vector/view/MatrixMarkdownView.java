package im.vector.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.acl.LastOwnerException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatrixMarkdownView extends WebView {
    private static String LOG_TAG = "MMarkdownView";

    public interface IMatrixMarkdownViewListener {
        /**
         * A markdown text has been parsed.
         * @param text the text to parse.
         * @param HTMLText the parsed text
         */
        void onMarkdownParsed(String text, String HTMLText);
    }

    /** Java <-> JS interface **/
    private MarkDownWebAppInterface mMarkDownWebAppInterface = new MarkDownWebAppInterface();

    public MatrixMarkdownView(Context context) {
        this(context, null);
    }

    public MatrixMarkdownView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MatrixMarkdownView(Context context, AttributeSet attrs, int defStyleAttr) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            getSettings().setAllowUniversalAccessFromFileURLs(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }

    /**
     *
     * @param markdownText
     */
    public void setMarkDownText(final String markdownText, final IMatrixMarkdownViewListener listener) {
        if (null == listener) {
            return;
        }

        String text = markdownText;

        if (null != markdownText) {
            text = markdownText.trim();
        }

        if (TextUtils.isEmpty(text)) {
            listener.onMarkdownParsed(markdownText, text);
            return;
        }

        mMarkDownWebAppInterface.initParams(markdownText, listener);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            loadUrl(String.format("javascript:convertToHtml('%s')", escapeText(markdownText)));
        } else {
            evaluateJavascript(String.format("convertToHtml('%s')", markdownText), null);
        }
    }

    /**
     * Escape text before converting it.
     * @param text the text to escape
     * @return the escaped text
     */
    private  static String escapeText(String text) {
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
        private IMatrixMarkdownViewListener mListener;

        /**
         * Init the search params.
         * @param textToParse the text to parse
         * @param listener the listener.
         */
        public void initParams(String textToParse, IMatrixMarkdownViewListener listener) {
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

                }
            }
        }
    }
}
