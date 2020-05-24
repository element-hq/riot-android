package im.vector.sharedpreferences;

import android.content.Context;
import android.content.SharedPreferences;

public class BatnaSharedPreferences {

    private SharedPreferences sharedPreferences;
    private Context context;
    private SharedPreferences.Editor editor;

    public BatnaSharedPreferences(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences("BatnaSharedPreferences", Context.MODE_PRIVATE);
        if (sharedPreferences != null) {
            editor = sharedPreferences.edit();
        }
    }

    public void saveStringData(String key, String value) {
        if (editor != null) {
            editor.putString(key, value);
            editor.apply();
        }
    }

    public void saveBooleanData(String key, Boolean value) {
        if (editor != null) {
            editor.putBoolean(key, value);
            editor.apply();
        }
    }

    public String getStringData(String key) {
        String value =  null;
        if (sharedPreferences != null) {
            value = sharedPreferences.getString(key, null);
        }
        return value;
    }

    public Boolean getBooleanData(String key) {
        Boolean value = false;
        if (sharedPreferences != null) {
            value = sharedPreferences.getBoolean(key, false);
        }
        return value;
    }
 }
