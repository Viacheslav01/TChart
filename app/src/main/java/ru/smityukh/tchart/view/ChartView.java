package ru.smityukh.tchart.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.CompoundButtonCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import android.widget.TextView;
import ru.smityukh.tchart.R;
import ru.smityukh.tchart.data.ChartData;

public class ChartView extends LinearLayout {

    @NonNull
    private Controller mController;

    public ChartView(Context context) {
        super(context);
        init(context);
    }

    public ChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs, R.attr.chartViewStyle);
        init(context);
    }

    public ChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        inflate(context, R.layout.chart_view, this);
        setOrientation(VERTICAL);
        mController = new Controller();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        State typedState = new State(superState);

        if (mController.mChartData == null) {
            typedState.mHasData = false;
        } else {
            typedState.mHasData = true;
            typedState.mChartsCount = mController.mChartData.mValues.length;
            typedState.mColumnsCount = mController.mChartData.mAxis.length;
            typedState.mVisibleCharts = mController.mChartSelector.getCheckedState();
            typedState.mSelectionStart = mController.mChartPeriodView.getStart();
            typedState.mSelectionEnd = mController.mChartPeriodView.getEnd();
        }

        return typedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        State typedState = (State) state;
        super.onRestoreInstanceState(typedState.getSuperState());

        if (!typedState.mHasData) {
            return;
        }

        if (mController.mChartData == null) {
            return;
        }

        if (typedState.mChartsCount != mController.mChartData.mValues.length
                || typedState.mColumnsCount != mController.mChartData.mAxis.length) {
            return;
        }

        for (int index = 0; index < typedState.mVisibleCharts.length; index++) {
            mController.mChartSelector.setCheckedState(index, typedState.mVisibleCharts[index]);
        }

        mController.mChartPeriodView.setSelection(typedState.mSelectionStart, typedState.mSelectionEnd);
    }

    public void setData(@NonNull ChartData data) {
        mController.setData(data);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // TODO: Optimize or search a nice solution
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            Rect rect = new Rect();
            mController.mChartPeriodView.getHitRect(rect);

            float x = event.getX();
            float y = event.getY();

            if (rect.left > x || rect.right < x) {
                if (rect.top <= y && rect.bottom >= y) {
                    if (rect.left > x) {
                        event.setLocation(rect.left, y);
                    } else {
                        event.setLocation(rect.right - 1, y);
                    }
                }
            }
        }

        return super.dispatchTouchEvent(event);
    }

    private class Controller {

        @Nullable
        private ChartData mChartData;

        private final ChartSelectorController mChartSelector;
        private TextView mChartHeaderView;
        private ChartMainView mChartMainView;
        private ChartPeriodView mChartPeriodView;

        Controller() {
            mChartHeaderView = findViewById(R.id.chart_header);
            mChartMainView = findViewById(R.id.chart_main);
            mChartPeriodView = findViewById(R.id.chart_period);
            mChartSelector = new ChartSelectorController(findViewById(R.id.chart_selector));

            mChartSelector.setOnCheckedChangedCallback((position, checked) -> {
                mChartPeriodView.setChartVisibility(position, checked);
                mChartMainView.setChartVisibility(position, checked);
            });

            mChartPeriodView.setOnSelectionChangedCallback((start, end) -> {
                mChartMainView.setSelection(start, end);
            });
        }

        void setData(@NonNull ChartData data) {
            mChartData = data;

            mChartMainView.setChartData(data);
            mChartPeriodView.setData(data);
            mChartSelector.setData(data);
        }
    }

    private static class State extends BaseSavedState {

        boolean mHasData;
        int mChartsCount;
        int mColumnsCount;
        boolean[] mVisibleCharts;
        float mSelectionStart;
        float mSelectionEnd;

        public State(Parcelable superState) {
            super(superState);
        }

        public State(Parcel source) {
            super(source);

            mHasData = source.readInt() == 1;
            if (!mHasData) {
                return;
            }

            mChartsCount = source.readInt();
            mColumnsCount = source.readInt();
            mVisibleCharts = new boolean[mChartsCount];
            source.readBooleanArray(mVisibleCharts);
            mSelectionStart = source.readFloat();
            mSelectionEnd = source.readFloat();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            out.writeInt(mHasData ? 1 : 0);
            if (!mHasData) {
                return;
            }

            out.writeInt(mChartsCount);
            out.writeInt(mColumnsCount);
            out.writeBooleanArray(mVisibleCharts);
            out.writeFloat(mSelectionStart);
            out.writeFloat(mSelectionEnd);
        }

        public static final Parcelable.Creator<State> CREATOR = new Parcelable.Creator<State>() {
            public State createFromParcel(Parcel in) {
                return new State(in);
            }

            public State[] newArray(int size) {
                return new State[size];
            }
        };
    }
}
