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
    private static final int MAX_ANALYSIS_LIMIT = 50;  // Number of recent callsigns to analyze
    private TableLayout metricsView;
    private TableLayout errorAnalysisView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_performance_metrics, container, false);

        metricsView = view.findViewById(R.id.metrics_view);
        errorAnalysisView = view.findViewById(R.id.metrics_view);  // Add this to your layout

        // Register broadcast receiver
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(updateReceiver, new IntentFilter("com.example.cwstataccelerator.CALLSIGN_UPDATE_METRICS"));

        updateMetricsView();  // Populate metrics initially

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(updateReceiver, new IntentFilter("com.example.cwstataccelerator.CALLSIGN_UPDATE_METRICS"));
        updateMetricsView(); // Refresh metrics
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
            updateMetricsView();  // Refresh dynamically when a new callsign is logged
        }
    };

    public void updateMetricsView() {
        if (metricsView == null || errorAnalysisView == null) return;

        metricsView.removeAllViews();  // Clear existing performance metrics
        errorAnalysisView.removeAllViews();  // Clear existing error analysis

        // Fetch performance metrics
        Map<String, Integer[]> metrics = CallsignTrainerUtils.getPerformanceMetrics(requireContext());

        if (metrics == null || metrics.isEmpty()) {
            addPlaceholderMessage(metricsView, "No performance metrics available. Start training.");
            addPlaceholderMessage(errorAnalysisView, "No error analysis available.");
            return;
        }

        // **Performance Metrics Table**
        addMetricsHeader(metricsView);
        List<Map.Entry<String, Integer[]>> sortedMetrics = sortMetricsByPerformance(metrics);

        for (Map.Entry<String, Integer[]> entry : sortedMetrics) {
            TableRow row = new TableRow(requireContext());

            String callsign = entry.getKey();
            Integer[] stats = entry.getValue();
            int attempts = stats[0];
            int correct = stats[1];
            int totalResponseTime = stats[2];
            int fastestResponseTime = stats[3];

            double successRate = (correct / (double) attempts) * 100;
            int averageResponseTime = totalResponseTime / attempts;

            row.addView(createTextView(callsign));
            row.addView(createTextView(String.valueOf(attempts)));
            row.addView(createTextView(String.format("%.1f%%", successRate)));
            row.addView(createTextView(String.valueOf(averageResponseTime)));
            row.addView(createTextView(String.valueOf(fastestResponseTime)));

            metricsView.addView(row);
        }

        // **Error Analysis**
        List<String[]> lastNCalls = CallsignTrainerUtils.getLastNCallsigns(requireContext(), MAX_ANALYSIS_LIMIT);

        if (lastNCalls.isEmpty()) {
            addPlaceholderMessage(errorAnalysisView, "No error analysis available.");
        } else {
            addErrorAnalysisHeader(errorAnalysisView);
            Map<String, Integer> errorData = CallsignErrorAnalyzer.analyzeErrors(lastNCalls);

            for (Map.Entry<String, Integer> entry : errorData.entrySet()) {
                TableRow row = new TableRow(requireContext());
                row.addView(createTextView(entry.getKey())); // Error Pattern
                row.addView(createTextView(String.valueOf(entry.getValue()))); // Occurrences
                errorAnalysisView.addView(row);
            }
        }
    }

    private void addMetricsHeader(TableLayout table) {
        TableRow headerRow = new TableRow(requireContext());
        String[] headers = {"Callsign", "Attempts", "Success Rate", "Avg Time (ms)", "Fastest Time (ms)"};
        for (String header : headers) {
            headerRow.addView(createTextView(header, true));
        }
        table.addView(headerRow);
    }

    private void addErrorAnalysisHeader(TableLayout table) {
        TableRow headerRow = new TableRow(requireContext());
        headerRow.addView(createTextView("Common Mistakes", true));
        headerRow.addView(createTextView("Occurrences", true));
        table.addView(headerRow);
    }

    private List<Map.Entry<String, Integer[]>> sortMetricsByPerformance(Map<String, Integer[]> metrics) {
        List<Map.Entry<String, Integer[]>> sortedMetrics = new ArrayList<>(metrics.entrySet());
        Collections.sort(sortedMetrics, Comparator.comparingDouble(o -> (o.getValue()[1] / (double) o.getValue()[0])));
        return sortedMetrics;
    }

    private TextView createTextView(String text) {
        return createTextView(text, false);
    }

    private TextView createTextView(String text, boolean isHeader) {
        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setPadding(8, 8, 8, 8);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(isHeader ? 12 : 14);
        if (isHeader) {
            textView.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        return textView;
    }

    private void addPlaceholderMessage(TableLayout table, String message) {
        TableRow placeholderRow = new TableRow(requireContext());
        TextView placeholderText = new TextView(requireContext());
        placeholderText.setText(message);
        placeholderText.setGravity(Gravity.CENTER);
        placeholderText.setPadding(16, 16, 16, 16);
        placeholderRow.addView(placeholderText);
        table.addView(placeholderRow);
    }
}
