package com.example.cwstataccelerator;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class PerformanceMetricsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_performance_metrics, container, false);
        updateMetricsView(); // Populate metrics on creation
        return view;
    }

    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(updateReceiver, new IntentFilter("com.example.cwstataccelerator.UPDATE_LOG"));
        updateMetricsView(); // Refresh when the fragment becomes visible
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateReceiver);
    }

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateMetricsView(); // Refresh the metrics dynamically
        }
    };

    public void updateMetricsView() {
        // Get the root view of the fragment
        View view = getView();
        if (view == null) return; // Ensure the view is not null

        TableLayout metricsTable = view.findViewById(R.id.metrics_view);
        if (metricsTable == null) {
            Log.e("PerformanceMetrics", "Metrics view not found!");
            return;
        }

        metricsTable.removeAllViews(); // Clear old metrics

        // Add header row
        TableRow headerRow = new TableRow(requireContext());
        headerRow.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        // Get performance metrics from TrainerUtils
        Map<String, Integer[]> metrics = TrainerUtils.getPerformanceMetrics(requireContext());

        if (metrics.isEmpty()) {
            // No data available
            TextView noDataMessage = new TextView(requireContext());
            noDataMessage.setText("No performance metrics available. Start training to generate data.");
            noDataMessage.setGravity(Gravity.CENTER);
            metricsTable.addView(noDataMessage);
            return;
        }

        String[] headers = {"Character", "Attempts", "Success Rate", "Avg Time (ms)", "Fastest Time (ms)"};
        for (String header : headers) {
            TextView headerView = new TextView(requireContext());
            headerView.setText(header);
            headerView.setPadding(8, 8, 8, 8);
            headerView.setTextSize(12);
            headerView.setGravity(Gravity.CENTER);
            headerView.setTypeface(null, android.graphics.Typeface.BOLD);
            headerRow.addView(headerView);
        }
        metricsTable.addView(headerRow);

        // Sort metrics by performance (worst-performing first)
        List<Map.Entry<String, Integer[]>> sortedMetrics = sortMetricsByPerformance(metrics);

        // Create table rows for metrics
        for (Map.Entry<String, Integer[]> entry : sortedMetrics) {
            TableRow row = new TableRow(requireContext());
            row.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
            ));

            MorseCodeGenerator morseCodeGenerator = new MorseCodeGenerator(requireContext());

            String character = entry.getKey() + "   " + morseCodeGenerator.getMorseCode(entry.getKey()) + "   ";;
            Integer[] stats = entry.getValue();
            int attempts = stats[0];
            int correct = stats[1];
            int totalResponseTime = stats[2];
            int fastestResponseTime = stats[3];

            double successRate = (correct / (double) attempts) * 100;
            int averageResponseTime = totalResponseTime / attempts;

            // Character column
            TextView charView = createTextView(character);
            row.addView(charView);

            // Attempts column
            TextView attemptsView = createTextView(String.valueOf(attempts));
            row.addView(attemptsView);

            // Success rate column
            TextView successRateView = createTextView(String.format("%.1f%%", successRate));
            row.addView(successRateView);

            // Average response time column
            TextView avgTimeView = createTextView(averageResponseTime + "");
            row.addView(avgTimeView);

            // Fastest response time column
            TextView fastestTimeView = createTextView(fastestResponseTime + "");
            row.addView(fastestTimeView);

            metricsTable.addView(row);
        }
    }



    private List<Map.Entry<String, Integer[]>> sortMetricsByPerformance(Map<String, Integer[]> metrics) {
        List<Map.Entry<String, Integer[]>> sortedMetrics = new ArrayList<>(metrics.entrySet());
        Collections.sort(sortedMetrics, new Comparator<Map.Entry<String, Integer[]>>() {
            @Override
            public int compare(Map.Entry<String, Integer[]> o1, Map.Entry<String, Integer[]> o2) {
                Integer[] stats1 = o1.getValue();
                Integer[] stats2 = o2.getValue();

                double successRate1 = stats1[1] / (double) stats1[0];
                double successRate2 = stats2[1] / (double) stats2[0];

                if (successRate1 != successRate2) {
                    return Double.compare(successRate1, successRate2); // Sort by success rate
                }

                return Integer.compare(stats1[2] / stats1[0], stats2[2] / stats2[0]); // Sort by average response time
            }
        });
        return sortedMetrics;
    }

    private TextView createTextView(String text) {
        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setPadding(16, 8, 16, 8);
        return textView;
    }
}
