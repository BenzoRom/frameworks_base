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
package com.benzorom.systemui.assist;

import static android.provider.Settings.Secure.ASSIST_GESTURE_SETUP_COMPLETE;
import static android.view.Display.DEFAULT_DISPLAY;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED;

import android.content.Context;
import android.metrics.LogMaker;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.view.IWindowManager;

import com.android.internal.app.AssistUtils;
import com.android.internal.app.IVoiceInteractionSessionListener;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.assist.AssistLogger;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.assist.AssistManager.UiController;
import com.android.systemui.assist.AssistantSessionEvent;
import com.android.systemui.assist.PhoneStateMonitor;
import com.android.systemui.assist.ui.DefaultUiController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.model.SysUiState;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.recents.OverviewProxyService;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.google.android.systemui.assist.OpaEnabledDispatcher;
import com.google.android.systemui.assist.OpaEnabledListener;
import com.google.android.systemui.assist.OpaEnabledReceiver;
import com.google.android.systemui.assist.uihints.AssistantPresenceHandler;
import com.google.android.systemui.assist.uihints.GoogleDefaultUiController;
import com.google.android.systemui.assist.uihints.NgaMessageHandler;
import com.google.android.systemui.assist.uihints.NgaUiController;

import java.util.Objects;

import javax.inject.Inject;

import dagger.Lazy;

@SysUISingleton
public class AssistManagerGoogle extends AssistManager {

    private static final String TAG = "AssistManagerGoogle";
    private static final String SET_ASSIST_GESTURE_GLOBAL_ACTION = "show_global_actions";

    private final AssistantPresenceHandler mAssistantPresenceHandler;
    private final GoogleDefaultUiController mDefaultUiController;
    private final NgaMessageHandler mNgaMessageHandler;
    private final NgaUiController mNgaUiController;
    private final OpaEnabledReceiver mOpaEnabledReceiver;
    private final Handler mUiHandler;
    private final IWindowManager mWindowManagerService;

    private boolean mGoogleIsAssistant;
    private int mNavigationMode;
    private boolean mNgaIsAssistant;
    private boolean mSqueezeSetUp;
    private UiController mUiController;
    private boolean mCheckAssistantStatus = true;
    private final Runnable mOnProcessBundle = this::onProcessBundle;

    @Inject
    public AssistManagerGoogle(
            DeviceProvisionedController controller,
            Context context,
            AssistUtils assistUtils,
            NgaUiController ngaUiController,
            CommandQueue commandQueue,
            OpaEnabledReceiver opaEnabledReceiver,
            PhoneStateMonitor phoneStateMonitor,
            OverviewProxyService overviewProxyService,
            OpaEnabledDispatcher opaEnabledDispatcher,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            NavigationModeController navigationModeController,
            AssistantPresenceHandler assistantPresenceHandler,
            NgaMessageHandler ngaMessageHandler,
            Lazy<SysUiState> sysUiState,
            @Main Handler uiHandler,
            DefaultUiController defaultUiController,
            GoogleDefaultUiController googleDefaultUiController,
            IWindowManager windowManagerService,
            AssistLogger assistLogger) {
        super(controller, context, assistUtils, commandQueue, phoneStateMonitor,
                overviewProxyService, sysUiState, defaultUiController, assistLogger,
                uiHandler);
        mUiHandler = uiHandler;
        mOpaEnabledReceiver = opaEnabledReceiver;
        addOpaEnabledListener(opaEnabledDispatcher);
        keyguardUpdateMonitor.registerCallback(new KeyguardUpdateMonitorCallback() {
            @Override
            public void onUserSwitching(int userId) {
                mOpaEnabledReceiver.onUserSwitching(userId);
            }
        });
        mNgaUiController = ngaUiController;
        mDefaultUiController = googleDefaultUiController;
        mUiController = googleDefaultUiController;
        mNavigationMode = navigationModeController.addListener(mode -> mNavigationMode = mode);
        mAssistantPresenceHandler = assistantPresenceHandler;
        mAssistantPresenceHandler.registerAssistantPresenceChangeListener(this::onAssistantPresenceChanged);
        mNgaMessageHandler = ngaMessageHandler;
        mWindowManagerService = windowManagerService;
    }

