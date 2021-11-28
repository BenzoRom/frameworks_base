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
package com.benzorom.systemui.fingerprint;

import android.annotation.Nullable;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.hardware.fingerprint.IUdfpsHbmListener;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.biometrics.AuthController;
import com.android.systemui.biometrics.UdfpsHbmTypes;
import com.android.systemui.biometrics.UdfpsHbmTypes.HbmType;
import com.android.systemui.biometrics.UdfpsHbmProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.util.Assert;
import com.benzorom.systemui.fingerprint.UdfpsHbmRequest;

import java.util.concurrent.Executor;
import javax.inject.Inject;

@SysUISingleton
public class UdfpsHbmController implements UdfpsHbmProvider, DisplayListener {
    private static final String TAG = "UdfpsHbmController";

    @VisibleForTesting
    static final float REFRESH_RATE_GHBM_HZ = 60.0f;

    private static final @HbmType int GLOBAL_HBM = UdfpsHbmTypes.GLOBAL_HBM;
    private static final @HbmType int LOCAL_HBM = UdfpsHbmTypes.LOCAL_HBM;

    private final Context mContext;
    private final @Main Handler mMainHandler;
    private final @UiBackground Executor mUiBgExecutor;
    private final UdfpsGhbmProvider mGhbmProvider;
    private final UdfpsLhbmProvider mLhbmProvider;
    private final AuthController mAuthController;
    private final Injector mInjector;
    private final float mPeakRefreshRate;
    private UdfpsHbmRequest mHbmRequest;

    @Override
    public void onDisplayAdded(int displayId) {}

    @Override
    public void onDisplayRemoved(int displayId) {}

    @VisibleForTesting
    private static class Injector {
        private final DisplayManager mDisplayManager;

        Injector(DisplayManager displayManager) {
            mDisplayManager = displayManager;
        }

        void registerDisplayListener(DisplayListener displayListener, Handler handler) {
            mDisplayManager.registerDisplayListener(displayListener, handler);
        }

        void unregisterDisplayListener(DisplayListener displayListener) {
            mDisplayManager.unregisterDisplayListener(displayListener);
        }

        float getPeakRefreshRate(int displayId) {
            float f = 0.0f;
            for (Display.Mode mode : mDisplayManager.getDisplay(displayId).getSupportedModes()) {
                f = Math.max(f, mode.getRefreshRate());
            }
            return f;
        }

        float getRefreshRate(int displayId) {
            return mDisplayManager.getDisplay(displayId).getRefreshRate();
        }
    }

    @Inject
    public UdfpsHbmController(
            Context context,
            @Main Handler mainHandler,
            @UiBackground Executor uiBgExecutor,
            UdfpsGhbmProvider udfpsGhbmProvider,
            UdfpsLhbmProvider udfpsLhbmProvider,
            AuthController authController,
            DisplayManager displayManager) {
        this(context,
             mainHandler,
             uiBgExecutor,
             udfpsGhbmProvider,
             udfpsLhbmProvider,
             authController,
             new Injector(displayManager));
    }

    @VisibleForTesting
    UdfpsHbmController(
            Context context,
            @Main Handler mainHandler,
            @UiBackground Executor uiBgExecutor,
            UdfpsGhbmProvider udfpsGhbmProvider,
            UdfpsLhbmProvider udfpsLhbmProvider,
            AuthController authController,
            Injector injector) {
        mContext = context;
        mMainHandler = mainHandler;
        mUiBgExecutor = uiBgExecutor;
        mGhbmProvider = udfpsGhbmProvider;
        mLhbmProvider = udfpsLhbmProvider;
        mAuthController = authController;
        mInjector = injector;
        mPeakRefreshRate = injector.getPeakRefreshRate(context.getDisplayId());
        Settings.Secure.putIntForUser(context.getContentResolver(),
                "com.android.systemui.biometrics.UdfpsSurfaceView.hbmType",
                !SystemProperties.getBoolean("persist.fingerprint.ghbm", false)
                ? 1 : 0, UserHandle.USER_CURRENT);
    }

    @Override
    public void enableHbm(@HbmType int hbmType,
                          @Nullable Surface surface,
                          @Nullable Runnable onHbmEnabled) {
        Assert.isMainThread();
        Trace.beginSection("UdfpsHbmController.enableHbm");
        Log.v(TAG, "enableHbm");
        if (hbmType != GLOBAL_HBM && hbmType != LOCAL_HBM) {
            Log.e(TAG, "enableHbm | unsupported hbmType: " + hbmType);
        } else if (hbmType == GLOBAL_HBM && surface == null) {
            Log.e(TAG, "enableHbm | surface must be non-null for GHBM");
        } else if (mAuthController.getUdfpsHbmListener() == null) {
            Log.e(TAG, "enableHbm | mDisplayManagerCallback is null");
        } else if (mHbmRequest != null) {
            Log.e(TAG, "enableHbm | HBM is already requested");
        } else {
            Trace.beginAsyncSection("UdfpsHbmController.e2e.enableHbm", 0);
            mHbmRequest = new UdfpsHbmRequest(mContext.getDisplayId(), hbmType, surface, onHbmEnabled);
            mInjector.registerDisplayListener(this, mMainHandler);
            try {
                IUdfpsHbmListener udfpsHbmListener = mAuthController.getUdfpsHbmListener();
                UdfpsHbmRequest.Args args = mHbmRequest.args;
                udfpsHbmListener.onHbmEnabled(args.hbmType, args.displayId);
                Log.v(TAG, "enableHbm | requested to freeze the refresh rate for hbmType: " + mHbmRequest.args.hbmType);
            } catch (RemoteException e) {
                Log.e(TAG, "enableHbm", e);
            }
            if (mInjector.getRefreshRate(mHbmRequest.args.displayId) == getRequiredRefreshRate(mHbmRequest.args.hbmType)) {
                onDisplayChanged(mHbmRequest.args.displayId);
            }
            Trace.endSection();
        }
    }

