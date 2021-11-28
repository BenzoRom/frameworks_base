/*
 * Copyright (C) 2021 Benzo Rom
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
package com.benzorom.systemui.fingerprint;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.google.hardware.pixel.display.IDisplay;

public class UdfpsLhbmProvider implements IBinder.DeathRecipient {
    private static final String TAG = "UdfpsLhbmProvider";

    private volatile IDisplay mDisplayHal;

    public UdfpsLhbmProvider() {
        getDisplayHal();
    }

    public void enableLhbm() {
        Log.v(TAG, "enableLhbm");
        IDisplay displayHal = getDisplayHal();
        if (displayHal == null) {
            Log.e(TAG, "enableLhbm | displayHal is null");
            return;
        }
        try {
            displayHal.setLhbmState(true);
        } catch (RemoteException e) {
            Log.e(TAG, "enableLhbm | RemoteException", e);
        }
    }

    public void disableLhbm() {
        Log.v(TAG, "disableLhbm");
        IDisplay displayHal = getDisplayHal();
        if (displayHal == null) {
            Log.e(TAG, "disableLhbm | displayHal is null");
            return;
        }
        try {
            displayHal.setLhbmState(false);
        } catch (RemoteException e) {
            Log.e(TAG, "disableLhbm | RemoteException", e);
        }
    }

    private IDisplay getDisplayHal() {
        IDisplay iDisplay = mDisplayHal;
        if (iDisplay != null) {
            return iDisplay;
        }
        IBinder waitForDeclaredService =
                ServiceManager.waitForDeclaredService(
                "com.google.hardware.pixel.display.IDisplay/default");
        if (waitForDeclaredService == null) {
            Log.e(TAG, "getDisplayHal | Failed to find the Display HAL");
            return null;
        }
        try {
            waitForDeclaredService.linkToDeath(this, 0);
            mDisplayHal = IDisplay.Stub.asInterface(waitForDeclaredService);
            return mDisplayHal;
        } catch (RemoteException e) {
            Log.e(TAG, "getDisplayHal | Failed to link to death", e);
            return null;
        }
    }

    public void binderDied() {
        Log.e(TAG, "binderDied | Display HAL died");
        mDisplayHal = null;
    }
}
