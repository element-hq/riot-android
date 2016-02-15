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

/**
 * Singleton class for tracking the currently viewed room.
 */
public class ViewedRoomTracker {

    private static ViewedRoomTracker instance = null;

    private String mViewedRoomId = null;
    private String mMatrixId = null;

    private ViewedRoomTracker(){
    }

    public static synchronized ViewedRoomTracker getInstance() {
        if (instance == null) {
            instance = new ViewedRoomTracker();
        }
        return instance;
    }

    public String getViewedRoomId() {
        return mViewedRoomId;
    }

    public String getMatrixId() {
        return mMatrixId;
    }

    public void setViewedRoomId(String roomId) {
        mViewedRoomId = roomId;
    }

    public void setMatrixId(String matrixId) {
        mMatrixId = matrixId;
    }
}
