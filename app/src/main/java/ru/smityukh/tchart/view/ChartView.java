package ru.smityukh.tchart.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.CompoundButtonCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
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

    private class Controller {

        private final ChartSelectorController mChartSelector;
        private TextView mChartHeaderView;
        private ChartMainView mChartMainView;
        private ChartPeriodView mChartPeriodView;

        Controller() {
            mChartHeaderView = findViewById(R.id.chart_header);
            mChartMainView = findViewById(R.id.chart_main);
            mChartPeriodView = findViewById(R.id.chart_period);
            mChartSelector = new ChartSelectorController((RecyclerView) findViewById(R.id.chart_selector));
        }

        void setData(@NonNull ChartData data) {
            mChartPeriodView.setData(data);
            mChartSelector.setData(data);
        }
    }

    private static class ChartSelectorController {
        @NonNull
        private RecyclerView mRecyclerView;
        @Nullable
        private Adapter mAdapter;

        ChartSelectorController(@NonNull RecyclerView recyclerView) {
            mRecyclerView = recyclerView;

            recyclerView.setHasFixedSize(true);

            recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));

            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(recyclerView.getContext());
            recyclerView.setLayoutManager(layoutManager);
        }

        void setData(@NonNull ChartData data) {
            mAdapter = new Adapter(data, this::onCheckedChanged);
            mRecyclerView.setAdapter(mAdapter);
        }

        @Nullable
        boolean[] getCheckedState() {
            if (mAdapter == null) {
                return null;
            }

            return mAdapter.mCheckedItems.clone();
        }

        private void onCheckedChanged(int position, boolean checked) {

        }

        private static class Adapter extends RecyclerView.Adapter<ViewHolder> {

            @NonNull
            private final ChartData mChartData;
            @NonNull
            private OnCheckedChangedCallback mCallback;

            @NonNull
            private final boolean[] mCheckedItems;

            Adapter(@NonNull ChartData chartData, @NonNull OnCheckedChangedCallback callback) {
                mChartData = chartData;
                mCallback = callback;

                mCheckedItems = new boolean[mChartData.mNames.length];
                for (int index = 0; index < mCheckedItems.length; index++) {
                    mCheckedItems[index] = true;
                }
            }

            private void onCheckedChanged(int position, boolean checked) {
                if (mCheckedItems[position] == checked) {
                    return;
                }

                mCheckedItems[position] = checked;
                mCallback.onCheckedChanged(position, checked);
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int position) {

                CheckBox checkBox = (CheckBox) LayoutInflater
                        .from(viewGroup.getContext())
                        .inflate(R.layout.chart_selector_item, viewGroup, false);

                return new ViewHolder(checkBox, this::onCheckedChanged);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
                CheckBox checkBox = viewHolder.mCheckBox;

                checkBox.setText(mChartData.mNames[position]);
                checkBox.setChecked(mCheckedItems[position]);

                int states[][] = {{android.R.attr.state_checked}, {}};
                int colors[] = {mChartData.mColors[position], mChartData.mColors[position]};
                CompoundButtonCompat.setButtonTintList(checkBox, new ColorStateList(states, colors));
            }

            @Override
            public int getItemCount() {
                return mChartData.mNames.length;
            }
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {

            @NonNull
            private final CheckBox mCheckBox;

            ViewHolder(@NonNull CheckBox checkBox, @NonNull final OnCheckedChangedCallback callback) {
                super(checkBox);
                mCheckBox = checkBox;

                mCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> callback.onCheckedChanged(getAdapterPosition(), isChecked));
            }
        }

        interface OnCheckedChangedCallback {
            void onCheckedChanged(int position, boolean checked);
        }
    }
}
