/*
 * Copyright 2015 OpenMarket Ltd
 * Copyright 2018 New Vector Ltd
 * Copyright 2019 New Vector Ltd
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

import android.content.Context;
import android.net.Uri;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;

import java.util.Map;

class WidgetsRestClient extends RestClient<WidgetsApi> {

    private static final String API_VERSION = "1.1";

    /**
     * {@inheritDoc}
     */
    public WidgetsRestClient(Context context, IntegrationManagerConfig config) {
        super(new HomeServerConnectionConfig.Builder()
                        .withHomeServerUri(Uri.parse(config.getApiUrl()))
                        .build(),
                WidgetsApi.class,
                "",
                false);
    }

    /**
     * Register to the server
     *
     * @param params   the put params.
     * @param callback the asynchronous callback called when finished
     */
    public void register(final Map<Object, Object> params, final ApiCallback<Map<String, String>> callback) {
        final String description = "Register";

        mApi.register(params, API_VERSION).enqueue(new RestAdapterCallback<>(description,
                mUnsentEventsManager, callback, () -> register(params, callback)));
    }

    /**
     * Validates the scalar token to the server
     */
    public void validateToken(final String scalarToken, final ApiCallback<Map<String, String>> callback) {
        final String description = "Validate";

        mApi.validateToken(scalarToken, API_VERSION).enqueue(new RestAdapterCallback<>(description,
                mUnsentEventsManager, callback, null));
    }
}
