package com.example.cwstataccelerator;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;


import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TrainerFragment extends Fragment {

    private Button startTrainingButton;
    private EditText inputField;
    private TableLayout logView; // Log display
    private boolean isTrainingActive = false;

    private CheckBox singleLetterCheckbox;
    private CheckBox alphabetCheckbox;
    private CheckBox numberCheckbox;
    private CheckBox specialCharacterCheckbox;

    private MorseCodeGenerator morseCodeGenerator;
    private Handler trainingHandler;
    private String currentCharacter;
    private long characterStartTime;
    private boolean waitingForReply = false;

    private int selectedSpeed;

    private static final String PREFS_NAME = "CWSettings";
    private static final String KEY_SPEED = "speed";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trainer, container, false);

        // Initialize MorseCodeGenerator
        morseCodeGenerator = new MorseCodeGenerator(requireContext());

        // Load the selected speed from shared preferences
        loadSelectedSpeed();

        // Setup training checkboxes
        //singleLetterCheckbox = view.findViewById(R.id.single_letter_checkbox);
        alphabetCheckbox = view.findViewById(R.id.alphabet_checkbox);
        numberCheckbox = view.findViewById(R.id.numbers_checkbox);
        specialCharacterCheckbox = view.findViewById(R.id.special_characters_checkbox);

        // Alphabet selected by default; at least one checkbox always remains checked
        alphabetCheckbox.setChecked(true);
        enforceMinimumCheckboxSelection();

        // Input field
        inputField = view.findViewById(R.id.input_field);
        inputField.setEnabled(false); // Initially disabled

        // Log view
        logView = view.findViewById(R.id.log_view);

        // Start/Stop training button
        startTrainingButton = view.findViewById(R.id.start_training_button);
        startTrainingButton.setOnClickListener(v -> toggleTraining());

        // Set up TabLayout and ViewPager2
        TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        ViewPager2 viewPager = view.findViewById(R.id.view_pager);

        // ViewPager Adapter
        TrainerPagerAdapter adapter = new TrainerPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Link TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("Performance Metrics");
            } else if (position == 1) {
                tab.setText("Recent Log");
            }
        }).attach();

        // Ensure tabs are updated with current data
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position == 0) {
                    // Update the Current Log tab
                    Fragment currentLogFragment = getChildFragmentManager().findFragmentByTag(
                            "f" + viewPager.getCurrentItem()
                    );
                    if (currentLogFragment instanceof RecentLogFragment) {
                        ((RecentLogFragment) currentLogFragment).updateLogView();
                    }
                } else if (position == 1) {
                    // Update the Performance Metrics tab
                    Fragment performanceMetricsFragment = getChildFragmentManager().findFragmentByTag(
                            "f" + viewPager.getCurrentItem()
                    );
                    if (performanceMetricsFragment instanceof PerformanceMetricsFragment) {
                        ((PerformanceMetricsFragment) performanceMetricsFragment).updateMetricsView();
                    }
                }
            }
        });

        // Listen for character input
        setupInputField();

        // Initialize training handler
        trainingHandler = new Handler();

        return view;
    }

    private void loadSelectedSpeed() {
        selectedSpeed = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_SPEED, 15);
    }

    private void toggleTraining() {
        Log.d("TrainerFragment", "Toggling training. Current state: " + isTrainingActive);
        isTrainingActive = !isTrainingActive;

        if (isTrainingActive) {
            Log.d("TrainerFragment", "Training activated.");
            startTrainingButton.setEnabled(false);
            startCountdown(() -> {
                Log.d("TrainerFragment", "Countdown finished. Starting training.");
                startTrainingButton.setText("Stop Training");
                startTrainingButton.setEnabled(true);

                inputField.setEnabled(true); // Enable the input field
                inputField.requestFocus(); // Focus on the input field
                showKeyboard(inputField); // Show the keyboard

                waitingForReply = false;
                startTrainingSession(); // Start training logic
            });
        } else {
            Log.d("TrainerFragment", "Training deactivated.");
            startTrainingButton.setText("Start Training");
            inputField.setEnabled(false); // Disable the input field
            hideKeyboard(inputField); // Hide the keyboard
            stopTrainingSession(); // Stop training logic
        }
    }


    private void startCountdown(Runnable onComplete) {
        Log.d("TrainerFragment", "Starting countdown.");
        new Thread(() -> {
            for (int i = 3; i > 0; i--) {
                int finalI = i;
                requireActivity().runOnUiThread(() -> {
                    Log.d("TrainerFragment", "Countdown: " + finalI);
                    startTrainingButton.setText("Starting in " + finalI + "...");
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            requireActivity().runOnUiThread(() -> {
                Log.d("TrainerFragment", "Countdown complete.");
                onComplete.run();
            });
        }).start();
    }



    private void setupInputField() {
        inputField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isTrainingActive && s.length() > 0 && waitingForReply) {
                    String enteredChar = s.toString().toUpperCase();
                    processInput(enteredChar); // Process the entered character
                    inputField.setText(""); // Clear the input field
                }
            }
        });
    }

    private void enforceMinimumCheckboxSelection() {
        CheckBox[] checkboxes = {alphabetCheckbox, numberCheckbox, specialCharacterCheckbox};
        for (CheckBox checkbox : checkboxes) {
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) {
                    boolean atLeastOneChecked = false;
                    for (CheckBox cb : checkboxes) {
                        if (cb.isChecked()) {
                            atLeastOneChecked = true;
                            break;
                        }
                    }
                    if (!atLeastOneChecked) {
                        buttonView.setChecked(true);
                    }
                }
            });
        }
    }

    private void startTrainingSession() {
        Log.d("TrainerFragment", "Starting training session.");
        playNextCharacter(true);
    }

    private void stopTrainingSession() {
        trainingHandler.removeCallbacksAndMessages(null);
        morseCodeGenerator.stopRepeatingTone(); // Stop any ongoing playback
    }

    private void playNextCharacter(boolean fetchNewCharacter) {
        try {
            if (fetchNewCharacter) {
                // Fetch a new character
                //currentCharacter = getRandomCharacter();
                currentCharacter = getRandomWeightedCharacter();
            }
            characterStartTime = SystemClock.elapsedRealtime();

            morseCodeGenerator.playMorseCode(currentCharacter);
            waitingForReply = true;
        } catch (Exception e) {
            Log.e("TrainerFragment", "Error during playNextCharacter: " + e.getMessage());
            stopTrainingSession(); // Stop training on critical error
        }
    }

    private String getRandomCharacter() {
        String characters = getSelectedCharacters();
        Random random = new Random();
        return String.valueOf(characters.charAt(random.nextInt(characters.length())));
    }

    public String getRandomWeightedCharacter() {
        int maxSamples = 20;
        int minSamples = 5;
        int baseWeight = 1;
        String selectedCharacters = getSelectedCharacters();

        // Get character weights
        List<Pair<String, Double>> characterWeights = TrainerUtils.calculateCharacterWeights(requireContext(), maxSamples, minSamples, baseWeight);

        // Create a weighted list, filtering by selected characters
        List<String> weightedList = new ArrayList<>();
        for (Pair<String, Double> pair : characterWeights) {
            String character = pair.first;
            double weight = pair.second;

            // Include only characters that are in the selectedCharacters string
            if (selectedCharacters.contains(character)) {
                int scaledWeight = (int) (weight * 100); // Scale weight to an integer count

                // Add the character to the weighted list 'scaledWeight' times
                for (int i = 0; i < scaledWeight; i++) {
                    weightedList.add(character);
                }
            }
        }

        // Ensure the weighted list is not empty
        if (weightedList.isEmpty()) {
            throw new IllegalStateException("No characters available for selection.");
        }

        // Select a random character from the weighted list
        Random random = new Random();
        return weightedList.get(random.nextInt(weightedList.size()));
    }

    private String getSelectedCharacters() {
        StringBuilder characters = new StringBuilder();

        if (alphabetCheckbox.isChecked()) {
            characters.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        }
        if (numberCheckbox.isChecked()) {
            characters.append("0123456789");
        }
        if (specialCharacterCheckbox.isChecked()) {
            characters.append("?!.,;:+-=/");
        }

        if (characters.length() == 0) {
            // Fallback to default (alphabet) if no checkboxes are selected
            Toast.makeText(requireContext(), "Defaulting to alphabet characters.", Toast.LENGTH_SHORT).show();
            return "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        }

        return characters.toString();
    }

    private void processInput(String enteredChar) {
        long responseTime = SystemClock.elapsedRealtime() - characterStartTime;
        boolean isCorrect = enteredChar.equals(currentCharacter);

        // Log the result
        TrainerUtils.logResult(requireContext(), currentCharacter, (int) responseTime, isCorrect, enteredChar, selectedSpeed);

        // Broadcast the update
        Intent intent = new Intent("com.example.cwstataccelerator.UPDATE_LOG");
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);

        // Provide user feedback
        if (isCorrect) {
            Toast.makeText(requireContext(), "👍 Correct!", Toast.LENGTH_SHORT).show();
            updateLogView();
            // Fetch a new character and play it
            playNextCharacter(true);
        } else {
            Toast.makeText(requireContext(), "👎 Incorrect!", Toast.LENGTH_SHORT).show();
            updateLogView();
            // Replay the same character
            playNextCharacter(false);
        }
    }



    private void notifyLogUpdate() {
        getParentFragmentManager().setFragmentResult("log_updated", new Bundle());
    }

    private void updateLogView() {
        List<String> logEntries = TrainerUtils.readRecentLogEntries(requireContext(), 500);

        // Ensure logView is not null and linked properly
        if (logView == null) {
            Log.e("TrainerFragment", "Log view is null! Ensure it is properly linked in the layout.");
            return;
        }

        logView.removeAllViews();

        // Populate logView with new data
        for (String logEntry : logEntries) {
            TableRow row = new TableRow(requireContext());

            for (String column : logEntry.split(",")) {
                TextView textView = new TextView(requireContext());
                textView.setText(column.trim());
                textView.setPadding(8, 8, 8, 8);
                row.addView(textView);
            }

            logView.addView(row);
        }
    }



    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
