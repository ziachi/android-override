/*
 * Copyright (C) 2025 Android Override Project
 * Licensed under the Apache License, Version 2.0
 */

package com.android.override.settings.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.android.override.OverrideController;
import com.android.override.settings.R;

import java.util.ArrayList;
import java.util.Map;

/**
 * Fragment for configuring device fingerprint spoofing.
 *
 * Features:
 * - Select from props database (dropdown)
 * - Manual fingerprint input
 * - Individual field overrides (model, manufacturer, etc.)
 * - Live preview of spoofed identity
 */
public class FingerprintFragment extends Fragment {

    private OverrideController mController;

    private Spinner mPropsSpinner;
    private EditText mFingerprintEdit;
    private EditText mModelEdit;
    private EditText mManufacturerEdit;
    private EditText mProductEdit;
    private EditText mDeviceEdit;
    private EditText mSecurityPatchEdit;
    private TextView mPreviewText;

    private boolean mIgnoreTextChange = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fingerprint, container, false);
        mController = OverrideController.getInstance();

        // Props database spinner
        mPropsSpinner = view.findViewById(R.id.spinner_props);
        setupPropsSpinner();

        // Edit fields
        mFingerprintEdit = view.findViewById(R.id.edit_fingerprint);
        mModelEdit = view.findViewById(R.id.edit_model);
        mManufacturerEdit = view.findViewById(R.id.edit_manufacturer);
        mProductEdit = view.findViewById(R.id.edit_product);
        mDeviceEdit = view.findViewById(R.id.edit_device);
        mSecurityPatchEdit = view.findViewById(R.id.edit_security_patch);
        mPreviewText = view.findViewById(R.id.text_preview);

        // Load current values
        loadCurrentValues();

        // Text watchers for auto-save
        addAutoSave(mFingerprintEdit, value -> mController.setFingerprint(value));
        addAutoSave(mModelEdit, value -> mController.setModel(value));
        addAutoSave(mManufacturerEdit, value -> mController.setManufacturer(value));
        addAutoSave(mProductEdit, value -> mController.setProduct(value));
        addAutoSave(mDeviceEdit, value -> mController.setDevice(value));
        addAutoSave(mSecurityPatchEdit, value -> mController.setSecurityPatch(value));

        // Parse fingerprint button
        view.findViewById(R.id.btn_parse_fingerprint).setOnClickListener(v -> {
            parseFingerprint();
        });

        return view;
    }

    private void setupPropsSpinner() {
        Map<String, OverrideController.PropsEntry> database = mController.getPropsDatabase();
        ArrayList<String> labels = new ArrayList<>();
        labels.add("-- Select device --");
        labels.addAll(database.keySet());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mPropsSpinner.setAdapter(adapter);

        mPropsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) return; // Skip "Select device"
                String label = labels.get(position);
                mController.applyPropsEntry(label);
                loadCurrentValues();
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadCurrentValues() {
        mIgnoreTextChange = true;
        setText(mFingerprintEdit, mController.getFingerprint());
        setText(mModelEdit, mController.getModel());
        setText(mManufacturerEdit, mController.getManufacturer());
        setText(mProductEdit, mController.getProduct());
        setText(mDeviceEdit, mController.getDevice());
        setText(mSecurityPatchEdit, mController.getSecurityPatch());
        mIgnoreTextChange = false;
        updatePreview();
    }

    private void setText(EditText edit, String value) {
        edit.setText(value != null ? value : "");
    }

    /**
     * Parse fingerprint string and auto-fill individual fields.
     * Format: brand/product/device:version/buildId/incremental:type/tags
     */
    private void parseFingerprint() {
        String fp = mFingerprintEdit.getText().toString().trim();
        if (fp.isEmpty()) return;

        try {
            String[] mainParts = fp.split(":");
            if (mainParts.length >= 2) {
                String[] brandParts = mainParts[0].split("/");
                if (brandParts.length >= 3) {
                    // brand/product/device
                    mManufacturerEdit.setText(brandParts[0]);
                    mProductEdit.setText(brandParts[1]);
                    mDeviceEdit.setText(brandParts[2]);
                }

                String[] versionParts = mainParts[1].split("/");
                if (versionParts.length >= 3) {
                    // Security patch from build ID
                    String buildId = versionParts[1];
                    // Try to extract date: AP4A.250205.002 → 2025-02-05
                    if (buildId.length() >= 11) {
                        String dateStr = buildId.substring(5, 11);
                        if (dateStr.length() == 6) {
                            String patch = "20" + dateStr.substring(0, 2) + "-"
                                    + dateStr.substring(2, 4) + "-"
                                    + dateStr.substring(4, 6);
                            mSecurityPatchEdit.setText(patch);
                        }
                    }
                }

                if (mainParts.length >= 3) {
                    String typeTags = mainParts[2]; // type/tags
                    // Could extract type (user/userdebug) if needed
                }
            }

            // Auto-detect model from known devices
            String brand = fp.split("/")[0].toLowerCase();
            if ("google".equals(brand)) {
                String device = fp.split("/")[2].split(":")[0];
                mModelEdit.setText(getGoogleModelName(device));
            }

            updatePreview();
        } catch (Exception e) {
            // Ignore parse errors
        }
    }

    private String getGoogleModelName(String device) {
        switch (device) {
            case "husky": return "Pixel 8 Pro";
            case "shiba": return "Pixel 8";
            case "akita": return "Pixel 8a";
            case "caiman": return "Pixel 9";
            case "komodo": return "Pixel 9 Pro";
            case "comet": return "Pixel 9 Pro Fold";
            case "tokay": return "Pixel 9a";
            case "cheetah": return "Pixel 7 Pro";
            case "panther": return "Pixel 7";
            case "lynx": return "Pixel 7a";
            case "felix": return "Pixel Fold";
            default: return device;
        }
    }

    private void updatePreview() {
        StringBuilder sb = new StringBuilder();
        sb.append("Spoofed Identity:\n\n");
        sb.append("Fingerprint: ").append(nullSafe(mFingerprintEdit)).append("\n");
        sb.append("Model: ").append(nullSafe(mModelEdit)).append("\n");
        sb.append("Manufacturer: ").append(nullSafe(mManufacturerEdit)).append("\n");
        sb.append("Product: ").append(nullSafe(mProductEdit)).append("\n");
        sb.append("Device: ").append(nullSafe(mDeviceEdit)).append("\n");
        sb.append("Security Patch: ").append(nullSafe(mSecurityPatchEdit));
        mPreviewText.setText(sb.toString());
    }

    private String nullSafe(EditText edit) {
        String text = edit.getText().toString().trim();
        return text.isEmpty() ? "(not set)" : text;
    }

    private void addAutoSave(EditText edit, ValueSetter setter) {
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!mIgnoreTextChange) {
                    setter.set(s.toString().trim());
                    updatePreview();
                }
            }
        });
    }

    private interface ValueSetter {
        void set(String value);
    }
}
