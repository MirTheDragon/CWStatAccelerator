package com.example.cwstataccelerator;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogCache {
    private static final int MAX_ENTRIES = 500; // Maximum entries per log type
    private static final Map<String, List<String>> cachedLogs = new HashMap<>();

    /**
     * Retrieves logs for a specific type.
     *
     * @param logType The type of log to retrieve (e.g., "callsign", "character").
     * @return A list of logs for the specified type.
     */
    public static List<String> getLogs(String logType) {
        List<String> logs = cachedLogs.getOrDefault(logType, new ArrayList<>());
        return new ArrayList<>(logs); // Return a copy to avoid external modification
    }

    /**
     * Adds a log entry for a specific type.
     *
     * @param logType  The type of log (e.g., "callsign", "single_character").
     * @param logEntry The log entry to add.
     */
    public static void addLog(String logType, String logEntry) {
        cachedLogs.putIfAbsent(logType, new ArrayList<>()); // Initialize if not already present
        List<String> logs = cachedLogs.get(logType);

        if (logs.size() >= MAX_ENTRIES) {
            logs.remove(0); // Remove the oldest entry if at capacity
        }
        logs.add(logEntry);
    }

    /**
     * Loads logs into the cache for a specific type.
     *
     * @param context  Application context.
     * @param logType  The type of log to load (e.g., "callsign", "single_character").
     * @param loader   A method to load logs from storage.
     */
    public static void loadLogs(Context context, String logType, LogLoader loader) {
        cachedLogs.put(logType, new ArrayList<>()); // Clear the cache for this log type
        List<String> recentLogs = loader.loadLogs(context, MAX_ENTRIES);
        cachedLogs.get(logType).addAll(recentLogs);
    }

    /**
     * Interface to load logs dynamically.
     */
    public interface LogLoader {
        List<String> loadLogs(Context context, int maxEntries);
    }
}
