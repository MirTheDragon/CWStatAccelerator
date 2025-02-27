package com.example.cwstataccelerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallsignErrorAnalyzer {

    /**
     * Analyzes mistakes from recent callsigns and returns a frequency map of errors.
     */
    public static Map<String, Integer> analyzeErrors(List<String[]> callsignLogs) {
        Map<String, Integer> errorCounts = new HashMap<>();

        for (String[] log : callsignLogs) {
            String original = log[0];
            String entered = log[1];

            detectErrors(original, entered, errorCounts);
        }

        return errorCounts;
    }

    /**
     * Compares the original and entered callsigns to detect common errors.
     */
    private static void detectErrors(String original, String entered, Map<String, Integer> errorCounts) {
        int len = Math.max(original.length(), entered.length());

        for (int i = 0; i < len; i++) {
            char origChar = (i < original.length()) ? original.charAt(i) : '-';
            char enteredChar = (i < entered.length()) ? entered.charAt(i) : '-';

            if (origChar != enteredChar) {
                String errorKey = origChar + " → " + enteredChar;
                errorCounts.put(errorKey, errorCounts.getOrDefault(errorKey, 0) + 1);
            }
        }

        // Check for difficult sequences
        analyzeSequences(original, errorCounts);
    }

    /**
     * Detects difficult character sequences (2, 3, and 4-letter patterns).
     */
    private static void analyzeSequences(String callsign, Map<String, Integer> errorCounts) {
        String[] twoLetterSequences = {"EE", "EI", "IE", "II", "TT", "MT", "NN", "UV", "OO", "LL", "XX", "PP"};
        String[] threeLetterSequences = {"MIT", "RUN", "LOW", "DAY", "NOW", "ZIP", "TOP", "VOW"};
        String[] fourLetterSequences = {"VICT", "MARS", "THIS", "LATE", "CARE", "STOP", "ZONE"};

        // Detect sequences
        for (String seq : twoLetterSequences) {
            if (callsign.contains(seq)) {
                errorCounts.put(seq, errorCounts.getOrDefault(seq, 0) + 1);
            }
        }

        for (String seq : threeLetterSequences) {
            if (callsign.contains(seq)) {
                errorCounts.put(seq, errorCounts.getOrDefault(seq, 0) + 1);
            }
        }

        for (String seq : fourLetterSequences) {
            if (callsign.contains(seq)) {
                errorCounts.put(seq, errorCounts.getOrDefault(seq, 0) + 1);
            }
        }
    }
}
