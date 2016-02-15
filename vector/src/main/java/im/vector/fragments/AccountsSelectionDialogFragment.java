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

package im.vector.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.db.MXMediasCache;
import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.AccountsAdapter;

import java.util.Collection;

/**
 * A dialog fragment showing a list of room members for a given room.
 */
public class AccountsSelectionDialogFragment extends DialogFragment {
    private static final String LOG_TAG = "AccountsSelectionDialogFragment";

    public interface AccountsListener {
        void onSelected(MXSession session);
    }

    public static AccountsSelectionDialogFragment newInstance(Collection<MXSession> sessions) {
        AccountsSelectionDialogFragment f= new AccountsSelectionDialogFragment();
        Bundle args = new Bundle();
        f.setArguments(args);
        f.setSessions(sessions);
        return f;
    }

    private ListView mListView;
    private AccountsAdapter mAdapter;
    private Collection<MXSession>  mSessions = null;

    private AccountsListener mListener = null;

    public void setListener(AccountsListener listener) {
        mListener = listener;
    }

    public void setSessions(Collection<MXSession> sessions) {
        mSessions = sessions;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mSessions == null) {
            throw new RuntimeException("No MXSession.");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        d.setTitle(getString(R.string.choose_account));
        return d;
    }

    /**
     * Return the used medias cache.
     * This method can be overridden to use another medias cache
     * @return the used medias cache
     */
    public MXMediasCache getMXMediasCache() {
        return Matrix.getInstance(getActivity()).getMediasCache();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_dialog_accounts_list, container, false);
        mListView = ((ListView)v.findViewById(R.id.listView_accounts));

        mAdapter = new AccountsAdapter(getActivity(), R.layout.adapter_item_account, getMXMediasCache());

        mAdapter.addAll(mSessions);
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if (null != mListener) {
                    mListener.onSelected(mAdapter.getItem(position));
                }

                // dismiss the list
                AccountsSelectionDialogFragment.this.dismiss();
            }
        });

        return v;
    }
}
