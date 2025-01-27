package com.example.cwstataccelerator; // Keep this package declaration at the top

// Standard imports
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import android.util.Log; // Add this import at the top of your MorseCodeGenerator class



public class MorseCodeGenerator {

    private static final int SAMPLE_RATE = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
    private static final String PREFS_NAME = "CWSettings";
    private static final String KEY_FREQUENCY = "frequency";
    private static final String KEY_SPEED = "speed";
    private static final String KEY_RAMP_TIME = "ramp_duration";
    private static final String KEY_SNR = "snr";

    private int frequency = 600; // Default frequency in Hz
    private int dotDuration = 80; // Default dot duration in ms
    private int rampDuration = 0; // Ramp duration in ms
    private int snr = 100; // Default SNR in %

    private final Map<String, AudioTrack> audioTrackCache = new HashMap<>();
    private final Context context;
    private final BandLimitedPinkNoiseGenerator pinkNoiseGenerator;

    private final Map<String, String> morseCodeMap = new HashMap<String, String>() {{
        put("A", ".-");
        put("B", "-...");
        put("C", "-.-.");
        put("D", "-..");
        put("E", ".");
        put("F", "..-.");
        put("G", "--.");
        put("H", "....");
        put("I", "..");
        put("J", ".---");
        put("K", "-.-");
        put("L", ".-..");
        put("M", "--");
        put("N", "-.");
        put("O", "---");
        put("P", ".--.");
        put("Q", "--.-");
        put("R", ".-.");
        put("S", "...");
        put("T", "-");
        put("U", "..-");
        put("V", "...-");
        put("W", ".--");
        put("X", "-..-");
        put("Y", "-.--");
        put("Z", "--..");
        put("0", "-----");
        put("1", ".----");
        put("2", "..---");
        put("3", "...--");
        put("4", "....-");
        put("5", ".....");
        put("6", "-....");
        put("7", "--...");
        put("8", "---..");
        put("9", "----.");
        put(".", ".-.-.-");
        put(",", "--..--");
        put("?", "..--..");
        put("!", "-.-.--");
        put(";", "-.-.-.");
        put(":", "---...");
        put("+", ".-.-.");
        put("-", "-....-");
        put("/", "-..-.");
        put("=", "-...-");
    }};


    public int getTotalSymbols() {
        return morseCodeMap.size();
    }
    public String getCharacterByIndex(int index) {
        // Convert the morseCodeMap keys to a list
        List<String> keys = new ArrayList<>(morseCodeMap.keySet());

        // Validate the index to ensure it's within bounds
        if (index < 0 || index >= keys.size()) {
            throw new IllegalArgumentException("Index out of bounds: " + index);
        }

        // Return the character at the specified index
        return keys.get(index);
    }

    public String getMorseCode(String character) {
        if (character == null || character.isEmpty()) {
            return ""; // Return an empty string for invalid input
        }
        return morseCodeMap.getOrDefault(character.toUpperCase(), ""); // Default to empty if character not found
    }

    public MorseCodeGenerator(Context context) {
        this.context = context;
        this.pinkNoiseGenerator = new BandLimitedPinkNoiseGenerator(SAMPLE_RATE);
        loadSettings();
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
        clearCache();
    }

    public void setDotDuration(int dotDuration) {
        this.dotDuration = dotDuration;
        clearCache();
    }

    public void setRampDuration(int rampDuration) {
        this.rampDuration = rampDuration;
        clearCache();
    }

    public void setSNR(int snr) {
        this.snr = snr;
        clearCache();
    }

