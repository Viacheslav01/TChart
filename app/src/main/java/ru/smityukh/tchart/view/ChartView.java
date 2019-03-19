package ru.smityukh.tchart.view;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
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

    private class Controller {

        private TextView mChartHeaderView;
        private ChartMainView mChartMainView;
        private ChartPeriodView mChartPeriodView;

        public Controller() {
            mChartHeaderView = findViewById(R.id.chart_header);
            mChartMainView = findViewById(R.id.chart_main);
            mChartPeriodView = findViewById(R.id.chart_period);
        }

        public void setData(@NonNull ChartData data) {
            mChartPeriodView.setData(data);
        }
    }
}
