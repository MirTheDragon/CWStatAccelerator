package com.example.cwstataccelerator;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class LogCache {
    private static final int MAX_ENTRIES = 500;
    private static final List<String> cachedLogs = new ArrayList<>();

    public static List<String> getLogs() {
        return new ArrayList<>(cachedLogs); // Return a copy to avoid external modification
    }

    public static void addLog(String logEntry) {
        if (cachedLogs.size() >= MAX_ENTRIES) {
            cachedLogs.remove(0); // Remove the oldest entry if at capacity
        }
        cachedLogs.add(logEntry);
    }

    public static void loadLogs(Context context) {
        cachedLogs.clear(); // Clear any existing cache
        List<String> recentLogs = TrainerUtils.readRecentLogEntries(context, MAX_ENTRIES);
        cachedLogs.addAll(recentLogs);
    }
}
