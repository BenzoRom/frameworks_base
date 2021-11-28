/*
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
package com.benzorom.systemui.fingerprint

import android.hardware.fingerprint.IUdfpsHbmListener
import android.os.Handler
import android.os.RemoteException
import android.os.Trace
import android.util.Log
import android.view.Surface
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsHbmTypes
import com.android.systemui.biometrics.UdfpsHbmTypes.HbmType
import com.android.systemui.dagger.qualifiers.Main
import java.util.concurrent.Executor

class HbmRequest(
    @Main private val mainHandler: Handler,
    @Main private val biometricExecutor: Executor,
    private val authController: AuthController,
    private val ghbmProvider: UdfpsGhbmProvider,
    private val lhbmProvider: UdfpsLhbmProvider,
    val displayId: Int,
    @HbmType val hbmType: Int,
    private val surface: Surface?,
    private val onHbmEnabled: Runnable?
) {

    companion object {
        private const val logTag = "UdfpsHbmController"
        @HbmType private val GLOBAL_HBM: Int = UdfpsHbmTypes.GLOBAL_HBM
        @HbmType private val LOCAL_HBM: Int = UdfpsHbmTypes.LOCAL_HBM
    }

    private var startedRequest = false
    var finishedStarting = false

    fun enable() {
        if (!startedRequest) {
            startedRequest = true
            biometricExecutor.execute {
                @HbmType val hbmType = hbmType
                when {
                    hbmType == GLOBAL_HBM -> {
                        val udfpsGhbmProvider = ghbmProvider
                        val udfpsSurface = surface
                        udfpsGhbmProvider.enableGhbm(udfpsSurface)
                    }
                    hbmType != LOCAL_HBM  -> {
                        Log.e(logTag, "doEnableHbm | unsupported HBM type: $hbmType")
                    }
                    else                  -> {
                        val udfpsLhbmProvider = lhbmProvider
                        udfpsLhbmProvider.enableLhbm()
                    }
                }
                Trace.endAsyncSection("UdfpsHbmController.e2e.enableHbm", 0)
                try {
                    val handler = mainHandler
                    val udfpsRunnable = onHbmEnabled
                    if (udfpsRunnable != null) {
                        handler.post(udfpsRunnable)
                    }
                } finally {
                    finishedStarting = true
                }
            }
        }
    }

    fun disable(onHbmDisabled: Runnable?) {
        if (startedRequest) {
            biometricExecutor.execute {
                @HbmType val hbmType = hbmType
                when {
                    hbmType == GLOBAL_HBM -> {
                        val udfpsGhbmProvider = ghbmProvider
                        val udfpsSurface = surface
                        udfpsGhbmProvider.disableGhbm(udfpsSurface)
                    }
                    hbmType != LOCAL_HBM  -> {
                        Log.e(logTag, "doDisableHbm | unsupported HBM type: $hbmType")
                    }
                    else                  -> {
                        val udfpsLhbmProvider = lhbmProvider
                        udfpsLhbmProvider.disableLhbm()
                    }
                }
                Trace.endAsyncSection("UdfpsHbmController.e2e.disableHbm", 0)
                val handler: Handler = mainHandler
                handler.post {
                    val udfpsAuthController: AuthController
                    try {
                        udfpsAuthController = authController
                        val udfpsHbmListener: IUdfpsHbmListener =
                            udfpsAuthController.udfpsHbmListener!!
                        udfpsHbmListener.onHbmDisabled(
                            hbmType,
                            displayId
                        )
                        Log.v(logTag, "disableHbm | requested to unfreeze the refresh rate")
                    } catch (ex: RemoteException) {
                        Log.e(logTag, "disableHbm", ex)
                    }
                }
                if (onHbmDisabled != null) {
                    handler.post(onHbmDisabled)
                    return@execute
                }
                Log.w(logTag, "doDisableHbm | onHbmDisabled is null")
            }
        }
    }
}
