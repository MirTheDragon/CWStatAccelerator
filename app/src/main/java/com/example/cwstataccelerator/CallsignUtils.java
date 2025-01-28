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
import org.json.JSONObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.json.JSONArray;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
        final String metaFileName = "master_dta_meta.json";
        final String hardcodedOnlineDate = "2025-01-02"; // Hardcoded for future reference

        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                Log.d("CallsignUtils", "Starting updateAndSortCallsignDatabase in background thread...");
                listener.onProgressUpdate("Initializing...", 0);

                ensureDatabaseDirectoryExists(context);

                File localMasterDtaFile = new File(context.getFilesDir(), MASTER_DTA_FILE_NAME);
                File metaFile = new File(context.getFilesDir(), metaFileName);

                // Step 1: Load meta file for local state
                String localDate = null;
                String localChecksum = null;
                String bucketChecksum = null;

                if (metaFile.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(metaFile))) {
                        JSONObject metaJson = new JSONObject(reader.readLine());
                        localDate = metaJson.optString("date", null);
                        localChecksum = metaJson.optString("checksum", null);
                        bucketChecksum = metaJson.optString("bucket_checksum", null);
                    }
                }

                // Step 2: Fetch the online MASTER.DTA date
                listener.onProgressUpdate("Checking for updates to MASTER.DTA...", 20);
                String onlineDate = fetchMasterDtaDateFromHtml();

                if (onlineDate == null) {
                    Log.w("CallsignUtils", "Failed to fetch MASTER.DTA date from HTML. Using hardcoded date.");
                    onlineDate = hardcodedOnlineDate; // Default to hardcoded date
                }

                boolean isMasterDtaUpToDate = localDate != null && onlineDate.equals(localDate);
                boolean checksumFallback = false;

                // Step 3: Decide if we need to update MASTER.DTA
                if (!isMasterDtaUpToDate) {
                    Log.d("CallsignUtils", "MASTER.DTA date mismatch. Falling back to checksum validation...");
                    if (localMasterDtaFile.exists()) {
                        String newChecksum = calculateChecksum(localMasterDtaFile);
                        checksumFallback = localChecksum != null && localChecksum.equals(newChecksum);

                        if (checksumFallback) {
                            Log.d("CallsignUtils", "Checksum validation confirms MASTER.DTA is up-to-date.");
                            isMasterDtaUpToDate = true;
                        }
                    }
                }

                // If MASTER.DTA is up-to-date, check if buckets need rebuilding
                if (isMasterDtaUpToDate) {
                    if (bucketChecksum != null && bucketChecksum.equals(localChecksum)) {
                        Log.d("CallsignUtils", "Buckets are up-to-date. No processing required.");
                        listener.onProgressUpdate("Buckets are already up-to-date.", 100);
                        return;
                    }
                }

                // Step 4: Download updated MASTER.DTA if needed
                if (!isMasterDtaUpToDate) {
                    listener.onProgressUpdate("Downloading MASTER.DTA...", 30);
                    File tempMasterFile = new File(context.getFilesDir(), TEMP_FILE_PREFIX + "master_online.txt");
                    downloadFile(MASTER_DTA_URL, tempMasterFile);

                    // Calculate checksum of the new file
                    String newChecksum = calculateChecksum(tempMasterFile);

                    // Replace the local MASTER.DTA with the new one
                    if (localMasterDtaFile.exists()) localMasterDtaFile.delete();
                    tempMasterFile.renameTo(localMasterDtaFile);

                    // Update meta file with new date and checksum
                    localDate = onlineDate;
                    localChecksum = newChecksum;

                    Log.d("CallsignUtils", "MASTER.DTA updated successfully.");
                    listener.onProgressUpdate("MASTER.DTA updated successfully.", 50);
                }

                // Step 5: Reset buckets if required
                listener.onProgressUpdate("Clearing old buckets...", 60);
                boolean resetSuccess = resetBuckets(context);

                if (resetSuccess) {
                    Log.d("CallsignUtils", "Buckets cleared successfully.");
                } else {
                    Log.e("CallsignUtils", "Failed to clear buckets. Proceeding anyway...");
                }

                // Step 6: Parse MASTER.DTA and save into buckets
                listener.onProgressUpdate("Parsing MASTER.DTA and sorting into buckets...", 70);
                Map<String, Object> parseResults = parseMasterDtaFile(context, localMasterDtaFile);
                if (parseResults.containsKey("error")) {
                    Log.e("CallsignUtils", "Error during MASTER.DTA parsing: " + parseResults.get("error"));
                    listener.onProgressUpdate("Failed to process MASTER.DTA. Process aborted.", 100);
                    return;
                }

                List<String> parsedCallsigns = (List<String>) parseResults.get("parsed_callsigns");
                if (parsedCallsigns == null || parsedCallsigns.isEmpty()) {
                    Log.e("CallsignUtils", "No callsigns parsed from MASTER.DTA. Aborting bucket sorting.");
                    listener.onProgressUpdate("No callsigns to process. Aborting.", 100);
                    return;
                }

                Log.d("CallsignUtils", "Passing " + parsedCallsigns.size() + " callsigns to processAndSaveBuckets...");
                processAndSaveBuckets(context, parsedCallsigns);

                // Step 7: Update the meta file with new bucket version
                JSONObject metaJson = new JSONObject();
                metaJson.put("date", localDate);
                metaJson.put("checksum", localChecksum);
                metaJson.put("bucket_checksum", localChecksum); // Use the MASTER.DTA checksum for buckets

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(metaFile))) {
                    writer.write(metaJson.toString());
                }

                listener.onProgressUpdate("Buckets processed and saved successfully.", 100);
                Log.d("CallsignUtils", "Buckets processed and saved successfully.");
            } catch (Exception e) {
                Log.e("CallsignUtils", "An error occurred: " + e.getMessage(), e);
                listener.onProgressUpdate("An error occurred: " + e.getMessage(), 100);
            }
        });
    }


    public static void updateMasterDta(Context context, ProgressListener listener) {
        final String metaFileName = "master_dta_meta.json";
        final String hardcodedOnlineDate = "2025-01-02"; // Hardcoded for future reference

        try {
            // Step 1: Fetch the current date from the HTML
            String onlineDate = fetchMasterDtaDateFromHtml();
            if (onlineDate == null) {
                Log.w("CallsignUtils", "Failed to fetch MASTER.DTA date from HTML. Falling back to checksum method.");
                onlineDate = hardcodedOnlineDate; // Default to the hardcoded date
            }

            // Step 2: Load the meta file to get local state
            File metaFile = new File(context.getFilesDir(), metaFileName);
            String localDate = null;
            String localChecksum = null;

            if (metaFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(metaFile))) {
                    JSONObject metaJson = new JSONObject(reader.readLine());
                    localDate = metaJson.optString("date", null);
                    localChecksum = metaJson.optString("checksum", null);
                }
            }

            // Step 3: Compare dates and decide if update is necessary
            if (localDate != null && onlineDate.equals(localDate)) {
                Log.d("CallsignUtils", "MASTER.DTA is up-to-date. No update required.");
                listener.onProgressUpdate("MASTER.DTA is already up-to-date.", 100);
                return;
            }

            // Step 4: Download the MASTER.DTA file and calculate checksum
            listener.onProgressUpdate("Downloading MASTER.DTA...", 50);
            File tempMasterFile = new File(context.getFilesDir(), "temp_master_dta");
            downloadFile(MASTER_DTA_URL, tempMasterFile);

            String newChecksum = calculateChecksum(tempMasterFile);
            if (localChecksum != null && newChecksum.equals(localChecksum)) {
                Log.d("CallsignUtils", "MASTER.DTA content is identical. No update required.");
                listener.onProgressUpdate("MASTER.DTA is already up-to-date.", 100);
                return;
            }

            // Step 5: Replace the local MASTER.DTA with the new one
            File localMasterFile = new File(context.getFilesDir(), MASTER_DTA_FILE_NAME);
            if (localMasterFile.exists()) localMasterFile.delete();
            tempMasterFile.renameTo(localMasterFile);

            // Step 6: Update the meta file with the new date and checksum
            JSONObject metaJson = new JSONObject();
            metaJson.put("date", onlineDate);
            metaJson.put("checksum", newChecksum);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(metaFile))) {
                writer.write(metaJson.toString());
            }

            listener.onProgressUpdate("MASTER.DTA updated successfully.", 100);
            Log.d("CallsignUtils", "MASTER.DTA updated and meta file saved.");
        } catch (Exception e) {
            Log.e("CallsignUtils", "Error updating MASTER.DTA: " + e.getMessage(), e);
            listener.onProgressUpdate("Failed to update MASTER.DTA.", 100);
        }
    }
    private static String fetchMasterDtaDateFromHtml() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://www.supercheckpartial.com").openConnection();
            connection.setRequestMethod("GET");

            if (connection.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("MASTER.DTA") && line.contains("Updated")) {
                            // Extract the date in the format YYYY-MM-DD
                            return line.replaceAll(".*Updated: (\\d{4}-\\d{2}-\\d{2}).*", "$1");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("CallsignUtils", "Failed to fetch MASTER.DTA date from HTML: " + e.getMessage(), e);
        }
        return null; // Return null if the date cannot be fetched
    }

    private static void downloadFile(String url, File destinationFile) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");

        try (InputStream inputStream = connection.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    public static String calculateChecksum(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] checksumBytes = digest.digest();
        StringBuilder checksumHex = new StringBuilder();
        for (byte b : checksumBytes) {
            checksumHex.append(String.format("%02x", b));
        }
        return checksumHex.toString();
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
        buckets.put("standard_callsigns", new ArrayList<>()); // Standard callsigns with no special features
        buckets.put("slashes_only", new ArrayList<>());       // Contains only a slash, no difficult letters or numbers
        buckets.put("slashes_and_letters", new ArrayList<>()); // Slash and difficult letters only
        buckets.put("slashes_and_numbers", new ArrayList<>()); // Slash and numbers only
        buckets.put("difficult_numbers", new ArrayList<>());   // Difficult number placements
        buckets.put("difficult_letters", new ArrayList<>());   // Difficult letter combinations
        buckets.put("numbers_and_letters", new ArrayList<>()); // Numbers and letters
        buckets.put("all_criteria", new ArrayList<>());        // All complex features: slash, numbers, and difficult letters

        // Process each callsign
        for (String callsign : callsigns) {
            short callsignDifficulty = 0;
            boolean isSlashed = hasSlash(callsign);
            boolean hasNumbers = hasUnusualNumberPlacement(callsign);
            boolean hasDifficultLetters = hasDifficultLetterCombinations(callsign);

            if (isSlashed) callsignDifficulty++;
            if (hasNumbers) callsignDifficulty++;
            if (hasDifficultLetters) callsignDifficulty++;

            switch (callsignDifficulty) {
                case 0:
                    buckets.get("standard_callsigns").add(callsign); // Standard callsigns
                    break;
                case 1:
                    if (isSlashed){
                        buckets.get("slashes_only").add(callsign); // Slash only
                    } else if (hasNumbers) {
                        buckets.get("difficult_numbers").add(callsign); // Numbers only
                    } else if (hasDifficultLetters) {
                        buckets.get("difficult_letters").add(callsign); // Letters only
                    }
                    break;
                case 2:
                    if (isSlashed && hasNumbers) {
                        buckets.get("slashes_and_numbers").add(callsign); // Slash + Numbers
                    } else if (hasNumbers && hasDifficultLetters) {
                        buckets.get("numbers_and_letters").add(callsign); // Numbers + Letters
                    } else if (hasDifficultLetters && isSlashed) {
                        buckets.get("slashes_and_letters").add(callsign); // Slash + Letters
                    }break;
                default:
                    buckets.get("all_criteria").add(callsign); // All conditions met
                    break;

            }


        }

        // Log bucket statistics for debugging
        for (Map.Entry<String, List<String>> entry : buckets.entrySet()) {
            Log.d("CallsignUtils", "Bucket: " + entry.getKey() + " -> " + entry.getValue().size() + " callsigns.");
        }

        return buckets;
    }

    /**
     * Analyzes a single callsign and returns the associated bucket.
     *
     * @param callsign The callsign to analyze.
     * @return The name of the bucket the callsign belongs to.
     */
    public static String getCallsignBucket(String callsign) {
        if (callsign == null || callsign.isEmpty()) {
            Log.e("CallsignUtils", "Invalid callsign provided.");
            return "invalid_callsign"; // Return a special bucket for invalid callsigns
        }

        boolean isSlashed = hasSlash(callsign);
        boolean hasNumbers = hasUnusualNumberPlacement(callsign);
        boolean hasDifficultLetters = hasDifficultLetterCombinations(callsign);

        short callsignDifficulty = 0;
        if (isSlashed) callsignDifficulty++;
        if (hasNumbers) callsignDifficulty++;
        if (hasDifficultLetters) callsignDifficulty++;

        // Determine the bucket based on the difficulty factors
        switch (callsignDifficulty) {
            case 0:
                return "standard_callsigns"; // Standard callsigns
            case 1:
                if (isSlashed) {
                    return "slashes_only"; // Slash only
                } else if (hasNumbers) {
                    return "difficult_numbers"; // Numbers only
                } else if (hasDifficultLetters) {
                    return "difficult_letters"; // Letters only
                }
                break;
            case 2:
                if (isSlashed && hasNumbers) {
                    return "slashes_and_numbers"; // Slash + Numbers
                } else if (hasNumbers && hasDifficultLetters) {
                    return "numbers_and_letters"; // Numbers + Letters
                } else if (hasDifficultLetters && isSlashed) {
                    return "slashes_and_letters"; // Slash + Letters
                }
                break;
            default:
                return "all_criteria"; // All conditions met
        }

        // If no bucket is matched, return unknown
        return "unknown";
    }

    private static boolean hasDifficultLetterCombinations(String callsign) {
        // Define CW-difficult letter clusters (hard to distinguish or send in CW)
        String[] cwDifficultClusters = {
                "HH", "RR", "SS", "ZZ", "LL", "KK", "MM", "NN", // Repeated letters with challenging timing
                "XZ", "JW", "YZ", "QZ", "WX", "CQ", "QR", "QT", // Known hard CW sequences
                "CFH", "JWX", "QXZ", "WQR",                    // Difficult 3-letter combinations
                "XZJW", "QWJZ", "WXQZ"                         // Challenging 4-letter sequences
        };

        // Check if the callsign contains any of the difficult CW clusters
        for (String cluster : cwDifficultClusters) {
            if (callsign.contains(cluster)) {
                return true;
            }
        }

        // Define patterns for 3-letter and 4-letter combinations that are challenging in CW
        String[] difficultThreeLetterClusters = {
                "HHH", "RRR", "SSS", "LLL", "ZZZ", "YYY", "XXX", "WWW", "QQQ", "VVV"
        };
        String[] difficultFourLetterClusters = {
                "HHHH", "RRRR", "SSSS", "LLLL", "ZZZZ", "YYYY", "XXXX", "WWWW", "QQQQ", "VVVV"
        };

        // Check for 3-letter clusters
        for (String cluster : difficultThreeLetterClusters) {
            if (callsign.contains(cluster)) {
                return true; // Flag as difficult
            }
        }

        // Check for 4-letter clusters
        for (String cluster : difficultFourLetterClusters) {
            if (callsign.contains(cluster)) {
                return true; // Flag as very difficult
            }
        }

        // Check for three or more consecutive consonants that create CW rhythm difficulty
        if (callsign.matches(".*[CFGHJKLMNPQRSTVWXYZ]{3,}.*")) {
            return true;
        }

        // Check for rare combinations of 4 or more consonants in a row (uncommon in CW)
        if (callsign.matches(".*[CFGHJKLMNPQRSTVWXYZ]{4,}.*")) {
            return true;
        }

        return false;
    }



    private static boolean hasUnusualNumberPlacement(String callsign) {
        // Check for multiple consecutive numbers (e.g., "123")
        boolean hasConsecutiveNumbers = callsign.matches(".*\\d{2,}.*");

        // Count the total number of numbers in the callsign
        int numberCount = 0;
        for (char c : callsign.toCharArray()) {
            if (Character.isDigit(c)) {
                numberCount++;
            }
        }

        // Check if there are three or more scattered numbers
        boolean hasScatteredNumbers = numberCount >= 3;

        // Flag as unusual if either condition is met
        return hasConsecutiveNumbers || hasScatteredNumbers;
    }


    private static boolean hasSlash(String callsign) {
        return callsign.contains("/");
    }

    public static String getDetailedBucketAnalysis(Context context) {
        File bucketDir = new File(context.getFilesDir(), BUCKET_DIRECTORY);
        StringBuilder analysis = new StringBuilder();

        if (!bucketDir.exists() || !bucketDir.isDirectory()) {
            analysis.append("No bucket directory found.\n");
            return analysis.toString();
        }

        File[] bucketFiles = bucketDir.listFiles();
        if (bucketFiles == null || bucketFiles.length == 0) {
            analysis.append("No bucket files found.\n");
            return analysis.toString();
        }

        Map<String, String> bucketDescriptions = new HashMap<>();
        bucketDescriptions.put("standard_callsigns",
                "Standard Callsigns:\n" +
                        "Majority of callsigns fall into this category, indicating that most callsigns are simple and meet standard expectations.\n");
        bucketDescriptions.put("slashes_only",
                "Slashes Only:\n" +
                        "A subset of callsigns with a single / that are otherwise simple.\n" +
                        "Indicates that the /-based logic is working well to differentiate these from others.\n");
        bucketDescriptions.put("difficult_numbers",
                "Difficult Numbers:\n" +
                        "These callsigns have unusual number placements or multiple consecutive numbers.\n" +
                        "Suggests that the number-placement logic correctly identifies challenges here without over-absorbing.\n");
        bucketDescriptions.put("slashes_and_numbers",
                "Slashes and Numbers:\n" +
                        "Small, well-isolated group combining / and number challenges.\n" +
                        "Perfectly indicates precise differentiation of these cases.\n");
        bucketDescriptions.put("slashes_and_letters",
                "Slashes and Letters:\n" +
                        "Represents callsigns with slashes alongside letter-specific challenges.\n" +
                        "A good-sized group that matches expectations.\n");
        bucketDescriptions.put("all_criteria",
                "All Criteria:\n" +
                        "Very rare cases that combine slashes, letters, and numbers in unusual or difficult ways.\n" +
                        "Shows excellent filtering precision for this group.\n");
        bucketDescriptions.put("difficult_letters",
                "Difficult Letters:\n" +
                        "Captures callsigns with truly difficult letter clusters based on CW timing and rhythm.\n" +
                        "Matches expectations for this subset, as it's common for complex callsigns to fall here.\n");
        bucketDescriptions.put("numbers_and_letters",
                "Numbers and Letters:\n" +
                        "Callsigns with challenging number/letter combinations.\n" +
                        "This smaller group ensures differentiation from standard or overly complex callsigns.\n");

        for (File bucketFile : bucketFiles) {
            if (bucketFile.isFile() && bucketFile.getName().endsWith(".txt")) {
                String bucketName = bucketFile.getName().replace(".txt", "");
                try (BufferedReader reader = new BufferedReader(new FileReader(bucketFile))) {
                    List<String> callsigns = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        callsigns.add(line.trim());
                    }
                    int count = callsigns.size();
                    String description = bucketDescriptions.getOrDefault(bucketName, "Unknown Bucket:\n");
                    analysis.append("(").append(count).append(") ");
                    analysis.append(description);
                    // Add the first 4 callsigns for preview
                    for (int i = 0; i < Math.min(4, callsigns.size()); i++) {
                        analysis.append("  ").append(callsigns.get(i)).append(" \n");
                    }
                    analysis.append("\n");
                } catch (IOException e) {
                    analysis.append("Error reading bucket: ").append(bucketFile.getName()).append("\n");
                }
            }
        }

        return analysis.toString();
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
