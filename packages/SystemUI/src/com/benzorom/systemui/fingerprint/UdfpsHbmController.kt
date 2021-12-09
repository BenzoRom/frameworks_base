/*
 * Copyright (C) 2021 Benzo Rom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import androidx.annotation.Nullable
import androidx.annotation.VisibleForTesting
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsHbmTypes
import com.android.systemui.biometrics.UdfpsHbmTypes.HbmType
import com.android.systemui.biometrics.UdfpsHbmProvider
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.util.Assert
import com.benzorom.systemui.fingerprint.UdfpsHbmRequest
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class UdfpsHbmController @VisibleForTesting private constructor(
    context: Context,
    @Main mainHandler: Handler,
    @UiBackground uiBgExecutor: Executor,
    udfpsGhbmProvider: UdfpsGhbmProvider,
    udfpsLhbmProvider: UdfpsLhbmProvider,
    authController: AuthController,
    injector: Injector
) : UdfpsHbmProvider, DisplayListener {
    private val mAuthController: AuthController
    private val mContext: Context
    private val mGhbmProvider: UdfpsGhbmProvider
    private var mHbmRequest: UdfpsHbmRequest? = null
    private val mInjector: Injector
    private val mLhbmProvider: UdfpsLhbmProvider
    @Main
    private val mMainHandler: Handler
    private val mPeakRefreshRate: Float
    @UiBackground
    private val mUiBgExecutor: Executor
    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}

    @VisibleForTesting
    internal class Injector(private val mDisplayManager: DisplayManager) {
        fun registerDisplayListener(displayListener: DisplayListener?, handler: Handler?) {
            mDisplayManager.registerDisplayListener(displayListener, handler)
        }

        fun unregisterDisplayListener(displayListener: DisplayListener?) {
            mDisplayManager.unregisterDisplayListener(displayListener)
        }

        fun getPeakRefreshRate(displayId: Int): Float {
            var f = 0.0f
            for (mode in mDisplayManager.getDisplay(displayId).supportedModes) {
                f = Math.max(f, mode.refreshRate)
            }
            return f
        }

        fun getRefreshRate(displayId: Int): Float {
            return mDisplayManager.getDisplay(displayId).refreshRate
        }
    }

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
        @Nullable surface: Surface?,
        @Nullable onHbmEnabled: Runnable?
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
            Log.e(TAG, "enableHbm | surface must be non-null for GHBM")
        } else if (mAuthController.getUdfpsHbmListener() == null) {
            Log.e(TAG, "enableHbm | mDisplayManagerCallback is null")
        } else if (mHbmRequest != null) {
            Log.e(TAG, "enableHbm | HBM is already requested")
        } else {
            Trace.beginAsyncSection("UdfpsHbmController.e2e.enableHbm", 0)
            mHbmRequest = UdfpsHbmRequest(mContext.getDisplayId(), hbmType, surface, onHbmEnabled)
            mInjector.registerDisplayListener(this, mMainHandler)
            try {
                val udfpsHbmListener: IUdfpsHbmListener? = mAuthController.getUdfpsHbmListener()
                val args = mHbmRequest!!.args
                udfpsHbmListener!!.onHbmEnabled(args.hbmType, args.displayId)
                Log.v(
                    TAG,
                    "enableHbm | requested to freeze the refresh rate for hbmType: " + mHbmRequest!!.args.hbmType
                )
            } catch (ex: RemoteException) {
                Log.e(TAG, "enableHbm", ex)
            }
            if (mInjector.getRefreshRate(mHbmRequest!!.args.displayId) == getRequiredRefreshRate(
                    mHbmRequest!!.args.hbmType
                )
            ) {
                onDisplayChanged(mHbmRequest!!.args.displayId)
            }
            Trace.endSection()
        }
    }

    private fun doEnableHbm(args: UdfpsHbmRequest.Args) {
        mUiBgExecutor.execute {
            @HbmType val hbmType: Int = args.hbmType
            when {
                hbmType == GLOBAL_HBM -> {
                    mGhbmProvider.enableGhbm(args.surface)
                }
                hbmType != LOCAL_HBM -> {
                    Log.e(TAG, "doEnableHbm | unsupported HBM type: " + args.hbmType)
                }
                else -> {
                    mLhbmProvider.enableLhbm()
                }
            }
            Trace.endAsyncSection("UdfpsHbmController.e2e.enableHbm", 0)
            if (args.onHbmEnabled != null) {
                mMainHandler.post {
                    args.onHbmEnabled.run()
                    val udfpsHbmRequest =
                        mHbmRequest
                    if (udfpsHbmRequest != null) {
                        udfpsHbmRequest.finishedEnablingHbm = true
                    }
                }
            } else {
                Log.w(
                    TAG,
                    "doEnableHbm | onHbmEnabled is null"
                )
            }
        }
    }

    override fun disableHbm(@Nullable onHbmDisabled: Runnable?) {
        Assert.isMainThread()
        Trace.beginSection("UdfpsHbmController.disableHbm")
        Log.v(TAG, "disableHbm")
        if (mHbmRequest == null) {
            Log.w(TAG, "disableHbm | HBM is already disabled")
            return
        }
        if (mAuthController.getUdfpsHbmListener() == null) {
            Log.e(TAG, "disableHbm | mDisplayManagerCallback is null")
        }
        Trace.beginAsyncSection("UdfpsHbmController.e2e.disableHbm", 0)
        val udfpsHbmRequest: UdfpsHbmRequest = mHbmRequest!!
        if (udfpsHbmRequest.beganEnablingHbm) {
            doDisableHbm(udfpsHbmRequest.args, onHbmDisabled)
        }
        mInjector.unregisterDisplayListener(this)
        mHbmRequest = null
        Trace.endSection()
    }

    private fun doDisableHbm(
        args: UdfpsHbmRequest.Args,
        @Nullable onHbmDisabled: Runnable?
    ) {
        mUiBgExecutor.execute {
            @HbmType val hbmType: Int = args.hbmType
            when {
                hbmType == GLOBAL_HBM -> {
                    mGhbmProvider.disableGhbm(args.surface)
                }
                hbmType != LOCAL_HBM -> {
                    Log.e(
                        TAG,
                        "doDisableHbm | unsupported HBM type: " + args.hbmType
                    )
                }
                else -> {
                    mLhbmProvider.disableLhbm()
                }
            }
            Trace.endAsyncSection("UdfpsHbmController.e2e.disableHbm", 0)
            mMainHandler.post {
                try {
                    val udfpsHbmListener: IUdfpsHbmListener? = mAuthController.getUdfpsHbmListener()
                    udfpsHbmListener!!.onHbmDisabled(args.hbmType, args.displayId)
                    Log.v(
                        TAG,
                        "disableHbm | requested to unfreeze the refresh rate"
                    )
                } catch (ex: RemoteException) {
                    Log.e(TAG, "disableHbm", ex)
                }
            }
            if (onHbmDisabled != null) {
                mMainHandler.post(onHbmDisabled)
            } else {
                Log.w(
                    TAG,
                    "doDisableHbm | onHbmDisabled is null"
                )
            }
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        Assert.isMainThread()
        val udfpsHbmRequest = mHbmRequest
        if (udfpsHbmRequest == null) {
            Log.w(TAG, "onDisplayChanged | mHbmRequest is null")
        } else if (displayId != udfpsHbmRequest.args.displayId) {
            Log.w(
                TAG, String.format(
                    "onDisplayChanged | displayId: %d != %d",
                    Integer.valueOf(displayId), Integer.valueOf(mHbmRequest!!.args.displayId)
                )
            )
        } else {
            val refreshRate = mInjector.getRefreshRate(displayId)
            val requiredRefreshRate = getRequiredRefreshRate(mHbmRequest!!.args.hbmType)
            if (refreshRate != requiredRefreshRate) {
                Log.w(
                    TAG,
                    String.format(
                        "onDisplayChanged | hz: %f != %f",
                        java.lang.Float.valueOf(refreshRate),
                        java.lang.Float.valueOf(requiredRefreshRate)
                    )
                )
                if (mHbmRequest!!.finishedEnablingHbm) {
                    Log.e(TAG, "onDisplayChanged | refresh rate changed while HBM is enabled.")
                }
            } else if (!mHbmRequest!!.beganEnablingHbm) {
                Log.v(
                    TAG,
                    "onDisplayChanged | froze the refresh rate at hz: $refreshRate"
                )
                val udfpsHbmRequest2 = mHbmRequest
                udfpsHbmRequest2!!.beganEnablingHbm = true
                doEnableHbm(udfpsHbmRequest2.args)
            }
        }
    }

    private fun getRequiredRefreshRate(@HbmType hbmType: Int): Float {
        if (hbmType == GLOBAL_HBM) {
            return REFRESH_RATE_GHBM_HZ
        }
        return if (hbmType != LOCAL_HBM) {
            0.0f
        } else mPeakRefreshRate
    }

    companion object {
        private const val TAG = "UdfpsHbmController"

        @VisibleForTesting
        val REFRESH_RATE_GHBM_HZ = 60.0f

        @HbmType
        private val GLOBAL_HBM: Int = UdfpsHbmTypes.GLOBAL_HBM

        @HbmType
        private val LOCAL_HBM: Int = UdfpsHbmTypes.LOCAL_HBM
    }

    init {
        mContext = context
        mMainHandler = mainHandler
        mUiBgExecutor = uiBgExecutor
        mGhbmProvider = udfpsGhbmProvider
        mLhbmProvider = udfpsLhbmProvider
        mAuthController = authController
        mInjector = injector
        mPeakRefreshRate = injector.getPeakRefreshRate(mContext.getDisplayId())
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
