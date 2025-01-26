package com.example.cwstataccelerator;

import android.os.Bundle;
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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import android.util.Log;
import android.util.TypedValue;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class RecentLogFragment extends Fragment {

    private static final int MAX_ENTRIES = 500; // Max log entries to display
    private TableLayout logView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recent_log, container, false);

        logView = view.findViewById(R.id.log_view);

        // Listen for log updates
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(logUpdateReceiver, new IntentFilter("LOG_UPDATED"));

        // Initialize the log view with existing entries
        updateLogView();

        return view;
    }

    private final BroadcastReceiver logUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Update the log view when a broadcast is received
            updateLogView();
        }
    };


    private TableRow createLogRow(String log) {
        TableRow row = new TableRow(requireContext());
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        // Add log text to the row
        TextView logText = new TextView(requireContext());
        logText.setText(log);
        logText.setPadding(16, 8, 16, 8);

        row.addView(logText);
        return row;
    }

    /**
     * Reads and parses a log file for display.
     *
     * @return A list of parsed log entries, reversed and limited to MAX_ENTRIES.
     */
    private ArrayList<String[]> readLogFile() {
        ArrayList<String[]> entries = new ArrayList<>();
        String logFilePath = requireContext().getFilesDir() + "/single_letter_training_" +
                TrainerUtils.getTodayDate() + ".log";

        try (BufferedReader reader = new BufferedReader(new FileReader(logFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                entries.add(line.split("\\|"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Reverse order and limit to MAX_ENTRIES
        Collections.reverse(entries);
        if (entries.size() > MAX_ENTRIES) {
            return new ArrayList<>(entries.subList(0, MAX_ENTRIES));
        }
        return entries;
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(updateReceiver, new IntentFilter("com.example.cwstataccelerator.UPDATE_LOG"));
    }

    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(logUpdateReceiver);
    }

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateLogView(); // Refresh the log view dynamically
        }
    };

    public void updateLogView() {
        View view = getView(); // Get the root view
        if (view == null) return;

        TableLayout logView = view.findViewById(R.id.log_view);
        // Fetch recent log entries (ensure TrainerUtils.readRecentLogEntries works correctly)
        List<String> logEntries = TrainerUtils.readRecentLogEntries(requireContext(), 500);

        // Ensure the log view is valid and clear any existing rows
        if (logView != null) {
            logView.removeAllViews();
        } else {
            Log.e("RecentLogFragment", "logView is null. Cannot update log.");
            return;
        }

        // Add a header row to the table
        TableRow headerRow = new TableRow(requireContext());
        headerRow.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        // Define column headers based on reordered structure
        String[] headers = {"Tested", "Reply", "Time (ms)", "WPM", "Date"};
        for (String header : headers) {
            TextView headerView = new TextView(requireContext());
            headerView.setText(header);
            headerView.setPadding(8, 8, 8, 8);
            headerView.setTextSize(12);
            headerView.setTypeface(null, android.graphics.Typeface.BOLD); // Make the header bold
            headerRow.addView(headerView);
        }

        // Add the header row to the table
        logView.addView(headerRow);

        // Sort log entries by timestamp using Collections.sort()
        Collections.sort(logEntries, new Comparator<String>() {
            @Override
            public int compare(String entry1, String entry2) {
                String[] parts1 = entry1.split(",");
                String[] parts2 = entry2.split(",");

                if (parts1.length < 6 || parts2.length < 6) {
                    return 0; // Skip sorting if entries don't have enough parts
                }

                String timestamp1 = parts1[5].trim(); // Assuming timestamp is in the last column
                String timestamp2 = parts2[5].trim();

                return timestamp2.compareTo(timestamp1); // Sort in descending order (most recent first)
            }
        });

        // Populate the log view with sorted entries
        for (String logEntry : logEntries) {
            String[] parts = logEntry.split(",");
            if (parts.length < 6) continue;

            String character = parts[0];
            String responseTime = parts[1];
            String correctness = parts[2];
            String typedReply = parts[3];
            String wpm = parts[4];
            String timestamp = parts[5];

            // Create a new row
            TableRow row = new TableRow(requireContext());
            row.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
            ));

// Apply color based on correctness
            if ("1".equals(correctness)) {
                // Get the correct background color from the theme
                TypedValue typedValue = new TypedValue();
                requireContext().getTheme().resolveAttribute(R.attr.correctBackground, typedValue, true);
                row.setBackgroundColor(typedValue.data); // Apply the resolved color
            } else {
                // Get the incorrect background color from the theme
                TypedValue typedValue = new TypedValue();
                requireContext().getTheme().resolveAttribute(R.attr.incorrectBackground, typedValue, true);
                row.setBackgroundColor(typedValue.data); // Apply the resolved color
            }

            // Add columns in the desired order
            row.addView(createTextView(character));       // Character
            row.addView(createTextView(typedReply));      // Typed Character
            row.addView(createTextView(responseTime));    // Response Time
            row.addView(createTextView(wpm));             // WPM
            row.addView(createTextView(timestamp));       // Timestamp

            // Add the row to the table
            logView.addView(row);
        }
    }

    // Helper method to create a styled TextView
    private TextView createTextView(String text) {
        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setPadding(8, 8, 8, 8);
        textView.setTextSize(14);
        return textView;
    }

    private void populateLogView() {
        TableLayout logView = getView().findViewById(R.id.log_view);
        logView.removeAllViews();

        List<String> logs = TrainerUtils.readRecentLogEntries(requireContext(), MAX_ENTRIES);

        for (String log : logs) {
            TableRow row = new TableRow(requireContext());
            TextView textView = new TextView(requireContext());
            textView.setText(log);
            textView.setPadding(8, 8, 8, 8);
            row.addView(textView);
            logView.addView(row);
        }
    }
}
