package im.vector.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.adapters.HomeRoomAdapter;

public class HomeSectionView extends RelativeLayout {

    @BindView(R.id.section_header)
    TextView mHeader;

    @BindView(R.id.section_badge)
    TextView mBadge;

    @BindView(R.id.section_recycler_view)
    RecyclerView mRecyclerView;

    HomeRoomAdapter mAdapter;

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

    private void setup() {
        inflate(getContext(), R.layout.home_section_view, this);
        ButterKnife.bind(this);

        //TODO move to xml layout/drawable
        mBadge.setTypeface(null, Typeface.BOLD);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(100);
        shape.setColor(ContextCompat.getColor(getContext(), R.color.vector_white_alpha_50));
        mBadge.setBackground(shape);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAdapter = null; // might be necessary to avoid memory leak?
    }

    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    public void setTitle(@StringRes final int title) {
        mHeader.setText(title);
    }

    public void attachAdapter(final HomeRoomAdapter adapter) {
        mRecyclerView.setAdapter(adapter);
        mAdapter = adapter;
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                mBadge.setText(String.valueOf(mAdapter.getItemCount()));
            }
        });
    }
}
