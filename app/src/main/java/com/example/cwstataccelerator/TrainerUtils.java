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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.util.Pair;
import java.util.Arrays;



public class TrainerUtils {

    private static final String LOG_DIRECTORY = "trainer_logs";

    public static void logResult(Context context, String character, int responseTime, boolean isCorrect, String typedReply, int wpm) {
        String logFileName = getTodayDate() + ".log";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getLogFile(context, logFileName), true))) {
            String logEntry = String.format(
                    "%s,%d,%d,%s,%d,%s",
                    character,
                    responseTime,
                    isCorrect ? 1 : 0,
                    typedReply,
                    wpm,
                    getCurrentDateTime()
            );
            writer.write(logEntry);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Integer[]> getPerformanceMetrics(Context context) {
        Map<String, Integer[]> metrics = new HashMap<>();
        File logDir = new File(context.getFilesDir(), LOG_DIRECTORY);

        if (!logDir.exists() || !logDir.isDirectory()) {
            return metrics;
        }

        File[] logFiles = logDir.listFiles();
        if (logFiles == null || logFiles.length == 0) {
            return metrics;
        }

        for (File logFile : logFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length != 6) continue;

                    String character = parts[0];
                    int responseTime = Integer.parseInt(parts[1]);
                    int correct = Integer.parseInt(parts[2]);

                    // Retrieve existing stats or initialize new ones
                    Integer[] stats = metrics.getOrDefault(character, new Integer[]{0, 0, 0, Integer.MAX_VALUE});
                    stats[0]++; // Total attempts
                    stats[1] += correct; // Total correct
                    stats[2] += responseTime; // Accumulate total response times
                    stats[3] = Math.min(stats[3], responseTime); // Update fastest response time
                    metrics.put(character, stats);
                }
            } catch (IOException | NumberFormatException e) {
                e.printStackTrace();
            }
        }

        return metrics;
    }


    public static List<String> readRecentLogEntries(Context context, int maxEntries) {
        List<String> logEntries = new ArrayList<>();
        File logDir = new File(context.getFilesDir(), LOG_DIRECTORY);

        if (!logDir.exists() || !logDir.isDirectory()) {
            return logEntries;
        }

        File[] logFiles = logDir.listFiles();
        if (logFiles == null || logFiles.length == 0) {
            return logEntries;
        }

        List<File> sortedLogFiles = new ArrayList<>();
        Collections.addAll(sortedLogFiles, logFiles);
        sortedLogFiles.sort((f1, f2) -> f2.getName().compareTo(f1.getName()));

        try {
            for (File logFile : sortedLogFiles) {
                BufferedReader reader = new BufferedReader(new FileReader(logFile));
                String line;

                while ((line = reader.readLine()) != null) {
                    logEntries.add(line);

                    if (logEntries.size() >= maxEntries) {
                        reader.close();
                        return logEntries;
                    }
                }
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return logEntries;
    }



    public static List<Pair<String, Double>> calculateCharacterWeights(
            Context context,
            int maxSamples,
            int minSamples,
            double baseWeight
    ) {
        List<Pair<String, Double>> characterWeights = new ArrayList<>();
        String allCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789?!.,;:+-/=";

        for (char c : allCharacters.toCharArray()) {
            String character = String.valueOf(c);

            // Get recent samples for this character
            List<Pair<Integer, Boolean>> recentSamples = getRecentSamplesWithoutOutliers(context, character, maxSamples);

            if (recentSamples.size() < minSamples) {
                // If not enough samples, assign the base weight
                characterWeights.add(new Pair<>(character, baseWeight));
                continue;
            }

            // Calculate success rate and average response time
            double totalResponseTime = 0;
            int correctCount = 0;

            for (Pair<Integer, Boolean> sample : recentSamples) {
                totalResponseTime += sample.first; // response time
                if (sample.second) correctCount++; // correctness
            }

            double successRate = (double) correctCount / recentSamples.size();
            double averageResponseTime = totalResponseTime / recentSamples.size();

            // Calculate the weight (penalize low success rate and high response time)
            double weight = Math.pow((1 - successRate), 2) + (averageResponseTime / 1000.0);

            // Add the character and its weight to the list
            characterWeights.add(new Pair<>(character, weight));
        }

        return characterWeights;
    }


    public static List<Pair<Integer, Boolean>> getRecentSamplesWithoutOutliers(Context context, String character, int maxSamples) {
        List<Pair<Integer, Boolean>> recentSamples = new ArrayList<>();
        List<Integer> responseTimes = new ArrayList<>();
        List<Boolean> correctnessFlags = new ArrayList<>();

        File logDir = new File(context.getFilesDir(), LOG_DIRECTORY);
        if (!logDir.exists() || !logDir.isDirectory()) return recentSamples;

        File[] logFiles = logDir.listFiles();
        if (logFiles == null || logFiles.length == 0) return recentSamples;

        // Sort log files by file name (most recent date first)
        List<File> sortedLogFiles = Arrays.asList(logFiles);
        Collections.sort(sortedLogFiles, (f1, f2) -> f2.getName().compareTo(f1.getName()));

        try {
            // Loop through log files and collect data
            for (File logFile : sortedLogFiles) {
                BufferedReader reader = new BufferedReader(new FileReader(logFile));
                String line;

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length < 6) continue;

                    String loggedChar = parts[0];
                    int responseTime = Integer.parseInt(parts[1]);
                    boolean correct = parts[2].equals("1");

                    if (character.equals(loggedChar)) {
                        responseTimes.add(responseTime);
                        correctnessFlags.add(correct);

                        // Stop collecting raw data early if we gather enough samples
                        if (responseTimes.size() >= maxSamples * 2) break;
                    }
                }

                reader.close();

                // Stop processing files early if enough samples are collected
                if (responseTimes.size() >= maxSamples * 2) break;
            }

            // Filter outliers from response times
            List<Integer> filteredResponseTimes = removeOutliers(responseTimes);

            // Match filtered response times with correctness flags
            int count = 0; // Track how many valid samples we've collected
            for (int i = 0; i < responseTimes.size(); i++) {
                int responseTime = responseTimes.get(i);
                boolean correct = correctnessFlags.get(i);

                if (filteredResponseTimes.contains(responseTime)) {
                    recentSamples.add(new Pair<>(responseTime, correct));
                    count++;

                    if (count >= maxSamples) break; // Stop once we've collected the required number of samples
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }

        // Return all valid samples, even if fewer than maxSamples
        return recentSamples;
    }



    private static List<Integer> removeOutliers(List<Integer> samples) {
        if (samples.isEmpty() || samples.size() < 3) { // Not enough data to identify outliers
            return samples;
        }

        // Calculate mean
        double sum = 0;
        for (int sample : samples) {
            sum += sample;
        }
        double mean = sum / samples.size();

        // Calculate standard deviation
        double variance = 0;
        for (int sample : samples) {
            variance += Math.pow(sample - mean, 2);
        }
        double stdDev = Math.sqrt(variance / samples.size());

        // Filter out outliers
        List<Integer> filteredSamples = new ArrayList<>();
        for (int sample : samples) {
            if (Math.abs(sample - mean) <= 2 * stdDev) { // Keep values within 2 standard deviations
                filteredSamples.add(sample);
            }
        }

        return filteredSamples;
    }


    public static String getTodayDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    public static String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

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
}
