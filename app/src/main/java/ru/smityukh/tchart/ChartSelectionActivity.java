package ru.smityukh.tchart;

import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatDelegate;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import ru.smityukh.tchart.data.ChartData;
import ru.smityukh.tchart.data.ChartDataSingleton;
import ru.smityukh.tchart.data.DataReader;
import ru.smityukh.tchart.data.WrongChartDataJsonException;
import ru.smityukh.tchart.view.ChartView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChartSelectionActivity extends AppCompatActivity {

    private static final String NIGHT_MODE_PREF_KEY = "NIGHT_MODE_PREF_KEY";

    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart_selection);
        AppCompatDelegate.setDefaultNightMode(getNightMode());

        ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setTitle("Select chart");
        }

        mListView = findViewById(R.id.charts_list);

        List<ChartData> chartData = ChartDataSingleton.getChartData(this);
        if (chartData == null) {
            return;
        }

        List<ChartSelectionItem> chartSelectionItems = new ArrayList<>();
        for (int i = 0; i < chartData.size(); i++) {
            chartSelectionItems.add(new ChartSelectionItem(i));
        }

        ArrayAdapter<ChartSelectionItem> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chartSelectionItems);
        mListView.setAdapter(adapter);

        mListView.setOnItemClickListener((parent, view, position, id) -> {
            ChartSelectionItem item = (ChartSelectionItem) parent.getItemAtPosition(position);
            ChartSelectionActivity.this.startActivity(MainActivity.createIntent(this, item.mIndex));
        });
    }

    @AppCompatDelegate.NightMode
    private int getNightMode() {
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(NIGHT_MODE_PREF_KEY, AppCompatDelegate.MODE_NIGHT_NO);
    }


    private static class ChartSelectionItem {
        private int mIndex;

        ChartSelectionItem(int index) {
            mIndex = index;
        }

        @Override
        public String toString() {
            return "Chart #" + mIndex;
        }
    }
}
