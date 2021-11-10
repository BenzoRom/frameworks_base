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

package com.benzorom.systemui.statusbar.phone;

import static com.android.systemui.Dependency.TIME_TICK_HANDLER_NAME;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;

import com.android.internal.logging.MetricsLogger;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.InitController;
import com.android.systemui.accessibility.floatingmenu.AccessibilityFloatingMenuController;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.classifier.FalsingCollector;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.demomode.DemoModeController;
import com.android.systemui.dock.DockManager;
import com.android.systemui.keyguard.DismissCallbackRegistry;
import com.android.systemui.keyguard.KeyguardUnlockAnimationController;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.navigationbar.NavigationBarController;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.PluginDependencyProvider;
import com.android.systemui.recents.ScreenPinningRequest;
import com.android.systemui.settings.brightness.BrightnessSlider;
import com.android.systemui.shared.plugins.PluginManager;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.FeatureFlags;
import com.android.systemui.statusbar.LockscreenShadeTransitionController;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.NotificationRemoteInputManager;
import com.android.systemui.statusbar.NotificationShadeDepthController;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.NotificationViewHierarchyManager;
import com.android.systemui.statusbar.PulseExpansionHandler;
import com.android.systemui.statusbar.SuperStatusBarViewFactory;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.VibratorHelper;
import com.android.systemui.statusbar.charging.WiredChargingRippleController;
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler;
import com.android.systemui.statusbar.notification.DynamicPrivacyController;
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator;
import com.android.systemui.statusbar.notification.collection.legacy.VisualStabilityManager;
import com.android.systemui.statusbar.notification.init.NotificationsController;
import com.android.systemui.statusbar.notification.interruption.BypassHeadsUpNotifier;
import com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProvider;
import com.android.systemui.statusbar.notification.logging.NotificationLogger;
import com.android.systemui.statusbar.notification.row.NotificationGutsManager;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.BiometricUnlockController;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.phone.DozeScrimController;
import com.android.systemui.statusbar.phone.DozeServiceHost;
import com.android.systemui.statusbar.phone.HeadsUpManagerPhone;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.KeyguardDismissUtil;
import com.android.systemui.statusbar.phone.KeyguardLiftController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.LightsOutNotifController;
import com.android.systemui.statusbar.phone.LockscreenWallpaper;
import com.android.systemui.statusbar.phone.NotificationIconAreaController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.phone.ShadeController;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.phone.StatusBarLocationPublisher;
import com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy;
import com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager;
import com.android.systemui.statusbar.phone.UnlockedScreenOffAnimationController;
import com.android.systemui.statusbar.phone.dagger.StatusBarComponent;
import com.android.systemui.statusbar.phone.ongoingcall.OngoingCallController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.ExtensionController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler;
import com.android.systemui.statusbar.policy.UserInfoControllerImpl;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.volume.VolumeComponent;
import com.android.systemui.wmshell.BubblesManager;
import com.android.wm.shell.bubbles.Bubbles;
import com.android.wm.shell.legacysplitscreen.LegacySplitScreen;
import com.android.wm.shell.startingsurface.StartingSurface;
import com.google.android.systemui.LiveWallpaperScrimController;
import com.google.android.systemui.NotificationLockscreenUserManagerGoogle;
import com.google.android.systemui.dreamliner.DockIndicationController;
import com.google.android.systemui.dreamliner.DockObserver;
import com.google.android.systemui.reversecharging.ReverseChargingViewController;
import com.google.android.systemui.smartspace.SmartSpaceController;
import com.google.android.systemui.statusbar.KeyguardIndicationControllerGoogle;
import com.google.android.systemui.statusbar.notification.voicereplies.NotificationVoiceReplyClient;
import com.google.android.systemui.statusbar.phone.WallpaperNotifier;

import dagger.Lazy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Provider;

public class StatusBarGoogle extends StatusBar {

