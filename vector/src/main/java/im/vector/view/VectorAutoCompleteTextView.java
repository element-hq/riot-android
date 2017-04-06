/* 
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.AppCompatMultiAutoCompleteTextView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.AutoCompleteTextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import im.vector.R;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.AutoCompletedUserAdapter;


public class VectorAutoCompleteTextView extends AppCompatMultiAutoCompleteTextView {

    AutoCompletedUserAdapter mAdapter;
    String mPendingText;

    Field mPopupCanBeUpdatedField;

    public VectorAutoCompleteTextView(Context context) {
        super(context, null);
    }

    public VectorAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VectorAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public void updatesUser(MXSession session, String roomId) {
        List<User> users = new ArrayList<>();

        if (TextUtils.isEmpty(roomId)) {
            users.addAll(session.getDataHandler().getStore().getUsers());
        } else {
            Room room = session.getDataHandler().getStore().getRoom(roomId);

            if (null != room) {
                Collection<RoomMember> members = room.getMembers();

                for (RoomMember member : members) {
                    User user = session.getDataHandler().getUser(member.getUserId());

                    if (null != user) {
                        users.add(user);
                    }
                }
            }
        }

        mAdapter = new AutoCompletedUserAdapter(getContext(), R.layout.item_user_auto_complete, session);
        mAdapter.updateItems(users);
        setThreshold(3);
        setAdapter(mAdapter);
        setTokenizer(new VectorRoomActivity.RoomTokenizer());

        if (null == mPopupCanBeUpdatedField) {
            try {
                mPopupCanBeUpdatedField = AutoCompleteTextView.class.getDeclaredField("mPopupCanBeUpdated");
                mPopupCanBeUpdatedField.setAccessible(true);
            } catch (Exception e) {
            }
        }
    }

    @Override
    protected void performFiltering(final CharSequence text, int keyCode) {
        if (null == mPopupCanBeUpdatedField) {
            super.performFiltering(text, keyCode);
        } else {
            if (!TextUtils.equals(text.toString(), mPendingText)) {
                dismissDropDown();
            }

            try {
                mPopupCanBeUpdatedField.setBoolean(this, true);
            } catch (Exception e) {
            }

            mPendingText = text.toString();

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (TextUtils.equals(getText().toString(), text.toString())) {
                        mAdapter.getFilter().filter(text, VectorAutoCompleteTextView.this);
                    }
                }
            }, 700);
        }
    }
}