    private void loadSettings() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        frequency = sharedPreferences.getInt(KEY_FREQUENCY, 600);
        int speed = sharedPreferences.getInt(KEY_SPEED, 15);
        dotDuration = 1200 / speed;
        rampDuration = sharedPreferences.getInt(KEY_RAMP_TIME, 0);
        snr = sharedPreferences.getInt(KEY_SNR, 100);
    }

    public void playMorseCode(String input) {
        input = input.toUpperCase(); // Convert input to uppercase for consistency

        if (input.isEmpty()) {
            throw new IllegalArgumentException("Input string is empty.");
        }

        // Handle single-character and multi-character strings
        for (char character : input.toCharArray()) {
            String charAsString = String.valueOf(character);
            if (!morseCodeMap.containsKey(charAsString)) {
                throw new IllegalArgumentException("Unsupported character in input: " + charAsString);
            }
        }

        // Check if the multi-character string is already cached
        if (!audioTrackCache.containsKey(input)) {
            StringBuilder morseCode = new StringBuilder();

            // Generate the combined Morse code for the string
            for (char character : input.toCharArray()) {
                morseCode.append(morseCodeMap.get(String.valueOf(character))).append(" "); // Add a space between characters
            }

            audioTrackCache.put(input, generateAudioTrack(morseCode.toString().trim())); // Cache the generated audio track
        }

        AudioTrack track = audioTrackCache.get(input);
        if (track != null) {
            track.stop();
            track.reloadStaticData();
            track.play();
        }
    }

    private AudioTrack repeatingToneTrack;

    public synchronized void playRepeatingTone(String character) {
        character = character.toUpperCase();
        if (!morseCodeMap.containsKey(character)) {
            throw new IllegalArgumentException("Unsupported character: " + character);
        }

        // Stop the current tone if playing
        stopRepeatingTone();

        String morseCode = morseCodeMap.get(character);
        short[] waveform = generateMorseSound(morseCode);

        // Apply noise based on the current SNR
        if (snr < 100) {
            waveform = applyNoise(waveform, snr);
        }

        // Calculate buffer size
        int bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (bufferSize < 0) {
            throw new IllegalStateException("Failed to calculate buffer size for AudioTrack");
        }

        repeatingToneTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
        );

        repeatingToneTrack.play();

        // Make `waveform` final
        final short[] finalWaveform = waveform;

        // Start a thread to loop the waveform
        new Thread(() -> {
            try {
                while (repeatingToneTrack != null && repeatingToneTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    repeatingToneTrack.write(finalWaveform, 0, finalWaveform.length);
                }
            } catch (IllegalStateException e) {
                Log.e("MorseCodeGenerator", "Error while writing waveform: " + e.getMessage());
            }
        }).start();
    }




    public synchronized void stopRepeatingTone() {
        if (repeatingToneTrack != null) {
            try {
                repeatingToneTrack.stop();
                repeatingToneTrack.release();
            } catch (IllegalStateException e) {
                Log.e("MorseCodeGenerator", "Error stopping repeating tone: " + e.getMessage());
            } finally {
                repeatingToneTrack = null;
            }
        }
    }


    private AudioTrack generateAudioTrack(String morseCode) {

        short[] waveform = generateMorseSound(morseCode);

        if (snr < 100) {
            waveform = applyNoise(waveform, snr);
        }

        int bufferSize = Math.max(
                AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                waveform.length * 2
        );

        AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STATIC
        );

        track.write(waveform, 0, waveform.length);
        return track;
    }

    private short[] generateMorseSound(String morseCodeString) {
        int dotSamples = (SAMPLE_RATE * dotDuration) / 1000;
        int dashSamples = dotSamples * 3;
        int rampSamples = (SAMPLE_RATE * rampDuration) / 1000;
        int silenceSamples = dotSamples;
        short[] dotTone = applyRamp(generateTone(dotSamples, frequency), rampSamples);
        short[] dashTone = applyRamp(generateTone(dashSamples, frequency), rampSamples);
        short[] silence = new short[silenceSamples];

        // Split input into characters to handle multi-character strings
        String[] morseCodes = morseCodeString.split(""); // Split into individual characters

        // Calculate total length, including padding and inter-character silence
        int totalLength = silenceSamples; // Start with one silence at the beginning
        for (int i = 0; i < morseCodes.length; i++) {
            String morseCode = morseCodes[i];
            for (char c : morseCode.toCharArray()) {
                totalLength += (c == '.') ? dotSamples : dashSamples;
                totalLength += silenceSamples; // Intra-symbol silence
            }
            if (i < morseCodes.length - 1) {
                totalLength += silenceSamples * 3; // Inter-character silence
            }
        }
        totalLength += silenceSamples; // Add trailing silence


        // Generate the sound
        short[] result = new short[totalLength];
        int position = 0;

        // Add initial silence
        System.arraycopy(silence, 0, result, position, silence.length);
        position += silence.length;

        for (int i = 0; i < morseCodes.length; i++) {
            String morseCode = morseCodes[i];
            for (char c : morseCode.toCharArray()) {
                if (c == '.') {
                    System.arraycopy(dotTone, 0, result, position, dotTone.length);
                    position += dotTone.length;
                } else if (c == '-') {
                    System.arraycopy(dashTone, 0, result, position, dashTone.length);
                    position += dashTone.length;
                }
                // Add intra-symbol silence
                System.arraycopy(silence, 0, result, position, silence.length);
                position += silence.length;
            }
            if (i < morseCodes.length - 1) {
                // Add inter-character silence (3 silence periods)
                for (int j = 0; j < 3; j++) {
                    System.arraycopy(silence, 0, result, position, silence.length);
                    position += silence.length;
                }
            }
        }

        // Add trailing silence
        System.arraycopy(silence, 0, result, position, silence.length);

        return result;
    }
    public long calculatePlaybackDuration(String morseString, int position) {
        int dotDurationMs = dotDuration; // Duration of a dot in milliseconds
        int dashDurationMs = dotDuration * 3; // Duration of a dash in milliseconds
        int interSymbolSilenceMs = dotDuration; // Silence between symbols
        int interCharacterSilenceMs = dotDuration * 3; // Silence between characters

        long totalDuration = 0;

        for (int i = 0; i < position; i++) {
            char currentChar = morseString.charAt(i);
            String morseCode = getMorseCode(String.valueOf(currentChar));

            // Add the duration of each symbol in the Morse code
            for (char symbol : morseCode.toCharArray()) {
                if (symbol == '.') {
                    totalDuration += dotDurationMs;
                } else if (symbol == '-') {
                    totalDuration += dashDurationMs;
                }
                totalDuration += interSymbolSilenceMs; // Silence between symbols
            }

            // Add the inter-character silence after the character
            if (i < position - 1) {
                totalDuration += interCharacterSilenceMs;
            }
        }

        return totalDuration;
    }

    private short[] generateTone(int samples, int frequency) {
        short[] tone = new short[samples];
        double angleIncrement = 2.0 * Math.PI * frequency / SAMPLE_RATE;
        for (int i = 0; i < samples; i++) {
            tone[i] = (short) (Math.sin(i * angleIncrement) * Short.MAX_VALUE);
        }
        return tone;
    }

    private short[] applyRamp(short[] signal, int rampSamples) {
        if (rampSamples == 0 || signal.length < rampSamples * 2) {
            return signal;
        }
        for (int i = 0; i < rampSamples; i++) {
            double rampFactor = (1 - Math.cos(Math.PI * i / rampSamples)) / 2;
            signal[i] *= rampFactor;
            signal[signal.length - i - 1] *= rampFactor;
        }
        return signal;
    }

    // Inside MorseCodeGenerator
    private short[] applyNoise(short[] signal, int snr) {
        if (snr == 100) {
            // No noise case, return original signal
            Log.d("MorseCodeGenerator", "applyNoise() - No noise applied, SNR: 100%");
            return signal;
        }

        // Calculate noise factor (SNR: 100 = no noise, 0 = full noise)
        double noiseFactor = (100 - snr) / 100.0;

        // Log the SNR and noise factor for debugging
        Log.d("MorseCodeGenerator", "applyNoise() - SNR: " + snr + "%, Noise Factor: " + noiseFactor);

        for (int i = 0; i < signal.length; i++) {
            // Generate pink noise sample
            double noiseSample = pinkNoiseGenerator.generateSample() * Short.MAX_VALUE * noiseFactor;

            // Apply the noise to the signal
            double result = signal[i] + noiseSample;

            // Ensure the signal stays within valid bounds (-32768 to 32767 for 16-bit PCM audio)
            result = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, result));

            // Assign the noisy result back to the signal
            signal[i] = (short) result;

            // Log signal and noise details for a subset of samples (to avoid excessive logging)
            if (i % 1000 == 0) {
                Log.d("MorseCodeGenerator", "Sample " + i + ": Signal=" + signal[i] + ", Noise=" + noiseSample + ", Result=" + result);
            }
        }

        return signal;
    }


    private void clearCache() {
        for (AudioTrack track : audioTrackCache.values()) {
            track.release();
        }
        audioTrackCache.clear();
    }
}
