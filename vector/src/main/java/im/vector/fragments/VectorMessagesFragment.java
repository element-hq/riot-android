/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.fragments;

import android.text.TextUtils;
import android.widget.Toast;

import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.fragments.MatrixMessagesFragment;

import im.vector.Matrix;
import im.vector.R;

public class VectorMessagesFragment extends MatrixMessagesFragment {
    private static final String LOG_TAG = VectorMessagesFragment.class.getSimpleName();

    public static VectorMessagesFragment newInstance(String roomId) {
        VectorMessagesFragment fragment = new VectorMessagesFragment();
        fragment.setArguments(getArgument(roomId));
        return fragment;
    }

    @Override
    protected void displayInitializeTimelineError(Object error) {
        String errorMessage = "";

        if (error instanceof MatrixError) {
            MatrixError matrixError = (MatrixError) error;

            if (TextUtils.equals(matrixError.errcode, MatrixError.NOT_FOUND)) {
                errorMessage = getContext().getString(R.string.failed_to_load_timeline_position, Matrix.getApplicationName());
            } else {
                errorMessage = matrixError.getLocalizedMessage();
            }
        } else if (error instanceof Exception) {
            errorMessage = ((Exception) error).getLocalizedMessage();
        }

        if (!TextUtils.isEmpty(errorMessage)) {
            Log.d(LOG_TAG, "displayInitializeTimelineError : " + errorMessage);
            Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
        }
    }
}