    private static final String TAG = "StatusBarGoogle";
    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final KeyguardIndicationControllerGoogle mKeyguardIndicationController;
    private final SmartSpaceController mSmartSpaceController;
    private final SysuiStatusBarStateController mStatusBarStateController;
    private final Lazy<Optional<NotificationVoiceReplyClient>> mVoiceReplyClient;
    private final Optional<ReverseChargingViewController> mReverseChargingViewControllerOptional;
    private final WallpaperNotifier mWallpaperNotifier;
    private long mAnimStartTime;
    private boolean mChargingAnimShown;
    private int mReceivingBatteryLevel;
    private boolean mReverseChargingAnimShown;

    public StatusBarGoogle(
            SmartSpaceController smartSpaceController,
            WallpaperNotifier wallpaperNotifier,
            Optional<ReverseChargingViewController> reverseChargingViewController,
            Context context,
            NotificationsController notificationsController,
            LightBarController lightBarController,
            AutoHideController autoHideController,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            StatusBarSignalPolicy signalPolicy,
            PulseExpansionHandler pulseExpansionHandler,
            NotificationWakeUpCoordinator notificationWakeUpCoordinator,
            KeyguardBypassController keyguardBypassController,
            KeyguardStateController keyguardStateController,
            HeadsUpManagerPhone headsUpManagerPhone,
            DynamicPrivacyController dynamicPrivacyController,
            BypassHeadsUpNotifier bypassHeadsUpNotifier,
            FalsingManager falsingManager,
            FalsingCollector falsingCollector,
            BroadcastDispatcher broadcastDispatcher,
            RemoteInputQuickSettingsDisabler remoteInputQuickSettingsDisabler,
            NotificationGutsManager notificationGutsManager,
            NotificationLogger notificationLogger,
            NotificationInterruptStateProvider notificationInterruptStateProvider,
            NotificationViewHierarchyManager notificationViewHierarchyManager,
            KeyguardViewMediator keyguardViewMediator,
            DisplayMetrics displayMetrics,
            MetricsLogger metricsLogger,
            @UiBackground Executor uiBgExecutor,
            NotificationMediaManager notificationMediaManager,
            NotificationLockscreenUserManager lockScreenUserManager,
            NotificationRemoteInputManager remoteInputManager,
            UserSwitcherController userSwitcherController,
            NetworkController networkController,
            BatteryController batteryController,
            SysuiColorExtractor colorExtractor,
            ScreenLifecycle screenLifecycle,
            WakefulnessLifecycle wakefulnessLifecycle,
            SysuiStatusBarStateController statusBarStateController,
            VibratorHelper vibratorHelper,
            Optional<BubblesManager> bubblesManagerOptional,
            Optional<Bubbles> bubblesOptional,
            VisualStabilityManager visualStabilityManager,
            DeviceProvisionedController deviceProvisionedController,
            NavigationBarController navigationBarController,
            AccessibilityFloatingMenuController accessibilityFloatingMenuController,
            Lazy<AssistManager> assistManagerLazy,
            ConfigurationController configurationController,
            NotificationShadeWindowController notificationShadeWindowController,
            DozeParameters dozeParameters,
            LiveWallpaperScrimController scrimController,
            @Nullable KeyguardLiftController keyguardLiftController,
            Lazy<LockscreenWallpaper> lockscreenWallpaperLazy,
            Lazy<BiometricUnlockController> biometricUnlockControllerLazy,
            Lazy<NotificationShadeDepthController> notificationShadeDepthControllerLazy,
            DozeServiceHost dozeServiceHost,
            PowerManager powerManager,
            ScreenPinningRequest screenPinningRequest,
            DozeScrimController dozeScrimController,
            VolumeComponent volumeComponent,
            CommandQueue commandQueue,
            Provider<StatusBarComponent.Builder> statusBarComponentBuilder,
            PluginManager pluginManager,
            Optional<LegacySplitScreen> splitScreenOptional,
            LightsOutNotifController lightsOutNotifController,
            StatusBarNotificationActivityStarter.Builder
                    statusBarNotificationActivityStarterBuilder,
            ShadeController shadeController,
            SuperStatusBarViewFactory superStatusBarViewFactory,
            StatusBarKeyguardViewManager statusBarKeyguardViewManager,
            ViewMediatorCallback viewMediatorCallback,
            InitController initController,
            @Named(TIME_TICK_HANDLER_NAME) Handler timeTickHandler,
            PluginDependencyProvider pluginDependency,
            KeyguardDismissUtil keyguardDismissUtil,
            ExtensionController extensionController,
            UserInfoControllerImpl userInfoControllerImpl,
            PhoneStatusBarPolicy phoneStatusBarPolicy,
            KeyguardIndicationControllerGoogle keyguardIndicationController,
            DismissCallbackRegistry dismissCallbackRegistry,
            DemoModeController demoModeController,
            StatusBarTouchableRegionManager statusBarTouchableRegionManager,
            NotificationIconAreaController notificationIconAreaController,
            BrightnessSlider.Factory brightnessSliderFactory,
            WiredChargingRippleController wiredChargingRippleController,
            OngoingCallController ongoingCallController,
            SystemStatusAnimationScheduler animationScheduler,
            StatusBarLocationPublisher locationPublisher,
            StatusBarIconController statusBarIconController,
            LockscreenShadeTransitionController lockscreenShadeTransitionController,
            FeatureFlags featureFlags,
            Lazy<Optional<NotificationVoiceReplyClient>> notificationVoiceReplyClient,
            KeyguardUnlockAnimationController keyguardUnlockAnimationController,
            UnlockedScreenOffAnimationController unlockedScreenOffAnimationController,
            Optional<StartingSurface> startingSurfaceOptional,
            TunerService tunerService) {
        super(context, notificationsController, lightBarController, autoHideController, keyguardUpdateMonitor,
                signalPolicy, pulseExpansionHandler, notificationWakeUpCoordinator, keyguardBypassController,
                keyguardStateController, headsUpManagerPhone, dynamicPrivacyController, bypassHeadsUpNotifier,
                falsingManager, falsingCollector, broadcastDispatcher, remoteInputQuickSettingsDisabler,
                notificationGutsManager, notificationLogger, notificationInterruptStateProvider,
                notificationViewHierarchyManager, keyguardViewMediator, displayMetrics, metricsLogger,
                uiBgExecutor, notificationMediaManager, lockScreenUserManager, remoteInputManager,
                userSwitcherController, networkController, batteryController, colorExtractor, screenLifecycle,
                wakefulnessLifecycle, statusBarStateController, vibratorHelper, bubblesManagerOptional,
                bubblesOptional, visualStabilityManager, deviceProvisionedController, navigationBarController,
                accessibilityFloatingMenuController, assistManagerLazy, configurationController,
                notificationShadeWindowController, dozeParameters, scrimController, keyguardLiftController,
                lockscreenWallpaperLazy, biometricUnlockControllerLazy, dozeServiceHost, powerManager,
                screenPinningRequest, dozeScrimController, volumeComponent, commandQueue, statusBarComponentBuilder,
                pluginManager, splitScreenOptional, lightsOutNotifController, statusBarNotificationActivityStarterBuilder,
                shadeController, superStatusBarViewFactory, statusBarKeyguardViewManager, viewMediatorCallback, initController,
                timeTickHandler, pluginDependency, keyguardDismissUtil, extensionController, userInfoControllerImpl,
                phoneStatusBarPolicy, keyguardIndicationController, dismissCallbackRegistry, demoModeController,
                notificationShadeDepthControllerLazy, statusBarTouchableRegionManager, notificationIconAreaController,
                brightnessSliderFactory, wiredChargingRippleController, ongoingCallController, animationScheduler,
                locationPublisher, statusBarIconController, lockscreenShadeTransitionController, featureFlags,
                keyguardUnlockAnimationController, unlockedScreenOffAnimationController, startingSurfaceOptional,
                tunerService);
        mSmartSpaceController = smartSpaceController;
        mWallpaperNotifier = wallpaperNotifier;
        mReverseChargingViewControllerOptional = reverseChargingViewController;
        mVoiceReplyClient = notificationVoiceReplyClient;
        mKeyguardIndicationController = keyguardIndicationController;
        mKeyguardIndicationController.setStatusBar(this);
        mStatusBarStateController = statusBarStateController;
    }

