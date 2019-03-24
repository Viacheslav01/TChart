package ru.smityukh.tchart.data;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

public class ChartDataSingleton {
    @Nullable
    private static List<ChartData> mChartData;

    @Nullable
    public static List<ChartData> getChartData(@NonNull Context context) {
        if (mChartData != null) {
            return mChartData;
        }

        DataReader dataReader = new DataReader();
        try {
            mChartData = dataReader.readData(context);
        } catch (IOException | WrongChartDataJsonException | IllegalStateException ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }

        return mChartData;
    }
}
