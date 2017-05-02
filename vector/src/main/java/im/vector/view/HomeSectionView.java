package im.vector.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.Filter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.adapters.AbsAdapter;
import im.vector.adapters.HomeRoomAdapter;
import im.vector.fragments.AbsHomeFragment;

public class HomeSectionView extends RelativeLayout {

    @BindView(R.id.section_header)
    TextView mHeader;

    @BindView(R.id.section_badge)
    TextView mBadge;

    @BindView(R.id.section_recycler_view)
    RecyclerView mRecyclerView;

    @BindView(R.id.section_placeholder)
    TextView mPlaceHolder;

    private HomeRoomAdapter mAdapter;

    private boolean mHideIfEmpty;
    private String mNoItemPlaceholder;
    private String mNoResultPlaceholder;
    private String mCurrentFilter;

    public HomeSectionView(Context context) {
        super(context);
        setup();
    }

    public HomeSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public HomeSectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public HomeSectionView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setup();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAdapter = null; // might be necessary to avoid memory leak?
    }

    private void setup() {
        inflate(getContext(), R.layout.home_section_view, this);
        ButterKnife.bind(this);

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(100);
        shape.setColor(ContextCompat.getColor(getContext(), R.color.vector_white_alpha_50));
        mBadge.setBackground(shape);
    }

    public void setTitle(@StringRes final int title) {
        mHeader.setText(title);
    }

    public void setPlaceholders(final String noItemPlaceholder, final String noResultPlaceholder) {
        mNoItemPlaceholder = noItemPlaceholder;
        mNoResultPlaceholder = noResultPlaceholder;
        mPlaceHolder.setText(TextUtils.isEmpty(mCurrentFilter) ? mNoItemPlaceholder : mNoResultPlaceholder);
    }

    public void setHideIfEmpty(final boolean hideIfEmpty) {
        mHideIfEmpty = hideIfEmpty;
        setVisibility(mHideIfEmpty && (mAdapter == null || mAdapter.isEmpty()) ? GONE : VISIBLE);
    }

    public void setupRecyclerView(final RecyclerView.LayoutManager layoutManager, @LayoutRes final int itemResId,
                                  final boolean nestedScrollEnabled, final HomeRoomAdapter.OnSelectRoomListener onSelectRoomListener,
                                  final AbsAdapter.InvitationListener invitationListener,
                                  final AbsAdapter.MoreRoomActionListener moreActionListener) {
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setNestedScrollingEnabled(nestedScrollEnabled);

        mAdapter = new HomeRoomAdapter(getContext(), itemResId, onSelectRoomListener, invitationListener, moreActionListener);
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                onDataUpdated();
            }
        });
    }

    /**
     * Update the views to reflect the new number of items
     */
    private void onDataUpdated() {
        setVisibility(mHideIfEmpty && mAdapter.isEmpty() ? GONE : VISIBLE);
        mBadge.setText(String.valueOf(mAdapter.getBadgeCount()));
        mBadge.setVisibility(mAdapter.getBadgeCount() == 0 ? GONE : VISIBLE);
        mRecyclerView.setVisibility(mAdapter.hasNoResult() ? GONE : VISIBLE);
        mPlaceHolder.setVisibility(mAdapter.hasNoResult() ? VISIBLE : GONE);
    }

    public void onFilter(final String pattern, final AbsHomeFragment.OnFilterListener listener) {
        mAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                if (listener != null) {
                    listener.onFilterDone(count);
                }
                mCurrentFilter = pattern;
                mPlaceHolder.setText(TextUtils.isEmpty(mCurrentFilter) ? mNoItemPlaceholder : mNoResultPlaceholder);
                onDataUpdated();
            }
        });
    }

    public HomeRoomAdapter getAdapter() {
        return mAdapter;
    }

    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }
}
