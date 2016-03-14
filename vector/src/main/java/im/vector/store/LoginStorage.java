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

package im.vector.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Stores login credentials in SharedPreferences.
 */
public class LoginStorage {
    private static final String LOG_TAG = "LoginStorage";

    public static final String PREFS_LOGIN = "Vector.LoginStorage";

    // one account
    public static final String PREFS_KEY_USERNAME = "PREFS_KEY_USERNAME";
    public static final String PREFS_KEY_HOME_SERVER = "PREFS_KEY_HOME_SERVER";
    public static final String PREFS_KEY_ACCESS_TOKEN = "PREFS_KEY_ACCESS_TOKEN";

    // multi accounts
    public static final String PREFS_KEY_USERNAMES = "PREFS_KEY_USERNAMES";
    public static final String PREFS_KEY_HOME_SERVERS = "PREFS_KEY_HOME_SERVERS";
    public static final String PREFS_KEY_ACCESS_TOKENS = "PREFS_KEY_ACCESS_TOKENS";

    // multi accounts + HomeserverConnectionConfig
    public static final String PREFS_KEY_CONNECTION_CONFIGS = "PREFS_KEY_CONNECTION_CONFIGS";

    private Context mContext;
    private Gson mGson;

    public LoginStorage(Context appContext) {
        mContext = appContext.getApplicationContext();
        mGson = new Gson();
    }

    private String serialize(ArrayList<String> list) {
        if (null == list) {
            return null;
        }
        return mGson.toJson(list);
    }

    private ArrayList<String> deserialize(String listAsString) {
        if (null == listAsString) {
            return null;
        }

        return new ArrayList(Arrays.asList(mGson.fromJson(listAsString,String[].class)));
    }

    /**
     * Return a list of HomeserverConnectionConfig.
     * @return a list of HomeserverConnectionConfig.
     */
    public ArrayList<HomeserverConnectionConfig> getCredentialsList() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);

        String connectionConfigsString = prefs.getString(PREFS_KEY_CONNECTION_CONFIGS, null);

        Log.d(LOG_TAG, "Got connection json: " + connectionConfigsString);

        if (connectionConfigsString == null) {
            return new ArrayList<HomeserverConnectionConfig>();
        }

        try {

            JSONArray connectionConfigsStrings = new JSONArray(connectionConfigsString);

            ArrayList<HomeserverConnectionConfig> configList = new ArrayList<HomeserverConnectionConfig>(
                    connectionConfigsStrings.length()
            );

            for (int i = 0; i < connectionConfigsStrings.length(); i++) {
                configList.add(
                        HomeserverConnectionConfig.fromJson(connectionConfigsStrings.getJSONObject(i))
                );
            }

            return configList;
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to deserialize accounts " + e.getMessage(), e);
            throw new RuntimeException("Failed to deserialize accounts");
        }
    }

    /**
     * Add a credentials to the credentials list
     * @param config the HomeserverConnectionConfig to add.
     */
    public void addCredentials(HomeserverConnectionConfig config) {
        if (null != config && config.getCredentials() != null) {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            ArrayList<HomeserverConnectionConfig> configs = getCredentialsList();

            configs.add(config);

            ArrayList<JSONObject> serialized = new ArrayList<JSONObject>(configs.size());

            try {
                for (HomeserverConnectionConfig c : configs) {
                    serialized.add(c.toJson());
                }
            } catch (JSONException e) {
                throw new RuntimeException("Failed to serialize connection config");
            }

            String ser = new JSONArray(serialized).toString();

            Log.d(LOG_TAG, "Storing " + serialized.size() + " credentials");

            editor.putString(PREFS_KEY_CONNECTION_CONFIGS, ser);
            editor.apply();
        }
    }

    /**
     * Remove the credentials from credentials list
     * @param config the credentials to remove
     */
    public void removeCredentials(HomeserverConnectionConfig config) {
        if (null != config && config.getCredentials() != null) {
            Log.d(LOG_TAG, "Removing account: " + config.getCredentials().userId);

            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            ArrayList<HomeserverConnectionConfig> configs = getCredentialsList();
            ArrayList<JSONObject> serialized = new ArrayList<JSONObject>(configs.size());

            boolean found = false;
            try {
                for (HomeserverConnectionConfig c : configs) {
                    if (c.getCredentials().userId.equals(config.getCredentials().userId)) {
                        found = true;
                    } else {
                        serialized.add(c.toJson());
                    }
                }
            } catch (JSONException e) {
                throw new RuntimeException("Failed to serialize connection config");
            }

            if (!found) return;

            String ser = new JSONArray(serialized).toString();

            Log.d(LOG_TAG, "Storing " + serialized.size() + " credentials");

            editor.putString(PREFS_KEY_CONNECTION_CONFIGS, ser);
            editor.apply();
        }
    }

    /**
     * Replace the credential from credentials list, based on credentials.userId.
     * If it does not match an existing credential it does *not* insert the new credentials.
     * @param config the credentials to insert
     */
    public void replaceCredentials(HomeserverConnectionConfig config) {
        if (null != config && config.getCredentials() != null) {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            ArrayList<HomeserverConnectionConfig> configs = getCredentialsList();
            ArrayList<JSONObject> serialized = new ArrayList<JSONObject>(configs.size());

            boolean found = false;
            try {
                for (HomeserverConnectionConfig c : configs) {
                    if (c.getCredentials().userId.equals(config.getCredentials().userId)) {
                        serialized.add(config.toJson());
                        found = true;
                    } else {
                        serialized.add(c.toJson());
                    }
                }
            } catch (JSONException e) {
                throw new RuntimeException("Failed to serialize connection config");
            }

            if (!found) return;

            String ser = new JSONArray(serialized).toString();

            Log.d(LOG_TAG, "Storing " + serialized.size() + " credentials");

            editor.putString(PREFS_KEY_CONNECTION_CONFIGS, ser);
            editor.apply();
        }
    }

    /**
     * Clear the stored values
     */
    public void clear() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(PREFS_KEY_CONNECTION_CONFIGS);
        editor.apply();
    }
}