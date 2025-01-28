package com.example.cwstataccelerator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
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

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class CallsignPerformanceMetricsFragment extends Fragment {

    private static final String TAG = "CallsignMetricsFragment";
    private TableLayout metricsView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_performance_metrics, container, false);

        metricsView = view.findViewById(R.id.metrics_view);

        // Register a broadcast receiver to update metrics dynamically
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(updateReceiver, new IntentFilter("com.example.cwstataccelerator.CALLSIGN_UPDATE_METRICS"));

        // Populate metrics initially
        updateMetricsView();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(updateReceiver, new IntentFilter("com.example.cwstataccelerator.CALLSIGN_UPDATE_METRICS"));
        updateMetricsView(); // Refresh metrics when fragment becomes visible
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateReceiver);
    }

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateMetricsView(); // Refresh metrics dynamically when broadcast is received
        }
    };

    public void updateMetricsView() {
        if (metricsView == null) return;

        metricsView.removeAllViews(); // Clear existing metrics

        // Fetch metrics from CallsignTrainerUtils
        Map<String, Integer[]> metrics = CallsignTrainerUtils.getPerformanceMetrics(requireContext());

        if (metrics == null || metrics.isEmpty()) {
            // Add a placeholder message if no metrics are available
            TableRow placeholderRow = new TableRow(requireContext());
            TextView placeholderText = new TextView(requireContext());
            placeholderText.setText("No performance metrics available. Start training to generate data.");
            placeholderText.setGravity(Gravity.CENTER);
            placeholderText.setPadding(16, 16, 16, 16);
            placeholderRow.addView(placeholderText);
            metricsView.addView(placeholderRow);
            return;
        }

        // Add a header row to the metrics table
        TableRow headerRow = new TableRow(requireContext());
        headerRow.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        String[] headers = {"Callsign", "Attempts", "Success Rate", "Avg Time (ms)", "Fastest Time (ms)"};
        for (String header : headers) {
            TextView headerView = new TextView(requireContext());
            headerView.setText(header);
            headerView.setPadding(8, 8, 8, 8);
            headerView.setGravity(Gravity.CENTER);
            headerView.setTextSize(12);
            headerView.setTypeface(null, android.graphics.Typeface.BOLD);
            headerRow.addView(headerView);
        }
        metricsView.addView(headerRow);

        // Sort metrics by performance (worst-performing first)
        List<Map.Entry<String, Integer[]>> sortedMetrics = sortMetricsByPerformance(metrics);

        // Populate the table with metrics
        for (Map.Entry<String, Integer[]> entry : sortedMetrics) {
            TableRow row = new TableRow(requireContext());
            row.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
            ));

            String callsign = entry.getKey();
            Integer[] stats = entry.getValue();
            int attempts = stats[0];
            int correct = stats[1];
            int totalResponseTime = stats[2];
            int fastestResponseTime = stats[3];

            double successRate = (correct / (double) attempts) * 100;
            int averageResponseTime = totalResponseTime / attempts;

            // Add columns to the row
            row.addView(createTextView(callsign));
            row.addView(createTextView(String.valueOf(attempts)));
            row.addView(createTextView(String.format("%.1f%%", successRate)));
            row.addView(createTextView(String.valueOf(averageResponseTime)));
            row.addView(createTextView(String.valueOf(fastestResponseTime)));

            metricsView.addView(row);
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
        textView.setPadding(8, 8, 8, 8);
        textView.setTextSize(14);
        return textView;
    }
}
