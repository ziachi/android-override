/*
 * Copyright (C) 2025 Android Override Project
 * Licensed under the Apache License, Version 2.0
 */

package com.android.override.settings.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.android.override.services.IntegrityChecker;
import com.android.override.settings.R;

/**
 * Fragment for built-in Play Integrity checker.
 *
 * Checks current spoofing state and predicts PI level
 * without requiring external apps.
 */
public class IntegrityFragment extends Fragment {

    private TextView mResultText;
    private TextView mBasicStatus;
    private TextView mDeviceStatus;
    private TextView mStrongStatus;
    private TextView mDiagText;
    private ProgressBar mProgress;
    private Button mCheckButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_integrity, container, false);

        mBasicStatus = view.findViewById(R.id.text_basic);
        mDeviceStatus = view.findViewById(R.id.text_device);
        mStrongStatus = view.findViewById(R.id.text_strong);
        mDiagText = view.findViewById(R.id.text_diagnostics);
        mProgress = view.findViewById(R.id.progress_check);
        mCheckButton = view.findViewById(R.id.btn_check);

        mCheckButton.setOnClickListener(v -> runCheck());

        // Auto-check on open
        runCheck();

        return view;
    }

    private void runCheck() {
        mProgress.setVisibility(View.VISIBLE);
        mCheckButton.setEnabled(false);

        // Run check in background
        new Thread(() -> {
            IntegrityChecker.CheckResult result = IntegrityChecker.runCheck(getContext());

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    mProgress.setVisibility(View.GONE);
                    mCheckButton.setEnabled(true);
                    displayResult(result);
                });
            }
        }).start();
    }

    private void displayResult(IntegrityChecker.CheckResult result) {
        // PI levels
        setStatus(mBasicStatus, "BASIC", result.predictBasic);
        setStatus(mDeviceStatus, "DEVICE", result.predictDevice);
        setStatus(mStrongStatus, "STRONG", result.predictStrong);

        // Diagnostics
        mDiagText.setText(result.getSummary());
    }

    private void setStatus(TextView view, String label, boolean pass) {
        if (pass) {
            view.setText(label + "  ✅");
            view.setTextColor(0xFF4CAF50);
        } else {
            view.setText(label + "  ❌");
            view.setTextColor(0xFFF44336);
        }
    }
}
