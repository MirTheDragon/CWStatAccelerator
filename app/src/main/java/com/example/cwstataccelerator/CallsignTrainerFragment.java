package com.example.cwstataccelerator;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.SystemClock;
import androidx.appcompat.app.AppCompatActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;


import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Arrays;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.slider.RangeSlider;

import java.util.List;

public class CallsignTrainerFragment extends Fragment {

    // UI Elements
    private Button startTrainingButton;
    private EditText inputField;
    private TableLayout logView; // Log display
    private boolean isTrainingActive = false;
    private TextView callsignLengthRangeLabel;
    private RangeSlider callsignLengthRangeSlider;
    private MorseCodeGenerator morseCodeGenerator;
    private Handler trainingHandler;
    private String currentCallsign;
    private long callsignStartTime;
    private boolean waitingForReply = false;

    // List to store selected buckets
    private final List<String> selectedBuckets = new ArrayList<>(Arrays.asList("standard_callsigns"));

    private int currentSpeed;

    private int minCallsignLength = 3;

    private int maxCallsignLength = 6;
    private CheckBox simpleCallsignCheckbox;
    private Spinner slashedSpinner;
    private Spinner numberSpinner;
    private Spinner letterSpinner;
    private static final String PREFS_NAME = "CWSettings";
    private static final String KEY_SPEED = "speed";

