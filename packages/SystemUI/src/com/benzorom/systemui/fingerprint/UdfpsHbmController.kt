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

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.hardware.fingerprint.IUdfpsHbmListener
import android.os.Handler
import android.os.RemoteException
import android.os.SystemProperties
import android.os.Trace
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.annotation.VisibleForTesting
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsHbmTypes
import com.android.systemui.biometrics.UdfpsHbmTypes.HbmType
import com.android.systemui.biometrics.UdfpsHbmProvider
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.util.Assert
import com.benzorom.systemui.fingerprint.UdfpsGhbmProvider
import com.benzorom.systemui.fingerprint.UdfpsHbmRequest
import com.benzorom.systemui.fingerprint.UdfpsLhbmProvider
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class UdfpsHbmController @VisibleForTesting internal constructor(
    private val context: Context,
    @Main private val mainHandler: Handler,
    @UiBackground private val uiBgExecutor: Executor,
    udfpsGhbmProvider: UdfpsGhbmProvider,
    udfpsLhbmProvider: UdfpsLhbmProvider,
    authController: AuthController,
    injector: Injector
) : UdfpsHbmProvider, DisplayListener {
    private val authController: AuthController
    private val ghbmProvider: UdfpsGhbmProvider
    private var hbmRequest: UdfpsHbmRequest? = null
    private val injector: Injector
    private val lhbmProvider: UdfpsLhbmProvider
    private val peakRefreshRate: Float
    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}

    @Inject
    constructor(
        context: Context,
        @Main mainHandler: Handler,
        @UiBackground uiBgExecutor: Executor,
        udfpsGhbmProvider: UdfpsGhbmProvider,
        udfpsLhbmProvider: UdfpsLhbmProvider,
        authController: AuthController,
        displayManager: DisplayManager
    ) : this(
        context,
        mainHandler,
        uiBgExecutor,
        udfpsGhbmProvider,
        udfpsLhbmProvider,
        authController,
        Injector(displayManager)
    )

    override fun enableHbm(
        @HbmType hbmType: Int,
        surface: Surface?,
        onHbmEnabled: Runnable?
    ) {
        Assert.isMainThread()
        Trace.beginSection("UdfpsHbmController.enableHbm")
        Log.v(TAG, "enableHbm")
        if (hbmType != GLOBAL_HBM && hbmType != LOCAL_HBM) {
            Log.e(
                TAG,
                "enableHbm | unsupported hbmType: $hbmType"
            )
        } else if (hbmType == GLOBAL_HBM && surface == null) {
            Log.e(
                TAG,
                "enableHbm | surface must be non-null for GHBM"
            )
        } else if (authController.getUdfpsHbmListener() == null) {
            Log.e(
                TAG,
                "enableHbm | displayManagerCallback is null"
            )
        } else if (hbmRequest != null) {
            Log.e(
                TAG,
                "enableHbm | HBM is already requested"
            )
        } else {
            Trace.beginAsyncSection("UdfpsHbmController.e2e.enableHbm", 0)
            hbmRequest = UdfpsHbmRequest(
                context.getDisplayId(), hbmType, surface, onHbmEnabled
            )
            injector.registerDisplayListener(this, mainHandler)
            try {
                val args = hbmRequest!!.args
                authController.getUdfpsHbmListener()!!.onHbmEnabled(
                    args.hbmType, args.displayId
                )
                Log.v(
                    TAG,
                    "enableHbm | requested to freeze the refresh rate for hbmType: "
                            + hbmRequest!!.args.hbmType
                )
            } catch (ex: RemoteException) {
                Log.e(TAG, "enableHbm", ex)
            }
            if (injector.getRefreshRate(hbmRequest!!.args.displayId) == getRequiredRefreshRate(
                    hbmRequest!!.args.hbmType
                )
            ) {
                onDisplayChanged(hbmRequest!!.args.displayId)
            }
            Trace.endSection()
        }
    }

    private fun doEnableHbm(args: UdfpsHbmRequest.Args) {
        uiBgExecutor.execute {
            @HbmType val hbmType = args.hbmType
            when {
                hbmType == GLOBAL_HBM -> {
                    ghbmProvider.enableGhbm(args.surface)
                }
                hbmType != LOCAL_HBM  -> {
                    Log.e(TAG, "doEnableHbm | unsupported HBM type: " + args.hbmType)
                }
                else                  -> {
                    lhbmProvider.enableLhbm()
                }
            }
            Trace.endAsyncSection("UdfpsHbmController.e2e.enableHbm", 0)
            if (args.onHbmEnabled != null) {
                mainHandler.post {
                    args.onHbmEnabled.run()
                    val udfpsHbmRequest = hbmRequest
                    if (udfpsHbmRequest != null) {
                        udfpsHbmRequest.finishedEnablingHbm = true
                    }
                }
            } else {
                Log.w(TAG, "doEnableHbm | onHbmEnabled is null")
            }
        }
    }

    override fun disableHbm(onHbmDisabled: Runnable?) {
        Assert.isMainThread()
        Trace.beginSection("UdfpsHbmController.disableHbm")
        Log.v(TAG, "disableHbm")
        if (hbmRequest == null) {
            Log.w(TAG, "disableHbm | HBM is already disabled")
            return
        }
        if (authController.getUdfpsHbmListener() == null) {
            Log.e(TAG, "disableHbm | displayManagerCallback is null")
        }
        Trace.beginAsyncSection("UdfpsHbmController.e2e.disableHbm", 0)
        val udfpsHbmRequest = hbmRequest!!
        if (udfpsHbmRequest.beganEnablingHbm) {
            doDisableHbm(udfpsHbmRequest.args, onHbmDisabled)
        }
        injector.unregisterDisplayListener(this)
        hbmRequest = null
        Trace.endSection()
    }

    private fun doDisableHbm(
        args: UdfpsHbmRequest.Args,
        onHbmDisabled: Runnable?
    ) {
        uiBgExecutor.execute {
            @HbmType val hbmType = args.hbmType
            when {
                hbmType == GLOBAL_HBM -> {
                    ghbmProvider.disableGhbm(args.surface)
                }
                hbmType != LOCAL_HBM  -> {
                    Log.e(TAG, "doDisableHbm | unsupported HBM type: " + args.hbmType)
                }
                else                  -> {
                    lhbmProvider.disableLhbm()
                }
            }
            Trace.endAsyncSection("UdfpsHbmController.e2e.disableHbm", 0)
            mainHandler.post {
                try {
                    authController.getUdfpsHbmListener()!!.onHbmDisabled(
                        args.hbmType, args.displayId
                    )
                    Log.v(TAG, "disableHbm | requested to unfreeze the refresh rate")
                } catch (ex: RemoteException) {
                    Log.e(TAG, "disableHbm", ex)
                }
            }
            mainHandler.post(onHbmDisabled)
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        Assert.isMainThread()
        val udfpsHbmRequest = hbmRequest
        if (udfpsHbmRequest == null) {
            Log.w(TAG, "onDisplayChanged | hbmRequest is null")
        } else if (displayId != udfpsHbmRequest.args.displayId) {
            Log.w(
                TAG,
                String.format(
                    "onDisplayChanged | displayId: %d != %d",
                    Integer.valueOf(displayId),
                    Integer.valueOf(hbmRequest!!.args.displayId)
                )
            )
        } else {
            val refreshRate = injector.getRefreshRate(displayId)
            val requiredRefreshRate = getRequiredRefreshRate(hbmRequest!!.args.hbmType)
            if (refreshRate != requiredRefreshRate) {
                Log.w(
                    TAG,
                    String.format(
                        "onDisplayChanged | hz: %f != %f",
                        java.lang.Float.valueOf(refreshRate),
                        java.lang.Float.valueOf(requiredRefreshRate)
                    )
                )
                if (hbmRequest!!.finishedEnablingHbm) {
                    Log.e(
                        TAG,
                        "onDisplayChanged | refresh rate changed while HBM is enabled."
                    )
                }
            } else if (!hbmRequest!!.beganEnablingHbm) {
                Log.v(
                    TAG,
                    "onDisplayChanged | froze the refresh rate at hz: $refreshRate"
                )
                udfpsHbmRequest.beganEnablingHbm = true
                doEnableHbm(udfpsHbmRequest.args)
            }
        }
    }

    private fun getRequiredRefreshRate(@HbmType hbmType: Int): Float {
        if (hbmType == GLOBAL_HBM) {
            return REFRESH_RATE_GHBM_HZ
        }
        return if (hbmType != LOCAL_HBM) {
            0.0f
        } else peakRefreshRate
    }

    @VisibleForTesting
    class Injector internal constructor(private val displayManager: DisplayManager) {
        fun registerDisplayListener(
                displayListener: DisplayListener?,
                handler: Handler?
        ) {
            displayManager.registerDisplayListener(displayListener, handler)
        }

        fun unregisterDisplayListener(displayListener: DisplayListener?) {
            displayManager.unregisterDisplayListener(displayListener)
        }

        fun getPeakRefreshRate(displayId: Int): Float {
            var f = 0.0f
            for (mode in displayManager.getDisplay(displayId).supportedModes) {
                f = Math.max(f, mode.refreshRate)
            }
            return f
        }

        fun getRefreshRate(displayId: Int): Float {
            return displayManager.getDisplay(displayId).refreshRate
        }
    }

    companion object {
        private const val TAG = "UdfpsHbmController"

        @HbmType
        private val GLOBAL_HBM = UdfpsHbmTypes.GLOBAL_HBM

        @HbmType
        private val LOCAL_HBM = UdfpsHbmTypes.LOCAL_HBM

        @VisibleForTesting
        val REFRESH_RATE_GHBM_HZ = 60.0f
    }

    init {
        this.ghbmProvider = udfpsGhbmProvider
        this.lhbmProvider = udfpsLhbmProvider
        this.authController = authController
        this.injector = injector
        peakRefreshRate = injector.getPeakRefreshRate(context.getDisplayId())
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
}
