package ru.smityukh.tchart.data;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.ArrayMap;
import android.util.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ru.smityukh.tchart.R;

public class DataReader {

    private static final String LINE_TYPE_NAME = "line";
    private static final int LINE_TYPE = 0;

    private static final String X_TYPE_NAME = "x";
    private static final int X_TYPE = 1;


    @NonNull
    public List<ChartData> readData(@NonNull Context context) throws IOException {
        List<ChartData> chartsData = new ArrayList<>();

        try (InputStream stream = context.getResources().openRawResource(R.raw.chart_data)) {
            Reader reader = new InputStreamReader(stream);
            JsonReader json = new JsonReader(reader);

            json.beginArray();
            while (json.hasNext()) {
                chartsData.add(readChartData(json));
            }
            json.endArray();
        }

        return chartsData;
    }

    private ChartData readChartData(@NonNull JsonReader json) throws IOException {
        json.beginObject();

        Map<String, long[]> columns = null;
        Map<String, Integer> types = null;
        Map<String, String> names = null;
        Map<String, Integer> colors = null;

        while (json.hasNext()) {
            String fieldName = json.nextName();
            switch (fieldName) {
                case "columns":
                    columns = new ArrayMap<>();
                    readChartColumns(json, columns);
                    break;

                case "types":
                    types = new ArrayMap<>();
                    readChartTypes(json, types);
                    break;

                case "names":
                    names = new ArrayMap<>();
                    readChartNames(json, names);
                    break;

                case "colors":
                    colors = new ArrayMap<>();
                    readChartColors(json, colors);
                    break;

                default:
                    throw new WrongChartDataJsonException("Unknown field in the json chart object: name=[" + fieldName + "]");
            }
        }

        checkField(columns, "columns");
        checkField(types, "types");
        checkField(names, "names");
        checkField(colors, "colors");

        json.endObject();

        int count = columns.size() - 1;

        long[] axisData = null;
        long[][] columnsData = new long[count][];
        int[] colorsData = new int[count];
        String[] namesData = new String[count];

        int index = 0;
        for (Map.Entry<String, Integer> entry : types.entrySet()) {
            String key = entry.getKey();

            if (entry.getValue() == X_TYPE) {
                axisData = columns.get(key);
                continue;
            }

            columnsData[index] = notNull(columns.get(key));
            colorsData[index] = notNull(colors.get(key));
            namesData[index] = notNull(names.get(key));

            index++;
        }

        return new ChartData(axisData, columnsData, namesData, colorsData);
    }

    private void readChartColumns(@NonNull JsonReader json, @NonNull Map<String, long[]> columns) throws IOException {
        json.beginArray();

        while (json.hasNext()) {
            json.beginArray();

            String name = json.nextString();

            ArrayList<Long> values = new ArrayList<>();
            while (json.hasNext()) {
                values.add(json.nextLong());
            }

            long[] array = new long[values.size()];
            for (int index = 0; index < values.size(); index++) {
                array[index] = values.get(index);
            }

            addValue(columns, name, array);

            json.endArray();
        }

        json.endArray();
    }

    private void readChartTypes(@NonNull JsonReader json, @NonNull Map<String, Integer> types) throws IOException {
        json.beginObject();

        boolean axisFound = false;

        while (json.hasNext()) {
            String name = json.nextName();
            String value = json.nextString();

            switch (value) {
                case X_TYPE_NAME:
                    if (axisFound) {
                        throw new WrongChartDataJsonException("Data column with type X must be a single one");
                    }
                    axisFound = true;
                    addValue(types, name, X_TYPE);
                    break;

                case LINE_TYPE_NAME:
                    addValue(types, name, LINE_TYPE);
                    break;

                default:
                    throw new WrongChartDataJsonException("Unknown json chart type: [" + value + "]");
            }
        }

        if (!axisFound) {
            throw new WrongChartDataJsonException("Data column with type X is not found");
        }

        json.endObject();
    }

    private void readChartColors(@NonNull JsonReader json, @NonNull Map<String, Integer> colors) throws IOException {
        json.beginObject();

        while (json.hasNext()) {
            String name = json.nextName();
            String value = json.nextString();

            addValue(colors, name, Color.parseColor(value));
        }

        json.endObject();
    }

    private void readChartNames(@NonNull JsonReader json, @NonNull Map<String, String> names) throws IOException {
        json.beginObject();

        while (json.hasNext()) {
            String name = json.nextName();
            String value = json.nextString();

            addValue(names, name, value);
        }

        json.endObject();
    }

    private <K, V> void addValue(Map<K, V> map, K key, V value) {
        if (map.put(key, value) != null) {
            throw new WrongChartDataJsonException("Duplicated key: [" + key + "]");
        }
    }

    private void checkField(@Nullable Object value, @NonNull String fieldName) {
        if (value == null) {
            throw new WrongChartDataJsonException("Json chart doesn't contain the " + fieldName + " field");
        }
    }

    @NonNull
    private <V> V notNull(@Nullable V value) {
        if (value == null) {
            throw new WrongChartDataJsonException("Value must not to be null");
        }

        return value;
    }
}
