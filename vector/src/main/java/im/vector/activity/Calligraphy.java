package im.vector.activity;

import android.app.Application;

import im.vector.R;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

public class Calligraphy extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("font/shabnam.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        );

    }

}
