package com.example.cwstataccelerator;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class TrainerPagerAdapter extends FragmentStateAdapter {
    public enum TrainerType {
        CHARACTER,
        CALLSIGN
    }

    private final TrainerType trainerType;

    public TrainerPagerAdapter(@NonNull Fragment fragment, TrainerType trainerType) {
        super(fragment);
        this.trainerType = trainerType;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (trainerType) {
            case CHARACTER:
                if (position == 0) {
                    return new PerformanceMetricsFragment(); // Tab 1 for Character Trainer
                } else {
                    return new RecentLogFragment(); // Tab 2 for Character Trainer
                }
            case CALLSIGN:
                if (position == 0) {
                    return new CallsignPerformanceMetricsFragment(); // Tab 1 for Callsign Trainer
                } else {
                    return new CallsignRecentLogFragment(); // Tab 2 for Callsign Trainer
                }
            default:
                throw new IllegalStateException("Unknown TrainerType: " + trainerType);
        }
    }

    @Override
    public int getItemCount() {
        return 2; // Two tabs
    }
}
