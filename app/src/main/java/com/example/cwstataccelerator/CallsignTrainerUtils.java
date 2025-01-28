package com.example.cwstataccelerator;

import android.content.Context;
import android.util.Log;

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
import java.util.Random;

public class CallsignTrainerUtils {

    private static final String LOG_DIRECTORY = "callsign_trainer_logs";
    private static final int CACHE_LIMIT = 1000; // Limit the number of entries stored in the cache
    private static final List<String> logCache = new ArrayList<>(); // Cache for log entries
    private static String lastCachedFile = null; // Tracks the last file read

    /**
     * Logs a callsign training result.
     */
    private static final Map<String, List<String>> bucketCache = new HashMap<>();
    private static final Map<String, Boolean> bucketSelectionState = new HashMap<>();
    private static int lastMinLength = -1;
    private static int lastMaxLength = -1;

    /**
     * Fetches a random callsign based on the selected buckets and length range.
     *
     * @param context          Application context.
     * @param selectedBuckets  List of selected bucket names (e.g., "standard_callsigns", "slashes_only").
     * @param minLength        Minimum callsign length.
     * @param maxLength        Maximum callsign length.
     * @return A random callsign from the filtered cache.
     */
    public static String getRandomCallsign(Context context, List<String> selectedBuckets, int minLength, int maxLength, boolean isTrainingActive) {
        // Check if the cache needs to be updated
        boolean cacheNeedsUpdate = false;

        // Check if the cache is empty
        for (String bucket : selectedBuckets) {
            if (!bucketCache.containsKey(bucket) || bucketCache.get(bucket).isEmpty()) {
                cacheNeedsUpdate = true;
                break;
            }
        }

        // Check if the parameters (min/max length) have changed
        if (minLength != lastMinLength || maxLength != lastMaxLength) {
            cacheNeedsUpdate = true;
        }

        // Update the cache if needed
        if (cacheNeedsUpdate || !isTrainingActive) {
            Log.d("CallsignTrainerUtils", "Cache needs update. Updating now...");
            boolean updated = updateBucketCache(context, selectedBuckets, minLength, maxLength);
            if (updated) {
                Log.d("CallsignTrainerUtils", "Cache updated successfully.");
            } else {
                Log.w("CallsignTrainerUtils", "Failed to update cache or no valid callsigns found.");
            }
        } else {
            Log.d("CallsignTrainerUtils", "Using existing cached callsigns without updating.");
        }

        // Gather callsigns from the selected cached buckets
        List<String> availableCallsigns = new ArrayList<>();
        for (String bucket : selectedBuckets) {
            if (bucketCache.containsKey(bucket)) {
                availableCallsigns.addAll(bucketCache.get(bucket));
            }
        }

        // Return a random callsign from the available callsigns
        if (!availableCallsigns.isEmpty()) {
            Random random = new Random();
            return availableCallsigns.get(random.nextInt(availableCallsigns.size()));
        } else {
            Log.w("CallsignTrainerUtils", "No valid callsigns available in the selected buckets.");
            return null; // No valid callsigns available
        }
    }



    /**
     * Updates the bucket cache by loading/unloading buckets based on the selected buckets and length range.
     *
     * @param context         Application context.
     * @param selectedBuckets List of selected bucket names.
     * @param minLength       Minimum callsign length.
     * @param maxLength       Maximum callsign length.
     * @return True if the cache was updated, false otherwise.
     */
    private static boolean updateBucketCache(Context context, List<String> selectedBuckets, int minLength, int maxLength) {
        boolean cacheUpdated = false;

        // Unload buckets that are no longer selected
        for (String bucket : bucketCache.keySet()) {
            if (!selectedBuckets.contains(bucket)) {
                bucketCache.remove(bucket);
                cacheUpdated = true;
                Log.d("CallsignTrainerUtils", "Unloaded bucket: " + bucket);
            }
        }

        // Load buckets that are newly selected or need to be updated
        for (String bucket : selectedBuckets) {
            boolean isNewBucket = !bucketCache.containsKey(bucket);

            if (isNewBucket) {
                List<String> bucketCallsigns = loadFilteredBucket(context, bucket, minLength, maxLength);
                bucketCache.put(bucket, bucketCallsigns);
                cacheUpdated = true;
                Log.d("CallsignTrainerUtils", "Loaded bucket: " + bucket + " with " + bucketCallsigns.size() + " callsigns.");
            }
        }

        // Update the length range state
        lastMinLength = minLength;
        lastMaxLength = maxLength;

        return cacheUpdated;
    }


    /**
     * Loads callsigns from a bucket and filters them based on the length range.
     *
     * @param context   Application context.
     * @param bucket    The name of the bucket to load.
     * @param minLength Minimum callsign length.
     * @param maxLength Maximum callsign length.
     * @return A list of filtered callsigns.
     */
    private static List<String> loadFilteredBucket(Context context, String bucket, int minLength, int maxLength) {
        List<String> filteredCallsigns = new ArrayList<>();
        List<String> bucketCallsigns = CallsignUtils.loadBucketFromFile(context, bucket);

        for (String callsign : bucketCallsigns) {
            if (callsign.length() >= minLength && callsign.length() <= maxLength) {
                filteredCallsigns.add(callsign);
            }
        }

        return filteredCallsigns;
    }

    public static void logResult(Context context, String callsign, String typedResponse, boolean isCorrect,
        int responseTime, int wpm, String bucketName) {
        String logFileName = getTodayDate() + "_callsign.log";

        if (callsign == null || callsign.isEmpty() || typedResponse == null) {
            Log.e("CallsignTrainerUtils", "Invalid callsign or typedResponse.");
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getLogFile(context, logFileName), true))) {
            String logEntry = String.format(
                    "%s,%s,%d,%d,%d,%s,%s",
                    callsign,
                    typedResponse,
                    isCorrect ? 1 : 0,
                    responseTime,
                    wpm,
                    bucketName,
                    getCurrentDateTime()
            );
            writer.write(logEntry);
            writer.newLine();

            // Add the log entry to the cache
            synchronized (logCache) {
                if (logCache.size() >= CACHE_LIMIT) {
                    logCache.remove(logCache.size() - 1); // Remove the oldest entry if the cache is full
                }
                logCache.add(0, logEntry); // Add the new entry at the start
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

    /**
     * Retrieves performance metrics aggregated by callsign.
     */
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
                    boolean isCorrect = "1".equals(parts[2]); // Correctness
                    int responseTime = Integer.parseInt(parts[3]); // Response time

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