    @Nullable
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("CallsignTrainerFragment", "onCreate called");
    }


    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d("CallsignTrainerFragment", "Initializing CallsignTrainerFragment");
        View view = inflater.inflate(R.layout.fragment_callsign_trainer, container, false);


        // Initialize MorseCodeGenerator
        morseCodeGenerator = new MorseCodeGenerator(requireContext());

        // Initialize UI elements
        callsignLengthRangeLabel = view.findViewById(R.id.callsign_length_range_label);
        callsignLengthRangeSlider = view.findViewById(R.id.callsign_length_range_slider);
        simpleCallsignCheckbox = view.findViewById(R.id.standard_callsigns_checkbox);
        slashedSpinner = view.findViewById(R.id.slashed_callsign_spinner);
        numberSpinner = view.findViewById(R.id.difficult_number_spinner);
        letterSpinner = view.findViewById(R.id.difficult_letter_spinner);
        startTrainingButton = view.findViewById(R.id.start_training_button);


        // Define the adapter properly before using it
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), // Use requireContext() instead of "this"
                R.array.filter_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Set adapters to spinners
        slashedSpinner.setAdapter(adapter);
        numberSpinner.setAdapter(adapter);
        letterSpinner.setAdapter(adapter);

        slashedSpinner.setSelection(2);
        numberSpinner.setSelection(2);
        letterSpinner.setSelection(2);

        // Load saved checkbox states and slider states
        loadPreferences();

        // Set up the callsign length range slider and checkbox and spinners
        setupCallsignLengthRangeSlider();
        setupListeners();
        enforceLogicCheckboxSelection(simpleCallsignCheckbox);

        // Input field
        inputField = view.findViewById(R.id.input_field);
        inputField.setEnabled(false); // Initially disabled

        // Log view
        logView = view.findViewById(R.id.log_view);


        AdapterView.OnItemSelectedListener updatesimpleCallsignCheckbox = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatesimpleCallsignCheckbox();
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        // Start/Stop training button
        startTrainingButton = view.findViewById(R.id.start_training_button);
        startTrainingButton.setOnClickListener(v -> toggleTraining());

        // Initialize tabs and view pager
        setupTabsAndViewPager(view);

        // Listen for character input
        setupInputField();

        // Initialize training handler
        trainingHandler = new Handler();

        return view;
    }

    private void updatesimpleCallsignCheckbox() {
        boolean allExcluded = slashedSpinner.getSelectedItemPosition() == 2 &&
                numberSpinner.getSelectedItemPosition() == 2 &&
                letterSpinner.getSelectedItemPosition() == 2;

        simpleCallsignCheckbox.setChecked(allExcluded);
    }

    private void setupTabsAndViewPager(View view) {
        try {
            TabLayout tabLayout = view.findViewById(R.id.tab_layout);
            ViewPager2 viewPager = view.findViewById(R.id.view_pager);

            // Ensure tabs and view pager are not null
            if (tabLayout == null || viewPager == null) {
                Log.e("CallsignTrainerFragment", "TabLayout or ViewPager2 is null!");
                return;
            }

            // Set up the adapter
            TrainerPagerAdapter adapter = new TrainerPagerAdapter(this, TrainerPagerAdapter.TrainerType.CALLSIGN);
            viewPager.setAdapter(adapter);

            // Link tabs with view pager
            new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                switch (position) {
                    case 0:
                        tab.setText("Performance Metrics");
                        break;
                    case 1:
                        tab.setText("Recent Log");
                        break;
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
        } catch (Exception e) {
            Log.e("CallsignTrainerFragment", "Error initializing tabs and view pager: " + e.getMessage());
        }
    }

    private void setupCallsignLengthRangeSlider() {
        // Load saved values
        loadPreferences(); // Ensures minCallsignLength and maxCallsignLength are set correctly

        // Initialize slider values using saved min/max values
        callsignLengthRangeSlider.setValues((float) minCallsignLength, (float) maxCallsignLength);

        // **Trigger the label update immediately upon initialization**
        updateCallsignLengthLabel(minCallsignLength, maxCallsignLength);

        // Update the label dynamically as the user moves the slider
        callsignLengthRangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            minCallsignLength = Math.round(values.get(0)); // Update local min length
            maxCallsignLength = Math.round(values.get(1)); // Update local max length

            // **Call the label update method**
            updateCallsignLengthLabel(minCallsignLength, maxCallsignLength);

            // Save preferences whenever the slider changes
            savePreferences();
        });
    }

    /**
     * Updates the callsign length label dynamically.
     */
    private void updateCallsignLengthLabel(int minLength, int maxLength) {
        String maxText = maxLength == 10 ? "15+ Characters" : maxLength + " Characters";

        if (minLength == maxLength && maxLength < 10) {
            callsignLengthRangeLabel.setText("Callsign Length: " + maxText);
        } else if (maxLength == 10) {
            callsignLengthRangeLabel.setText("Callsign Length: " + minLength + " Characters and Above");
        } else {
            callsignLengthRangeLabel.setText("Callsign Length: " + minLength + " to " + maxLength + " Characters");
        }

        Log.d("CallsignTrainerFragment", "Updated label: " + callsignLengthRangeLabel.getText().toString());
    }


    private void setupListeners() {
        // Set up checkbox listeners
        CheckBox[] checkboxes = {
                simpleCallsignCheckbox
        };
        for (CheckBox checkbox : checkboxes) {
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Log.d("CallsignTrainerFragment", "Checkbox changed: " + buttonView.getId() + " -> " + isChecked);
                enforceLogicCheckboxSelection(checkbox);
                updateSelectedBuckets();
                savePreferences();
            });
        }
        // Set up spinner listeners
        Spinner[] spinners = {
                slashedSpinner, numberSpinner, letterSpinner
        };
        for (Spinner spinner : spinners) {
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    enforceLogicSpinnerSelection(spinner);
                    updateSelectedBuckets();
                    savePreferences();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Do nothing
                }
            });
        }
    }

    private void enforceLogicSpinnerSelection(Spinner changedSpinner) {
        boolean hasOnlySelected = changedSpinner.getSelectedItem().toString().equals("Only");
        boolean hasIncludeSelected = changedSpinner.getSelectedItem().toString().equals("Include");

        Log.d("CallsignTrainerFragment", "Spinner changed to " + changedSpinner.getSelectedItem().toString());


        Spinner[] spinners = {slashedSpinner, numberSpinner, letterSpinner};

        for (Spinner spinner : spinners) {
            String selectedValue = spinner.getSelectedItem().toString();

            if (hasOnlySelected && selectedValue.equals("Include")) {
                spinner.setSelection(1, true);
            } else
            if (hasIncludeSelected && selectedValue.equals("Only")) {
                spinner.setSelection(0, true);
            }
        }

        // If any spinner is set to "Only", disable the standard callsign checkbox
        if (hasIncludeSelected) {
            simpleCallsignCheckbox.setChecked(true);
        } else {
            simpleCallsignCheckbox.setChecked(false);
        }
    }

    private void enforceLogicCheckboxSelection(CheckBox checkBox) {
        boolean isChecked = checkBox.isChecked();

        Log.d("CallsignTrainerFragment", "Checkbox changed: " + checkBox.getId() + " -> " + isChecked);

        Spinner[] spinners = {slashedSpinner, numberSpinner, letterSpinner};

        for (Spinner spinner : spinners) {
            String selectedValue = spinner.getSelectedItem().toString();

            // If standard callsigns is selected, reset any "Only" selections to "Include"
            if (isChecked && selectedValue.equals("Only")) {
                spinner.setSelection(0, true); // Assume "Only" is at index 1
            } else
            // If standard callsigns is not selected, reset any "include" selections to "Only"
            if (!isChecked && selectedValue.equals("Include")) {
                spinner.setSelection(1, true); // Assume "Include" is at index 0
            }
        }
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
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                if (isTrainingActive && waitingForReply) {
                    String enteredString = inputField.getText().toString().trim().toUpperCase();

                    if (!enteredString.isEmpty()) {
                        processInput(enteredString); // Process the full entered string
                        inputField.setText(""); // Clear the input field after processing
                    } else {
                        // Optional: Provide feedback to enter a string
                        Toast.makeText(requireContext(), "Please enter a callsign before pressing Done", Toast.LENGTH_SHORT).show();
                    }
                }
                return true; // Consume the action
            }
            return false; // Pass the action to other listeners if it's not "Done" or "Go"
        });
    }




    private void startTrainingSession() {
        isTrainingActive = true;
        toggleInputFields(false); // Disable inputs

        Log.d("TrainerFragment", "Starting training session.");
        playNextCallsign(true);
    }

    private void stopTrainingSession() {
        isTrainingActive = false;
        toggleInputFields(true); // Re-enable inputs

        trainingHandler.removeCallbacksAndMessages(null);
        morseCodeGenerator.stopRepeatingTone(); // Stop any ongoing playback
    }

    private void toggleInputFields(boolean enabled) {
        // Enable/disable the checkboxes and slider
        simpleCallsignCheckbox.setEnabled(enabled);
        slashedSpinner.setEnabled(enabled);
        numberSpinner.setEnabled(enabled);
        letterSpinner.setEnabled(enabled);
        callsignLengthRangeSlider.setEnabled(enabled);

        // Enable/disable the input field for callsign entry
        inputField.setEnabled(!enabled); // Disable when training is not active
        if (!enabled) {
            inputField.setText(""); // Clear the input field when disabling it
        }

        // Optionally disable the start training button (if needed)
        // startTrainingButton.setEnabled(enabled);
    }


    // Function to update the selected buckets based on checkbox state
    private void updateSelectedBuckets() {
        selectedBuckets.clear();

        // Default to standard callsigns
        if (simpleCallsignCheckbox.isChecked()) {
            selectedBuckets.add("standard_callsigns");
        } else {
            selectedBuckets.remove("standard_callsigns");
        }

        short callsignDifficulty = 0;
        boolean isSlashed = slashedSpinner.getSelectedItem().toString().equals("Only") || slashedSpinner.getSelectedItem().toString().equals("Include");
        boolean hasNumbers = numberSpinner.getSelectedItem().toString().equals("Only") || numberSpinner.getSelectedItem().toString().equals("Include");
        boolean hasLetters = letterSpinner.getSelectedItem().toString().equals("Only") || letterSpinner.getSelectedItem().toString().equals("Include");

        if (isSlashed) callsignDifficulty++;
        if (hasNumbers) callsignDifficulty++;
        if (hasLetters) callsignDifficulty++;

        switch (callsignDifficulty) {
            case 0:
                selectedBuckets.add("standard_callsigns");
                break;
            case 1:
                if (isSlashed) {
                    selectedBuckets.add("difficult_slashed");
                } else if (hasNumbers) {
                    selectedBuckets.add("difficult_numbers");
                } else if (hasLetters) {
                    selectedBuckets.add("difficult_letters");
                }
                break;
            case 2:
                if (isSlashed && hasNumbers) {
                    selectedBuckets.add("slashed_and_numbers");
                } else if (hasNumbers && hasLetters) {
                    selectedBuckets.add("numbers_and_letters");
                } else if (hasLetters && isSlashed) {
                    selectedBuckets.add("slashes_and_letters");
                }
                break;
            default:
                selectedBuckets.add("all_criteria");
                break;
        }

        // Log or debug the selected buckets
        Log.d("CallsignTrainerUtils", "Selected Buckets: " + selectedBuckets.toString());
    }


    private void savePreferences() {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        // Save min and max callsign length
        editor.putInt("minCallsignLength", minCallsignLength);
        editor.putInt("maxCallsignLength", maxCallsignLength);

        // Save checkbox states
        editor.putBoolean("standardCallsignsCheckbox", simpleCallsignCheckbox.isChecked());

        // Save dropdown selections
        editor.putInt("slashedCallsignsSelection", slashedSpinner.getSelectedItemPosition());
        editor.putInt("numberCombinationsSelection", numberSpinner.getSelectedItemPosition());
        editor.putInt("letterCombinationsSelection", letterSpinner.getSelectedItemPosition());

        editor.apply(); // Save changes asynchronously
        Log.d("CallsignTrainerFragment", "Preferences saved successfully.");
    }

    private void loadPreferences() {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load min and max callsign length
        minCallsignLength = preferences.getInt("minCallsignLength", 3);
        maxCallsignLength = preferences.getInt("maxCallsignLength", 6);

        // Load checkbox states
        simpleCallsignCheckbox.setChecked(preferences.getBoolean("standardCallsignsCheckbox", true));

        // Load dropdown selections
        slashedSpinner.setSelection(preferences.getInt("slashedCallsignsSelection", 2), true);
        numberSpinner.setSelection(preferences.getInt("numberCombinationsSelection", 2), true);
        letterSpinner.setSelection(preferences.getInt("letterCombinationsSelection", 2), true);

        Log.d("CallsignTrainerFragment", "Preferences loaded successfully.");
    }


    private void playNextCallsign(boolean fetchNewCallsign) {
        try {
            if (fetchNewCallsign) {
                // Use TrainerUtils to fetch a random callsign
                currentCallsign = CallsignTrainerUtils.getRandomCallsign(
                        getContext(),
                        selectedBuckets,
                        minCallsignLength,
                        maxCallsignLength,
                        isTrainingActive);

                if (currentCallsign == null || currentCallsign.isEmpty()) {
                    throw new IllegalStateException("Unable to fetch a valid callsign.");
                }

                // Debugging log
                Log.d("CTrainerFragment", "Generated callsign: " + currentCallsign);
            }

            // Reset training session variables
            callsignStartTime = SystemClock.elapsedRealtime();
            // Play Morse code for the callsign
            if (morseCodeGenerator != null) {
                morseCodeGenerator.playMorseCode(currentCallsign);
                waitingForReply = true;
            } else {
                throw new IllegalStateException("MorseCodeGenerator is not initialized.");
            }
            waitingForReply = true;
        } catch (Exception e) {
            Log.e("CTrainerFragment", "Error during playNextCallsign: " + e.getMessage());
            stopTrainingSession(); // Stop training on critical error
        }
    }


    private void processInput(String enteredCallsign) {
        // Ensure `currentCallsign` is valid
        if (currentCallsign == null || currentCallsign.isEmpty()) {
            Log.e("TrainerFragment", "Invalid currentCallsign: " + currentCallsign);
            stopTrainingSession();
            return;
        }

        int toastTime = 800;

        // Ensure the entered callsign is not null or empty
        if (enteredCallsign == null || enteredCallsign.isEmpty()) {
            ToastUtils.showCustomToast(requireContext(), "👎 No callsign entered!", toastTime);
            return;
        }

        // Compare the entered callsign to the current callsign
        boolean isCorrect = enteredCallsign.trim().equalsIgnoreCase(currentCallsign);

        // Log the result
        long responseTime = SystemClock.elapsedRealtime() - callsignStartTime;
        String bucket = CallsignUtils.getCallsignBucket(currentCallsign); // Get the bucket this callsign belongs to
        CallsignTrainerUtils.logResult(getContext(), currentCallsign, enteredCallsign, isCorrect, (int) responseTime, currentSpeed, bucket);

        // Update the cache with additional details
        LogCache.addLog(
                "callsign",
                currentCallsign + "," +
                        responseTime + "," +
                        (isCorrect ? "1" : "0") + "," +
                        enteredCallsign + "," +
                        currentSpeed + "," +
                        bucket + "," +
                        TrainerUtils.getCurrentDateTime()
        );

        // Broadcast the update
        Intent intent = new Intent("com.example.cwstataccelerator.UPDATE_LOG");
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);

        // Provide user feedback
        String feedbackMessage;
        if (isCorrect) {
            feedbackMessage = "👍 Correct! Callsign matched.";
            ToastUtils.showCustomToast(requireContext(), feedbackMessage, toastTime);
            playNextCallsign(true); // Move to the next callsign
        } else {
            feedbackMessage = "👎 Incorrect! Try again.";
            ToastUtils.showCustomToast(requireContext(), feedbackMessage, toastTime);
            playNextCallsign(false); // Replay the current callsign
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
