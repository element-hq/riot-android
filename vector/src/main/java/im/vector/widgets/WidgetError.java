/*
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

import org.matrix.androidsdk.core.model.MatrixError;

/**
 * Widget error code
 */
public class WidgetError extends MatrixError {
    public static final String WIDGET_NOT_ENOUGH_POWER_ERROR_CODE = "WIDGET_NOT_ENOUGH_POWER_ERROR_CODE";
    public static final String WIDGET_CREATION_FAILED_ERROR_CODE = "WIDGET_CREATION_FAILED_ERROR_CODE";

    /**
     * Create a widget error
     *
     * @param code                     the error code (see XX_ERROR_CODE)
     * @param detailedErrorDescription the detailed error description
     */
    public WidgetError(String code, String detailedErrorDescription) {
        errcode = code;
        error = detailedErrorDescription;
    }
}
