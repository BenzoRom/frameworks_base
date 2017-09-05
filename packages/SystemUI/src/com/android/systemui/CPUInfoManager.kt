/*
 * Copyright (C) 2017 The OmniROM Project
 * Copyright (C) 2022 Benzo Rom
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
package com.android.systemui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.tuner.TunerService
import javax.inject.Inject

@SysUISingleton
class CPUInfoManager
@Inject
constructor(
    private val context: Context
) : CoreStartable, TunerService.Tunable {

    override fun start() {}
    override fun onBootCompleted() {
        Log.i(logTag, "onBootCompleted()")
        @Suppress("DEPRECATION")
        Dependency.get(TunerService::class.java).addTunable(this, SHOW_CPU_OVERLAY)
    }

    private fun updateCPUInfoOverlay() {
        try {
            val service = Intent(context, CPUInfoService::class.java)
            if (shouldEnableOverlay()) {
                context.startService(service)
            } else {
                context.stopService(service)
            }
        } catch (ex: Exception) {
            Log.e(logTag, "updateCPUInfoOverlay() ", ex)
        }
    }

    private fun shouldEnableOverlay(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.SHOW_CPU_OVERLAY,
            0
        ) != 0
    }

    override fun onTuningChanged(key: String, newValue: String) {
        updateCPUInfoOverlay()
    }

    companion object {
        private const val logTag = "CPUInfoManager"
        private val SHOW_CPU_OVERLAY = "benzoglobal:" + Settings.Global.SHOW_CPU_OVERLAY
    }
}
