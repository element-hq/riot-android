package im.vector.pref;

import android.content.Context;
import android.preference.EditTextPreference;
import android.preference.PreferenceScreen;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.matrix.androidsdk.MXSession;

import im.vector.R;
import im.vector.util.VectorUtils;

public class AvatarPreference extends EditTextPreference {

    Context mContext;
    ImageView mAvatarView;
    MXSession mSession;

    public AvatarPreference(Context context) {
        super(context);
        mContext = context;
    }

    public AvatarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public AvatarPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    protected View onCreateView(ViewGroup parent) {
        setWidgetLayoutResource(R.layout.vector_settings_round_avatar);
        View layout = super.onCreateView(parent);
        mAvatarView = (ImageView)layout.findViewById(R.id.avatar_img);
        refreshAvatar();
        return layout;
    }

    public void refreshAvatar() {
        if ((null !=  mAvatarView) && (null != mSession)) {
            VectorUtils.setMemberAvatar(mAvatarView, mSession.getMyUser().userId, mSession.getMyUser().displayname);
            mSession.getMediasCache().loadAvatarThumbnail(mSession.getHomeserverConfig(), mAvatarView, mSession.getMyUser().avatarUrl, mContext.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size));
        }
    }

    public void setSession(MXSession session) {
        mSession = session;
        refreshAvatar();
    }

    public void performClick(PreferenceScreen preferenceScreen) {
        // call only the click listener
        if (getOnPreferenceClickListener() != null && getOnPreferenceClickListener().onPreferenceClick(this)) {
            getOnPreferenceClickListener().onPreferenceClick(this);
        }
    }
}