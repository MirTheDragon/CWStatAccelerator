package com.example.cwstataccelerator;

import android.content.Context;
import android.content.SharedPreferences;
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

    private int charactersInTraining = 1;

    private CheckBox alphabetCheckbox;
    private CheckBox basicLettersCheckbox;
    private CheckBox intermediateLettersCheckbox;
    private CheckBox advancedLettersCheckbox;
    private CheckBox rareLettersCheckbox;
    private CheckBox numberCheckbox;
    private CheckBox specialCharacterCheckbox;
    private CheckBox twoCharacterCheckbox;
    private CheckBox threeCharacterCheckbox;
    private CheckBox fourCharacterCheckbox;

    private MorseCodeGenerator morseCodeGenerator;
    private Handler trainingHandler;
    private String currentCharacter;
    private int currentCharacterIndex = 0; // Tracks the current character in the training string
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

        // Initialize all UI elements
        alphabetCheckbox = view.findViewById(R.id.alphabet_checkbox);
        basicLettersCheckbox = view.findViewById(R.id.basic_letters_checkbox);
        intermediateLettersCheckbox = view.findViewById(R.id.intermediate_letters_checkbox);
        advancedLettersCheckbox = view.findViewById(R.id.advanced_letters_checkbox);
        rareLettersCheckbox = view.findViewById(R.id.rare_letters_checkbox);
        numberCheckbox = view.findViewById(R.id.number_checkbox);
        specialCharacterCheckbox = view.findViewById(R.id.special_character_checkbox);
        twoCharacterCheckbox = view.findViewById(R.id.two_character_checkbox);
        threeCharacterCheckbox = view.findViewById(R.id.three_character_checkbox);
        fourCharacterCheckbox = view.findViewById(R.id.four_character_checkbox);

        // Load saved checkbox states BEFORE setting any defaults
        loadCheckboxStates();

        inputField = view.findViewById(R.id.input_field);
        startTrainingButton = view.findViewById(R.id.start_training_button);
        logView = view.findViewById(R.id.log_view);

        // Log initialization status
        Log.d("TrainerFragment", "alphabetCheckbox initialized: " + (alphabetCheckbox != null));
        Log.d("TrainerFragment", "inputField initialized: " + (inputField != null));

        // Set initial states
        updateCheckboxesState(true); // Enable checkboxes initially
        updateInputFieldState(false); // Disable input field initially

        // **ONLY NOW enforce rules after loading correct state**
        enforceMinimumCheckboxSelection();

        // Set up listeners (ensures any changes are saved)
        setupCheckboxListeners();


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
        TrainerPagerAdapter adapter = new TrainerPagerAdapter(this, TrainerPagerAdapter.TrainerType.CHARACTER);
        viewPager.setAdapter(adapter);

        // Link TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("Performance Metrics");
            } else if (position == 1) {
                tab.setText("Recent Log");
            }
        }).attach();

        // Ensure tabs are updated on tab switch
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Fragment fragment = getChildFragmentManager().findFragmentByTag("f" + position);

                if (position == 0 && fragment instanceof PerformanceMetricsFragment) {
                    ((PerformanceMetricsFragment) fragment).updateMetricsView();
                } else if (position == 1 && fragment instanceof RecentLogFragment) {
                    ((RecentLogFragment) fragment).updateLogView();
                }
            }
        });

        // Force update on Recent Log when the fragment is first created
        viewPager.post(() -> {
            Fragment fragment = getChildFragmentManager().findFragmentByTag("f1");
            if (fragment instanceof RecentLogFragment) {
                ((RecentLogFragment) fragment).updateLogView();
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

                // Disable checkboxes and enable the input field
                updateCheckboxesState(false); // Disable checkboxes
                updateInputFieldState(true); // Enable input field

                waitingForReply = false;
                startTrainingSession(); // Start training logic
            });
        } else {
            Log.d("TrainerFragment", "Training deactivated.");
            startTrainingButton.setText("Start Training");

            // Enable checkboxes and disable the input field
            updateCheckboxesState(true); // Enable checkboxes
            updateInputFieldState(false); // Disable input field

            stopTrainingSession(); // Stop training logic
        }
    }

    private void updateCheckboxesState(boolean enabled) {
        if (alphabetCheckbox != null) alphabetCheckbox.setEnabled(enabled);
        if (basicLettersCheckbox != null) basicLettersCheckbox.setEnabled(enabled);
        if (intermediateLettersCheckbox != null) intermediateLettersCheckbox.setEnabled(enabled);
        if (advancedLettersCheckbox != null) advancedLettersCheckbox.setEnabled(enabled);
        if (rareLettersCheckbox != null) rareLettersCheckbox.setEnabled(enabled);
        if (numberCheckbox != null) numberCheckbox.setEnabled(enabled);
        if (specialCharacterCheckbox != null) specialCharacterCheckbox.setEnabled(enabled);
        if (twoCharacterCheckbox != null) twoCharacterCheckbox.setEnabled(enabled);
        if (threeCharacterCheckbox != null) threeCharacterCheckbox.setEnabled(enabled);
        if (fourCharacterCheckbox != null) fourCharacterCheckbox.setEnabled(enabled);
    }

    private void updateInputFieldState(boolean enabled) {
        if (inputField != null) {
            inputField.setEnabled(enabled);
            if (enabled) {
                inputField.requestFocus(); // Focus on the input field
                showKeyboard(inputField); // Show the keyboard
            } else {
                hideKeyboard(inputField); // Hide the keyboard
            }
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
                    // inputField.setText(""); // Clear the input field
                }
            }
        });
    }

    private void setupCheckboxListeners() {
        CheckBox[] checkboxes = {
                alphabetCheckbox, basicLettersCheckbox, intermediateLettersCheckbox, advancedLettersCheckbox,
                rareLettersCheckbox, numberCheckbox, specialCharacterCheckbox, twoCharacterCheckbox,
                threeCharacterCheckbox, fourCharacterCheckbox
        };

        for (CheckBox checkbox : checkboxes) {
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Log.d("TrainerFragment", "Checkbox changed: " + buttonView.getId() + " -> " + isChecked);

                enforceMinimumCheckboxSelection();
                saveCheckboxStates(); // ✅ Ensure save happens EVERY TIME
            });
        }
    }



    /**
     * Ensures the minimum checkboxes are selected.
     */
    private void enforceMinimumCheckboxSelection() {
        CheckBox[] mainCheckboxes = {alphabetCheckbox, numberCheckbox, specialCharacterCheckbox};
        CheckBox[] subgroupCheckboxes = {basicLettersCheckbox, intermediateLettersCheckbox, advancedLettersCheckbox, rareLettersCheckbox};
        CheckBox[] multiCharacterCheckboxes = {twoCharacterCheckbox, threeCharacterCheckbox, fourCharacterCheckbox};

        // ** Ensure subgroup checkboxes are greyed out if Alphabet is unchecked **
        boolean isAlphabetChecked = alphabetCheckbox.isChecked();
        for (CheckBox subgroupCheckbox : subgroupCheckboxes) {
            subgroupCheckbox.setEnabled(isAlphabetChecked); // Disable but do not uncheck
        }

        // ** Handle main checkboxes **
        for (CheckBox checkbox : mainCheckboxes) {
            checkbox.setOnCheckedChangeListener(null); // Temporarily remove listener to prevent loops
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) {
                    boolean atLeastOneChecked = false;
                    for (CheckBox cb : mainCheckboxes) {
                        if (cb.isChecked()) {
                            atLeastOneChecked = true;
                            break;
                        }
                    }
                    if (!atLeastOneChecked) {
                        buttonView.setChecked(true);
                        return;
                    }
                }

                // Handle the "alphabet" checkbox specifically
                if (buttonView == alphabetCheckbox) {
                    for (CheckBox subgroupCheckbox : subgroupCheckboxes) {
                        subgroupCheckbox.setEnabled(isChecked);
                    }
                }
                saveCheckboxStates(); // ✅ Ensure state is saved
            });
        }

        // ** Handle subgroup checkboxes **
        for (CheckBox subgroupCheckbox : subgroupCheckboxes) {
            subgroupCheckbox.setOnCheckedChangeListener(null);
            subgroupCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) {
                    boolean anySubgroupChecked = false;
                    for (CheckBox subgroup : subgroupCheckboxes) {
                        if (subgroup.isChecked()) {
                            anySubgroupChecked = true;
                            break;
                        }
                    }
                    if (!anySubgroupChecked) {
                        buttonView.setChecked(true);
                        return;
                    }
                }

                // Ensure Alphabet is checked if any subgroup is checked
                boolean anySubgroupChecked = false;
                for (CheckBox subgroup : subgroupCheckboxes) {
                    if (subgroup.isChecked()) {
                        anySubgroupChecked = true;
                        break;
                    }
                }
                alphabetCheckbox.setChecked(anySubgroupChecked);
                saveCheckboxStates();
            });
        }

        // ** Handle multi-character checkboxes (only one active at a time, or none) **
        for (CheckBox checkbox : multiCharacterCheckboxes) {
            checkbox.setOnCheckedChangeListener(null);
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Set training length based on selected checkbox
                    if (checkbox == twoCharacterCheckbox) charactersInTraining = 2;
                    if (checkbox == threeCharacterCheckbox) charactersInTraining = 3;
                    if (checkbox == fourCharacterCheckbox) charactersInTraining = 4;
                    Log.d("TrainerFragment", "Characters in training set to " + charactersInTraining + ".");

                    // Deselect all other multi-character checkboxes
                    for (CheckBox otherCheckbox : multiCharacterCheckboxes) {
                        if (otherCheckbox != buttonView) {
                            otherCheckbox.setChecked(false);
                        }
                    }
                } else {
                    if (areAllCheckboxesOff(multiCharacterCheckboxes)) {
                        charactersInTraining = 1;
                    }
                }
                saveCheckboxStates();
            });
        }
    }

    /**
     * Checks if all checkboxes in an array are unchecked.
     */
    private boolean areAllCheckboxesOff(CheckBox[] checkboxes) {
        for (CheckBox checkbox : checkboxes) {
            if (checkbox.isChecked()) {
                return false; // At least one checkbox is checked
            }
        }
        return true; // All checkboxes are off
    }


    /**
     * Saves the checkbox states.
     */
    private void saveCheckboxStates() {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean("alphabetCheckbox", alphabetCheckbox.isChecked());
        editor.putBoolean("basicLettersCheckbox", basicLettersCheckbox.isChecked());
        editor.putBoolean("intermediateLettersCheckbox", intermediateLettersCheckbox.isChecked());
        editor.putBoolean("advancedLettersCheckbox", advancedLettersCheckbox.isChecked());
        editor.putBoolean("rareLettersCheckbox", rareLettersCheckbox.isChecked());
        editor.putBoolean("numberCheckbox", numberCheckbox.isChecked());
        editor.putBoolean("specialCharacterCheckbox", specialCharacterCheckbox.isChecked());
        editor.putBoolean("twoCharacterCheckbox", twoCharacterCheckbox.isChecked());
        editor.putBoolean("threeCharacterCheckbox", threeCharacterCheckbox.isChecked());
        editor.putBoolean("fourCharacterCheckbox", fourCharacterCheckbox.isChecked());

        editor.apply();
        Log.d("TrainerFragment", "Checkbox states saved.");
    }

    private void loadCheckboxStates() {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load values from SharedPreferences (no default value, so it stays false if not set)
        alphabetCheckbox.setChecked(preferences.getBoolean("alphabetCheckbox", true));
        basicLettersCheckbox.setChecked(preferences.getBoolean("basicLettersCheckbox", true));
        intermediateLettersCheckbox.setChecked(preferences.getBoolean("intermediateLettersCheckbox", false));
        advancedLettersCheckbox.setChecked(preferences.getBoolean("advancedLettersCheckbox", false));
        rareLettersCheckbox.setChecked(preferences.getBoolean("rareLettersCheckbox", false));
        numberCheckbox.setChecked(preferences.getBoolean("numberCheckbox", false));
        specialCharacterCheckbox.setChecked(preferences.getBoolean("specialCharacterCheckbox", false));
        twoCharacterCheckbox.setChecked(preferences.getBoolean("twoCharacterCheckbox", false));
        threeCharacterCheckbox.setChecked(preferences.getBoolean("threeCharacterCheckbox", false));
        fourCharacterCheckbox.setChecked(preferences.getBoolean("fourCharacterCheckbox", false));

        Log.d("TrainerFragment", "Checkbox states loaded: Alphabet=" + alphabetCheckbox.isChecked());
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
                // Use StringBuilder to efficiently build the string
                StringBuilder characterBuilder = new StringBuilder();

                // Generate the initial random character
                //currentCharacter = getRandomCharacter();
                characterBuilder.append(getRandomWeightedCharacter());

                // Add more characters if needed
                int maxAttempts = 100; // Safeguard to prevent infinite loop
                int attempts = 0;

                // Add additional random characters if multi-character training is enabled
                while (characterBuilder.length() < charactersInTraining && attempts < maxAttempts) {
                    characterBuilder.append(getRandomWeightedCharacter());
                    attempts++;
                }

                if (attempts >= maxAttempts) {
                    throw new IllegalStateException("Unable to generate a valid character sequence.");
                }

                // Set the current character string
                currentCharacter = characterBuilder.toString();

                // Debugging log
                Log.d("TrainerFragment", "Generated character sequence: " + currentCharacter);
            }

            characterStartTime = SystemClock.elapsedRealtime();
            currentCharacterIndex = 0;
            Log.d("TrainerFragment", "Resetting character index to 0.");

            // Play the generated Morse code
            morseCodeGenerator.playMorseCode(currentCharacter);
            waitingForReply = true;
        } catch (Exception e) {
            Log.e("TrainerFragment", "Error during playNextCharacter: " + e.getMessage());
            stopTrainingSession(); // Stop training on critical error
        }
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

        // Alphabet and its subgroups
        if (alphabetCheckbox.isChecked()) {
            // Add letter groups if the main alphabet checkbox is checked
            // Subgroup logic
            if (basicLettersCheckbox != null && basicLettersCheckbox.isChecked()) {
                characters.append("ETAOINS");
            }
            if (intermediateLettersCheckbox != null && intermediateLettersCheckbox.isChecked()) {
                characters.append("HRDLCUMW");
            }
            if (advancedLettersCheckbox != null && advancedLettersCheckbox.isChecked()) {
                characters.append("FGYPBVK");
            }
            if (rareLettersCheckbox != null && rareLettersCheckbox.isChecked()) {
                characters.append("JXQZ");
            }
        }

        // Numbers
        if (numberCheckbox != null && numberCheckbox.isChecked()) {
            characters.append("0123456789");
        }

        // Special Characters
        if (specialCharacterCheckbox != null && specialCharacterCheckbox.isChecked()) {
            characters.append("?!.,;:+-=/");
        }

        // Fallback to default (alphabet) if no checkboxes are selected
        if (characters.length() == 0) {
            Toast.makeText(requireContext(), "Defaulting to alphabet characters.", Toast.LENGTH_SHORT).show();
            return "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        }

        return characters.toString();
    }


    private void processInput(String enteredChar) {
        // Ensure `currentCharacter` is valid
        if (currentCharacter == null || currentCharacter.isEmpty()) {
            Log.e("TrainerFragment", "Invalid currentCharacter: " + currentCharacter);
            stopTrainingSession();
            return;
        }

        // Ensure `currentPosition` is within bounds
        if (currentCharacterIndex >= currentCharacter.length()) {
            Log.e("TrainerFragment", "currentPosition is out of bounds: " + currentCharacterIndex);
            stopTrainingSession();
            return;
        }

        // Get the current character to compare and check correctness
        char expectedChar = currentCharacter.charAt(currentCharacterIndex);
        boolean isCorrect = enteredChar.length() <= charactersInTraining && enteredChar.charAt(currentCharacterIndex) == expectedChar;

        // Log the result
        // Calculate the actual response time from when the current character finished playing

        long playbackDuration = morseCodeGenerator.calculatePlaybackDuration(currentCharacter, currentCharacterIndex);
        long responseTime = SystemClock.elapsedRealtime() - (characterStartTime + playbackDuration);
        TrainerUtils.logResult(
                requireContext(),
                String.valueOf(expectedChar),
                (int) responseTime,
                isCorrect,
                enteredChar,
                selectedSpeed
        );

        // Update the cache
        LogCache.addLog("character", currentCharacter + "," + responseTime + "," + (isCorrect ? "1" : "0") + "," + enteredChar + "," + selectedSpeed + "," + TrainerUtils.getCurrentDateTime());


        // Check for remaining characters;
        currentCharacterIndex++; // Move to the next character
        int remaining = currentCharacter.length() - currentCharacterIndex;
        int toastTime = 800;

        // Broadcast the update
        Intent intent = new Intent("com.example.cwstataccelerator.UPDATE_LOG");
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);

        // Provide user feedback
        if (charactersInTraining == 1) {
            String message = isCorrect ? "👍 Correct!" : "👎 Incorrect!";
            ToastUtils.showCustomToast(requireContext(), message, toastTime);
            inputField.setText(""); // Clear the input field
            playNextCharacter(isCorrect);
        } else if (charactersInTraining > 1 && remaining > 0) {
            String message = isCorrect ? "👍 Correct! Keep Going!" : "👎 Incorrect! Starting over.";
            ToastUtils.showCustomToast(requireContext(), message, toastTime);
            if (!isCorrect) {
                inputField.setText(""); // Clear the input field
                playNextCharacter(false);
            }
        } else if (charactersInTraining > 1 && remaining <= 0) {
            String message = isCorrect ? "👍 Correct! Training text complete." : "👎 Incorrect! Starting over.";
            ToastUtils.showCustomToast(requireContext(), message, toastTime);
            inputField.setText(""); // Clear the input field
            playNextCharacter(isCorrect);
        }

        updateLogView();

    }


    private void notifyLogUpdate() {
        getParentFragmentManager().setFragmentResult("log_updated", new Bundle());
    }

    private void updateLogView() {
        List<String> logEntries = LogCache.getLogs("character"); // Fetch logs from the cache

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
