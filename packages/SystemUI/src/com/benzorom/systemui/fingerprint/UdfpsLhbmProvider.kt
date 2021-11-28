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
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import com.google.hardware.pixel.display.IDisplay

class UdfpsLhbmProvider : IBinder.DeathRecipient {

    companion object {
        private const val logTag = "UdfpsLhbmProvider"
    }

    @Volatile private var iDisplayHal: IDisplay? = null
    fun enableLhbm() {
        Log.v(logTag, "enableLhbm")
        val displayHal: IDisplay? = displayHal
        if (displayHal == null) {
            Log.e(logTag, "enableLhbm | displayHal is null")
            return
        }
        try {
            displayHal.setLhbmState(true)
        } catch (ex: RemoteException) {
            Log.e(logTag, "enableLhbm | RemoteException", ex)
        }
    }

    fun disableLhbm() {
        Log.v(logTag, "disableLhbm")
        val displayHal: IDisplay? = displayHal
        if (displayHal == null) {
            Log.e(logTag, "disableLhbm | displayHal is null")
            return
        }
        try {
            displayHal.setLhbmState(false)
        } catch (ex: RemoteException) {
            Log.e(logTag, "disableLhbm | RemoteException", ex)
        }
    }

    private val displayHal: IDisplay?
        get() {
            val display: IDisplay? = iDisplayHal
            if (display != null) {
                return display
            }
            val waitForDeclaredService: IBinder
            waitForDeclaredService = ServiceManager.waitForDeclaredService(
                "com.google.hardware.pixel.display.IDisplay/default"
            )
            return try {
                waitForDeclaredService.linkToDeath(this, 0)
                iDisplayHal = IDisplay.Stub.asInterface(waitForDeclaredService)
                iDisplayHal
            } catch (ex: RemoteException) {
                Log.e(logTag, "getDisplayHal | Failed to link to death", ex)
                null
            }
        }

    override fun binderDied() {
        Log.e(logTag, "binderDied | Display HAL died")
        iDisplayHal = null
    }

    init {
        displayHal
    }
}
