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
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper.*
import android.provider.Settings
import android.util.Log
import com.android.systemui.dagger.qualifiers.Main

class CPUInfoManager(private val context: Context) : SystemUI(context) {

    companion object {
        private const val TAG = "CPUInfoManager"
    }

    @Main private val mainHandler = Handler(getMainLooper())
    private var settingsObserver: SettingsObserver? = null
    override fun start() {}
    override fun onBootCompleted() {
        try {
            if (settingsObserver == null) {
                settingsObserver = SettingsObserver(mainHandler)
                settingsObserver!!.observe()
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Can't start CPUInfo service", ex)
        }
    }

    private inner class SettingsObserver(handler: Handler?) : ContentObserver(handler) {
        fun observe() {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(
                    Settings.Global.SHOW_CPU_OVERLAY),
                false, this
            )
            updateCPUInfoOverlay()
        }

        override fun onChange(selfChange: Boolean) {
            updateCPUInfoOverlay()
        }

        fun update() {
            updateCPUInfoOverlay()
        }
    }

    private fun updateCPUInfoOverlay() {
        try {
            val cpuinfo = Intent(context, CPUInfoService::class.java)
            when {
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.SHOW_CPU_OVERLAY,
                    0
                ) != 0 -> with(context) { startService(cpuinfo) }
                else   -> with(context) { stopService(cpuinfo) }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "CPUInfoManager update ", ex)
        }
    }
}
