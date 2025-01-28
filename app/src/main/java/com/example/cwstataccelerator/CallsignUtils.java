package com.example.cwstataccelerator;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CallsignUtils {

    private static final String MASTER_DTA_URL = "https://www.supercheckpartial.com/MASTER.DTA";
    private static final String DATABASE_DIRECTORY_NAME = "callsign_database";
    private static final String MASTER_DTA_FILE_NAME = "MASTER.DTA";
    private static final String DATABASE_FILE_NAME = "merged_callsigns.txt";
    private static final String TEMP_FILE_PREFIX = "callsign_temp_";
    private static final int MAX_RETRIES = 10; // Max retries for each URL
    private static final int CHUNK_SIZE = 50000; // Number of lines to process in memory at once

    private static String masterDtaVersion;

    /**
     * Updates and sorts the callsign database with fallback handling for unavailable URLs.
     */

    /**
     * Updates and sorts the callsign database with fallback handling for QRZ binary file parsing.
     */
    public static void updateAndSortCallsignDatabase(Context context, ProgressListener listener) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                Log.d("CallsignUtils", "Starting updateAndSortCallsignDatabase in background thread...");
                listener.onProgressUpdate("Initializing...", 0);

                ensureDatabaseDirectoryExists(context);

                // Paths for the local MASTER.DTA and its temp storage
                File localMasterDtaFile = new File(context.getFilesDir(), TEMP_FILE_PREFIX + "master_local.txt");
                boolean localMasterExists = localMasterDtaFile.exists();
                boolean masterDtaUpdated = false;

                // Step 1: Ensure MASTER.DTA exists, extract from res/raw if not found
                if (!localMasterExists) {
                    Log.d("CallsignUtils", "No local MASTER.DTA found. Extracting from res/raw...");
                    listener.onProgressUpdate("Extracting MASTER.DTA from res/raw...", 10);
                    try {
                        extractMasterDtaFromQrz(context, localMasterDtaFile);
                        Log.d("CallsignUtils", "Extracted MASTER.DTA from res/raw successfully.");
                    } catch (Exception e) {
                        Log.e("CallsignUtils", "Failed to extract MASTER.DTA from res/raw.", e);
                        listener.onProgressUpdate("Failed to extract MASTER.DTA. Process aborted.", 100);
                        return;
                    }
                }

                // Step 2: Check for online updates to MASTER.DTA
                listener.onProgressUpdate("Checking for updates to MASTER.DTA...", 20);
                try {
                    masterDtaVersion = fetchMasterDtaVersion(); // Get the online version of MASTER.DTA
                    if (!isFileUpToDate(context, masterDtaVersion)) {
                        Log.d("CallsignUtils", "MASTER.DTA online version is newer. Downloading update...");
                        listener.onProgressUpdate("Downloading updated MASTER.DTA...", 30);
                        File tempMasterFile = new File(context.getFilesDir(), TEMP_FILE_PREFIX + "master_online.txt");
                        if (fetchCallsignsToFileWithRetries(MASTER_DTA_URL, tempMasterFile)) {
                            if (localMasterDtaFile.exists()) localMasterDtaFile.delete(); // Delete old file
                            tempMasterFile.renameTo(localMasterDtaFile); // Replace with updated file
                            masterDtaUpdated = true;
                            Log.d("CallsignUtils", "MASTER.DTA updated successfully.");
                        } else {
                            Log.e("CallsignUtils", "Failed to download updated MASTER.DTA. Using local copy.");
                        }
                    } else {
                        Log.d("CallsignUtils", "MASTER.DTA is already up-to-date.");
                    }
                } catch (IOException e) {
                    Log.e("CallsignUtils", "Failed to fetch MASTER.DTA version. Proceeding with local copy.", e);
                }

                // Step 3: Reset buckets before processing
                listener.onProgressUpdate("Clearing old buckets...", 40);
                boolean resetSuccess = resetBuckets(context); // Clear all old buckets
                if (resetSuccess) {
                    Log.d("CallsignUtils", "Buckets cleared successfully.");
                } else {
                    Log.e("CallsignUtils", "Failed to clear buckets. Proceeding anyway...");
                }

                // Step 4: Parse MASTER.DTA and save into buckets
                listener.onProgressUpdate("Parsing MASTER.DTA and sorting into buckets...", 50);
                Log.d("CallsignUtils", "Parsing MASTER.DTA and sorting into buckets...");
                Map<String, Object> parseResults = parseMasterDtaFile(context, localMasterDtaFile);
                if (parseResults.containsKey("error")) {
                    Log.e("CallsignUtils", "Error during MASTER.DTA parsing: " + parseResults.get("error"));
                    listener.onProgressUpdate("Failed to process MASTER.DTA. Process aborted.", 100);
                    return;
                }

                // Log parsing results
                int totalCallsigns = (int) parseResults.get("total_callsigns");
                List<String> first10 = (List<String>) parseResults.get("first_10_callsigns");
                List<String> last10 = (List<String>) parseResults.get("last_10_callsigns");

                Log.d("CallsignUtils", "Total callsigns parsed: " + totalCallsigns);
                Log.d("CallsignUtils", "First 10 callsigns: " + first10);
                Log.d("CallsignUtils", "Last 10 callsigns: " + last10);

                // Get parsed callsigns
                List<String> parsedCallsigns = (List<String>) parseResults.get("parsed_callsigns");
                if (parsedCallsigns == null || parsedCallsigns.isEmpty()) {
                    Log.e("CallsignUtils", "No callsigns parsed from MASTER.DTA. Aborting bucket sorting.");
                    listener.onProgressUpdate("No callsigns to process. Aborting.", 100);
                    return;
                }

                // Pass parsed callsigns to processAndSaveBuckets
                Log.d("CallsignUtils", "Passing " + parsedCallsigns.size() + " callsigns to processAndSaveBuckets...");
                processAndSaveBuckets(context, parsedCallsigns);

                // Step 5: Finish the process
                listener.onProgressUpdate("Process completed successfully.", 100);
                Log.d("CallsignUtils", masterDtaUpdated
                        ? "MASTER.DTA updated, processed, and sorted successfully."
                        : "MASTER.DTA processed and sorted successfully.");
            } catch (Exception e) {
                Log.e("CallsignUtils", "An error occurred: " + e.getMessage(), e);
                listener.onProgressUpdate("An error occurred: " + e.getMessage(), 100);
            }
        });
    }





    /**
     * Extract MASTER.DTA from QRZ database ZIP file in raw resources.
     */
    private static void extractMasterDtaFromQrz(Context context, File outputFile) throws IOException {
        try (InputStream inputStream = context.getResources().openRawResource(R.raw.qrz_database);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equalsIgnoreCase("MASTER.DTA")) {
                    parseQrzBinaryFile(zipInputStream, outputFile);
                    Log.d("CallsignUtils", "MASTER.DTA extracted and parsed successfully.");
                    return;
                }
            }
            throw new IOException("MASTER.DTA not found in QRZ database ZIP.");
        }
    }

    /**
     * Parses the binary MASTER.DTA file directly from a ZipInputStream.
     *
     * @param zipInputStream The input stream of the zip file containing MASTER.DTA.
     * @param outputFile     The file where extracted callsigns will be saved.
     * @throws IOException If an error occurs during processing.
     */
    private static void parseQrzBinaryFile(ZipInputStream zipInputStream, File outputFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            byte[] buffer = new byte[1024];
            StringBuilder callsign = new StringBuilder();
            int bytesRead;

            while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    byte b = buffer[i];

                    // Check for ASCII characters in the callsign range
                    if (b >= 32 && b <= 126) {
                        callsign.append((char) b);
                    } else if (b == '\0' || b == '\n') { // End of a callsign
                        if (callsign.length() > 0) {
                            writer.write(callsign.toString());
                            writer.newLine();
                            callsign.setLength(0); // Clear buffer for the next callsign
                        }
                    }
                }
            }

            // Write any remaining callsign
            if (callsign.length() > 0) {
                writer.write(callsign.toString());
                writer.newLine();
            }
        }
    }
    /**
     * Parses the MASTER.DTA file, extracts callsigns, and sorts them into buckets.
     *
     * @param context    The application context for accessing storage directories.
     * @param inputFile  The MASTER.DTA file to parse.
     * @throws IOException If an error occurs while reading the file.
     */
    public static Map<String, Object> parseMasterDtaFile(Context context, File masterDtaFile) {
        Map<String, Object> result = new HashMap<>();
        List<String> callsigns = new ArrayList<>();

        if (!masterDtaFile.exists()) {
            Log.e("CallsignUtils", "MASTER.DTA file does not exist in: " + masterDtaFile.getAbsolutePath());
            result.put("error", "MASTER.DTA file not found");
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(masterDtaFile))) {
            StringBuilder currentCallsign = new StringBuilder();
            int charRead;

            // Read file character by character to extract callsigns
            while ((charRead = reader.read()) != -1) {
                char character = (char) charRead;

                if (character >= 32 && character <= 126) { // ASCII printable range
                    currentCallsign.append(character);
                } else if (currentCallsign.length() > 0) { // Callsign delimiter (space or non-printable char)
                    String callsign = currentCallsign.toString().trim();
                    if (isValidCallsign(callsign)) { // Validate the callsign
                        callsigns.add(callsign);
                    }
                    currentCallsign.setLength(0); // Clear the buffer
                }
            }

            // Add the last callsign if available
            if (currentCallsign.length() > 0) {
                String callsign = currentCallsign.toString().trim();
                if (isValidCallsign(callsign)) {
                    callsigns.add(callsign);
                }
            }

            // Populate results
            result.put("total_callsigns", callsigns.size());
            result.put("first_10_callsigns", callsigns.subList(0, Math.min(10, callsigns.size())));
            result.put("last_10_callsigns", callsigns.subList(Math.max(callsigns.size() - 10, 0), callsigns.size()));
            result.put("parsed_callsigns", callsigns); // Include the full list of callsigns

        } catch (IOException e) {
            Log.e("CallsignUtils", "Error reading MASTER.DTA file: " + e.getMessage(), e);
            result.put("error", "Error reading MASTER.DTA file");
        }

        return result;
    }




    /**
     * Determines if a string is a valid callsign.
     *
     * @param callsign The string to validate.
     * @return True if the string is a valid callsign, false otherwise.
     */
    private static boolean isValidCallsign(String callsign) {
        // Updated regex to accept all possible legal amateur radio callsigns

        // Check if the callsign length is valid
        if (callsign.length() <= 2) {
            return false; // Callsigns shorter than or equal to 2 characters are invalid
        }

        // Match the callsign pattern
        return callsign.matches("^(?:[A-Z]{1,2}/)?[A-Z]{1,2}\\d{1,4}[A-Z0-9]*(?:/[A-Z]{1,3}|/\\d{1,2})?$");
    }

    /**
     * Interface for updating progress in the UI.
     */
    public interface ProgressListener {
        void onProgressUpdate(String message, int progress);
    }

    /**
     * Ensures the database directory exists.
     */
    private static void ensureDatabaseDirectoryExists(Context context) {
        File databaseDir = new File(context.getFilesDir(), DATABASE_DIRECTORY_NAME);
        if (!databaseDir.exists()) {
            databaseDir.mkdir();
        }
    }

    /**
     * Fetch callsigns from a URL with retries.
     */
    private static boolean fetchCallsignsToFileWithRetries(String url, File outputFile) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Log.d("CallsignUtils", "Attempting to fetch from " + url + " (Attempt " + attempt + ")...");
                fetchCallsignsToFile(url, outputFile);
                return true;
            } catch (IOException e) {
                Log.e("CallsignUtils", "Failed to fetch from " + url + " on attempt " + attempt, e);
            }
        }
        return false;
    }

    private static void fetchCallsignsToFile(String url, File outputFile) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line.trim());
                    writer.newLine();
                }
            }
        } else {
            throw new IOException("Failed to fetch data from " + url);
        }
    }

    /**
     * Gets the total number of callsigns in all buckets.
     *
     * @param context Application context.
     * @return The total number of callsigns across all bucket files.
     */
    public static int getTotalCallsignsInDatabase(Context context) {
        File bucketDir = new File(context.getFilesDir(), BUCKET_DIRECTORY);
        int totalCallsigns = 0;

        if (!bucketDir.exists() || !bucketDir.isDirectory()) {
            Log.w("CallsignUtils", "Bucket directory does not exist. Returning 0 callsigns.");
            return 0;
        }

        File[] bucketFiles = bucketDir.listFiles();
        if (bucketFiles == null || bucketFiles.length == 0) {
            Log.w("CallsignUtils", "No bucket files found in bucket directory. Returning 0 callsigns.");
            return 0;
        }

        // Iterate through each bucket file
        for (File bucketFile : bucketFiles) {
            if (bucketFile.isFile() && bucketFile.getName().endsWith(".txt")) {
                int bucketCount = 0;
                try (BufferedReader reader = new BufferedReader(new FileReader(bucketFile))) {
                    while (reader.readLine() != null) {
                        bucketCount++;
                    }
                    totalCallsigns += bucketCount;
                } catch (IOException e) {
                    Log.e("CallsignUtils", "Error reading bucket file: " + bucketFile.getName(), e);
                }
            }
        }

        // Log the total callsigns across all buckets
        Log.d("CallsignUtils", "Total callsigns across all buckets: " + totalCallsigns);
        return totalCallsigns;
    }



    /**
     * Checks if the MASTER.DTA file is up-to-date.
     */
    private static boolean isFileUpToDate(Context context, String version) {
        File databaseFile = new File(context.getFilesDir(), DATABASE_DIRECTORY_NAME + "/" + version + "_" + DATABASE_FILE_NAME);
        return databaseFile.exists();
    }

    /**
     * Fetches the version identifier from MASTER.DTA using HTTP HEAD.
     */
    private static String fetchMasterDtaVersion() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(MASTER_DTA_URL).openConnection();
        connection.setRequestMethod("HEAD");

        String lastModified = connection.getHeaderField("Last-Modified");
        if (lastModified != null) {
            return "VER" + lastModified.replace(" ", "").replace(":", "").replace("GMT", "");
        }

        throw new IOException("Unable to determine MASTER.DTA version.");
    }

    /**
     * Deletes all bucket files and resets the bucket directory.
     *
     * @param context Application context.
     * @return True if all buckets were successfully deleted, false otherwise.
     */
    public static boolean resetBuckets(Context context) {
        File bucketDir = new File(context.getFilesDir(), BUCKET_DIRECTORY);

        // Check if the bucket directory exists
        if (!bucketDir.exists() || !bucketDir.isDirectory()) {
            Log.w("CallsignUtils", "Bucket directory does not exist. Nothing to reset.");
            return false; // Nothing to reset
        }

        File[] bucketFiles = bucketDir.listFiles();
        if (bucketFiles == null || bucketFiles.length == 0) {
            Log.w("CallsignUtils", "No bucket files found in bucket directory. Nothing to reset.");
            return true; // No buckets to clear
        }

        boolean allCleared = true;

        // Clear the content of each bucket file
        for (File bucketFile : bucketFiles) {
            if (bucketFile.isFile() && bucketFile.getName().endsWith(".txt")) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(bucketFile, false))) {
                    // Open the file in overwrite mode to clear its content
                    writer.write("");
                    Log.d("CallsignUtils", "Cleared bucket file: " + bucketFile.getName());
                } catch (IOException e) {
                    Log.e("CallsignUtils", "Failed to clear bucket file: " + bucketFile.getName(), e);
                    allCleared = false; // Mark failure if any file fails to clear
                }
            }
        }

        // Log the result
        if (allCleared) {
            Log.d("CallsignUtils", "All bucket files cleared successfully.");
        } else {
            Log.e("CallsignUtils", "Some bucket files could not be cleared. Check logs for details.");
        }

        return allCleared;
    }






    private static final String BUCKET_DIRECTORY = "callsign_buckets";
    private static final int MAX_CACHE_SIZE = 500000; // Maximum callsigns in cache
    private static Map<String, List<String>> bucketCache = new HashMap<>();
    private static int currentCacheSize = 0;


    /**
     * Sorts callsigns into predefined buckets based on their characteristics.
     *
     * @param callsigns List of callsigns to be sorted.
     * @return A map of bucket names to lists of callsigns.
     */
    public static Map<String, List<String>> sortCallsignsIntoBuckets(List<String> callsigns) {
        // Check if the callsigns list is null or empty
        if (callsigns == null || callsigns.isEmpty()) {
            Log.e("CallsignUtils", "No callsigns provided to sort into buckets.");
            return new HashMap<>(); // Return an empty map to avoid further errors
        }

        // Define the buckets for all combinations
        Map<String, List<String>> buckets = new HashMap<>();
        buckets.put("slashes_only", new ArrayList<>());
        buckets.put("difficult_numbers", new ArrayList<>());
        buckets.put("difficult_letters", new ArrayList<>());
        buckets.put("slashes_and_numbers", new ArrayList<>());
        buckets.put("slashes_and_letters", new ArrayList<>());
        buckets.put("numbers_and_letters", new ArrayList<>());
        buckets.put("all_criteria", new ArrayList<>());
        buckets.put("standard_callsigns", new ArrayList<>()); // Standard callsigns with no special features

        // Define difficult patterns
        List<String> difficultPatterns = List.of("HH", "RR", "SS", "LZ", "XZ", "YZ", "JW", "EE", "IE");

        // Process each callsign
        for (String callsign : callsigns) {
            boolean isSlashed = callsign.contains("/");
            boolean hasDifficultNumbers = callsign.matches(".*[A-Z]+\\d+[A-Z]+.*") || callsign.matches("^\\d.*");
            boolean hasDifficultLetters = false;

            // Check for difficult letter combinations
            for (String pattern : difficultPatterns) {
                if (callsign.contains(pattern)) {
                    hasDifficultLetters = true;
                    break;
                }
            }

            // Determine the bucket based on characteristics
            if (isSlashed && hasDifficultNumbers && hasDifficultLetters) {
                buckets.get("all_criteria").add(callsign);
            } else if (isSlashed && hasDifficultNumbers) {
                buckets.get("slashes_and_numbers").add(callsign);
            } else if (isSlashed && hasDifficultLetters) {
                buckets.get("slashes_and_letters").add(callsign);
            } else if (hasDifficultNumbers && hasDifficultLetters) {
                buckets.get("numbers_and_letters").add(callsign);
            } else if (isSlashed) {
                buckets.get("slashes_only").add(callsign);
            } else if (hasDifficultNumbers) {
                buckets.get("difficult_numbers").add(callsign);
            } else if (hasDifficultLetters) {
                buckets.get("difficult_letters").add(callsign);
            } else {
                buckets.get("standard_callsigns").add(callsign); // Add to fallback bucket if no characteristics match
            }
        }

        return buckets;
    }



    /**
     * Saves sorted buckets to files for future use.
     *
     * @param context  Application context.
     * @param buckets  Map of bucket names to callsign lists.
     */
    public static void saveBucketsToFiles(Context context, Map<String, List<String>> buckets) {
        File bucketDir = new File(context.getFilesDir(), BUCKET_DIRECTORY);
        if (!bucketDir.exists() && !bucketDir.mkdirs()) {
            throw new RuntimeException("Failed to create bucket directory.");
        }

        for (Map.Entry<String, List<String>> entry : buckets.entrySet()) {
            String bucketName = entry.getKey();
            List<String> callsigns = entry.getValue();
            File bucketFile = new File(bucketDir, bucketName + ".txt");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(bucketFile, true))) { // 'true' to append
                for (String callsign : callsigns) {
                    writer.write(callsign);
                    writer.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void processAndSaveBuckets(Context context, List<String> callsignChunk) {
        // Sort the callsigns into buckets
        Map<String, List<String>> buckets = sortCallsignsIntoBuckets(callsignChunk);

        // Log bucket details before saving
        Log.d("CallsignUtils", "Bucket statistics:");
        for (Map.Entry<String, List<String>> entry : buckets.entrySet()) {
            Log.d("CallsignUtils", "Bucket: " + entry.getKey() + " -> " + entry.getValue().size() + " callsigns.");
        }

        // Save the buckets to files
        saveBucketsToFiles(context, buckets);
    }

    /**
     * Adds a bucket to the cache.
     *
     * @param bucketName The name of the bucket.
     * @param callsigns  The list of callsigns for this bucket.
     */
    public static void addBucketToCache(String bucketName, List<String> callsigns) {
        if (!bucketCache.containsKey(bucketName)) {
            bucketCache.put(bucketName, new ArrayList<>());
        }

        List<String> cachedBucket = bucketCache.get(bucketName);
        for (String callsign : callsigns) {
            if (currentCacheSize >= MAX_CACHE_SIZE) {
                evictOldestBucket(); // Evict the oldest bucket if cache size exceeds limit
            }
            cachedBucket.add(callsign);
            currentCacheSize++;
        }
    }

    /**
     * Retrieves a bucket from the cache.
     *
     * @param bucketName The name of the bucket.
     * @return A list of callsigns in the bucket, or an empty list if not found.
     */
    public static List<String> getBucketFromCache(String bucketName) {
        return bucketCache.getOrDefault(bucketName, new ArrayList<>());
    }

    /**
     * Clears a specific bucket from the cache.
     *
     * @param bucketName The name of the bucket to clear.
     */
    public static void clearBucketFromCache(String bucketName) {
        if (bucketCache.containsKey(bucketName)) {
            currentCacheSize -= bucketCache.get(bucketName).size();
            bucketCache.remove(bucketName);
        }
    }

    /**
     * Clears all buckets from the cache.
     */
    public static void clearAllBucketsFromCache() {
        bucketCache.clear();
        currentCacheSize = 0;
    }

    /**
     * Evicts the oldest bucket from the cache to free up space.
     */
    private static void evictOldestBucket() {
        String oldestBucket = null;
        for (String bucketName : bucketCache.keySet()) {
            oldestBucket = bucketName;
            break;
        }

        if (oldestBucket != null) {
            currentCacheSize -= bucketCache.get(oldestBucket).size();
            bucketCache.remove(oldestBucket);
        }
    }

    public static List<String> loadBucketFromFile(Context context, String bucketName) {
        File bucketDir = new File(context.getFilesDir(), BUCKET_DIRECTORY);
        File bucketFile = new File(bucketDir, bucketName + ".txt");
        List<String> callsigns = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(bucketFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                callsigns.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return callsigns;
    }

    /**
     * Loads buckets from files into the cache.
     *
     * @param context Application context.
     */
    public static void loadBucketsIntoCache(Context context) {
        File bucketDir = new File(context.getFilesDir(), BUCKET_DIRECTORY);
        if (!bucketDir.exists() || !bucketDir.isDirectory()) {
            throw new RuntimeException("Callsign bucket directory not found.");
        }

        File[] bucketFiles = bucketDir.listFiles();
        if (bucketFiles == null || bucketFiles.length == 0) {
            throw new RuntimeException("No buckets found in the bucket directory.");
        }

        for (File file : bucketFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String bucketName = file.getName().replace(".txt", "");
                List<String> callsigns = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    callsigns.add(line.trim());
                }
                addBucketToCache(bucketName, callsigns);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
