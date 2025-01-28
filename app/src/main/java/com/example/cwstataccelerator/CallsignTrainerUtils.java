package com.example.cwstataccelerator;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class CallsignTrainerUtils {

    private static final String LOG_DIRECTORY = "callsign_trainer_logs";
    private static final int CACHE_LIMIT = 1000; // Limit the number of entries stored in the cache
    private static List<String> logCache = new ArrayList<>(); // Cache for log entries
    private static String lastCachedFile = null; // Tracks the last file read

    /**
     * Logs a callsign training result.
     */
    public static void logResult(Context context, String callsign, int responseTime, boolean isCorrect,
                                 String typedResponse, int wpm, String bucketName) {
        String logFileName = getTodayDate() + "_callsign.log";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getLogFile(context, logFileName), true))) {
            String logEntry = String.format(
                    "%s,%d,%d,%s,%d,%s,%s",
                    callsign,
                    responseTime,
                    isCorrect ? 1 : 0,
                    typedResponse,
                    wpm,
                    bucketName,
                    getCurrentDateTime()
            );
            writer.write(logEntry);
            writer.newLine();

            // Add the log entry to the cache
            synchronized (logCache) {
                if (logCache.size() < CACHE_LIMIT) {
                    logCache.add(0, logEntry); // Add to the start of the cache
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads recent callsign log entries with caching support.
     */
    public static List<String> readRecentLogEntries(Context context, int maxEntries) {
        synchronized (logCache) {
            if (logCache.size() >= maxEntries) {
                return new ArrayList<>(logCache.subList(0, maxEntries)); // Return from cache if enough entries exist
            }
        }

        File logDir = new File(context.getFilesDir(), LOG_DIRECTORY);
        if (!logDir.exists() || !logDir.isDirectory()) {
            return new ArrayList<>(); // Return empty list if log directory doesn't exist
        }

        File[] logFiles = logDir.listFiles();
        if (logFiles == null || logFiles.length == 0) {
            return new ArrayList<>(); // Return empty list if no log files are present
        }

        List<File> sortedLogFiles = new ArrayList<>();
        Collections.addAll(sortedLogFiles, logFiles);
        sortedLogFiles.sort((f1, f2) -> f2.getName().compareTo(f1.getName())); // Sort by newest first

        // Retrieve logs from files
        try {
            List<String> fetchedLogs = new ArrayList<>();
            for (File logFile : sortedLogFiles) {
                if (lastCachedFile != null && logFile.getName().equals(lastCachedFile)) {
                    continue; // Skip already cached files
                }

                BufferedReader reader = new BufferedReader(new FileReader(logFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    fetchedLogs.add(line);

                    synchronized (logCache) {
                        if (logCache.size() < CACHE_LIMIT) {
                            logCache.add(line);
                        }
                    }

                    if (fetchedLogs.size() + logCache.size() >= maxEntries) {
                        reader.close();
                        lastCachedFile = logFile.getName();
                        fetchedLogs.addAll(logCache); // Add cached entries
                        return fetchedLogs.subList(0, maxEntries); // Limit results to maxEntries
                    }
                }
                reader.close();

                // Update the last cached file
                lastCachedFile = logFile.getName();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        synchronized (logCache) {
            return new ArrayList<>(logCache.subList(0, Math.min(logCache.size(), maxEntries))); // Return available cache
        }
    }

    /**
     * Retrieves the log file for the current day or creates it if it doesn't exist.
     */
    private static File getLogFile(Context context, String logFileName) throws IOException {
        File logDir = new File(context.getFilesDir(), LOG_DIRECTORY);
        if (!logDir.exists() && !logDir.mkdirs()) {
            throw new IOException("Failed to create log directory.");
        }
        File logFile = new File(logDir, logFileName);
        if (!logFile.exists() && !logFile.createNewFile()) {
            throw new IOException("Failed to create log file.");
        }
        return logFile;
    }

    /**
     * Gets the current date in "yyyy-MM-dd" format.
     */
    private static String getTodayDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * Gets the current date and time in "yyyy-MM-dd HH:mm:ss" format.
     */
    private static String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    public static Map<String, Integer[]> getPerformanceMetrics(Context context) {
        Map<String, Integer[]> metrics = new HashMap<>();
        File logDir = new File(context.getFilesDir(), LOG_DIRECTORY);

        if (!logDir.exists() || !logDir.isDirectory()) {
            return metrics; // Return empty metrics if no log directory exists
        }

        File[] logFiles = logDir.listFiles();
        if (logFiles == null || logFiles.length == 0) {
            return metrics; // Return empty metrics if no log files exist
        }

        // Iterate over log files
        for (File logFile : logFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length < 7) {
                        continue; // Skip malformed entries
                    }

                    String callsign = parts[0]; // Callsign
                    int responseTime = Integer.parseInt(parts[1]); // Response time
                    boolean isCorrect = "1".equals(parts[2]); // Correctness

                    // Retrieve or initialize metrics for the callsign
                    Integer[] stats = metrics.getOrDefault(callsign, new Integer[]{0, 0, 0, Integer.MAX_VALUE});
                    stats[0]++; // Total attempts
                    if (isCorrect) {
                        stats[1]++; // Total correct
                    }
                    stats[2] += responseTime; // Accumulate total response times
                    stats[3] = Math.min(stats[3], responseTime); // Update fastest response time

                    metrics.put(callsign, stats); // Save metrics back to the map
                }
            } catch (IOException | NumberFormatException e) {
                e.printStackTrace();
            }
        }

        return metrics;
    }

}
