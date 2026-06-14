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
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.override.OverrideController;
import com.android.override.settings.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fragment for anti-detection settings.
 *
 * Features:
 * - Master anti-detection toggle
 * - Hide apps from detection
 * - Hide root indicators
 * - Clean logcat
 * - Auto-fallback on PI failure
 * - TEE spoofing toggle
 * - Bootloader state override
 */
public class AntiDetectionFragment extends Fragment {

    private OverrideController mController;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_anti_detection, container, false);
        mController = OverrideController.getInstance();

        // Anti-detection master toggle
        setupSwitch(view, R.id.switch_anti_detection,
                mController.isAntiDetectionEnabled(),
                checked -> mController.setAntiDetection(checked));

        // Hide apps toggle
        setupSwitch(view, R.id.switch_hide_apps,
                mController.isHideAppsEnabled(),
                checked -> mController.setHideApps(checked));

        // Hidden apps list
        RecyclerView hiddenList = view.findViewById(R.id.list_hidden_apps);
        if (hiddenList != null) {
            hiddenList.setLayoutManager(new LinearLayoutManager(getContext()));
            loadHiddenApps(hiddenList);
        }

        // Add hidden app button
        view.findViewById(R.id.btn_add_hidden_app).setOnClickListener(v -> showAddHiddenAppDialog());

        return view;
    }

    private void setupSwitch(View root, int id, boolean initialValue, SwitchCallback callback) {
        Switch sw = root.findViewById(id);
        if (sw != null) {
            sw.setChecked(initialValue);
            sw.setOnCheckedChangeListener((buttonView, isChecked) -> callback.onChanged(isChecked));
        }
    }

    private void loadHiddenApps(RecyclerView recyclerView) {
        Map<String, Boolean> hiddenApps = mController.getHiddenApps();
        List<String> appList = new ArrayList<>(hiddenApps.keySet());
        recyclerView.setAdapter(new HiddenAppAdapter(appList));
    }

    private void showAddHiddenAppDialog() {
        PackageManager pm = getContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String> names = new ArrayList<>();
        List<String> packages = new ArrayList<>();

        for (ApplicationInfo app : apps) {
            if (!mController.isAppHidden(app.packageName)) {
                names.add(pm.getApplicationLabel(app) + "\n" + app.packageName);
                packages.add(app.packageName);
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Hide App")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    mController.addHiddenApp(packages.get(which));
                    RecyclerView list = getView().findViewById(R.id.list_hidden_apps);
                    if (list != null) loadHiddenApps(list);
                })
                .show();
    }

    private interface SwitchCallback {
        void onChanged(boolean checked);
    }

    private class HiddenAppAdapter extends RecyclerView.Adapter<HiddenAppAdapter.ViewHolder> {
        private List<String> mApps;

        HiddenAppAdapter(List<String> apps) { mApps = apps; }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String pkg = mApps.get(position);
            holder.text.setText(pkg);
            holder.itemView.setOnLongClickListener(v -> {
                mController.removeHiddenApp(pkg);
                mApps.remove(position);
                notifyItemRemoved(position);
                return true;
            });
        }

        @Override public int getItemCount() { return mApps.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            ViewHolder(View v) {
                super(v);
                text = v.findViewById(android.R.id.text1);
            }
        }
    }
}
