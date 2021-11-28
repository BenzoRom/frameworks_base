/*
 * Copyright (C) 2021 Benzo Rom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.benzorom.systemui.fingerprint

import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import com.google.hardware.pixel.display.IDisplay

class UdfpsLhbmProvider : DeathRecipient {
    @Volatile
    private var volatileDisplayHal: IDisplay? = null

    fun enableLhbm() {
        Log.v(TAG, "enableLhbm")
        val displayHal: IDisplay? = displayHal
        if (displayHal == null) {
            Log.e(
                TAG,
                "enableLhbm | displayHal is null"
            )
            return
        }
        try {
            displayHal.setLhbmState(true)
        } catch (e: RemoteException) {
            Log.e(
                TAG,
                "enableLhbm | RemoteException",
                e
            )
        }
    }

    fun disableLhbm() {
        Log.v(TAG, "disableLhbm")
        val displayHal: IDisplay? = displayHal
        if (displayHal == null) {
            Log.e(
                TAG,
                "disableLhbm | displayHal is null"
            )
            return
        }
        try {
            displayHal.setLhbmState(false)
        } catch (e: RemoteException) {
            Log.e(
                TAG,
                "disableLhbm | RemoteException",
                e
            )
        }
    }

    private val displayHal: IDisplay?
        get() {
            val iDisplay: IDisplay? = volatileDisplayHal
            if (iDisplay != null) {
                return iDisplay
            }
            val waitForDeclaredService: IBinder =
                ServiceManager.waitForDeclaredService(
                    "com.google.hardware.pixel.display.IDisplay/default"
                )
            return try {
                waitForDeclaredService.linkToDeath(this, 0)
                volatileDisplayHal =
                    IDisplay.Stub.asInterface(waitForDeclaredService)
                volatileDisplayHal
            } catch (e: RemoteException) {
                Log.e(
                    TAG,
                    "getDisplayHal | Failed to link to death",
                    e
                )
                null
            }
        }

    override fun binderDied() {
        Log.e(TAG, "binderDied | Display HAL died")
        volatileDisplayHal = null
    }

    companion object {
        private const val TAG = "UdfpsLhbmProvider"
    }

    init {
        displayHal
    }
}
