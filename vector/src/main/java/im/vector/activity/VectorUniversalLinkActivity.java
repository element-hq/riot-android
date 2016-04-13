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


/*
Room URLs:
Giom + Pedro    = https://vector.im/beta/#/room/#Giom_and_Pedro:matrix.org       (main@: #Giom_and_Pedro:matrix.org)
                = https://vector.im/beta/#/room/!pgfhmgkxsEmuFNfJPV:matrix.org
Pedro + Yannick = https://vector.im/beta/#/room/!quwbwtvMMHXdqvHZvv:matrix.org   (roomID: !quwbwtvMMHXdqvHZvv:matrix.org)
Matrix HQ       = https://vector.im/beta/#/room/#freenode_#matrix:matrix.org     (main@: #matrix:matrix.org)
Matrix internal = https://vector.im/beta/#/room/#irc_#matrix:openmarket.com      (remote@: #irc_#matrix:openmarket.com)
Matrix-dev      = https://vector.im/beta/#/room/#freenode_#matrix-dev:matrix.org (main@: #matrix-dev:matrix.org local@: #freenode_#matrix:matrix.org)


Permalinks:
Matrix internal = https://vector.im/beta/#/room/#irc_#matrix:openmarket.com/$1460117526842HOOkZ:sw1v.org
Matix HQ        = https://vector.im/beta/#/room/#freenode_#matrix:matrix.org/$1460115980839wSSUo:sw1v.org
Vector          = https://vector.im/beta/#/room/#vector:matrix.org/$1460117159519454bLNpf:matrix.org
 */
package im.vector.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.util.HashMap;

import im.vector.receiver.VectorUniversalLinkReceiver;

public class VectorUniversalLinkActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent myBroadcastIntent = new Intent(VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK, getIntent().getData());
        sendBroadcast(myBroadcastIntent);

        // Start the home activity with the waiting view enabled, while the URL link
        // is processed in the receiver. The receiver, once the URL was parsed, will stop the waiting view.
        /*Intent intent = new Intent(getApplicationContext(), VectorHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(VectorHomeActivity.EXTRA_WAITING_VIEW_STATUS, VectorHomeActivity.WAITING_VIEW_START);
        startActivity(intent);*/

        finish();
    }
}
