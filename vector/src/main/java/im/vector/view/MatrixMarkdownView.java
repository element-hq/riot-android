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


  private static String LOG_TAG = "MatrixMarkdownView";

  MarkDownWebAppInterface mMarkDownWebAppInterface = new MarkDownWebAppInterface();

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
    loadUrl("file:///android_asset/html/preview.html");

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
  public void setMarkDownText(String markdownText) {
    String escMdText = escapeForText(markdownText);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      loadUrl(String.format("javascript:preview('%s')", escMdText));
    } else {
      evaluateJavascript(String.format("preview('%s')", escMdText), new ValueCallback<String>() {
        @Override
        public void onReceiveValue(String value) {
          MatrixMarkdownView.this.loadUrl("javascript:getValue()");
        }
      });
    }
  }

  private  static String escapeForText(String mdText) {
    String escText = mdText.replace("\n", "\\\\n");
    escText = escText.replace("'", "\\\'");
    escText = escText.replace("\r", "");
    return escText;
  }

  // private class
  private class MarkDownWebAppInterface {
    @JavascriptInterface
    public void wSalut(String anObject) {
      Log.e("GG", "---> " + anObject);
    }
  }
}
