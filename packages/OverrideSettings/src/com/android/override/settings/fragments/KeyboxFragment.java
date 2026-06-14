/*
 * Copyright (C) 2025 Android Override Project
 * Licensed under the Apache License, Version 2.0
 */

package com.android.override.settings.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.override.OverrideController;
import com.android.override.KeyboxManager;
import com.android.override.settings.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Fragment for keybox management.
 *
 * Features:
 * - Import keybox XML from storage
 * - View loaded keybox info
 * - Manage multiple keybox slots
 * - Keybox health status
 * - Activate/deactivate keybox
 */
public class KeyboxFragment extends Fragment {

    private static final int REQUEST_IMPORT_KEYBOX = 100;

    private OverrideController mController;
    private KeyboxManager mKeyboxManager;

    private Switch mKeyboxSwitch;
    private TextView mKeyboxInfo;
    private TextView mHealthStatus;
    private RecyclerView mSlotsList;
    private Button mImportButton;
    private Button mCheckHealthButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_keybox, container, false);
        mController = OverrideController.getInstance();
        mKeyboxManager = KeyboxManager.getInstance();

        // Keybox enable switch
        mKeyboxSwitch = view.findViewById(R.id.switch_keybox);
        mKeyboxSwitch.setChecked(mController.isKeyboxEnabled());
        mKeyboxSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mController.setKeyboxEnabled(isChecked);
            if (isChecked) {
                mKeyboxManager.loadActiveKeybox();
            }
            updateDisplay();
        });

        // Info displays
        mKeyboxInfo = view.findViewById(R.id.text_keybox_info);
        mHealthStatus = view.findViewById(R.id.text_health_status);

        // Import button
        mImportButton = view.findViewById(R.id.btn_import_keybox);
        mImportButton.setOnClickListener(v -> openFilePicker());

        // Health check button
        mCheckHealthButton = view.findViewById(R.id.btn_check_health);
        mCheckHealthButton.setOnClickListener(v -> checkHealth());

        // Keybox slots list
        mSlotsList = view.findViewById(R.id.list_keybox_slots);
        mSlotsList.setLayoutManager(new LinearLayoutManager(getContext()));
        updateSlotsList();

        // Import as named slot
        Button importNamedButton = view.findViewById(R.id.btn_import_named);
        if (importNamedButton != null) {
            importNamedButton.setOnClickListener(v -> {
                // Show dialog to name the slot, then import
                showNameDialog();
            });
        }

        updateDisplay();
        return view;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"text/xml", "application/xml", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_IMPORT_KEYBOX);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMPORT_KEYBOX && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importKeyboxFromUri(uri);
            }
        }
    }

    private void importKeyboxFromUri(Uri uri) {
        try {
            // Copy file to temp location
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            File tempFile = new File(getContext().getCacheDir(), "keybox_import.xml");
            FileOutputStream fos = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            is.close();

            // Import via controller
            boolean success = mController.importKeybox(tempFile.getAbsolutePath());
            tempFile.delete();

            if (success) {
                mKeyboxManager.reload();
                Toast.makeText(getContext(), "✅ Keybox imported successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "❌ Invalid keybox XML", Toast.LENGTH_LONG).show();
            }

            updateDisplay();
        } catch (Exception e) {
            Toast.makeText(getContext(), "❌ Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkHealth() {
        boolean healthy = mKeyboxManager.checkHealth();
        if (healthy) {
            mHealthStatus.setText("✅ Keybox is healthy — certificates valid");
            mHealthStatus.setTextColor(0xFF4CAF50); // Green
        } else if (mKeyboxManager.isRevoked()) {
            mHealthStatus.setText("🔴 Keybox is REVOKED — replace with a new keybox");
            mHealthStatus.setTextColor(0xFFF44336); // Red
        } else {
            mHealthStatus.setText("⚠️ Keybox not loaded");
            mHealthStatus.setTextColor(0xFFFF9800); // Orange
        }
    }

    private void updateDisplay() {
        // Keybox info
        mKeyboxInfo.setText(mKeyboxManager.getKeyboxInfo());

        // Health status
        if (mKeyboxManager.isLoaded()) {
            if (mKeyboxManager.isRevoked()) {
                mHealthStatus.setText("🔴 REVOKED");
                mHealthStatus.setTextColor(0xFFF44336);
            } else {
                mHealthStatus.setText("✅ Active");
                mHealthStatus.setTextColor(0xFF4CAF50);
            }
        } else {
            mHealthStatus.setText("No keybox loaded");
            mHealthStatus.setTextColor(0xFF9E9E9E);
        }
    }

    private void updateSlotsList() {
        String[] slots = mController.listKeyboxSlots();
        // Update RecyclerView adapter with slots
        // Each slot shows: name, activate button, delete button
    }

    private void showNameDialog() {
        // Show AlertDialog with EditText to name the keybox slot
        // Then open file picker and import to that slot
    }
}
