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

package org.matrix.console.store;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Stores login credentials in SharedPreferences.
 */
public class LoginStorage {
    public static final String PREFS_LOGIN = "org.matrix.console.store.LoginStorage";

    // one account
    public static final String PREFS_KEY_USERNAME = "org.matrix.console.store.LoginStorage.PREFS_KEY_USERNAME";
    public static final String PREFS_KEY_HOME_SERVER = "org.matrix.console.store.LoginStorage.PREFS_KEY_HOME_SERVER";
    public static final String PREFS_KEY_ACCESS_TOKEN = "org.matrix.console.store.LoginStorage.PREFS_KEY_ACCESS_TOKEN";

    // multi accounts
    public static final String PREFS_KEY_USERNAMES = "org.matrix.console.store.LoginStorage.PREFS_KEY_USERNAMES";
    public static final String PREFS_KEY_HOME_SERVERS = "org.matrix.console.store.LoginStorage.PREFS_KEY_HOME_SERVERS";
    public static final String PREFS_KEY_ACCESS_TOKENS = "org.matrix.console.store.LoginStorage.PREFS_KEY_ACCESS_TOKENS";

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

    public Credentials getDefaultCredentials() {
        ArrayList<Credentials> credentialsList = getCredentialsList();

        if ((null == credentialsList) || (0 == credentialsList.size())) {
            return null;
        }

        return credentialsList.get(0);
    }

    /**
     * Return a list of credentials.
     * @return a list of credentials.
     */
    public ArrayList<Credentials> getCredentialsList() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);

        String username = prefs.getString(PREFS_KEY_USERNAME, null);
        String server = prefs.getString(PREFS_KEY_HOME_SERVER, null);
        String token = prefs.getString(PREFS_KEY_ACCESS_TOKEN, null);

        String usernames = prefs.getString(PREFS_KEY_USERNAMES, null);
        String servers = prefs.getString(PREFS_KEY_HOME_SERVERS, null);
        String tokens = prefs.getString(PREFS_KEY_ACCESS_TOKENS, null);

        boolean withDefaultCredentials = (username != null) && (server != null) && (token != null);

        // backward compatibility
        if (((null == usernames) || (null == servers) || (null == tokens)) && !withDefaultCredentials) {
            return null;
        }

        ArrayList<Credentials> credsList = new ArrayList<Credentials>();

        // the client used to manage only one account
        // backward compatibility
        if (withDefaultCredentials) {
            Credentials creds = new Credentials();
            creds.userId = username;
            creds.homeServer = server;
            creds.accessToken = token;

            if (addCredentials(creds)) {
                SharedPreferences.Editor e = prefs.edit();
                e.putString(PREFS_KEY_ACCESS_TOKEN, null);
                e.putString(PREFS_KEY_HOME_SERVER, null);
                e.putString(PREFS_KEY_USERNAME, null);
                e.commit();
            }

            credsList.add(creds);
        } else {
            // return a list of credentials
            ArrayList<String> usernamesList = deserialize(usernames);
            ArrayList<String> serversList = deserialize(servers);
            ArrayList<String> tokensList = deserialize(tokens);

            for(int index = 0; index < usernamesList.size(); index++) {
                Credentials creds = new Credentials();
                creds.userId = usernamesList.get(index);
                creds.homeServer = serversList.get(index);
                creds.accessToken = tokensList.get(index);

                credsList.add(creds);
            }
        }

        return credsList;
    }

    /**
     * Add a credentials to the credentials list
     * @param credentials the credentials to add.
     * @return true if the credentials has been succcessfully added
     */
    public boolean addCredentials(Credentials credentials) {
        if (null != credentials) {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
            SharedPreferences.Editor e = prefs.edit();

            // add the usernames
            {
                ArrayList<String> usernamesList = deserialize(prefs.getString(PREFS_KEY_USERNAMES, null));

                if (null == usernamesList) {
                    usernamesList = new ArrayList<String>();
                }

                usernamesList.add(credentials.userId);
                e.putString(PREFS_KEY_USERNAMES, serialize(usernamesList));
            }

            // add the home server
            {
                ArrayList<String> homeServersList = deserialize(prefs.getString(PREFS_KEY_HOME_SERVERS, null));

                if (null == homeServersList) {
                    homeServersList = new ArrayList<String>();
                }

                homeServersList.add(credentials.homeServer);
                e.putString(PREFS_KEY_HOME_SERVERS, serialize(homeServersList));
            }

            // add the token
            {
                ArrayList<String> tokensList = deserialize(prefs.getString(PREFS_KEY_ACCESS_TOKENS, null));

                if (null == tokensList) {
                    tokensList = new ArrayList<String>();
                }

                tokensList.add(credentials.accessToken);
                e.putString(PREFS_KEY_ACCESS_TOKENS, serialize(tokensList));
            }

            return e.commit();
        }

        return false;
    }

    /**
     * Remove the credentials from credentials list
     * @param credentials teh credentials to remove
     * @return
     */
    public Boolean removeCredentials(Credentials credentials) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);

        String usernames = prefs.getString(PREFS_KEY_USERNAMES, null);
        String servers = prefs.getString(PREFS_KEY_HOME_SERVERS, null);
        String tokens = prefs.getString(PREFS_KEY_ACCESS_TOKENS, null);

        // has some credentials
        if ((null == usernames) || (null == servers) || (null == tokens)) {
            return false;
        }

        ArrayList<String> usernamesList = deserialize(usernames);
        int pos = usernamesList.indexOf(credentials.userId);

        if (pos >= 0) {
            SharedPreferences.Editor e = prefs.edit();

            usernamesList.remove(pos);
            if (0 == usernamesList.size()) {
                e.putString(PREFS_KEY_USERNAMES, null);
            } else {
                e.putString(PREFS_KEY_USERNAMES, serialize(usernamesList));
            }

            ArrayList<String> homeServersList = deserialize(servers);
            homeServersList.remove(pos);
            if (0 == homeServersList.size()) {
                e.putString(PREFS_KEY_HOME_SERVERS, null);
            } else {
                e.putString(PREFS_KEY_HOME_SERVERS, serialize(homeServersList));
            }

            ArrayList<String> tokensList = deserialize(tokens);
            tokensList.remove(pos);
            if (0 == tokensList.size()) {
                e.putString(PREFS_KEY_ACCESS_TOKENS, null);
            } else {
                e.putString(PREFS_KEY_ACCESS_TOKENS, serialize(tokensList));
            }

            // remove the old storage
            e.putString(PREFS_KEY_ACCESS_TOKEN, null);
            e.putString(PREFS_KEY_HOME_SERVER, null);
            e.putString(PREFS_KEY_USERNAME, null);

            return e.commit();
        }

        return true;
    }
}
