package ru.smityukh.tchart;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

import ru.smityukh.tchart.data.ChartData;
import ru.smityukh.tchart.data.DataReader;
import ru.smityukh.tchart.data.WrongChartDataJsonException;
import ru.smityukh.tchart.view.ChartView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();

        DataReader dataReader = new DataReader();
        List<ChartData> chartData = null;
        try {
            chartData = dataReader.readData(this);
        } catch (IOException | WrongChartDataJsonException | IllegalStateException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (chartData == null) {
            return;
        }

        ChartView periodView = findViewById(R.id.chart_view);
        periodView.setData(chartData.get(0));
    }
}
