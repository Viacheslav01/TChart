package ru.smityukh.tchart.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
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
        super(context, attrs);
        init(context);
    }

    public ChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        inflate(context, R.layout.chart_view, this);
        mController = new Controller();
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
            });
        }

        void setData(@NonNull ChartData data) {
            mChartPeriodView.setData(data);
            mChartSelector.setData(data);
        }
    }
}