    private void onAssistantPresenceChanged(boolean isGoogleAssistant, boolean isNgaAssistant) {
        if (!(mGoogleIsAssistant == isGoogleAssistant && mNgaIsAssistant == isNgaAssistant)) {
            if (!isNgaAssistant) {
                if (!mUiController.equals(mDefaultUiController)) {
                    final UiController uiController = mUiController;
                    mUiController = mDefaultUiController;
                    Objects.requireNonNull(uiController);
                    mUiHandler.post(uiController::hide);
                }
                mDefaultUiController.setGoogleAssistant(isGoogleAssistant);
            } else if (!mUiController.equals(mNgaUiController)) {
                final UiController uiController = mUiController;
                mUiController = mNgaUiController;
                Objects.requireNonNull(uiController);
                mUiHandler.post(uiController::hide);
            }
            mGoogleIsAssistant = isGoogleAssistant;
            mNgaIsAssistant = isNgaAssistant;
        }
        mCheckAssistantStatus = false;
    }

    private void onProcessBundle() {
        mAssistantPresenceHandler.requestAssistantPresenceUpdate();
        mCheckAssistantStatus = false;
    }

    public boolean shouldUseHomeButtonAnimations() {
        return !QuickStepContract.isGesturalMode(mNavigationMode);
    }

    @Override
    protected void registerVoiceInteractionSessionListener() {
        mAssistUtils.registerVoiceInteractionSessionListener(new IVoiceInteractionSessionListener.Stub() {
            @Override
            public void onVoiceSessionShown() throws RemoteException {
                mAssistLogger.reportAssistantSessionEvent(
                        AssistantSessionEvent.ASSISTANT_SESSION_UPDATE);
            }

            @Override
            public void onVoiceSessionHidden() throws RemoteException {
                mAssistLogger.reportAssistantSessionEvent(
                        AssistantSessionEvent.ASSISTANT_SESSION_CLOSE);
            }

            @Override
            public void onSetUiHints(Bundle hints) {
                String action = hints.getString(ACTION_KEY);
                if (SET_ASSIST_GESTURE_CONSTRAINED_ACTION.equals(action)) {
                    mSysUiState.get()
                            .setFlag(SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED,
                                    hints.getBoolean(CONSTRAINED_KEY, false))
                            .commitUpdate(DEFAULT_DISPLAY);
                } else if (SET_ASSIST_GESTURE_GLOBAL_ACTION.equals(action)) {
                    try {
                        mWindowManagerService.showGlobalActions();
                    } catch (RemoteException ex) {
                        Log.e(TAG, "showGlobalActions failed", ex);
                    }
                } else {
                    mNgaMessageHandler.processBundle(hints, mOnProcessBundle);
                }
            }
        });
    }

    /** Called when the user is performing an assistant invocation action (e.g. Active Edge) */
    @Override
    public void onInvocationProgress(int type, float progress) {
        if (progress == 0.0f || progress == 1.0f) {
            mCheckAssistantStatus = true;
            if (type == AssistUtils.INVOCATION_TYPE_PHYSICAL_GESTURE) {
                checkSqueezeGestureStatus();
            }
        }
        if (mCheckAssistantStatus) {
            mAssistantPresenceHandler.requestAssistantPresenceUpdate();
            mCheckAssistantStatus = false;
        }
        if (type != AssistUtils.INVOCATION_TYPE_PHYSICAL_GESTURE
                || mSqueezeSetUp) {
            mUiController.onInvocationProgress(type, progress);
        }
    }

    /**
     * Called when the user has invoked the assistant with the incoming velocity, in pixels per
     * millisecond. For invocations without a velocity (e.g. slow drag), the velocity is set to
     * zero.
     */
    @Override
    public void onGestureCompletion(float velocity) {
        mCheckAssistantStatus = true;
        mUiController.onGestureCompletion(
                velocity / mContext.getResources().getDisplayMetrics().density);
    }

    /** Returns the logging flags for the given Assistant invocation type. */
    @Override
    protected void logStartAssistLegacy(int invocationType, int phoneState) {
        MetricsLogger.action(
                new LogMaker(MetricsEvent.ASSISTANT)
                        .setType(MetricsEvent.TYPE_OPEN)
                        .setSubtype(((mAssistantPresenceHandler.isNgaAssistant() ? 1 : 0) << 8)
                                | toLoggingSubType(invocationType, phoneState)));
    }

    public void addOpaEnabledListener(OpaEnabledListener opaEnabledListener) {
        mOpaEnabledReceiver.addOpaEnabledListener(opaEnabledListener);
    }

    public boolean isActiveAssistantNga() {
        return mNgaIsAssistant;
    }

    public void dispatchOpaEnabledState() {
        mOpaEnabledReceiver.dispatchOpaEnabledState();
    }

    private void checkSqueezeGestureStatus() {
        mSqueezeSetUp = Settings.Secure.getInt(
                mContext.getContentResolver(),
                ASSIST_GESTURE_SETUP_COMPLETE, 0) == 1;
    }
}