    private void doEnableHbm(UdfpsHbmRequest.Args args) {
        mUiBgExecutor.execute(() -> {
                @HbmType int type = args.hbmType;
                if (type == GLOBAL_HBM) {
                    mGhbmProvider.enableGhbm(args.surface);
                } else if (type != LOCAL_HBM) {
                    Log.e(TAG, "doEnableHbm | unsupported HBM type: " + args.hbmType);
                } else {
                    mLhbmProvider.enableLhbm();
                }
                Trace.endAsyncSection("UdfpsHbmController.e2e.enableHbm", 0);
                if (args.onHbmEnabled != null) {
                    mMainHandler.post(() -> {
                            args.onHbmEnabled.run();
                            UdfpsHbmRequest udfpsHbmRequest = mHbmRequest;
                            if (udfpsHbmRequest != null) {
                                udfpsHbmRequest.finishedEnablingHbm = true;
                             }
                    });
                } else {
                    Log.w(TAG, "doEnableHbm | onHbmEnabled is null");
                }
        });
    }

    @Override
    public void disableHbm(@Nullable Runnable onHbmDisabled) {
        Assert.isMainThread();
        Trace.beginSection("UdfpsHbmController.disableHbm");
        Log.v("UdfpsHbmController", "disableHbm");
        if (mHbmRequest == null) {
            Log.w(TAG, "disableHbm | HBM is already disabled");
            return;
        }
        if (mAuthController.getUdfpsHbmListener() == null) {
            Log.e(TAG, "disableHbm | mDisplayManagerCallback is null");
        }
        Trace.beginAsyncSection("UdfpsHbmController.e2e.disableHbm", 0);
        UdfpsHbmRequest udfpsHbmRequest = mHbmRequest;
        if (udfpsHbmRequest.beganEnablingHbm) {
            doDisableHbm(udfpsHbmRequest.args, onHbmDisabled);
        }
        mInjector.unregisterDisplayListener(this);
        mHbmRequest = null;
        Trace.endSection();
    }

    private void doDisableHbm(UdfpsHbmRequest.Args args,
                              @Nullable Runnable onHbmDisabled) {
        mUiBgExecutor.execute(() -> {
                @HbmType int type = args.hbmType;
                if (type == GLOBAL_HBM) {
                    mGhbmProvider.disableGhbm(args.surface);
                } else if (type != LOCAL_HBM) {
                     Log.e(TAG, "doDisableHbm | unsupported HBM type: " + args.hbmType);
                } else {
                     mLhbmProvider.disableLhbm();
                }
                Trace.endAsyncSection("UdfpsHbmController.e2e.disableHbm", 0);
                mMainHandler.post(() -> {
                        try {
                            mAuthController.getUdfpsHbmListener().onHbmDisabled(args.hbmType, args.displayId);
                            Log.v(TAG, "disableHbm | requested to unfreeze the refresh rate");
                        } catch (RemoteException e) {
                            Log.e(TAG, "disableHbm", e);
                        }
                });
                if (onHbmDisabled != null) {
                    mMainHandler.post(onHbmDisabled);
                } else {
                    Log.w(TAG, "doDisableHbm | onHbmDisabled is null");
                }
        });
    }

    public void onDisplayChanged(int displayId) {
        Assert.isMainThread();
        UdfpsHbmRequest udfpsHbmRequest = mHbmRequest;
        if (udfpsHbmRequest == null) {
            Log.w(TAG, "onDisplayChanged | mHbmRequest is null");
        } else if (displayId != udfpsHbmRequest.args.displayId) {
            Log.w(TAG, String.format("onDisplayChanged | displayId: %d != %d",
                    Integer.valueOf(displayId), Integer.valueOf(mHbmRequest.args.displayId)));
        } else {
            float refreshRate = mInjector.getRefreshRate(displayId);
            float requiredRefreshRate = getRequiredRefreshRate(mHbmRequest.args.hbmType);
            if (refreshRate != requiredRefreshRate) {
                Log.w(TAG, String.format("onDisplayChanged | hz: %f != %f",
                        Float.valueOf(refreshRate), Float.valueOf(requiredRefreshRate)));
                if (mHbmRequest.finishedEnablingHbm) {
                    Log.e(TAG, "onDisplayChanged | refresh rate changed while HBM is enabled.");
                }
            } else if (!mHbmRequest.beganEnablingHbm) {
                Log.v(TAG, "onDisplayChanged | froze the refresh rate at hz: " + refreshRate);
                UdfpsHbmRequest udfpsHbmRequest2 = mHbmRequest;
                udfpsHbmRequest2.beganEnablingHbm = true;
                doEnableHbm(udfpsHbmRequest2.args);
            }
        }
    }

    private float getRequiredRefreshRate(@HbmType int hbmType) {
        if (hbmType == GLOBAL_HBM) {
            return REFRESH_RATE_GHBM_HZ;
        }
        if (hbmType != LOCAL_HBM) {
            return 0.0f;
        }
        return mPeakRefreshRate;
    }
}
