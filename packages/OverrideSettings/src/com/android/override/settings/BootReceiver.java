/*
 * Copyright (C) 2025 Android Override Project
 * Licensed under the Apache License, Version 2.0
 */

package com.android.override.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.override.OverrideController;

/**
 * Boot receiver to initialize Override system at boot.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "OverrideBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            Log.i(TAG, "Boot completed, initializing Override...");
            OverrideController.init(context);
        }
    }
}
