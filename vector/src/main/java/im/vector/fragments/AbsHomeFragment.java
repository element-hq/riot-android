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

package im.vector.fragments;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.util.Log;

import butterknife.ButterKnife;
import butterknife.Unbinder;
import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.VectorHomeActivity;

/**
 * Abstract fragment providing the universal search
 */
public abstract class AbsHomeFragment extends Fragment {

    private static final String LOG_TAG = AbsHomeFragment.class.getSimpleName();

    // Butterknife unbinder
    private Unbinder mUnBinder;

    protected VectorHomeActivity mActivity;

    protected String mCurrentFilter;

    protected MXSession mSession;

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    @CallSuper
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    @CallSuper
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mUnBinder = ButterKnife.bind(this, view);
    }

    @Override
    @CallSuper
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = (VectorHomeActivity) getActivity();
        mSession = Matrix.getInstance(getActivity()).getDefaultSession();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ic_action_mark_all_as_read:
                Log.e(LOG_TAG, "onOptionsItemSelected mark all as read");
                onMarkAllAsRead();
                return true;
        }
        return false;
    }

    @Override
    @CallSuper
    public void onDestroyView() {
        super.onDestroyView();
        mUnBinder.unbind();

        mCurrentFilter = null;
    }

    @Override
    @CallSuper
    public void onDetach() {
        super.onDetach();
        mActivity = null;
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Apply the filter
     *
     * @param pattern
     */
    public void applyFilter(final String pattern) {
        if (TextUtils.isEmpty(pattern)) {
            if (mCurrentFilter != null) {
                onResetFilter();
                mCurrentFilter = null;
            }
        } else if (!TextUtils.equals(mCurrentFilter, pattern)) {
            onFilter(pattern, new OnFilterListener() {
                @Override
                public void onFilterDone(int nbItems) {
                    mCurrentFilter = pattern;
                }
            });
        }
    }

    /*
     * *********************************************************************************************
     * Abstract methods
     * *********************************************************************************************
     */

    protected abstract void onMarkAllAsRead();

    protected abstract void onFilter(final String pattern, final OnFilterListener listener);

    protected abstract void onResetFilter();

    /*
     * *********************************************************************************************
     * Listener
     * *********************************************************************************************
     */

    public interface OnFilterListener {
        void onFilterDone(final int nbItems);
    }
}
