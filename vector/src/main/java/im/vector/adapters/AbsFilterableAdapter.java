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

package im.vector.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.widget.Filter;
import android.widget.Filterable;

import org.matrix.androidsdk.MXSession;

import im.vector.Matrix;

/**
 * Abstract adapter to manage filtering
 *
 * @param <T> view holder type
 */
public abstract class AbsFilterableAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> implements Filterable {

    final Context mContext;
    final MXSession mSession;

    CharSequence mCurrentFilterPattern;
    private final Filter mFilter;

    AbsAdapter.RoomInvitationListener mRoomInvitationListener;
    AbsAdapter.GroupInvitationListener mGroupInvitationListener;
    AbsAdapter.MoreRoomActionListener mMoreRoomActionListener;
    AbsAdapter.MoreGroupActionListener mMoreGroupActionListener;
    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    AbsFilterableAdapter(final Context context) {
        mContext = context;

        mSession = Matrix.getInstance(context).getDefaultSession();
        mFilter = createFilter();
    }

    AbsFilterableAdapter(final Context context, final AbsAdapter.RoomInvitationListener invitationListener,
                         final AbsAdapter.MoreRoomActionListener moreActionListener) {
        mContext = context;

        mRoomInvitationListener = invitationListener;
        mMoreRoomActionListener = moreActionListener;

        mSession = Matrix.getInstance(context).getDefaultSession();
        mFilter = createFilter();
    }

    AbsFilterableAdapter(final Context context, final AbsAdapter.GroupInvitationListener invitationListener,
                         final AbsAdapter.MoreGroupActionListener moreActionListener) {
        mContext = context;

        mGroupInvitationListener = invitationListener;
        mMoreGroupActionListener = moreActionListener;

        mSession = Matrix.getInstance(context).getDefaultSession();
        mFilter = createFilter();
    }

    /*
     * *********************************************************************************************
     * Filtering methods
     * *********************************************************************************************
     */

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    public void onFilterDone(CharSequence currentPattern) {
        mCurrentFilterPattern = currentPattern;
    }

    /*
     * *********************************************************************************************
     * Abstract methods
     * *********************************************************************************************
     */

    protected abstract Filter createFilter();

}
