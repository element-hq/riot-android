package im.vector.directory;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;
import android.util.DisplayMetrics;

public class RoundedBackgroundSpan extends ReplacementSpan {

    private static final int CORNER_RADIUS = 12;

    private static final float PADDING_X = convertDpToPixel(12);
    private static final float PADDING_Y = convertDpToPixel(2);

    private static final float MAGIC_NUMBER = convertDpToPixel(2);

    private int mBackgroundColor;
    private int mTextColor;
    private float mTextSize;

    /**
     * @param backgroundColor color value, not res id
     * @param textSize        in pixels
     */
    public RoundedBackgroundSpan(int backgroundColor, int textColor, float textSize) {
        mBackgroundColor = backgroundColor;
        mTextColor = textColor;
        mTextSize = textSize;
    }

    public static float convertDpToPixel(float dp) {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float px = dp * (metrics.densityDpi / 160f);
        return Math.round(px);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        paint = new Paint(paint); // make a copy for not editing the referenced paint

        paint.setTextSize(mTextSize);

        // Draw the rounded background
        paint.setColor(mBackgroundColor);
        float textHeightWrapping = convertDpToPixel(4);
        float tagBottom = top + textHeightWrapping + PADDING_Y + mTextSize + PADDING_Y + textHeightWrapping;
        float tagRight = x + getTagWidth(text, start, end, paint);
        RectF rect = new RectF(x, top, tagRight, tagBottom);
        canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, paint);

        // Draw the text
        paint.setColor(mTextColor);
        canvas.drawText(text, start, end, x + PADDING_X, tagBottom - PADDING_Y - textHeightWrapping - MAGIC_NUMBER, paint);
    }

    private int getTagWidth(CharSequence text, int start, int end, Paint paint) {
        return Math.round(PADDING_X + paint.measureText(text.subSequence(start, end).toString()) + PADDING_X);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        paint = new Paint(paint); // make a copy for not editing the referenced paint
        paint.setTextSize(mTextSize);
        return getTagWidth(text, start, end, paint);
    }
}
