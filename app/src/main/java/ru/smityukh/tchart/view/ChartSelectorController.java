package ru.smityukh.tchart.view;

import android.content.res.ColorStateList;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.CompoundButtonCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CheckBox;
import ru.smityukh.tchart.R;
import ru.smityukh.tchart.data.ChartData;

public class ChartSelectorController {
    @NonNull
    private RecyclerView mRecyclerView;
    @Nullable
    private Adapter mAdapter;

    @Nullable
    private OnCheckedChangedCallback mOnCheckedChangedCallback;

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

    public void setOnCheckedChangedCallback(@Nullable OnCheckedChangedCallback onCheckedChangedCallback) {
        mOnCheckedChangedCallback = onCheckedChangedCallback;
    }

    @Nullable
    boolean[] getCheckedState() {
        if (mAdapter == null) {
            return null;
        }

        return mAdapter.mCheckedItems.clone();
    }

    void setCheckedState(int position, boolean checked) {
        if (mAdapter == null) {
            return;
        }

        if (position >= mAdapter.mCheckedItems.length) {
            return;
        }

        mAdapter.onCheckedChanged(position, checked);
    }

    private void onCheckedChanged(int position, boolean checked) {
        if (mOnCheckedChangedCallback != null) {
            mOnCheckedChangedCallback.onCheckedChanged(position, checked);
        }
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
