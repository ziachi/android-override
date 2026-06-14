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

import androidx.fragment.app.Fragment;

import com.android.override.OverrideController;
import com.android.override.settings.OverrideSettingsActivity;
import com.android.override.settings.R;

public class MainFragment extends Fragment {

    private Switch mMasterSwitch;
    private TextView mStatusText;
    private TextView mFingerprintPreview;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        OverrideController controller = OverrideController.getInstance();

        mMasterSwitch = view.findViewById(R.id.switch_master);
        mMasterSwitch.setChecked(controller.isEnabled());
        mMasterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            controller.setEnabled(isChecked);
            updateStatus();
        });

        mStatusText = view.findViewById(R.id.text_status);
        mFingerprintPreview = view.findViewById(R.id.text_fingerprint_preview);

        view.findViewById(R.id.card_fingerprint).setOnClickListener(v ->
                navigateTo("fingerprint"));
        view.findViewById(R.id.card_keybox).setOnClickListener(v ->
                navigateTo("keybox"));
        view.findViewById(R.id.card_integrity).setOnClickListener(v ->
                navigateTo("integrity"));
        view.findViewById(R.id.card_anti_detection).setOnClickListener(v ->
                navigateTo("anti_detection"));

        updateStatus();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        OverrideController controller = OverrideController.getInstance();

        if (controller.isEnabled()) {
            String fp = controller.getFingerprint();
            if (fp != null && !fp.isEmpty()) {
                mStatusText.setText("Active — Override enabled");
                String display = fp.length() > 50 ? fp.substring(0, 50) + "..." : fp;
                mFingerprintPreview.setText(display);
                mFingerprintPreview.setVisibility(View.VISIBLE);
            } else {
                mStatusText.setText("Enabled — No fingerprint set");
                mFingerprintPreview.setVisibility(View.GONE);
            }
        } else {
            mStatusText.setText("Disabled — Device uses real identity");
            mFingerprintPreview.setVisibility(View.GONE);
        }
    }

    private void navigateTo(String section) {
        ((OverrideSettingsActivity) getActivity()).navigateTo(section);
    }
}