    @Override
    public void start() {
        super.start();
        ((NotificationLockscreenUserManagerGoogle) Dependency.get(NotificationLockscreenUserManager.class)).updateSmartSpaceVisibilitySettings();
        DockObserver dockObserver = (DockObserver) Dependency.get(DockManager.class);
        dockObserver.setDreamlinerGear(mNotificationShadeWindowView.findViewById(R.id.dreamliner_gear));
        dockObserver.setPhotoPreview(mNotificationShadeWindowView.findViewById(R.id.photo_preview));
        dockObserver.setIndicationController(new DockIndicationController(mContext, mKeyguardIndicationController, mStatusBarStateController, this));
        dockObserver.registerDockAlignInfo();
        if (mReverseChargingViewControllerOptional.isPresent()) {
            mReverseChargingViewControllerOptional.get().initialize();
        }
        mWallpaperNotifier.attach();
        mVoiceReplyClient.get().ifPresent(NotificationVoiceReplyClient::startClient);
    }

    @Override
    public void setLockscreenUser(int newUserId) {
        super.setLockscreenUser(newUserId);
        mSmartSpaceController.reloadData();
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        mSmartSpaceController.dump(fd, pw, args);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        super.onBatteryLevelChanged(level, pluggedIn, charging);
        mReceivingBatteryLevel = level;
        if (!mBatteryController.isWirelessCharging()) {
            if (SystemClock.uptimeMillis() - mAnimStartTime > 1500) {
                mChargingAnimShown = false;
            }
            mReverseChargingAnimShown = false;
        }
        if (DEBUG) {
            Log.d(TAG, "onBatteryLevelChanged(): level=" + level + ",wlc=" + (mBatteryController.isWirelessCharging() ? 1 : 0)
                    + ",wlcs=" + mChargingAnimShown + ",rtxs=" + mReverseChargingAnimShown + ",this=" + this);
        }
    }

