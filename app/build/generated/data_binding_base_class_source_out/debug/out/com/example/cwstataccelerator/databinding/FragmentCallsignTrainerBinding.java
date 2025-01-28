// Generated by view binder compiler. Do not edit!
package com.example.cwstataccelerator.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import androidx.viewpager2.widget.ViewPager2;
import com.example.cwstataccelerator.R;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.tabs.TabLayout;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class FragmentCallsignTrainerBinding implements ViewBinding {
  @NonNull
  private final LinearLayout rootView;

  @NonNull
  public final TextView callsignLengthRangeLabel;

  @NonNull
  public final RangeSlider callsignLengthRangeSlider;

  @NonNull
  public final TextView countdownTimer;

  @NonNull
  public final CheckBox difficultLetterCombinationsCheckbox;

  @NonNull
  public final CheckBox includeSpecialCharactersCheckbox;

  @NonNull
  public final EditText inputField;

  @NonNull
  public final CheckBox numbersPlacementCheckbox;

  @NonNull
  public final Button startTrainingButton;

  @NonNull
  public final TabLayout tabLayout;

  @NonNull
  public final ViewPager2 viewPager;

  private FragmentCallsignTrainerBinding(@NonNull LinearLayout rootView,
      @NonNull TextView callsignLengthRangeLabel, @NonNull RangeSlider callsignLengthRangeSlider,
      @NonNull TextView countdownTimer, @NonNull CheckBox difficultLetterCombinationsCheckbox,
      @NonNull CheckBox includeSpecialCharactersCheckbox, @NonNull EditText inputField,
      @NonNull CheckBox numbersPlacementCheckbox, @NonNull Button startTrainingButton,
      @NonNull TabLayout tabLayout, @NonNull ViewPager2 viewPager) {
    this.rootView = rootView;
    this.callsignLengthRangeLabel = callsignLengthRangeLabel;
    this.callsignLengthRangeSlider = callsignLengthRangeSlider;
    this.countdownTimer = countdownTimer;
    this.difficultLetterCombinationsCheckbox = difficultLetterCombinationsCheckbox;
    this.includeSpecialCharactersCheckbox = includeSpecialCharactersCheckbox;
    this.inputField = inputField;
    this.numbersPlacementCheckbox = numbersPlacementCheckbox;
    this.startTrainingButton = startTrainingButton;
    this.tabLayout = tabLayout;
    this.viewPager = viewPager;
  }

  @Override
  @NonNull
  public LinearLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static FragmentCallsignTrainerBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static FragmentCallsignTrainerBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.fragment_callsign_trainer, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static FragmentCallsignTrainerBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.callsign_length_range_label;
      TextView callsignLengthRangeLabel = ViewBindings.findChildViewById(rootView, id);
      if (callsignLengthRangeLabel == null) {
        break missingId;
      }

      id = R.id.callsign_length_range_slider;
      RangeSlider callsignLengthRangeSlider = ViewBindings.findChildViewById(rootView, id);
      if (callsignLengthRangeSlider == null) {
        break missingId;
      }

      id = R.id.countdown_timer;
      TextView countdownTimer = ViewBindings.findChildViewById(rootView, id);
      if (countdownTimer == null) {
        break missingId;
      }

      id = R.id.difficult_letter_combinations_checkbox;
      CheckBox difficultLetterCombinationsCheckbox = ViewBindings.findChildViewById(rootView, id);
      if (difficultLetterCombinationsCheckbox == null) {
        break missingId;
      }

      id = R.id.include_special_characters_checkbox;
      CheckBox includeSpecialCharactersCheckbox = ViewBindings.findChildViewById(rootView, id);
      if (includeSpecialCharactersCheckbox == null) {
        break missingId;
      }

      id = R.id.input_field;
      EditText inputField = ViewBindings.findChildViewById(rootView, id);
      if (inputField == null) {
        break missingId;
      }

      id = R.id.numbers_placement_checkbox;
      CheckBox numbersPlacementCheckbox = ViewBindings.findChildViewById(rootView, id);
      if (numbersPlacementCheckbox == null) {
        break missingId;
      }

      id = R.id.start_training_button;
      Button startTrainingButton = ViewBindings.findChildViewById(rootView, id);
      if (startTrainingButton == null) {
        break missingId;
      }

      id = R.id.tab_layout;
      TabLayout tabLayout = ViewBindings.findChildViewById(rootView, id);
      if (tabLayout == null) {
        break missingId;
      }

      id = R.id.view_pager;
      ViewPager2 viewPager = ViewBindings.findChildViewById(rootView, id);
      if (viewPager == null) {
        break missingId;
      }

      return new FragmentCallsignTrainerBinding((LinearLayout) rootView, callsignLengthRangeLabel,
          callsignLengthRangeSlider, countdownTimer, difficultLetterCombinationsCheckbox,
          includeSpecialCharactersCheckbox, inputField, numbersPlacementCheckbox,
          startTrainingButton, tabLayout, viewPager);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}
