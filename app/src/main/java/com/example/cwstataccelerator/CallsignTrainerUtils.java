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
import java.util.Comparator;
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



        // Check if the cache is empty or parameters changed
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


        // Breakdown log map to count callsigns by length
        Map<String, Map<Integer, Integer>> bucketBreakdown = new HashMap<>();
        List<String> availableCallsigns = new ArrayList<>();

        for (String bucket : selectedBuckets) {
            if (bucketCache.containsKey(bucket)) {
                List<String> callsigns = bucketCache.get(bucket);
                availableCallsigns.addAll(callsigns);

                // Log callsigns per bucket
                Map<Integer, Integer> lengthCount = new HashMap<>();
                for (String callsign : callsigns) {
                    int length = callsign.length();
                    lengthCount.put(length, lengthCount.getOrDefault(length, 0) + 1);
                }

                bucketBreakdown.put(bucket, lengthCount);
            }
        }

        // Log breakdown details
        for (Map.Entry<String, Map<Integer, Integer>> entry : bucketBreakdown.entrySet()) {
            String bucketName = entry.getKey();
            Map<Integer, Integer> lengthCount = entry.getValue();

            StringBuilder breakdownLog = new StringBuilder("Bucket: " + bucketName + " -> ");
            for (Map.Entry<Integer, Integer> countEntry : lengthCount.entrySet()) {
                breakdownLog.append("Length ").append(countEntry.getKey()).append(": ").append(countEntry.getValue()).append(", ");
            }
            Log.d("CallsignTrainerUtils", breakdownLog.toString());
        }

        // Return a random callsign
        if (!availableCallsigns.isEmpty()) {
            Random random = new Random();
            return availableCallsigns.get(random.nextInt(availableCallsigns.size()));
        } else {
            Log.w("CallsignTrainerUtils", "No valid callsigns available in the selected buckets.");
            return null;
        }
    }

    public static int[] adjustCallsignLengthRange(Context context, List<String> selectedBuckets, int requestedMin, int requestedMax, boolean isTrainingActive) {
        boolean cacheNeedsUpdate = false;
        boolean standardHasValidCallsigns = false; // Tracks if `standard_callsigns` has callsigns in range

        // Step 1: Check if cache needs updating
        for (String bucket : selectedBuckets) {
            if (!bucketCache.containsKey(bucket) || bucketCache.get(bucket).isEmpty()) {
                cacheNeedsUpdate = true;
                break;
            }
        }

        if (requestedMin != lastMinLength || requestedMax != lastMaxLength) {
            cacheNeedsUpdate = true;
        }

        // Step 2: Update and sort cache if needed
        if (cacheNeedsUpdate || !isTrainingActive) {
            Log.d("CallsignUtils", "Cache needs update. Updating now...");
            boolean updated = updateBucketCache(context, selectedBuckets, requestedMin, requestedMax);
            if (updated) {
                Log.d("CallsignUtils", "Cache updated successfully.");
            } else {
                Log.w("CallsignUtils", "Failed to update cache or no valid callsigns found.");
            }
        } else {
            Log.d("CallsignUtils", "Using existing cached callsigns without updating.");
        }

        // Step 3: Verify callsigns in requested range, prioritize `standard_callsigns`
        int adjustedMin = requestedMin;
        int adjustedMax = requestedMax;
        boolean needsAdjustment = false;
        int totalCallsigns = 0;  // Track total callsigns in final range

        for (String bucket : selectedBuckets) {
            if (!bucketCache.containsKey(bucket) || bucketCache.get(bucket).isEmpty()) {
                Log.w("CallsignUtils", "Bucket " + bucket + " is empty after cache update.");
                continue;
            }

            List<String> callsigns = bucketCache.get(bucket);
            callsigns.sort(Comparator.comparingInt(String::length)); // Ensure sorting

            int minInBucket = Integer.MAX_VALUE;
            int maxInBucket = Integer.MIN_VALUE;
            int validCount = 0;

            for (String callsign : callsigns) {
                int length = callsign.length();
                if (length >= requestedMin && length <= requestedMax) {
                    validCount++;
                }
                minInBucket = Math.min(minInBucket, length);
                maxInBucket = Math.max(maxInBucket, length);
            }

            // Log bucket details
            Log.d("CallsignUtils", "Bucket: " + bucket + " -> Min: " + minInBucket + ", Max: " + maxInBucket + ", Callsigns in range: " + validCount);

            // If the `standard_callsigns` bucket has valid callsigns, stop adjusting
            if (bucket.equals("standard_callsigns") && validCount > 0) {
                standardHasValidCallsigns = true;
            }

            // If bucket has no valid callsigns, adjust range minimally
            if (validCount == 0 && !standardHasValidCallsigns) {
                needsAdjustment = true;
                if (minInBucket > requestedMax) {
                    adjustedMax = Math.min(adjustedMax, minInBucket);
                } else if (maxInBucket < requestedMin) {
                    adjustedMin = Math.max(adjustedMin, maxInBucket);
                }
            } else {
                totalCallsigns += validCount;
            }
        }

        // Step 4: Apply minimal adjustments if needed
        if (needsAdjustment && !standardHasValidCallsigns) {
            Log.w("CallsignUtils", "Requested min/max range (" + requestedMin + "-" + requestedMax + ") does not cover all selected buckets.");
            Log.w("CallsignUtils", "Adjusted range: " + adjustedMin + "-" + adjustedMax);
        } else {
            Log.d("CallsignUtils", "Requested range is valid. No adjustments needed.");
        }

        // Step 5: Ensure cache is re-sorted with final range
        updateBucketCache(context, selectedBuckets, adjustedMin, adjustedMax);
        Log.d("CallsignUtils", "Final cache updated with sorted callsigns for range " + adjustedMin + "-" + adjustedMax);
        Log.d("CallsignUtils", "Total callsigns available in adjusted range: " + totalCallsigns);

        // Step 6: Return adjusted range + callsign count
        return new int[]{adjustedMin, adjustedMax, totalCallsigns};
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

        // Load buckets that are newly selected or need updating
        for (String bucket : selectedBuckets) {
            List<String> bucketCallsigns = loadFilteredBucket(context, bucket, minLength, maxLength);

            if (!bucketCallsigns.isEmpty()) {  // ✅ Ensure the bucket is not empty
                bucketCache.put(bucket, bucketCallsigns);
                cacheUpdated = true;
                Log.d("CallsignTrainerUtils", "Loaded bucket: " + bucket + " with " + bucketCallsigns.size() + " callsigns.");
            } else {
                Log.w("CallsignTrainerUtils", "❗ Bucket " + bucket + " is empty after attempting to load from file.");
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

        // ✅ Load the bucket file using CallsignUtils
        List<String> bucketCallsigns = CallsignUtils.loadBucketFromFile(context, bucket);

        // ✅ Log error if the file does not exist or is empty
        if (bucketCallsigns == null || bucketCallsigns.isEmpty()) {
            Log.e("CallsignTrainerUtils", "⚠️ Bucket file not found or empty: " + bucket);
            return filteredCallsigns;
        }

        // ✅ Filter the callsigns by length
        for (String callsign : bucketCallsigns) {
            if (callsign.length() >= minLength && callsign.length() <= maxLength) {
                filteredCallsigns.add(callsign);
            }
        }

        Log.d("CallsignTrainerUtils", "Bucket " + bucket + " loaded with " + filteredCallsigns.size() + " callsigns after filtering.");

        return filteredCallsigns;
    }

    public static void debugCheckCallsignFiles(Context context) {
        File filesDir = context.getFilesDir();
        File[] files = filesDir.listFiles();

        if (files == null || files.length == 0) {
            Log.e("CallsignTrainerUtils", "❌ No callsign files found in app storage.");
            return;
        }

        Log.d("CallsignTrainerUtils", "📂 Available Callsign Files:");
        for (File file : files) {
            Log.d("CallsignTrainerUtils", " - " + file.getName() + " (Size: " + file.length() + " bytes)");
        }
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
