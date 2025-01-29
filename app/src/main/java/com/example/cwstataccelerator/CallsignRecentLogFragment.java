package com.example.cwstataccelerator;

import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CallsignRecentLogFragment extends Fragment {

    private static final int MAX_ENTRIES = 500; // Max log entries to display

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recent_log, container, false);

        // Register broadcast receiver for updates
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                logUpdateReceiver,
                new IntentFilter("com.example.cwstataccelerator.CALLSIGN_LOG_UPDATED")
        );

        // Populate logs on creation
        updateLogView();

        return view;
    }

    private final BroadcastReceiver logUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateLogView(); // Refresh logs dynamically when a broadcast is received
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        updateLogView(); // Refresh logs when the fragment is visible
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(logUpdateReceiver);
    }

    public void updateLogView() {
        View view = getView();
        if (view == null) return;

        TableLayout logTable = view.findViewById(R.id.log_view);
        logTable.removeAllViews(); // Clear previous logs

        // Fetch recent logs from CallsignTrainerUtils
        List<String> logEntries = CallsignTrainerUtils.readRecentLogEntries(requireContext(), MAX_ENTRIES);

        if (logEntries == null || logEntries.isEmpty()) {
            // Show "No logs available" message
            TableRow placeholderRow = new TableRow(requireContext());
            TextView placeholderText = new TextView(requireContext());
            placeholderText.setText("No recent logs available. Start training to generate logs.");
            placeholderText.setGravity(Gravity.CENTER);
            placeholderText.setPadding(16, 16, 16, 16);
            placeholderRow.addView(placeholderText);
            logTable.addView(placeholderRow);
            return;
        }

        // Define the headers (only keeping relevant columns)
        String[] headers = {"Callsign", "Typed Response", "Response Time (ms)", "WPM", "Timestamp"};
        int[] columnIndices = {0, 1, 3, 4, 6}; // Corresponding indices in logEntry format

        // Add a header row
        TableRow headerRow = new TableRow(requireContext());
        headerRow.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        for (String header : headers) {
            TextView headerView = new TextView(requireContext());
            headerView.setText(header);
            headerView.setPadding(8, 8, 8, 8);
            headerView.setTextSize(12);
            headerView.setGravity(Gravity.CENTER);
            headerView.setTypeface(null, android.graphics.Typeface.BOLD);
            headerRow.addView(headerView);
        }
        logTable.addView(headerRow);

        // Sort logs by timestamp (descending)
        List<String[]> parsedLogs = new ArrayList<>();
        for (String log : logEntries) {
            parsedLogs.add(log.split(","));
        }
        parsedLogs.sort((log1, log2) -> log2[6].compareTo(log1[6])); // Compare by timestamp (column 6)

        // Display logs
        for (String[] logEntry : parsedLogs) {
            TableRow row = new TableRow(requireContext());
            row.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
            ));

            // Highlight row based on correctness
            boolean isCorrect = logEntry[2].equals("1");
            TypedValue typedValue = new TypedValue();
            if (isCorrect) {
                requireContext().getTheme().resolveAttribute(R.attr.correctBackground, typedValue, true);
            } else {
                requireContext().getTheme().resolveAttribute(R.attr.incorrectBackground, typedValue, true);
            }
            row.setBackgroundColor(typedValue.data);

            // Populate row with only selected columns
            for (int index : columnIndices) {
                TextView textView = new TextView(requireContext());
                textView.setText(logEntry[index].trim());
                textView.setPadding(8, 8, 8, 8);
                row.addView(textView);
            }

            logTable.addView(row);
        }
    }
}
