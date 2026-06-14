/*
 * Copyright (C) 2025 Android Override Project
 * Licensed under the Apache License, Version 2.0
 */

package com.android.override.settings.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.override.OverrideController;
import com.android.override.settings.R;

import java.util.Arrays;
import java.util.List;

/**
 * Fragment for managing spoofing profiles.
 *
 * Save/load/switch entire configurations as profiles.
 * Each profile includes fingerprint, keybox, per-app configs.
 */
public class ProfilesFragment extends Fragment {

    private OverrideController mController;
    private RecyclerView mProfilesList;
    private TextView mActiveProfile;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profiles, container, false);
        mController = OverrideController.getInstance();

        mActiveProfile = view.findViewById(R.id.text_active_profile);
        mActiveProfile.setText("Active: " + mController.getActiveProfile());

        mProfilesList = view.findViewById(R.id.list_profiles);
        mProfilesList.setLayoutManager(new LinearLayoutManager(getContext()));

        // Save current as new profile
        view.findViewById(R.id.btn_save_profile).setOnClickListener(v -> showSaveDialog());

        loadProfiles();
        return view;
    }

    private void loadProfiles() {
        String[] profiles = mController.listProfiles();
        List<String> profileList = Arrays.asList(profiles);
        mProfilesList.setAdapter(new ProfileAdapter(profileList));
    }

    private void showSaveDialog() {
        EditText input = new EditText(getContext());
        input.setHint("Profile name");

        new AlertDialog.Builder(getContext())
                .setTitle("Save Profile")
                .setMessage("Save current configuration as a new profile")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        if (mController.saveProfile(name)) {
                            Toast.makeText(getContext(), "✅ Profile saved: " + name,
                                    Toast.LENGTH_SHORT).show();
                            loadProfiles();
                        } else {
                            Toast.makeText(getContext(), "❌ Failed to save profile",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ViewHolder> {
        private List<String> mProfiles;

        ProfileAdapter(List<String> profiles) {
            mProfiles = profiles;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_profile, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String name = mProfiles.get(position);
            holder.name.setText(name);

            boolean isActive = name.equals(mController.getActiveProfile());
            holder.status.setText(isActive ? "✅ Active" : "");

            holder.itemView.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                        .setTitle(name)
                        .setItems(new String[]{"Activate", "Delete"}, (dialog, which) -> {
                            if (which == 0) {
                                mController.loadProfile(name);
                                mActiveProfile.setText("Active: " + name);
                                loadProfiles();
                                Toast.makeText(getContext(), "✅ Profile activated: " + name,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                mController.deleteProfile(name);
                                loadProfiles();
                            }
                        })
                        .show();
            });
        }

        @Override
        public int getItemCount() { return mProfiles.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, status;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.text_name);
                status = v.findViewById(R.id.text_status);
            }
        }
    }
}
