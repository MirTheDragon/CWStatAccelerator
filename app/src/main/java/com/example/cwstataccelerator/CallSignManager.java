package com.example.cwstataccelerator;
import android.content.Context;
import android.os.AsyncTask;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import org.json.JSONArray;


public class CallSignManager {

    private static final String QRZ_DATABASE_URL = "https://archive.org/download/qrz-ham-radio-callsign-database-volume-20/qrz-ham-radio-callsign-database-volume-20.txt";
    private static final String MASTER_DTA_URL = "https://www.supercheckpartial.com/MASTER.DTA";

    private static final String BUCKET_DIRECTORY_NAME = "call_sign_buckets";

    private final Context context;
    private String qrzVersion = "VER20030000"; // Static version for QRZ Volume 20
    private String masterDtaVersion;

    public CallSignManager(Context context) {
        this.context = context;
        ensureBucketDirectoryExists();
    }

    /**
     * Ensures the bucket directory exists.
     */
    private void ensureBucketDirectoryExists() {
        File bucketDir = new File(context.getFilesDir(), BUCKET_DIRECTORY_NAME);
        if (!bucketDir.exists()) {
            bucketDir.mkdir();
        }
    }

    /**
     * Entry point to fetch, sort, and save call signs asynchronously.
     */
    public void updateDatabases() {
        new UpdateDatabaseTask().execute();
    }

    /**
     * AsyncTask to fetch, process, and save call signs.
     */
    private class UpdateDatabaseTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                // Check if MASTER.DTA needs to be updated
                masterDtaVersion = fetchMasterDtaVersion();
                boolean masterDtaNeedsUpdate = !isFileUpToDate(MASTER_DTA_URL, masterDtaVersion);

                // Check if QRZ database needs to be updated
                boolean qrzNeedsUpdate = !isFileExistsWithVersion(qrzVersion);

                // Fetch and update only if necessary
                if (masterDtaNeedsUpdate || qrzNeedsUpdate) {
                    System.out.println("Updating databases...");
                    List<String> callSigns = fetchAndMergeCallSigns(masterDtaNeedsUpdate, qrzNeedsUpdate);
                    Map<String, List<String>> buckets = sortIntoBuckets(callSigns);
                    saveBuckets(buckets);
                    System.out.println("Buckets updated successfully.");
                } else {
                    System.out.println("Databases are already up-to-date.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Fetch and merge call signs if updates are needed.
     */
    private List<String> fetchAndMergeCallSigns(boolean fetchMasterDta, boolean fetchQRZ) throws IOException {
        List<String> qrzCallSigns = fetchQRZ ? fetchCallSignsFromQRZ() : new ArrayList<>();
        List<String> masterDtaCallSigns = fetchMasterDta ? fetchCallSignsFromMasterDTA() : new ArrayList<>();

        Set<String> mergedCallSigns = new HashSet<>();
        mergedCallSigns.addAll(qrzCallSigns);
        mergedCallSigns.addAll(masterDtaCallSigns);

        return new ArrayList<>(mergedCallSigns);
    }

    /**
     * Check if the file already exists locally with the correct version.
     */
    private boolean isFileExistsWithVersion(String version) {
        File bucketDir = new File(context.getFilesDir(), BUCKET_DIRECTORY_NAME);
        File[] files = bucketDir.listFiles((dir, name) -> name.contains(version));
        return files != null && files.length > 0;
    }

    /**
     * Check if the remote MASTER.DTA version is different from the local one.
     */
    private boolean isFileUpToDate(String url, String remoteVersion) {
        File bucketDir = new File(context.getFilesDir(), BUCKET_DIRECTORY_NAME);
        File[] files = bucketDir.listFiles((dir, name) -> name.contains(remoteVersion));
        return files != null && files.length > 0;
    }

    /**
     * Fetch the version identifier from MASTER.DTA using HTTP HEAD.
     */
    private String fetchMasterDtaVersion() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(MASTER_DTA_URL).openConnection();
        connection.setRequestMethod("HEAD");

        String lastModified = connection.getHeaderField("Last-Modified");
        if (lastModified != null) {
            return "VER" + lastModified.replace(" ", "").replace(":", "").replace("GMT", "");
        }

        throw new IOException("Unable to determine MASTER.DTA version.");
    }

    /**
     * Fetch call signs from QRZ Volume 20.
     */
    private List<String> fetchCallSignsFromQRZ() throws IOException {
        return downloadAndParseTextFile(QRZ_DATABASE_URL);
    }

    /**
     * Fetch call signs from MASTER.DTA.
     */
    private List<String> fetchCallSignsFromMasterDTA() throws IOException {
        return downloadAndParseTextFile(MASTER_DTA_URL);
    }

    /**
     * Download and parse a text file into a list of call signs.
     */
    private List<String> downloadAndParseTextFile(String url) throws IOException {
        List<String> callSigns = new ArrayList<>();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    callSigns.add(line.trim());
                }
            }
        } else {
            throw new IOException("Failed to fetch file from " + url);
        }

        return callSigns;
    }

    /**
     * Sort call signs into buckets.
     */
    private Map<String, List<String>> sortIntoBuckets(List<String> callSigns) {
        Map<String, List<String>> buckets = new HashMap<>();

        for (String callSign : callSigns) {
            String bucket = assignBucket(callSign);
            //buckets.putIfAbsent(bucket, new ArrayList<>());
            if (!buckets.containsKey(bucket)) {
                buckets.put(bucket, new ArrayList<>());
            }
            buckets.get(bucket).add(callSign);
        }

        return buckets;
    }

    /**
     * Assign a bucket to a call sign.
     */
    private String assignBucket(String callSign) {
        boolean hasSpecialChars = callSign.contains("/") || callSign.contains("-");
        boolean hasManyNumbers = callSign.replaceAll("[A-Z/\\-]", "").length() > 1;

        int difficultyScore = calculateDifficulty(callSign);

        if (hasSpecialChars) {
            if (hasManyNumbers) {
                return difficultyScore > 20 ? "B9" : "B8";
            }
            return difficultyScore > 20 ? "B7" : "B6";
        } else {
            if (hasManyNumbers) {
                return difficultyScore > 20 ? "B5" : "B4";
            }
            return difficultyScore > 20 ? "B3" : (difficultyScore > 10 ? "B2" : "B1");
        }
    }

    /**
     * Calculate the difficulty score for a call sign.
     */
    private int calculateDifficulty(String callSign) {
        int score = 0;
        score += callSign.length() * 2;

        for (char c : callSign.toCharArray()) {
            if ("QXZ".indexOf(c) >= 0) {
                score += 5;
            }
        }

        if (callSign.replaceAll("[A-Z/\\-]", "").length() > 0) {
            score += 5;
        }

        if (callSign.matches(".*(.)\\1.*")) {
            score += 3;
        }

        return score;
    }

    /**
     * Save sorted buckets as JSON files.
     */
    private void saveBuckets(Map<String, List<String>> buckets) {
        for (Map.Entry<String, List<String>> entry : buckets.entrySet()) {
            String bucketId = entry.getKey();
            List<String> callSigns = entry.getValue();

            File file = new File(context.getFilesDir(), BUCKET_DIRECTORY_NAME + "/bucket_" + bucketId + "_" + qrzVersion + "_" + masterDtaVersion + ".json");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                JSONArray jsonArray = new JSONArray(callSigns);
                writer.write(jsonArray.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
