package com.example.cwstataccelerator;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawer;
    private NavigationView navigationView;
    private ActionBarDrawerToggle toggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure this layout file has a Toolbar with id "toolbar" and the DrawerLayout structure
        setContentView(R.layout.activity_main);

        // Load logs into memory
        LogCache.loadLogs(this);

        // Set up the Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ImageView appIcon = findViewById(R.id.app_icon);

        // Dynamically set height to match the toolbar height
        int toolbarHeight = toolbar.getLayoutParams().height;
        appIcon.getLayoutParams().height = toolbarHeight;
        appIcon.getLayoutParams().width = toolbarHeight; // Make it square
        appIcon.requestLayout();

        // Adjust the icon's height dynamically
        toolbar.post(() -> {
            ViewGroup.LayoutParams layoutParams = appIcon.getLayoutParams();
            layoutParams.height = toolbar.getHeight(); // Match the Toolbar height
            layoutParams.width = (int)(toolbar.getHeight() * 1.625); // Match the Toolbar height
            appIcon.setLayoutParams(layoutParams);
        });

        // Initialize DrawerLayout and NavigationView
        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Set up the ActionBarDrawerToggle to tie together the DrawerLayout and the Toolbar
        toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // Load the default fragment (HomeFragment) when starting
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
            navigationView.setCheckedItem(R.id.nav_home);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            selectedFragment = new HomeFragment();
        } else if (id == R.id.nav_cw_speed) {
            selectedFragment = new CwSpeedFragment();
        } else if (id == R.id.nav_reference) {
            selectedFragment = new ReferenceSheetFragment();
        } else if (id == R.id.nav_trainer) {
            selectedFragment = new TrainerFragment();
        }

        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        // Close drawer if open, else default back behavior
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
