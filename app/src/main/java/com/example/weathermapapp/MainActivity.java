package com.example.weathermapapp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private int currentNavIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment(), false);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            int newIndex;
            Fragment fragment;

            if (id == R.id.nav_home) {
                newIndex = 0;
                fragment = new HomeFragment();
            } else if (id == R.id.nav_map) {
                newIndex = 1;
                fragment = new MapFragment();
            } else if (id == R.id.nav_profile) {
                newIndex = 2;
                fragment = new ProfileFragment();
            } else {
                return false;
            }

            boolean goingRight = newIndex > currentNavIndex;
            currentNavIndex = newIndex;
            loadFragment(fragment, goingRight);
            return true;
        });
    }

    private void loadFragment(Fragment fragment, boolean goingRight) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        goingRight ? R.anim.slide_in_right : R.anim.slide_in_left,
                        goingRight ? R.anim.slide_out_left : R.anim.slide_out_right
                )
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
