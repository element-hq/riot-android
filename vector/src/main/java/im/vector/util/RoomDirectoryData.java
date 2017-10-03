/*
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

import android.text.TextUtils;

import org.matrix.androidsdk.MXSession;

import java.io.Serializable;

/**
 * This class describes a directory server.
 */
public class RoomDirectoryData implements Serializable {

    public static final String DEFAULT_HOME_SERVER_NAME = "Matrix";

    /**
     * The display name
     */
    final String mDisplayName;

    /**
     * The directory server URL (might be null)
     */
    final String mServerUrl;

    /**
     * The third party server identifier
     */
    final String mThirdPartyInstanceId;

    /**
     * Tell if all the federated servers must be included
     */
    final boolean mIncludeAllNetworks;

    /**
     * the avatar url
     */
    final String mAvatarUrl;

    /**
     * Creator
     *
     * @param session the session
     * @param serverUrl the home server url
     * @param serverName the home server displayname
     * @return a new instance
     */
    public static RoomDirectoryData getIncludeAllServers(MXSession session, String serverUrl, String serverName) {
        // the self server url should be null
        if (TextUtils.equals(session.getHomeServerConfig().getHomeserverUri().getHost(), serverUrl)) {
            return new RoomDirectoryData(null, serverName, null, null, true);
        } else {
            return new RoomDirectoryData(serverUrl, serverName, null, null, true);
        }
    }

    /**
     * Provides the default value
     * @return the default value
     */
    public static RoomDirectoryData getDefault() {
        return new RoomDirectoryData(null, DEFAULT_HOME_SERVER_NAME, null, null, false);
    }

    /**
     * Constructor
     *
     * @param serverUrl            the server URL (might be null)
     * @param displayName          the displayName
     * @param avatarUrl            the avatar URL (might be null)
     * @param thirdPartyInstanceId the third party instance id (might be null)
     * @param includeAllNetworks   true to tell
     */
    public RoomDirectoryData(String serverUrl, String displayName, String avatarUrl, String thirdPartyInstanceId, boolean includeAllNetworks) {
        mServerUrl = serverUrl;
        mDisplayName = displayName;
        mAvatarUrl = avatarUrl;
        mThirdPartyInstanceId = thirdPartyInstanceId;
        mIncludeAllNetworks = includeAllNetworks;
    }

    public String getServerUrl() {
        return mServerUrl;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getAvatarUrl() {
        return mAvatarUrl;
    }

    public String getThirdPartyInstanceId() {
        return mThirdPartyInstanceId;
    }

    public boolean isIncludedAllNetworks() {
        return mIncludeAllNetworks;
    }
}