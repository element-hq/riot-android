/*
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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

    private static final String DEFAULT_HOME_SERVER_NAME = "Matrix";

    /**
     * The display name
     */
    private final String mDisplayName;

    /**
     * The directory server URL (might be null)
     */
    private final String mServerUrl;

    /**
     * The third party server identifier
     */
    private final String mThirdPartyInstanceId;

    /**
     * Tell if all the federated servers must be included
     */
    private final boolean mIncludeAllNetworks;

    /**
     * the avatar url
     */
    private final String mAvatarUrl;

    /**
     * Creator
     *
     * @param serverName        the home server name (optional). Set null when the server is the current user's home server.
     * @param serverDisplayName the home server displayname
     * @return a new instance
     */
    public static RoomDirectoryData getIncludeAllServers(String serverName, String serverDisplayName) {
        return new RoomDirectoryData(serverName, serverName, null, null, true);
    }

    /**
     * Provides the default value
     *
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