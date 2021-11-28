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

import android.os.SystemProperties
import android.util.Log
import android.view.Surface

class UdfpsGhbmProvider {
    private external fun disableGhbmNative(surface: Surface?)
    private external fun enableGhbmNative(surface: Surface?)

    fun enableGhbm(surface: Surface?) {
        Log.v(TAG, "enableGhbm")
        enableGhbmNative(surface)
    }

    fun disableGhbm(surface: Surface?) {
        Log.v(TAG, "disableGhbm")
        disableGhbmNative(surface)
    }

    companion object {
        private const val TAG = "UdfpsGhbmProvider"

        init {
            if (SystemProperties.getBoolean("persist.fingerprint.ghbm", false)) {
                try {
                    System.loadLibrary("udfps_ghbm_jni")
                } catch (e: UnsatisfiedLinkError) {
                    Log.d(TAG, "Failed to load udfps_ghbm_jni.so", e)
                }
            }
        }
    }
}
