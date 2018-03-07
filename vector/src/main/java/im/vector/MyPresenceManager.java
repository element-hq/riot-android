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
package im.vector;

import android.content.Context;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Singleton class for handling the current user's presence.
 */
public class MyPresenceManager {
    // Array of presence states ordered by priority. If the current device thinks our user is online,
    // it will disregard a presence event saying the user is unavailable and advertise that they are in
    // fact online as a correction.
    private static final String[] orderedPresenceArray = new String[]{
            User.PRESENCE_ONLINE,
            User.PRESENCE_UNAVAILABLE,
            User.PRESENCE_OFFLINE
    };

    // We need the reverse structure to associate an order to a given presence state
    private static final Map<String, Integer> presenceOrderMap = new HashMap<>();

    static {
        for (int i = 0; i < orderedPresenceArray.length; i++) {
            presenceOrderMap.put(orderedPresenceArray[i], i);
        }
    }

    private static final HashMap<MXSession, MyPresenceManager> instances = new HashMap<>();

    private MyUser myUser;
    private String latestAdvertisedPresence = ""; // Presence we're advertising

    private MyPresenceManager(Context context, MXSession session) {
        myUser = session.getMyUser();

        myUser.addEventListener(new MXEventListener() {
            @Override
            public void onPresenceUpdate(Event event, User user) {
                myUser.presence = user.presence;

                // If the received presence is the same as the last one we've advertised, this must be
                // the event stream sending back our own event => nothing more to do
                if (!user.presence.equals(latestAdvertisedPresence)) {
                    // If we're here, the presence event comes from another of this user's devices. If it's saying for example that it's
                    // offline but we're currently online, our presence takes precedence; in which case, we broadcast the correction
                    Integer newPresenceOrder = presenceOrderMap.get(user.presence);
                    if (newPresenceOrder != null) {
                        int ourPresenceOrder = presenceOrderMap.get(latestAdvertisedPresence);
                        // If the new presence is further down the order list, we correct it
                        if (newPresenceOrder > ourPresenceOrder) {
                            advertisePresence(latestAdvertisedPresence);
                        }
                    }
                }
            }
        });
    }

    /**
     * Create an instance without any check.
     *
     * @param context the context
     * @param session the session
     * @return the presence manager
     */
    private static MyPresenceManager createInstance(Context context, MXSession session) {
        MyPresenceManager instance = new MyPresenceManager(context, session);
        instances.put(session, instance);
        return instance;
    }

    /**
     * Search a presence manager from a dedicated session
     *
     * @param context the context
     * @param session the session
     * @return the linked presence manager
     */
    public static synchronized MyPresenceManager getInstance(Context context, MXSession session) {
        if (!instances.containsKey(session)) {
            createInstance(context, session);
        }
        return instances.get(session);
    }

    /**
     * Create an MyPresenceManager instance for each session if it was not yet done.
     *
     * @param context  the context
     * @param sessions the sessions
     */
    public static synchronized void createPresenceManager(Context context, Collection<MXSession> sessions) {
        for (MXSession session : sessions) {
            if (!instances.containsKey(session)) {
                createInstance(context, session);
            }
        }
    }

    /**
     * Remove a presence manager for a session.
     *
     * @param session the session
     */
    public static synchronized void remove(MXSession session) {
        instances.remove(session);
    }

    /**
     * Send the advertise presence message.
     *
     * @param presence the presence message.
     */
    private void advertisePresence(String presence) {
        if (!latestAdvertisedPresence.equals(presence)) {
            latestAdvertisedPresence = presence;
        }
    }

    private static void advertiseAll(String presence) {
        Collection<MyPresenceManager> values = instances.values();

        for (MyPresenceManager myPresenceManager : values) {
            myPresenceManager.advertisePresence(presence);
        }
    }

    public static void advertiseAllOnline() {
        advertiseAll(User.PRESENCE_ONLINE);
    }

    public static void advertiseAllUnavailable() {
        advertiseAll(User.PRESENCE_UNAVAILABLE);
    }

    public void advertiseOffline() {
        advertisePresence(User.PRESENCE_OFFLINE);
    }
}
