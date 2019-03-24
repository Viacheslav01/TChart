package ru.smityukh.tchart;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatDelegate;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

import ru.smityukh.tchart.data.ChartData;
import ru.smityukh.tchart.data.DataReader;
import ru.smityukh.tchart.data.WrongChartDataJsonException;
import ru.smityukh.tchart.view.ChartView;

public class MainActivity extends AppCompatActivity {

    private static final String NIGHT_MODE_PREF_KEY = "NIGHT_MODE_PREF_KEY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setDefaultNightMode(getNightMode());

        setContentView(R.layout.activity_main);

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_switch_theme) {

            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                saveNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                saveNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }

            startActivity(new Intent(this, getClass()));
            overridePendingTransition(0, 0);
            finish();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @AppCompatDelegate.NightMode
    private int getNightMode() {
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(NIGHT_MODE_PREF_KEY, AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void saveNightMode(@AppCompatDelegate.NightMode int nightMode) {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putInt(NIGHT_MODE_PREF_KEY, nightMode)
                .apply();
    }
}
