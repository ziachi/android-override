/*
 * Copyright (C) 2025 Android Override Project
 * Licensed under the Apache License, Version 2.0
 */

package com.android.override.settings.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.android.override.OverrideController;
import com.android.override.settings.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;

public class FingerprintFragment extends Fragment {

    private static final int PICK_JSON_FILE = 2001;

    private OverrideController mController;

    private EditText mFingerprintEdit;
    private EditText mModelEdit;
    private EditText mManufacturerEdit;
    private EditText mProductEdit;
    private EditText mDeviceEdit;
    private EditText mBrandEdit;
    private EditText mSecurityPatchEdit;
    private Spinner mDatabaseSpinner;

    private boolean mIgnoreTextChange = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fingerprint, container, false);
        mController = OverrideController.getInstance();

        mFingerprintEdit = view.findViewById(R.id.edit_fingerprint);
        mModelEdit = view.findViewById(R.id.edit_model);
        mManufacturerEdit = view.findViewById(R.id.edit_manufacturer);
        mProductEdit = view.findViewById(R.id.edit_product);
        mDeviceEdit = view.findViewById(R.id.edit_device);
        mBrandEdit = view.findViewById(R.id.edit_brand);
        mSecurityPatchEdit = view.findViewById(R.id.edit_security_patch);
        mDatabaseSpinner = view.findViewById(R.id.spinner_database);

        loadCurrentValues();
        setupDatabaseSpinner();

        // JSON import
        view.findViewById(R.id.btn_import_json).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(intent, "Select Build JSON"), PICK_JSON_FILE);
        });

        // Save
        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            mController.setFingerprint(mFingerprintEdit.getText().toString().trim());
            mController.setModel(mModelEdit.getText().toString().trim());
            mController.setManufacturer(mManufacturerEdit.getText().toString().trim());
            mController.setProduct(mProductEdit.getText().toString().trim());
            mController.setDevice(mDeviceEdit.getText().toString().trim());
            mController.setBrand(mBrandEdit.getText().toString().trim());
            mController.setSecurityPatch(mSecurityPatchEdit.getText().toString().trim());
            Toast.makeText(getContext(), "Saved", Toast.LENGTH_SHORT).show();
        });

        // Clear
        view.findViewById(R.id.btn_clear).setOnClickListener(v -> {
            mIgnoreTextChange = true;
            mFingerprintEdit.setText("");
            mModelEdit.setText("");
            mManufacturerEdit.setText("");
            mProductEdit.setText("");
            mDeviceEdit.setText("");
            mBrandEdit.setText("");
            mSecurityPatchEdit.setText("");
            mIgnoreTextChange = false;
            mController.clearFingerprint();
            Toast.makeText(getContext(), "Cleared", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_JSON_FILE && getActivity() != null
                && resultCode == getActivity().RESULT_OK
                && data != null && data.getData() != null) {
            importBuildJson(data.getData());
        }
    }

    private void importBuildJson(Uri uri) {
        try {
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            is.close();

            JSONObject json = new JSONObject(sb.toString());

            mIgnoreTextChange = true;

            if (json.has("FINGERPRINT") && !json.optString("FINGERPRINT").isEmpty()) {
                mFingerprintEdit.setText(json.getString("FINGERPRINT"));
            } else if (json.has("BRAND") && json.has("PRODUCT") && json.has("DEVICE")) {
                String fp = json.optString("BRAND", "") + "/"
                        + json.optString("PRODUCT", "") + "/"
                        + json.optString("DEVICE", "") + ":"
                        + json.optString("RELEASE", "15") + "/"
                        + json.optString("ID", "") + "/"
                        + json.optString("ID", "") + ":"
                        + json.optString("TYPE", "user") + "/release-keys";
                mFingerprintEdit.setText(fp);
            }

            if (json.has("MODEL")) mModelEdit.setText(json.getString("MODEL"));
            if (json.has("MANUFACTURER")) mManufacturerEdit.setText(json.getString("MANUFACTURER"));
            if (json.has("PRODUCT")) mProductEdit.setText(json.getString("PRODUCT"));
            if (json.has("DEVICE")) mDeviceEdit.setText(json.getString("DEVICE"));
            if (json.has("BRAND")) mBrandEdit.setText(json.getString("BRAND"));
            if (json.has("SECURITY_PATCH")) mSecurityPatchEdit.setText(json.getString("SECURITY_PATCH"));

            mIgnoreTextChange = false;

            Toast.makeText(getContext(),
                    "Imported: " + json.optString("MODEL", "Unknown"),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(),
                    "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadCurrentValues() {
        mIgnoreTextChange = true;
        setText(mFingerprintEdit, mController.getFingerprint());
        setText(mModelEdit, mController.getModel());
        setText(mManufacturerEdit, mController.getManufacturer());
        setText(mProductEdit, mController.getProduct());
        setText(mDeviceEdit, mController.getDevice());
        setText(mBrandEdit, mController.getBrand());
        setText(mSecurityPatchEdit, mController.getSecurityPatch());
        mIgnoreTextChange = false;
    }

    private void setText(EditText edit, String value) {
        edit.setText(value != null ? value : "");
    }

    private void setupDatabaseSpinner() {
        Map<String, OverrideController.PropsEntry> database = mController.getPropsDatabase();
        ArrayList<String> labels = new ArrayList<>();
        labels.add("Select device...");
        labels.addAll(database.keySet());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDatabaseSpinner.setAdapter(adapter);

        mDatabaseSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) return;
                String label = labels.get(position);
                mController.applyPropsEntry(label);
                loadCurrentValues();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}
