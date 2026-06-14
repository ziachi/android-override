/*
 * Copyright (C) 2025 Android Override Project
 * Licensed under the Apache License, Version 2.0
 */

package com.android.override.settings.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.override.OverrideController;
import com.android.override.KeyboxManager;
import com.android.override.settings.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class KeyboxFragment extends Fragment {

    private static final int REQUEST_IMPORT_KEYBOX = 100;

    private OverrideController mController;
    private KeyboxManager mKeyboxManager;

    private Switch mKeyboxSwitch;
    private TextView mKeyboxStatus;
    private TextView mKeyboxAlgorithm;
    private TextView mKeyboxSlot;
    private TextView mKeyboxHealth;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_keybox, container, false);
        mController = OverrideController.getInstance();
        mKeyboxManager = KeyboxManager.getInstance();

        mKeyboxSwitch = view.findViewById(R.id.switch_keybox);
        mKeyboxSwitch.setChecked(mController.isKeyboxEnabled());
        mKeyboxSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mController.setKeyboxEnabled(isChecked);
            if (isChecked) {
                mKeyboxManager.loadActiveKeybox();
            }
            updateStatus();
        });

        mKeyboxStatus = view.findViewById(R.id.text_keybox_status);
        mKeyboxAlgorithm = view.findViewById(R.id.text_keybox_algorithm);
        mKeyboxSlot = view.findViewById(R.id.text_keybox_slot);
        mKeyboxHealth = view.findViewById(R.id.text_keybox_health);

        // Import from file picker — use */* to accept all file types
        view.findViewById(R.id.btn_import_keybox).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(intent, "Select Keybox XML"),
                    REQUEST_IMPORT_KEYBOX);
        });

        view.findViewById(R.id.btn_import_path).setOnClickListener(v ->
                showImportFromPathDialog());
        view.findViewById(R.id.btn_health_check).setOnClickListener(v ->
                checkHealth());
        view.findViewById(R.id.btn_manage_slots).setOnClickListener(v ->
                showSlotsDialog());

        updateStatus();
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_KEYBOX
                && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            importKeyboxFromUri(data.getData());
        }
    }

    private void importKeyboxFromUri(Uri uri) {
        try {
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            File tempFile = new File(getContext().getCacheDir(), "keybox_import.xml");
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
            fos.close();
            is.close();

            boolean success = mController.importKeybox(tempFile.getAbsolutePath());
            tempFile.delete();

            if (success) {
                mKeyboxManager.reload();
                Toast.makeText(getContext(), "Keybox imported!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Invalid keybox XML", Toast.LENGTH_LONG).show();
            }

            updateStatus();
        } catch (Exception e) {
            Toast.makeText(getContext(),
                    "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showImportFromPathDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Import Keybox from Path");

        final EditText input = new EditText(getContext());
        input.setHint("/sdcard/keybox.xml");
        builder.setView(input);

        builder.setPositiveButton("Import", (dialog, which) -> {
            String path = input.getText().toString().trim();
            if (!path.isEmpty()) {
                boolean success = mController.importKeybox(path);
                if (success) {
                    mKeyboxManager.reload();
                    Toast.makeText(getContext(), "Keybox imported!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Import failed", Toast.LENGTH_LONG).show();
                }
                updateStatus();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showSlotsDialog() {
        String[] slots = mController.listKeyboxSlots();

        if (slots.length == 0) {
            Toast.makeText(getContext(), "No keybox slots", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentSlot = mController.getActiveKeyboxSlot();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Keybox Slots");

        String[] displaySlots = new String[slots.length];
        int checkedItem = 0;
        for (int i = 0; i < slots.length; i++) {
            displaySlots[i] = slots[i]
                    + (slots[i].equals(currentSlot) ? " (active)" : "");
            if (slots[i].equals(currentSlot)) checkedItem = i;
        }

        final int[] selectedIndex = {checkedItem};
        builder.setSingleChoiceItems(displaySlots, checkedItem,
                (dialog, which) -> selectedIndex[0] = which);

        builder.setPositiveButton("Switch", (dialog, which) -> {
            mController.setActiveKeyboxSlot(slots[selectedIndex[0]]);
            mKeyboxManager.reload();
            Toast.makeText(getContext(), "Switched to: " + slots[selectedIndex[0]],
                    Toast.LENGTH_SHORT).show();
            updateStatus();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void checkHealth() {
        if (!mKeyboxManager.isLoaded()) {
            mKeyboxManager.loadActiveKeybox();
        }

        boolean healthy = mKeyboxManager.checkHealth();
        if (healthy) {
            mKeyboxHealth.setText("Healthy — certificates valid");
            mKeyboxHealth.setTextColor(0xFF4CAF50);
        } else if (mKeyboxManager.isRevoked()) {
            mKeyboxHealth.setText("REVOKED — replace with a new keybox");
            mKeyboxHealth.setTextColor(0xFFF44336);
        } else {
            mKeyboxHealth.setText("Not loaded");
            mKeyboxHealth.setTextColor(0xFFFF9800);
        }
        mKeyboxHealth.setVisibility(View.VISIBLE);
    }

    private void updateStatus() {
        if (mController.isKeyboxEnabled() && mKeyboxManager.isLoaded()) {
            mKeyboxStatus.setText("Loaded");
            mKeyboxAlgorithm.setText("Algorithm: " + mKeyboxManager.getAlgorithm());
            mKeyboxAlgorithm.setVisibility(View.VISIBLE);
            mKeyboxSlot.setText("Device: " + (mKeyboxManager.getDeviceId() != null ?
                    mKeyboxManager.getDeviceId() : "N/A"));
            mKeyboxSlot.setVisibility(View.VISIBLE);
        } else if (mController.isKeyboxEnabled()) {
            mKeyboxStatus.setText("Enabled but not loaded");
            mKeyboxAlgorithm.setVisibility(View.GONE);
            mKeyboxSlot.setVisibility(View.GONE);
        } else {
            mKeyboxStatus.setText("Disabled");
            mKeyboxAlgorithm.setVisibility(View.GONE);
            mKeyboxSlot.setVisibility(View.GONE);
        }
    }
}
