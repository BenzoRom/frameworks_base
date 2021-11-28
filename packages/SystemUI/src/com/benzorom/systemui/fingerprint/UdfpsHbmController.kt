/*
 * Copyright (C) 2021-2022 Benzo Rom
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

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.fingerprint.IUdfpsHbmListener
import android.os.Handler
import android.os.RemoteException
import android.os.Trace
import android.os.UserHandle
import android.provider.Settings
import android.os.SystemProperties
import android.util.Log
import android.view.Surface
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsHbmProvider
import com.android.systemui.biometrics.UdfpsHbmTypes
import com.android.systemui.biometrics.UdfpsHbmTypes.HbmType
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.concurrency.Execution
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class UdfpsHbmController @Inject constructor(
    private val context: Context,
    private val execution: Execution,
    @Main private val mainHandler: Handler,
    @Main private val biometricExecutor: Executor,
    private val ghbmProvider: UdfpsGhbmProvider,
    private val lhbmProvider: UdfpsLhbmProvider,
    private val authController: AuthController,
    private val displayManager: DisplayManager
) : UdfpsHbmProvider, DisplayManager.DisplayListener {

    companion object {
        private const val logTag = "UdfpsHbmController"
        private const val REFRESH_RATE_GHBM_HZ = 60.0f
        private const val SECURE_SETTINGS_HBM_TYPE =
            "com.android.systemui.biometrics.UdfpsSurfaceView.hbmType"
        @HbmType private val GLOBAL_HBM = UdfpsHbmTypes.GLOBAL_HBM
        @HbmType private val LOCAL_HBM = UdfpsHbmTypes.LOCAL_HBM
    }

    private var currentRequest: HbmRequest? = null
    private val peakRefreshRate: Float
    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}

    override fun enableHbm(
        @HbmType hbmType: Int,
        surface: Surface?,
        onHbmEnabled: Runnable?
    ) {
        execution.isMainThread()
        Log.v(logTag, "enableHbm")
        Trace.beginSection("UdfpsHbmController.enableHbm")
        if (canEnableHbm(hbmType, surface)) {
            Trace.beginAsyncSection("UdfpsHbmController.e2e.enableHbm", 0)
            val hbmRequest = HbmRequest(
                mainHandler,
                biometricExecutor,
                authController,
                ghbmProvider,
                lhbmProvider,
                context.getDisplayId(),
                hbmType,
                surface,
                onHbmEnabled
            )
            currentRequest = hbmRequest
            displayManager.registerDisplayListener(this, mainHandler)
            try {
                val udfpsHbmListener: IUdfpsHbmListener?
                udfpsHbmListener = authController.udfpsHbmListener
                if (udfpsHbmListener != null) {
                    udfpsHbmListener.onHbmEnabled(
                        hbmRequest.hbmType,
                        hbmRequest.displayId
                    )
                }
                Log.v(
                    logTag,
                    "enableHbm | request freeze refresh rate for type: ${hbmRequest.hbmType}"
                )
            } catch (ex: RemoteException) {
                Log.e(logTag, "enableHbm", ex)
            }
            if (getRefreshRate(hbmRequest.displayId).equals(
                    getRequiredRefreshRate(hbmRequest.hbmType)
                )
            ) onDisplayChanged(hbmRequest.displayId)
        }
        Trace.endSection()
    }

    private fun canEnableHbm(
        @HbmType hbmType: Int,
        surface: Surface?
    ): Boolean {
        return if (hbmType != GLOBAL_HBM && hbmType != LOCAL_HBM) {
            Log.e(logTag, "enableHbm | unsupported hbmType: $hbmType")
            false
        } else if (hbmType == GLOBAL_HBM && surface == null) {
            Log.e(logTag, "enableHbm | surface must be non-null for GHBM")
            false
        } else if (authController.udfpsHbmListener == null) {
            Log.e(logTag, "enableHbm | mDisplayManagerCallback is null")
            false
        } else if (currentRequest == null) {
            true
        } else {
            Log.e(logTag, "enableHbm | HBM is already requested")
            false
        }
    }

    override fun disableHbm(onHbmDisabled: Runnable?) {
        execution.isMainThread()
        Log.v(logTag, "disableHbm")
        Trace.beginSection("UdfpsHbmController.disableHbm")
        val hbmRequest = currentRequest
        if (hbmRequest == null) {
            Log.w(logTag, "disableHbm | HBM is already disabled")
            return
        }
        if (authController.udfpsHbmListener == null) {
            Log.e(logTag, "disableHbm | mDisplayManagerCallback is null")
        }
        Trace.beginAsyncSection("UdfpsHbmController.e2e.disableHbm", 0)
        displayManager.unregisterDisplayListener(this)
        currentRequest = null
        hbmRequest.disable(onHbmDisabled)
        Trace.endSection()
    }

    override fun onDisplayChanged(displayId: Int) {
        execution.isMainThread()
        val hbmRequest = currentRequest
        when {
            hbmRequest == null                -> {
                Log.w(logTag, "onDisplayChanged | mHbmRequest is null")
            }
            displayId != hbmRequest.displayId -> {
                Log.w(logTag, "onDisplayChanged | displayId: $displayId != ${hbmRequest.displayId}")
            }
            else                              -> {
                val refreshRate = getRefreshRate(displayId)
                val requiredRefreshRate = getRequiredRefreshRate(hbmRequest.hbmType)
                if (refreshRate != requiredRefreshRate) {
                    Log.w(logTag, "onDisplayChanged | hz: $refreshRate != $requiredRefreshRate")
                    if (hbmRequest.finishedStarting) {
                        Log.e(logTag, "onDisplayChanged | refresh rate changed while HBM is enabled.")
                        return
                    }
                    return
                }
                Log.v(logTag, "onDisplayChanged | froze the refresh rate at hz: $refreshRate")
                hbmRequest.enable()
            }
        }
    }

    private fun getPeakRefreshRate(displayId: Int): Float {
        var f = 0.0f
        for (mode in displayManager.getDisplay(displayId).supportedModes)
            f = kotlin.math.max(f, mode.refreshRate)
        return f
    }

    private fun getRefreshRate(displayId: Int): Float {
        return displayManager.getDisplay(displayId).refreshRate
    }

    private fun getRequiredRefreshRate(@HbmType hbmType: Int): Float {
        if (hbmType == GLOBAL_HBM) return REFRESH_RATE_GHBM_HZ
        return if (hbmType == LOCAL_HBM) peakRefreshRate else 0.0f
    }

    init {
        peakRefreshRate = getPeakRefreshRate(context.getDisplayId())
        Settings.Secure.putIntForUser(
            context.contentResolver,
            SECURE_SETTINGS_HBM_TYPE,
            if (!SystemProperties.getBoolean(
                    "persist.fingerprint.ghbm",
                    false
                )
            ) 1 else 0,
            UserHandle.USER_CURRENT
        )
    }
}
