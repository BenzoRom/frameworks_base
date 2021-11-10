/*
 * Copyright (C) 2021 The Pixel Experience Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.benzorom.systemui.statusbar.phone;

import static com.android.systemui.qs.dagger.QSFlagsModule.RBC_AVAILABLE;

import android.content.Context;
import android.hardware.display.NightDisplayListener;
import android.os.Build;
import android.os.Handler;

import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.qs.AutoAddTracker;
import com.android.systemui.qs.QSTileHost;
import com.android.systemui.qs.ReduceBrightColorsController;
import com.android.systemui.statusbar.phone.AutoTileManager;
import com.android.systemui.statusbar.phone.ManagedProfileController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.DataSaverController;
import com.android.systemui.statusbar.policy.DeviceControlsController;
import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.WalletController;
import com.android.systemui.util.settings.SecureSettings;

import javax.inject.Named;

public class AutoTileManagerGoogle extends AutoTileManager {

    private final BatteryController mBatteryController;

    private final BatteryController.BatteryStateChangeCallback mBatteryControllerCallback =
        new BatteryController.BatteryStateChangeCallback() {
            @Override
            public void onReverseChanged(boolean isReverse, int level, String name) {
                if (!mAutoTracker.isAdded("reverse") && isReverse) {
                    mHost.addTile("reverse");
                    mAutoTracker.setTileAdded("reverse");
                    mHandler.post(() -> mBatteryController.removeCallback(mBatteryControllerCallback));
                }
            }
        };

    public AutoTileManagerGoogle(
            Context context,
            AutoAddTracker.Builder autoAddTrackerBuilder,
            QSTileHost host,
            @Background Handler handler,
            SecureSettings secureSettings,
            HotspotController hotspotController,
            DataSaverController dataSaverController,
            ManagedProfileController managedProfileController,
            NightDisplayListener nightDisplayListener,
            CastController castController,
            BatteryController batteryController,
            ReduceBrightColorsController reduceBrightColorsController,
            DeviceControlsController deviceControlsController,
            WalletController walletController,
            @Named(RBC_AVAILABLE) boolean isReduceBrightColorsAvailable) {
        super(context, autoAddTrackerBuilder, host, handler, secureSettings,
                hotspotController, dataSaverController, managedProfileController,
                nightDisplayListener, castController, reduceBrightColorsController,
                deviceControlsController, walletController, isReduceBrightColorsAvailable);
        mBatteryController = batteryController;
    }

    @Override
    public void init() {
        super.init();
        if (!mAutoTracker.isAdded("ott") && Build.IS_DEBUGGABLE) {
            mAutoTracker.setTileAdded("ott");
            mHost.addTile("ott");
        }
    }

    @Override
    public void startControllersAndSettingsListeners() {
        super.startControllersAndSettingsListeners();
        if (!mAutoTracker.isAdded("reverse")) {
            mBatteryController.addCallback(mBatteryControllerCallback);
        }
    }

    @Override
    public void stopListening() {
        super.stopListening();
        mBatteryController.removeCallback(mBatteryControllerCallback);
    }
}
