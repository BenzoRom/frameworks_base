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

    @Volatile var iDisplayHal: IDisplay? = null
    override fun binderDied() {
        Log.e(logTag, "binderDied | Display HAL died")
        iDisplayHal = null
    }

    val displayHal: IDisplay?
        get() {
            var iDisplay: IDisplay? = iDisplayHal
            if (iDisplay != null) {
                return iDisplay
            }
            val displayService: IBinder = ServiceManager.waitForDeclaredService(
                "com.google.hardware.pixel.display.IDisplay/default"
            )
            return try {
                displayService.linkToDeath(this, 0)
                val displayInterface = displayService.queryLocalInterface(IDisplay.DESCRIPTOR)
                when {
                    displayInterface != null && displayInterface is IDisplay -> {
                        displayInterface
                    }
                    else -> {
                        IDisplay.Stub.asInterface(displayService)
                    }
                }.also { iDisplay = it }
                iDisplayHal = iDisplay
                iDisplayHal
            } catch (ex: RemoteException) {
                Log.e(logTag, "getDisplayHal | Failed to link to death", ex)
                null
            }
        }

    init {
        displayHal
    }
}
