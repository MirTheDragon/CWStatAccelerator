package com.example.cwstataccelerator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class HomeFragment extends Fragment {

    private TextView databaseStatusMessage;
    private ProgressBar databaseUpdateProgress;
    private TextView databaseStats;
    private TextView bucketAnalysisTextView;

    private final BroadcastReceiver databaseUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.cwstataccelerator.DATABASE_UPDATED".equals(intent.getAction())) {
                updateDetailedAnalysis();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize UI elements
        databaseStatusMessage = view.findViewById(R.id.database_status_message);
        databaseUpdateProgress = view.findViewById(R.id.database_update_progress);
        databaseStats = view.findViewById(R.id.database_stats);
        bucketAnalysisTextView = view.findViewById(R.id.bucket_summary); // ✅ Make sure this is initialized


        // Register Broadcast Receiver
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                databaseUpdateReceiver,
                new IntentFilter("com.example.cwstataccelerator.DATABASE_UPDATED")
        );

        // Check and update the database
        checkAndUpdateDatabase();

        // Initial fetch for detailed analysis
        updateDetailedAnalysis();

        return view;
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(databaseUpdateReceiver);
    }
    private void checkAndUpdateDatabase() {
        databaseStatusMessage.setText("Checking database status...");
        databaseUpdateProgress.setVisibility(View.VISIBLE);

        CallsignUtils.updateAndSortCallsignDatabase(requireContext(), new CallsignUtils.ProgressListener() {
            @Override
            public void onProgressUpdate(String message, int progress) {
                requireActivity().runOnUiThread(() -> {
                    databaseStatusMessage.setText(message);
                    if (progress == 100) {
                        databaseUpdateProgress.setVisibility(View.GONE);
                        showDatabaseStats();
                    }
                });
            }
        });
    }

    private void showDatabaseStats() {
        int totalCallsigns = CallsignUtils.getTotalCallsignsInDatabase(requireContext());
        databaseStats.setText("Database Stats:\nTotal Callsigns: " + totalCallsigns);
    }

    private void updateDetailedAnalysis() {
        requireActivity().runOnUiThread(() -> {
            String detailedAnalysis = CallsignUtils.getDetailedBucketAnalysis(requireContext());
            bucketAnalysisTextView.setText(detailedAnalysis);
        });
    }
}
