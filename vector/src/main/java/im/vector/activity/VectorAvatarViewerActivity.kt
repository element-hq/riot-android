/*
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

package im.vector.activity

import android.content.Context
import android.content.Intent
import android.media.ExifInterface
import butterknife.BindView
import com.github.chrisbanes.photoview.PhotoView
import im.vector.R

class VectorAvatarViewerActivity : MXCActionBarActivity() {

    @BindView(R.id.avatar_viewer_image)
    lateinit var photoView: PhotoView

    override fun getLayoutRes() = R.layout.activity_avatar_viewer

    override fun initUiAndData() {
        super.initUiAndData()

        val session = getSession(intent)

        if (session != null) {
            session.mediaCache.loadBitmap(session.homeServerConfig,
                    photoView, intent.getStringExtra(EXTRA_AVATAR_URL), 0, ExifInterface.ORIENTATION_UNDEFINED, null, null)
        } else {
            finish()
        }
    }

    companion object {
        private const val EXTRA_AVATAR_URL = "EXTRA_AVATAR_URL"

        fun getIntent(context: Context, matrixId: String, avatarUrl: String): Intent {
            return Intent(context, VectorAvatarViewerActivity::class.java).apply {
                putExtra(EXTRA_MATRIX_ID, matrixId)
                putExtra(EXTRA_AVATAR_URL, avatarUrl)
            }
        }
    }

    // For transition
    override fun onBackPressed() {
        supportFinishAfterTransition()
    }
}