package com.example.cwstataccelerator;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.slider.RangeSlider;

import java.util.List;

public class CallsignTrainerFragment extends Fragment {

    // UI Elements
    private TextView callsignLengthRangeLabel;
    private RangeSlider callsignLengthRangeSlider;
    private CheckBox includeSpecialCharactersCheckbox;
    private CheckBox numbersPlacementCheckbox;
    private CheckBox difficultLetterCombinationsCheckbox;
    private Button startTrainingButton;

    @Nullable
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("CallsignTrainerFragment", "onCreate called");
    }


    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d("CallsignTrainerFragment", "Initializing CallsignTrainerFragment");
        View view = inflater.inflate(R.layout.fragment_callsign_trainer, container, false);


        // Initialize tabs and view pager
        setupTabsAndViewPager(view);

        // Countdown Timer and Training Button setup
        TextView countdownTimer = view.findViewById(R.id.countdown_timer);
        Button startTrainingButton = view.findViewById(R.id.start_training_button);
        startTrainingButton.setOnClickListener(v -> {
            // TODO: Add countdown timer and training logic
            countdownTimer.setVisibility(View.VISIBLE);
            countdownTimer.setText("Starting in 3...");
            // Add countdown logic here
        });

        // Initialize UI elements
        callsignLengthRangeLabel = view.findViewById(R.id.callsign_length_range_label);
        callsignLengthRangeSlider = view.findViewById(R.id.callsign_length_range_slider);
        includeSpecialCharactersCheckbox = view.findViewById(R.id.include_special_characters_checkbox);
        numbersPlacementCheckbox = view.findViewById(R.id.numbers_placement_checkbox);
        difficultLetterCombinationsCheckbox = view.findViewById(R.id.difficult_letter_combinations_checkbox);
        startTrainingButton = view.findViewById(R.id.start_training_button);

        // Set up the callsign length range slider
        setupCallsignLengthRangeSlider();

        // Handle the Start Training button click
        setupStartTrainingButton(view);

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
        } catch (Exception e) {
            Log.e("CallsignTrainerFragment", "Error initializing tabs and view pager: " + e.getMessage());
        }
    }

    private void setupCallsignLengthRangeSlider() {
        // Default range: min = 3, max = 14+
        callsignLengthRangeSlider.setValues(3f, 15f);

        // Update the label dynamically as the user moves the slider
        callsignLengthRangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            int minLength = Math.round(values.get(0));
            int maxLength = Math.round(values.get(1));
            String maxText = maxLength == 15 ? "15+ Characters" : maxLength + " Characters";

            if(minLength == maxLength && maxLength < 15) {
                callsignLengthRangeLabel.setText( "Callsign Length: " + maxText + " Characters");
            } else if (  maxLength == 15){
                callsignLengthRangeLabel.setText( "Callsign Length: " + minLength + " Characters and Above");
            } else {
                callsignLengthRangeLabel.setText( "Callsign Length: " + minLength + " to " + maxLength +" Characters");
            }

        });
    }

    private void setupStartTrainingButton(View view) {
        Button startTrainingButton = view.findViewById(R.id.start_training_button);

        if (startTrainingButton == null) {
            Log.e("CallsignTrainerFragment", "start_training_button not found in layout!");
            return; // Prevent further errors
        }

        startTrainingButton.setOnClickListener(v -> {
            Log.d("CallsignTrainerFragment", "Start Training button clicked!");
            // Add your training start logic here
        });
    }
}
