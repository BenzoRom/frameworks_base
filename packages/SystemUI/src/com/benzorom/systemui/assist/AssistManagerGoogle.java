/*
 * Copyright (C) 2021 The Pixel Experience Project
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

package com.benzorom.systemui.assist;

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
import com.android.systemui.assist.AssistantSessionEvent;
import com.android.systemui.assist.PhoneStateMonitor;
import com.android.systemui.assist.ui.DefaultUiController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.model.SysUiState;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.recents.OverviewProxyService;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.google.android.systemui.assist.OpaEnabledDispatcher;
import com.google.android.systemui.assist.OpaEnabledListener;
import com.google.android.systemui.assist.OpaEnabledReceiver;
import com.google.android.systemui.assist.uihints.AssistantPresenceHandler;
import com.google.android.systemui.assist.uihints.GoogleDefaultUiController;
import com.google.android.systemui.assist.uihints.NgaMessageHandler;
import com.google.android.systemui.assist.uihints.NgaUiController;

import dagger.Lazy;

import java.util.Objects;

import javax.inject.Inject;

@SysUISingleton
public class AssistManagerGoogle extends AssistManager {
    private static final String TAG = "AssistManagerGoogle";
    protected static final String ACTION_KEY = "action";
    protected static final String SET_ASSIST_GESTURE_CONSTRAINED_ACTION =
            "set_assist_gesture_constrained";
    protected static final String CONSTRAINED_KEY = "should_constrain";
    protected static final String SHOW_GLOBAL_ACTIONS_ACTION = "show_global_actions";
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
    private AssistManager.UiController mUiController;
    private boolean mCheckAssistantStatus = true;
    private final Runnable mOnProcessBundle = new Runnable() {
        @Override
        public final void run() {
            mAssistantPresenceHandler.requestAssistantPresenceUpdate();
            mCheckAssistantStatus = false;
        }
    };

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
            ConfigurationController configurationController,
            AssistantPresenceHandler assistantPresenceHandler,
            NgaMessageHandler ngaMessageHandler,
            Lazy<SysUiState> sysUiState,
            Handler uiHandler,
            DefaultUiController defaultUiController,
            GoogleDefaultUiController googleDefaultUiController,
            IWindowManager windowManagerService,
            AssistLogger assistLogger) {
        super(controller, context, assistUtils, commandQueue,
              phoneStateMonitor, overviewProxyService, configurationController,
              sysUiState, defaultUiController, assistLogger);
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
        mNavigationMode = navigationModeController.addListener((mode) -> mNavigationMode = mode);
        mAssistantPresenceHandler = assistantPresenceHandler;
        assistantPresenceHandler.registerAssistantPresenceChangeListener(
            (isGoogleAssistant, isNgaAssistant) -> {
                if (!(mGoogleIsAssistant == isGoogleAssistant && mNgaIsAssistant == isNgaAssistant)) {
                    if (!isNgaAssistant) {
                        if (!mUiController.equals(mDefaultUiController)) {
                            mUiController = mDefaultUiController;
                            Objects.requireNonNull(mUiController);
                            mUiHandler.post(() -> mUiController.hide());
                        }
                        mDefaultUiController.setGoogleAssistant(isGoogleAssistant);
                    } else if (!mUiController.equals(mNgaUiController)) {
                        mUiController = mNgaUiController;
                        Objects.requireNonNull(mUiController);
                        mUiHandler.post(() -> mUiController.hide());
                    }
                    mGoogleIsAssistant = isGoogleAssistant;
                    mNgaIsAssistant = isNgaAssistant;
                }
                mCheckAssistantStatus = false;
            }
        );
        mNgaMessageHandler = ngaMessageHandler;
        mWindowManagerService = windowManagerService;
    }

    @Override
    public boolean shouldShowOrb() {
        return false;
    }

    public boolean shouldUseHomeButtonAnimations() {
        return !QuickStepContract.isGesturalMode(mNavigationMode);
    }

    @Override
    protected void registerVoiceInteractionSessionListener() {
        mAssistUtils.registerVoiceInteractionSessionListener(new IVoiceInteractionSessionListener.Stub() {
            @Override
            public void onVoiceSessionShown() {
                mAssistLogger.reportAssistantSessionEvent(AssistantSessionEvent.ASSISTANT_SESSION_UPDATE);
            }

            @Override
            public void onVoiceSessionHidden() {
                mAssistLogger.reportAssistantSessionEvent(AssistantSessionEvent.ASSISTANT_SESSION_CLOSE);
            }

            @Override
            public void onSetUiHints(Bundle hints) {
                String action = hints.getString(ACTION_KEY);
                if (SET_ASSIST_GESTURE_CONSTRAINED_ACTION.equals(action)) {
                    mSysUiState.get().setFlag(
                        SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED,
                        hints.getBoolean(CONSTRAINED_KEY, false)
                    ).commitUpdate(DEFAULT_DISPLAY);
                } else if (SHOW_GLOBAL_ACTIONS_ACTION.equals(action)) {
                    try {
                        mWindowManagerService.showGlobalActions();
                    } catch (RemoteException e) {
                        Log.e(TAG, "showGlobalActions failed", e);
                    }
                } else {
                    mNgaMessageHandler.processBundle(hints, mOnProcessBundle);
                }
            }
        });
    }

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
        if (type != AssistUtils.INVOCATION_TYPE_PHYSICAL_GESTURE || mSqueezeSetUp) {
            mUiController.onInvocationProgress(type, progress);
        }
    }

    @Override
    public void onGestureCompletion(float velocity) {
        mCheckAssistantStatus = true;
        mUiController.onGestureCompletion(velocity / mContext.getResources().getDisplayMetrics().density);
    }

    @Override
    protected void logStartAssistLegacy(int invocationType, int phoneState) {
        MetricsLogger.action(
                new LogMaker(MetricsEvent.ASSISTANT)
                        .setType(MetricsEvent.TYPE_OPEN)
                        .setSubtype(((mAssistantPresenceHandler.isNgaAssistant() ? 1 : 0) << 8) | toLoggingSubType(invocationType, phoneState)));
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
        boolean completed = false;
        if (Settings.Secure.getInt(mContext.getContentResolver(),
                    "assist_gesture_setup_complete", 0) == 1) {
            completed = true;
        }
        mSqueezeSetUp = completed;
    }
}
