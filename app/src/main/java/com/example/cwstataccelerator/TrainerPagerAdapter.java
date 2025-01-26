package com.example.cwstataccelerator;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class TrainerPagerAdapter extends FragmentStateAdapter {

    public TrainerPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new PerformanceMetricsFragment(); // Tab 1
        } else {
            return new RecentLogFragment(); // Tab 2
        }
    }

    @Override
    public int getItemCount() {
        return 2; // Two tabs
    }
}
