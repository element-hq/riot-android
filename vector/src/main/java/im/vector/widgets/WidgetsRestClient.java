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
package im.vector.widgets;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;

import java.util.Map;

public class WidgetsRestClient extends RestClient<WidgetsApi> {
    /**
     * {@inheritDoc}
     */
    public WidgetsRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, WidgetsApi.class, "/api", false);
    }

    /**
     * Register to the server
     * @param params the put params.
     * @param callback the asynchronous callback called when finished
     */
    public void register(final Map<Object, Object> params, final ApiCallback<Map<String, String>> callback) {
        final String description = "Register";

        mApi.register(params, new RestAdapterCallback<Map<String, String>>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                register(params, callback);
            }
        }));
    }
}
