/*
 * Copyright (C) 2025 Android Override Project
 * Licensed under the Apache License, Version 2.0
 */

package com.android.override.settings.fragments;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.override.OverrideController;
import com.android.override.OverrideController.PerAppConfig;
import com.android.override.settings.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fragment for per-app spoofing configuration.
 *
 * Allows users to set different fingerprint/model for specific apps.
 * Useful for banking apps that need Samsung fingerprint, etc.
 */
public class PerAppFragment extends Fragment {

    private OverrideController mController;
    private RecyclerView mAppList;
    private PerAppAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_per_app, container, false);
        mController = OverrideController.getInstance();

        mAppList = view.findViewById(R.id.list_per_app);
        mAppList.setLayoutManager(new LinearLayoutManager(getContext()));

        // Add app button
        view.findViewById(R.id.btn_add_app).setOnClickListener(v -> showAppPicker());

        loadPerAppConfigs();
        return view;
    }

    private void loadPerAppConfigs() {
        Map<String, PerAppConfig> configs = mController.getAllPerAppConfigs();
        List<PerAppConfig> configList = new ArrayList<>(configs.values());
        mAdapter = new PerAppAdapter(configList);
        mAppList.setAdapter(mAdapter);
    }

    private void showAppPicker() {
        PackageManager pm = getContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        // Filter to user-installed + GMS
        List<String> appNames = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    app.packageName.startsWith("com.google.")) {
                String label = pm.getApplicationLabel(app).toString();
                appNames.add(label + " (" + app.packageName + ")");
                packageNames.add(app.packageName);
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Select App")
                .setItems(appNames.toArray(new String[0]), (dialog, which) -> {
                    String pkg = packageNames.get(which);
                    showConfigDialog(pkg);
                })
                .show();
    }

    private void showConfigDialog(String packageName) {
        PerAppConfig existing = mController.getPerAppConfig(packageName);

        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_per_app_config, null);

        EditText fpEdit = dialogView.findViewById(R.id.edit_fp);
        EditText modelEdit = dialogView.findViewById(R.id.edit_model);
        EditText mfrEdit = dialogView.findViewById(R.id.edit_manufacturer);
        Switch enableSwitch = dialogView.findViewById(R.id.switch_enabled);

        if (existing != null) {
            fpEdit.setText(existing.fingerprint);
            modelEdit.setText(existing.model);
            mfrEdit.setText(existing.manufacturer);
            enableSwitch.setChecked(existing.spoofingEnabled);
        } else {
            enableSwitch.setChecked(true);
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Configure: " + packageName)
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    PerAppConfig config = new PerAppConfig(packageName);
                    config.fingerprint = fpEdit.getText().toString().trim();
                    config.model = modelEdit.getText().toString().trim();
                    config.manufacturer = mfrEdit.getText().toString().trim();
                    config.spoofingEnabled = enableSwitch.isChecked();
                    mController.setPerAppConfig(config);
                    loadPerAppConfigs();
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Remove", (dialog, which) -> {
                    mController.removePerAppConfig(packageName);
                    loadPerAppConfigs();
                })
                .show();
    }

    /**
     * RecyclerView adapter for per-app configs.
     */
    private class PerAppAdapter extends RecyclerView.Adapter<PerAppAdapter.ViewHolder> {
        private List<PerAppConfig> mConfigs;

        PerAppAdapter(List<PerAppConfig> configs) {
            mConfigs = configs;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_per_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            PerAppConfig config = mConfigs.get(position);
            holder.packageName.setText(config.packageName);

            String detail = "";
            if (config.fingerprint != null && !config.fingerprint.isEmpty()) {
                detail = "FP: " + config.fingerprint.substring(0, Math.min(30, config.fingerprint.length())) + "...";
            }
            if (config.model != null && !config.model.isEmpty()) {
                detail += (detail.isEmpty() ? "" : "\n") + "Model: " + config.model;
            }
            holder.detail.setText(detail.isEmpty() ? "Using global config" : detail);
            holder.status.setText(config.spoofingEnabled ? "✅" : "❌");

            holder.itemView.setOnClickListener(v -> showConfigDialog(config.packageName));
        }

        @Override
        public int getItemCount() { return mConfigs.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView packageName, detail, status;
            ViewHolder(View v) {
                super(v);
                packageName = v.findViewById(R.id.text_package);
                detail = v.findViewById(R.id.text_detail);
                status = v.findViewById(R.id.text_status);
            }
        }
    }
}
