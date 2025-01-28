package com.example.cwstataccelerator;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize UI elements
        databaseStatusMessage = view.findViewById(R.id.database_status_message);
        databaseUpdateProgress = view.findViewById(R.id.database_update_progress);
        databaseStats = view.findViewById(R.id.database_stats);

        // Check and update the database
        checkAndUpdateDatabase();

        return view;
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

}
