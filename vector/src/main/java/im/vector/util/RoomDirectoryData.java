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

import java.io.Serializable;

/**
 * This class describes a rooms directory server.
 */
public class RoomDirectoryData implements Serializable {

    private static final String DEFAULT_HOME_SERVER_NAME = "Matrix";

    /**
     * The display name (the server description)
     */
    private final String mDisplayName;

    /**
     * The server name (might be null)
     */
    private final String mHomeServer;

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
     * @param server            the home server (optional). Set null when the server is the current user's home server.
     * @param serverDisplayName the home server displayname
     * @return a new instance
     */
    public static RoomDirectoryData createIncludingAllNetworks(String server, String serverDisplayName) {
        return new RoomDirectoryData(server, serverDisplayName, null, null, true);
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
     * @param homeServer           the server (might be null)
     * @param displayName          the displayName
     * @param avatarUrl            the avatar URL (might be null)
     * @param thirdPartyInstanceId the third party instance id (might be null)
     * @param includeAllNetworks   true to tell
     */
    public RoomDirectoryData(String homeServer, String displayName, String avatarUrl, String thirdPartyInstanceId, boolean includeAllNetworks) {
        mHomeServer = homeServer;
        mDisplayName = displayName;
        mAvatarUrl = avatarUrl;
        mThirdPartyInstanceId = thirdPartyInstanceId;
        mIncludeAllNetworks = includeAllNetworks;
    }

    public String getHomeServer() {
        return mHomeServer;
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