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
package com.benzorom.systemui.elmyra;

import android.content.Context;

import com.android.systemui.R;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.telephony.TelephonyListenerManager;
import com.google.android.systemui.elmyra.ServiceConfigurationGoogle;
import com.google.android.systemui.elmyra.actions.CameraAction;
import com.google.android.systemui.elmyra.actions.LaunchOpa;
import com.google.android.systemui.elmyra.actions.UnpinNotifications;
import com.google.android.systemui.elmyra.actions.SettingsAction;
import com.google.android.systemui.elmyra.actions.SetupWizardAction;
import com.google.android.systemui.elmyra.actions.SilenceCall;
import com.google.android.systemui.elmyra.feedback.AssistInvocationEffect;
import com.google.android.systemui.elmyra.feedback.OpaHomeButton;
import com.google.android.systemui.elmyra.feedback.OpaLockscreen;
import com.google.android.systemui.elmyra.feedback.SquishyNavigationButtons;
import com.google.android.systemui.elmyra.gates.TelephonyActivity;
import com.benzorom.systemui.assist.AssistManagerGoogle;

import java.util.*;
import java.util.Optional;

import dagger.Module;
import dagger.Provides;

@Module
public interface ElmyraModule {
    @Provides
    @SysUISingleton
    static ServiceConfigurationGoogle provideServiceConfigurationGoogle(
            Context context,
            AssistInvocationEffect assistInvocationEffect,
            LaunchOpa.Builder launchOpaBuilder,
            SettingsAction.Builder settingsActionBuilder,
            CameraAction.Builder cameraActionBuilder,
            SetupWizardAction.Builder setupWizardActionBuilder,
            SquishyNavigationButtons squishyNavigationButtons,
            UnpinNotifications unpinNotifications,
            SilenceCall silenceCall,
            TelephonyActivity telephonyActivity) {
        return new ServiceConfigurationGoogle(
                context,
                assistInvocationEffect,
                launchOpaBuilder,
                settingsActionBuilder,
                cameraActionBuilder,
                setupWizardActionBuilder,
                squishyNavigationButtons,
                unpinNotifications,
                silenceCall,
                telephonyActivity);
    }

    @Provides
    @SysUISingleton
    static AssistInvocationEffect provideAssistInvocationEffectElmyra(
            AssistManagerGoogle assistManagerGoogle,
            OpaHomeButton opaHomeButton,
            OpaLockscreen opaLockscreen) {
        return new AssistInvocationEffect(assistManagerGoogle, opaHomeButton, opaLockscreen);
    }

    @Provides
    @SysUISingleton
    static OpaHomeButton provideOpaHomeButton(
            KeyguardViewMediator keyguardViewMediator,
            StatusBar statusBar,
            NavigationModeController navModeController) {
        return new OpaHomeButton(keyguardViewMediator, statusBar, navModeController);
    }

    @Provides
    @SysUISingleton
    static OpaLockscreen provideOpaLockscreen(
            StatusBar statusBar, KeyguardStateController keyguardStateController) {
        return new OpaLockscreen(statusBar, keyguardStateController);
    }

    @Provides
    @SysUISingleton
    static SquishyNavigationButtons provideSquishyNavigationButtons(
            Context context,
            KeyguardViewMediator keyguardViewMediator,
            StatusBar statusBar,
            NavigationModeController navModeController) {
        return new SquishyNavigationButtons(
                context,
                keyguardViewMediator,
                statusBar,
                navModeController);
    }

    @Provides
    @SysUISingleton
    static TelephonyActivity provideTelephonyActivityElmyra(
            Context context, TelephonyListenerManager telephonyListenerManager) {
        return new TelephonyActivity(context, telephonyListenerManager);
    }

    @Provides
    @SysUISingleton
    static SetupWizardAction.Builder provideSetupWizardAction(
            Context context, StatusBar statusBar) {
        return new SetupWizardAction.Builder(context, statusBar);
    }

    @Provides
    @SysUISingleton
    static UnpinNotifications provideUnpinNotificationsElmyra(
            Context context, Optional<HeadsUpManager> headsUpManagerOptional) {
        return new UnpinNotifications(context, headsUpManagerOptional);
    }

    @Provides
    @SysUISingleton
    static LaunchOpa.Builder provideLaunchOpaElmyra(
            Context context, StatusBar statusBar) {
        return new LaunchOpa.Builder(context, statusBar);
    }

    @Provides
    @SysUISingleton
    static SilenceCall provideSilenceCallElmyra(
            Context context, TelephonyListenerManager telephonyListenerManager) {
        return new SilenceCall(context, telephonyListenerManager);
    }

    @Provides
    @SysUISingleton
    static SettingsAction.Builder provideSettingsActionElmyra(
            Context context, StatusBar statusBar) {
        return new SettingsAction.Builder(context, statusBar);
    }

    @Provides
    @SysUISingleton
    static CameraAction.Builder provideCameraAction(
            Context context, StatusBar statusBar) {
        return new CameraAction.Builder(context, statusBar);
    }
}
