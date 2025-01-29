package com.example.cwstataccelerator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;

public class CwSpeedFragment extends Fragment {

    private static final String PREFS_NAME = "CWSettings";
    private static final String KEY_FREQUENCY = "frequency";
    private static final String KEY_SPEED = "speed";
    private static final String KEY_MIN_TRAINING_SPEED = "min_training_speed";
    private static final String KEY_MAX_TRAINING_SPEED = "max_training_speed";
    private static final String KEY_RAMP_TIME = "ramp_time";
    private static final String KEY_SNR = "snr";

    private int selectedFrequency;
    private int selectedSpeed;
    private int minTrainingSpeed;
    private int maxTrainingSpeed;
    private int rampTime;
    private int selectedSNR; // SNR as a percentage (1% to 100%)

    private MorseCodeGenerator morseCodeGenerator;

    private SharedPreferences sharedPreferences;

    private SeekBar minSpeedSeekBar;
    private SeekBar maxSpeedSeekBar;
    private SeekBar speedSeekBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cw_speed, container, false);

        // Initialize MorseCodeGenerator with context
        morseCodeGenerator = new MorseCodeGenerator(requireContext());

        // Access SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load saved settings or use defaults
        selectedFrequency = sharedPreferences.getInt(KEY_FREQUENCY, 600); // Default: 600 Hz
        selectedSpeed = sharedPreferences.getInt(KEY_SPEED, 15); // Default: 15 WPM
        minTrainingSpeed = sharedPreferences.getInt(KEY_MIN_TRAINING_SPEED, 10); // Default: 10 WPM
        maxTrainingSpeed = sharedPreferences.getInt(KEY_MAX_TRAINING_SPEED, 80); // Default: 80 WPM
        rampTime = sharedPreferences.getInt(KEY_RAMP_TIME, 5); // Default: 0 ms
        selectedSNR = sharedPreferences.getInt(KEY_SNR, 100); // Default: 100% (perfect signal)

        // Log loaded settings
        Log.d("CwSpeedFragment", "Loaded settings -> Frequency: " + selectedFrequency +
                " Hz, Speed: " + selectedSpeed + " WPM, Min Speed: " + minTrainingSpeed +
                " WPM, Max Speed: " + maxTrainingSpeed + " WPM, Ramp: " + rampTime + " ms, SNR: " + selectedSNR + "%");


        // Frequency Slider
        setupFrequencySlider(view);

        // Default Speed Slider
        setupDefaultSpeedSlider(view);

        // Min Training Speed Slider
        setupMinTrainingSpeedSlider(view);

        // Max Training Speed Slider
        setupMaxTrainingSpeedSlider(view);

        // Ramp Time Slider
        setupRampTimeSlider(view);

        // SNR Slider
        setupSNRSlider(view);

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        saveSettings();
        morseCodeGenerator.stopRepeatingTone(); // Ensure tone stops when fragment is paused
    }

    // Save the current settings to SharedPreferences
    private void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_FREQUENCY, selectedFrequency);
        editor.putInt(KEY_SPEED, selectedSpeed);
        editor.putInt(KEY_MIN_TRAINING_SPEED, minTrainingSpeed);
        editor.putInt(KEY_MAX_TRAINING_SPEED, maxTrainingSpeed);
        editor.putInt(KEY_RAMP_TIME, rampTime);
        editor.putInt(KEY_SNR, selectedSNR);
        editor.apply();

        // Log saved settings
        Log.d("CwSpeedFragment", "Saved settings -> Frequency: " + selectedFrequency +
                " Hz, Speed: " + selectedSpeed + " WPM, Min Speed: " + minTrainingSpeed +
                " WPM, Max Speed: " + maxTrainingSpeed + " WPM, Ramp: " + rampTime + " ms, SNR: " + selectedSNR + "%");
    }

    private void setupFrequencySlider(View view) {
        TextView frequencyLabel = view.findViewById(R.id.frequency_label);
        SeekBar frequencySeekBar = view.findViewById(R.id.frequency_seekbar);
        frequencySeekBar.setMax(17); // Adjusted for 50 Hz increments from 400 Hz to 1250 Hz
        frequencySeekBar.setProgress((selectedFrequency - 400) / 50);
        frequencyLabel.setText("Frequency: " + selectedFrequency + " Hz");

        frequencySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedFrequency = 400 + progress * 50; // Adjusted to start from 400 Hz
                frequencyLabel.setText("Frequency: " + selectedFrequency + " Hz");
                morseCodeGenerator.setFrequency(selectedFrequency);
                morseCodeGenerator.playRepeatingTone("/"); // Update tone dynamically
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.playRepeatingTone("/"); // Start playback
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.stopRepeatingTone(); // Stop playback
                saveSettings();
            }
        });
    }

    private void setupDefaultSpeedSlider(View view) {
        TextView speedLabel = view.findViewById(R.id.speed_label);
        speedSeekBar = view.findViewById(R.id.speed_seekbar);
        speedSeekBar.setMax(70); // 10 to 80 WPM
        speedSeekBar.setProgress(selectedSpeed - 10);
        speedLabel.setText("Default Speed: " + selectedSpeed + " WPM");

        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedSpeed = 10 + progress;
                speedLabel.setText("Default Speed: " + selectedSpeed + " WPM");
                morseCodeGenerator.setDotDuration(1200 / selectedSpeed);
                adjustMinMaxTrainingSpeeds();
                morseCodeGenerator.playRepeatingTone("/"); // Update tone dynamically
                Log.d("CwSpeedFragment", "Speed updated: " + selectedSpeed + " WPM");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.playRepeatingTone("/"); // Start playback
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.stopRepeatingTone(); // Stop playback
                saveSettings();
            }
        });
    }

    private void setupMinTrainingSpeedSlider(View view) {
        TextView minSpeedLabel = view.findViewById(R.id.min_training_speed_label);
        minSpeedSeekBar = view.findViewById(R.id.min_training_speed_seekbar);
        minSpeedSeekBar.setMax(70); // 10 to 80 WPM
        minSpeedSeekBar.setProgress(minTrainingSpeed - 10);
        minSpeedLabel.setText("Min Training Speed: " + minTrainingSpeed + " WPM");

        minSpeedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                minTrainingSpeed = 10 + progress;
                if (minTrainingSpeed > selectedSpeed) {
                    selectedSpeed = minTrainingSpeed;
                    speedSeekBar.setProgress(selectedSpeed - 10);
                }
                if (selectedSpeed > maxTrainingSpeed) {
                    maxTrainingSpeed = selectedSpeed;
                    maxSpeedSeekBar.setProgress(maxTrainingSpeed - 10);
                }
                minSpeedLabel.setText("Min Training Speed: " + minTrainingSpeed + " WPM");
                updateDefaultSpeedLabel(view);
                updateMaxTrainingSpeedLabel(view);
                morseCodeGenerator.playRepeatingTone("/"); // Update tone dynamically
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.playRepeatingTone("/"); // Start playback
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.stopRepeatingTone(); // Stop playback
                saveSettings();
            }
        });
    }

    private void setupMaxTrainingSpeedSlider(View view) {
        TextView maxSpeedLabel = view.findViewById(R.id.max_training_speed_label);
        maxSpeedSeekBar = view.findViewById(R.id.max_training_speed_seekbar);
        maxSpeedSeekBar.setMax(70); // 10 to 80 WPM
        maxSpeedSeekBar.setProgress(maxTrainingSpeed - 10);
        maxSpeedLabel.setText("Max Training Speed: " + maxTrainingSpeed + " WPM");

        maxSpeedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                maxTrainingSpeed = 10 + progress;
                if (maxTrainingSpeed < selectedSpeed) {
                    selectedSpeed = maxTrainingSpeed;
                    speedSeekBar.setProgress(selectedSpeed - 10);
                }
                if (selectedSpeed < minTrainingSpeed) {
                    minTrainingSpeed = selectedSpeed;
                    minSpeedSeekBar.setProgress(minTrainingSpeed - 10);
                }
                maxSpeedLabel.setText("Max Training Speed: " + maxTrainingSpeed + " WPM");
                updateDefaultSpeedLabel(view);
                updateMinTrainingSpeedLabel(view);
                morseCodeGenerator.playRepeatingTone("/"); // Update tone dynamically
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.playRepeatingTone("/"); // Start playback
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.stopRepeatingTone(); // Stop playback
                saveSettings();
            }
        });
    }

    private void setupRampTimeSlider(View view) {
        TextView rampTimeLabel = view.findViewById(R.id.ramp_label);
        SeekBar rampTimeSeekBar = view.findViewById(R.id.ramp_seekbar);
        rampTimeSeekBar.setMax(10); // 0 to 10 ms
        rampTimeSeekBar.setProgress(rampTime);
        rampTimeLabel.setText("Ramp Time: " + rampTime + " ms");

        rampTimeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rampTime = progress;
                rampTimeLabel.setText("Ramp Time: " + rampTime + " ms");
                morseCodeGenerator.setRampDuration(rampTime);
                morseCodeGenerator.playRepeatingTone("/"); // Update tone dynamically

                // Debugging log
                Log.d("CwSpeedFragment", "Ramp Time changed: " + rampTime + " ms");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.playRepeatingTone("/"); // Start playback
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.stopRepeatingTone(); // Stop playback
                saveSettings();
            }
        });
    }

    private void setupSNRSlider(View view) {
        TextView snrLabel = view.findViewById(R.id.snr_label);
        SeekBar snrSeekBar = view.findViewById(R.id.snr_seekbar);
        snrSeekBar.setMax(99); // 100% (index 0) to 1% (index 99)
        snrSeekBar.setProgress(100 - selectedSNR);
        snrLabel.setText("SNR: " + selectedSNR + "% (" + calculateSNRdB(selectedSNR) + " dB)");

        snrSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedSNR = 100 - progress;
                snrLabel.setText("SNR: " + selectedSNR + "% (" + calculateSNRdB(selectedSNR) + " dB)");
                morseCodeGenerator.setSNR(selectedSNR);
                morseCodeGenerator.playRepeatingTone("/"); // Update tone dynamically
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.playRepeatingTone("/"); // Start playback
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                morseCodeGenerator.stopRepeatingTone(); // Stop playback
                saveSettings();
            }
        });
    }

    private String calculateSNRdB(int snrPercentage) {
        if (snrPercentage == 100) return "∞"; // Perfect SNR, no noise
        double snrRatio = snrPercentage / 100.0; // Convert percentage to ratio
        double snrDb = 20 * Math.log10(snrRatio); // Calculate SNR in dB
        return String.format("%.1f", snrDb); // Format to one decimal place
    }

    private void updateDefaultSpeedLabel(View view) {
        TextView speedLabel = view.findViewById(R.id.speed_label);
        speedLabel.setText("Default Speed: " + selectedSpeed + " WPM");
    }

    private void updateMinTrainingSpeedLabel(View view) {
        TextView minSpeedLabel = view.findViewById(R.id.min_training_speed_label);
        minSpeedLabel.setText("Min Training Speed: " + minTrainingSpeed + " WPM");
    }

    private void updateMaxTrainingSpeedLabel(View view) {
        TextView maxSpeedLabel = view.findViewById(R.id.max_training_speed_label);
        maxSpeedLabel.setText("Max Training Speed: " + maxTrainingSpeed + " WPM");
    }

    private void adjustMinMaxTrainingSpeeds() {
        if (selectedSpeed < minTrainingSpeed) {
            minTrainingSpeed = selectedSpeed;
            minSpeedSeekBar.setProgress(minTrainingSpeed - 10);
        }
        if (selectedSpeed > maxTrainingSpeed) {
            maxTrainingSpeed = selectedSpeed;
            maxSpeedSeekBar.setProgress(maxTrainingSpeed - 10);
        }
    }
}
