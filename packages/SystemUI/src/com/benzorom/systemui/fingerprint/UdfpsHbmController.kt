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
import android.os.*
import android.provider.Settings
import android.util.Log.*
import android.view.Surface
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsHbmProvider
import com.android.systemui.biometrics.UdfpsHbmTypes.HbmType
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.DisplayId
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.concurrency.Execution
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.math.max

@SysUISingleton
class UdfpsHbmController @Inject constructor(
    private val context: Context,
    private val execution: Execution,
    @Main private val mainHandler: Handler,
    @Main private val biometricExecutor: Executor,
    private val lhbmProvider: UdfpsLhbmProvider,
    private val authController: AuthController,
    private val displayManager: DisplayManager
) : UdfpsHbmProvider, DisplayManager.DisplayListener {
    private var currentRequest: HbmRequest? = null
    private val peakRefreshRate: Float
    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}

    init {
        var refreshRate = 0.0f
        displayManager.getDisplay(context.displayId).supportedModes.forEach {
            refreshRate = max(refreshRate, it.refreshRate)
        }
        peakRefreshRate = refreshRate
        Settings.Secure.putIntForUser(
            context.contentResolver,
            "com.android.systemui.biometrics.UdfpsSurfaceView.hbmType",
            if (!SystemProperties.getBoolean(
                    "persist.fingerprint.ghbm",
                    false
                )
            ) 1 else 0,
            UserHandle.USER_CURRENT
        )
    }

    override fun enableHbm(
        @HbmType hbmType: Int,
        surface: Surface?,
        onHbmEnabled: Runnable?
    ) {
        execution.run(Execution::isMainThread)
        logv("enableHbm")
        if (authController.udfpsHbmListener == null) {
            loge("enableHbm | mDisplayManagerCallback is null")
        } else if (currentRequest != null) {
            loge("enableHbm | HBM is already requested")
        } else {
            @DisplayId val displayId = context.displayId
            val hbmRequest = HbmRequest(
                mainHandler,
                biometricExecutor,
                authController,
                lhbmProvider,
                displayId,
                hbmType,
                onHbmEnabled
            )
            currentRequest = hbmRequest
            displayManager.registerDisplayListener(this, mainHandler)
            try {
                with(authController) {
                    udfpsHbmListener?.onHbmEnabled(hbmType, displayId)
                }
                logv("enableHbm | request freeze refresh rate")
            } catch (ex: RemoteException) {
                loge("enableHbm: $ex")
            }
            if (displayManager.getDisplay(hbmRequest.displayId).refreshRate
                    .equals(peakRefreshRate)
            ) onDisplayChanged(hbmRequest.displayId)
        }
    }

    override fun disableHbm(onHbmDisabled: Runnable?) {
        execution.run(Execution::isMainThread)
        logv("disableHbm")
        val hbmRequest = currentRequest
        if (hbmRequest == null) {
            logw("disableHbm | HBM is already disabled")
            return
        }
        if (authController.udfpsHbmListener == null) {
            loge("disableHbm | mDisplayManagerCallback is null")
        }
        displayManager.unregisterDisplayListener(this)
        currentRequest = null
        if (hbmRequest.startedRequest) {
            with(hbmRequest) {
                biometricExecutor.execute {
                    val udfpsLhbmProvider = lhbmProvider
                    udfpsLhbmProvider.javaClass
                    logv("UdfpsLhbmProvider: disableLhbm")
                    val displayHal = udfpsLhbmProvider.displayHal
                    if (displayHal != null) {
                        with(displayHal) {
                            setLhbmState(false)
                        }
                    } else {
                        loge("UdfpsLhbmProvider: disableLhbm | displayHal is null")
                    }
                    mainHandler.post {
                        try {
                            authController.udfpsHbmListener!!.onHbmDisabled(hbmType, displayId)
                            logv("disableHbm | requested to unfreeze the refresh rate")
                        } catch (ex: RemoteException) {
                            loge("disableHbm: $ex")
                        }
                    }
                    if (onHbmDisabled != null) {
                        mainHandler.run { post(onHbmDisabled) }
                    } else {
                        logw("doDisableHbm | onHbmDisabled is null")
                    }
                }
            }
        }
    }

    override fun onDisplayChanged(@DisplayId displayId: Int) {
        execution.run(Execution::isMainThread)
        val hbmRequest = currentRequest
        if (hbmRequest == null) {
            logw("onDisplayChanged | mHbmRequest is null")
        } else if (displayId != hbmRequest.displayId) {
            logw("onDisplayChanged | displayId: $displayId != ${hbmRequest.displayId}")
        } else {
            val refreshRate = displayManager.getDisplay(displayId).refreshRate
            if (!refreshRate.equals(peakRefreshRate)) {
                logw("onDisplayChanged | hz: $refreshRate != $peakRefreshRate")
                if (hbmRequest.finishedStarting) {
                    loge("onDisplayChanged | refresh rate changed while HBM is enabled.")
                }
                return
            }
            logv("onDisplayChanged | froze the refresh rate at hz: $refreshRate")
            if (!hbmRequest.startedRequest) {
                with(hbmRequest) {
                    startedRequest = true
                    biometricExecutor.execute {
                        val udfpsLhbmProvider = lhbmProvider
                        udfpsLhbmProvider.javaClass
                        logv("UdfpsLhbmProvider: enableLhbm")
                        val displayHal = udfpsLhbmProvider.displayHal
                        if (displayHal != null) {
                            with(displayHal) {
                                setLhbmState(true)
                            }
                        } else {
                            loge("UdfpsLhbmProvider: enableLhbm | displayHal is null")
                        }
                        try {
                            if (onHbmEnabled != null) {
                                mainHandler.run { post(onHbmEnabled) }
                            } else {
                                logw("doEnableHbm | onHbmEnabled is null")
                            }
                        } finally {
                            finishedStarting = true
                        }
                    }
                }
            }
        }
    }
}

internal fun log(level: Int, message: String) {
    val logTag = "UdfpsHbmController"
    if (isLoggable(logTag, DEBUG))
        when (level) {
            ERROR   -> e(logTag, message)
            VERBOSE -> v(logTag, message)
            WARN    -> w(logTag, message)
        }
}
internal fun loge(message: String) = log(ERROR, message)
internal fun logv(message: String) = log(VERBOSE, message)
internal fun logw(message: String) = log(WARN, message)