    @Override
    public void onReverseChanged(boolean isReverse, int level, String name) {
        super.onReverseChanged(isReverse, level, name);
        if (!isReverse && level >= 0 && !TextUtils.isEmpty(name) && mBatteryController.isWirelessCharging() && mChargingAnimShown && !mReverseChargingAnimShown) {
            mReverseChargingAnimShown = true;
            long uptimeMillis = SystemClock.uptimeMillis() - mAnimStartTime;
            showChargingAnimation(mReceivingBatteryLevel, level, uptimeMillis > 1500 ? 0 : 1500 - uptimeMillis);
        }
        if (DEBUG) {
            Log.d(TAG, "onReverseChanged(): rtx=" + (isReverse ? 1 : 0) + ",rxlevel=" + mReceivingBatteryLevel
                    + ",level=" + level + ",name=" + name + ",wlc=" + (mBatteryController.isWirelessCharging() ? 1 : 0)
                    + ",wlcs=" + mChargingAnimShown + ",rtxs=" + mReverseChargingAnimShown + ",this=" + this);
        }
    }

    @Override
    public void showWirelessChargingAnimation(int batteryLevel) {
        if (DEBUG) Log.d(TAG, "showWirelessChargingAnimation()");
        mChargingAnimShown = true;
        super.showWirelessChargingAnimation(batteryLevel);
        mAnimStartTime = SystemClock.uptimeMillis();
    }
}
