/*
 * Copyright (C) 2025 Android Override Project
 * Licensed under the Apache License, Version 2.0
 */

package com.android.override.settings;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.android.override.settings.R;
import com.android.override.settings.fragments.*;

/**
 * Main activity for Override Settings.
 *
 * Hosts all settings fragments and provides navigation
 * between different configuration sections.
 *
 * Can be launched:
 * - As standalone app
 * - From Settings → System → Override (via intent filter)
 */
public class OverrideSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.app_name);
        }

        if (savedInstanceState == null) {
            loadFragment(new MainFragment(), false);
        }
    }

    public void loadFragment(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment);

        if (addToBackStack) {
            transaction.addToBackStack(null);
        }

        transaction.commit();
    }

    public void navigateTo(String section) {
        Fragment fragment;
        switch (section) {
            case "fingerprint":
                fragment = new FingerprintFragment();
                break;
            case "keybox":
                fragment = new KeyboxFragment();
                break;
            case "anti_detection":
                fragment = new AntiDetectionFragment();
                break;
            default:
                fragment = new MainFragment();
                break;
        }
        loadFragment(fragment, true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
