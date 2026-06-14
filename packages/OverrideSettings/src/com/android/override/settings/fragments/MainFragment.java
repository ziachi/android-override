/*
 * Copyright (C) 2025 Android Override Project
 * Licensed under the Apache License, Version 2.0
 */

package com.android.override.settings.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.android.override.OverrideController;
import com.android.override.KeyboxManager;
import com.android.override.services.IntegrityChecker;
import com.android.override.settings.OverrideSettingsActivity;
import com.android.override.settings.R;

/**
 * Main fragment showing override status and navigation to sub-sections.
 */
public class MainFragment extends Fragment {

    private OverrideController mController;
    private Switch mMasterSwitch;
    private TextView mStatusText;
    private TextView mPiStatus;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        mController = OverrideController.getInstance();

        // Master switch
        mMasterSwitch = view.findViewById(R.id.switch_master);
        mMasterSwitch.setChecked(mController.isEnabled());
        mMasterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mController.setEnabled(isChecked);
            updateStatus();
        });

        // Status display
        mStatusText = view.findViewById(R.id.text_status);
        mPiStatus = view.findViewById(R.id.text_pi_status);
        updateStatus();

        // Navigation cards
        setupCard(view, R.id.card_fingerprint, "fingerprint");
        setupCard(view, R.id.card_keybox, "keybox");
        setupCard(view, R.id.card_per_app, "per_app");
        setupCard(view, R.id.card_profiles, "profiles");
        setupCard(view, R.id.card_integrity, "integrity");
        setupCard(view, R.id.card_anti_detection, "anti_detection");

        return view;
    }

    private void setupCard(View root, int cardId, String section) {
        CardView card = root.findViewById(cardId);
        if (card != null) {
            card.setOnClickListener(v -> {
                OverrideSettingsActivity activity = (OverrideSettingsActivity) getActivity();
                if (activity != null) {
                    activity.navigateTo(section);
                }
            });
        }
    }

    private void updateStatus() {
        if (mController.isEnabled()) {
            String fp = mController.getFingerprint();
            String status = "✅ Active";
            if (fp != null && !fp.isEmpty()) {
                // Show short fingerprint
                String[] parts = fp.split("/");
                if (parts.length >= 2) {
                    status += "\n" + parts[0] + "/" + parts[1];
                }
            }
            mStatusText.setText(status);

            // Quick PI check
            mPiStatus.setText(IntegrityChecker.quickCheck());
        } else {
            mStatusText.setText("❌ Disabled");
            mPiStatus.setText("Override is disabled");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatus();
    }
}
