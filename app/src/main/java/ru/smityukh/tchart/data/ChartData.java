package ru.smityukh.tchart.data;

import android.support.annotation.NonNull;

public final class ChartData {
    @NonNull
    public final long[] mAxis;
    @NonNull
    public final long[][] mValues;
    @NonNull
    public final String[] mNames;
    @NonNull
    public final int[] mColors;

    ChartData(@NonNull long[] axis, @NonNull long[][] values, @NonNull String[] names, @NonNull int[] colors) {
        mAxis = axis;
        mValues = values;
        mNames = names;
        mColors = colors;

        validate();
    }

    private void validate() {
        int chartCount = mValues.length;
        if (mNames.length != chartCount) {
            throw new WrongChartDataJsonException("Charts and names have to contain the same number of elements");
        }

        if (mColors.length != chartCount) {
            throw new WrongChartDataJsonException("Charts and colors have to contain the same number of elements");
        }

        int columnsCount = mAxis.length;
        for (int index = 0; index < chartCount; index++) {
            if (mValues[index].length != columnsCount) {
                throw new WrongChartDataJsonException("Every chart has to contain " + columnsCount + " elements");
            }
        }
    }
}
