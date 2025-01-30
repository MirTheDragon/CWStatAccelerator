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
import android.app.Activity;

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
    private Spinner simpleSpinner;
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
        simpleSpinner = view.findViewById(R.id.simple_callsign_spinner);
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
        simpleSpinner.setAdapter(adapter);
        slashedSpinner.setAdapter(adapter);
        numberSpinner.setAdapter(adapter);
        letterSpinner.setAdapter(adapter);

        simpleSpinner.setSelection(0);
        slashedSpinner.setSelection(2);
        numberSpinner.setSelection(2);
        letterSpinner.setSelection(2);

        // Load saved checkbox states and slider states
        loadPreferences();

        // Set up the callsign length range slider and checkbox and spinners
        setupCallsignLengthRangeSlider();
        setupListeners();
        enforceLogicSpinnerSelection(simpleSpinner);
        //enforceLogicCheckboxSelection(simpleCallsignCheckbox); // Not necesary to call on load
        updateSelectedBuckets();

        // Input field
        inputField = view.findViewById(R.id.input_field);
        inputField.setEnabled(false); // Initially disabled

        // Log view
        logView = view.findViewById(R.id.log_view);

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
        String maxText = maxLength == 8 ? "8+ Characters" : maxLength + " Characters";

        if (minLength == maxLength && maxLength < 8) {
            callsignLengthRangeLabel.setText("Length: " + maxText);
        } else if (maxLength == 8) {
            callsignLengthRangeLabel.setText("Length: " + minLength + " Characters and Above");
        } else {
            callsignLengthRangeLabel.setText("Length: " + minLength + " to " + maxLength + " Characters");
        }

        Log.d("CallsignTrainerFragment", "Updated label: " + callsignLengthRangeLabel.getText().toString());
    }


    private void setupListeners() {
        // Set up spinner listeners
        Spinner[] spinners = {
                simpleSpinner, slashedSpinner, numberSpinner, letterSpinner
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
        Activity activity = getActivity();
        if (!isAdded() || getActivity() == null) { // Prevents crash
            Log.w("CallsignTrainerFragment", "Fragment not attached. Skipping enforceLogicSpinnerSelection.");
            return;
        }

        boolean hasOnlySelected = changedSpinner.getSelectedItem().toString().equals("Only");
        boolean hasIncludeSelected = changedSpinner.getSelectedItem().toString().equals("Include");
        boolean hasExcludeSelected = changedSpinner.getSelectedItem().toString().equals("Exclude");

        Log.d("CallsignTrainerFragment", "Spinner changed to " + changedSpinner.getSelectedItem().toString());

        Spinner[] spinners = {slashedSpinner, numberSpinner, letterSpinner};
        boolean allExcluded = slashedSpinner.getSelectedItem().toString().equals("Exclude")
                            && numberSpinner.getSelectedItem().toString().equals("Exclude")
                            && letterSpinner.getSelectedItem().toString().equals("Exclude");
        boolean simpleExcluded = simpleSpinner.getSelectedItem().toString().equals("Exclude");


        activity.runOnUiThread(() -> {
            if (changedSpinner == simpleSpinner) {
                if (allExcluded) {
                    simpleSpinner.setSelection(1, true);
                } else {
                    for (Spinner spinner : spinners) {
                        String selectedValue = spinner.getSelectedItem().toString();

                        if (hasOnlySelected) {
                            spinner.setSelection(2, true);
                        } else if (hasIncludeSelected && selectedValue.equals("Only")) {
                            spinner.setSelection(0, true);
                        }
                    }
                }
            } else {
                if (hasExcludeSelected && allExcluded && simpleExcluded) {
                    changedSpinner.setSelection(1, true);
                }
                if (hasOnlySelected) {
                    simpleSpinner.setSelection(2, true);
                } else if (hasIncludeSelected && simpleSpinner.getSelectedItem().toString().equals("Only")) {
                    simpleSpinner.setSelection(0, true);
                }
                for (Spinner spinner : spinners) {
                    String selectedValue = spinner.getSelectedItem().toString();

                    if (hasOnlySelected && selectedValue.equals("Include")) {
                        spinner.setSelection(1, true);
                    } else if (hasIncludeSelected && selectedValue.equals("Only")) {
                        spinner.setSelection(0, true);
                    }
                }
            }
        });
    }

    private void toggleTraining() {
        Log.d("CallsignTrainerFragment", "Toggling training. Current state: " + isTrainingActive);
        isTrainingActive = !isTrainingActive;

        if (isTrainingActive) {
            // Adjust callsign range before starting
            int[] adjustedData = CallsignTrainerUtils.adjustCallsignLengthRange(getContext(), selectedBuckets, minCallsignLength, maxCallsignLength, true);
            int newMin = adjustedData[0];
            int newMax = adjustedData[1];
            int totalCallsigns = adjustedData[2];

            // Apply adjustments to UI if necessary
            if (newMin != minCallsignLength || newMax != maxCallsignLength) {
                callsignLengthRangeSlider.setValueFrom(newMin);
                callsignLengthRangeSlider.setValueTo(newMax);

                String message = "Range adjusted to " + newMin + " - " + newMax + " (Total: " + totalCallsigns + " callsigns)";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                Log.w("CallsignUtils", message);
            }

            Log.d("CallsignTrainerFragment", "Training activated.");
            startTrainingButton.setEnabled(false);
            startCountdown(() -> {
                Log.d("CallsignTrainerFragment", "Countdown finished. Starting training.");
                startTrainingButton.setText("Stop Training");
                startTrainingButton.setEnabled(true);

                inputField.setEnabled(true); // Enable the input field
                inputField.requestFocus(); // Focus on the input field
                showKeyboard(inputField); // Show the keyboard

                waitingForReply = false;
                startTrainingSession(); // Start training logic
            });
        } else {
            Log.d("CallsignTrainerFragment", "Training deactivated.");
            startTrainingButton.setText("Start Training");
            inputField.setEnabled(false); // Disable the input field
            hideKeyboard(inputField); // Hide the keyboard
            stopTrainingSession(); // Stop training logic
        }
    }

    private void startCountdown(Runnable onComplete) {
        Log.d("CallsignTrainerFragment", "Starting countdown.");
        new Thread(() -> {
            for (int i = 3; i > 0; i--) {
                int finalI = i;
                requireActivity().runOnUiThread(() -> {
                    Log.d("CallsignTrainerFragment", "Countdown: " + finalI);
                    startTrainingButton.setText("Starting in " + finalI + "...");
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            requireActivity().runOnUiThread(() -> {
                Log.d("CallsignTrainerFragment", "Countdown complete.");
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

        Log.d("CallsignTrainerFragment", "Starting training session.");
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
        simpleSpinner.setEnabled(enabled);
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

        short callsignDifficultyOnly = 0;
        boolean isSimpleOnly = simpleSpinner.getSelectedItem().toString().equals("Only");
        boolean isSlashedOnly = slashedSpinner.getSelectedItem().toString().equals("Only");
        boolean hasNumbersOnly = numberSpinner.getSelectedItem().toString().equals("Only");
        boolean hasLettersOnly = letterSpinner.getSelectedItem().toString().equals("Only");

        short callsignDifficultyInclude = 0;
        boolean isSimpleIncluded = simpleSpinner.getSelectedItem().toString().equals("Include");
        boolean isSlashedIncluded = slashedSpinner.getSelectedItem().toString().equals("Include");
        boolean hasNumbersIncluded= numberSpinner.getSelectedItem().toString().equals("Include");
        boolean hasLettersIncluded = letterSpinner.getSelectedItem().toString().equals("Include");

        if (isSlashedOnly) callsignDifficultyOnly++;
        if (hasNumbersOnly) callsignDifficultyOnly++;
        if (hasLettersOnly) callsignDifficultyOnly++;

        if (isSlashedIncluded) callsignDifficultyInclude++;
        if (hasNumbersIncluded) callsignDifficultyInclude++;
        if (hasLettersIncluded) callsignDifficultyInclude++;

        if (callsignDifficultyOnly > 0 && callsignDifficultyInclude > 0) {
            Log.d("CallsignTrainerUtils", "Error in interface logic for selected buckets, continuing anyway...");
        }
        // Default to standard callsigns
        if (isSimpleOnly || isSimpleIncluded) {
            selectedBuckets.add("standard_callsigns");
        } else {
            selectedBuckets.remove("standard_callsigns");
        }

        if (callsignDifficultyInclude > 0) {
            if(isSlashedIncluded) selectedBuckets.add("slashes_only");
            if (hasNumbersIncluded) selectedBuckets.add("difficult_numbers");
            if (hasLettersIncluded) selectedBuckets.add("difficult_letters");
            if (isSlashedIncluded && hasNumbersIncluded) selectedBuckets.add("slashes_and_numbers");
            if (hasNumbersIncluded && hasLettersIncluded) selectedBuckets.add("numbers_and_letters");
            if (hasLettersIncluded && isSlashedIncluded) selectedBuckets.add("slashes_and_letters");
            if (isSlashedIncluded && hasNumbersIncluded && hasLettersIncluded) selectedBuckets.add("all_criteria");
        }

        switch (callsignDifficultyOnly) {
            case 1:
                if (isSlashedOnly) {
                    selectedBuckets.add("slashes_only");
                } else if (hasNumbersOnly) {
                    selectedBuckets.add("difficult_numbers");
                } else if (hasLettersOnly) {
                    selectedBuckets.add("difficult_letters");
                }
                break;
            case 2:
                if (isSlashedOnly && hasNumbersOnly) {
                    selectedBuckets.add("slashes_and_numbers");
                } else if (hasNumbersOnly && hasLettersOnly) {
                    selectedBuckets.add("numbers_and_letters");
                } else if (hasLettersOnly && isSlashedOnly) {
                    selectedBuckets.add("slashes_and_letters");
                }
                break;
            case 3:
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

        // Save simple callsign selection
        editor.putInt("simpeleCallsignSelection", simpleSpinner.getSelectedItemPosition());

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
        simpleSpinner.setSelection(preferences.getInt("simpeleCallsignSelection", 0), true);

        // Load dropdown selections
        slashedSpinner.setSelection(preferences.getInt("slashedCallsignsSelection", 0), true);
        numberSpinner.setSelection(preferences.getInt("numberCombinationsSelection", 0), true);
        letterSpinner.setSelection(preferences.getInt("letterCombinationsSelection", 0), true);

        Log.d("CallsignTrainerFragment", "Preferences loaded successfully.");
    }


    private void playNextCallsign(boolean fetchNewCallsign) {
        try {
            if (fetchNewCallsign) {

                // CHeck if callsigns can be extremely long
                int openEndedCallsignLength = maxCallsignLength;

                if ( openEndedCallsignLength >= 10) {
                    openEndedCallsignLength = 100;
                }

                // Use TrainerUtils to fetch a random callsign
                currentCallsign = CallsignTrainerUtils.getRandomCallsign(
                        getContext(),
                        selectedBuckets,
                        minCallsignLength,
                        openEndedCallsignLength,
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
