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
package com.benzorom.systemui.statusbar.phone;

import static com.android.systemui.Dependency.TIME_TICK_HANDLER_NAME;

import android.annotation.Nullable;
import android.app.WallpaperManager;
import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.lifecycle.Lifecycle;

import com.android.internal.logging.MetricsLogger;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.InitController;
import com.android.systemui.accessibility.floatingmenu.AccessibilityFloatingMenuController;
import com.android.systemui.animation.ActivityLaunchAnimator;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.classifier.FalsingCollector;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.demomode.DemoModeController;
import com.android.systemui.dock.DockManager;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.fragments.FragmentService;
import com.android.systemui.keyguard.DismissCallbackRegistry;
import com.android.systemui.keyguard.KeyguardUnlockAnimationController;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.navigationbar.NavigationBarController;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.PluginDependencyProvider;
import com.android.systemui.recents.ScreenPinningRequest;
import com.android.systemui.settings.brightness.BrightnessSliderController;
import com.android.systemui.shared.plugins.PluginManager;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.LockscreenShadeTransitionController;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.NotificationRemoteInputManager;
import com.android.systemui.statusbar.NotificationShadeDepthController;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.NotificationViewHierarchyManager;
import com.android.systemui.statusbar.OperatorNameViewController;
import com.android.systemui.statusbar.PulseExpansionHandler;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.VibratorHelper;
import com.android.systemui.statusbar.charging.WiredChargingRippleController;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler;
import com.android.systemui.statusbar.notification.DynamicPrivacyController;
import com.android.systemui.statusbar.notification.NotificationEntryManager;
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator;
import com.android.systemui.statusbar.notification.collection.legacy.VisualStabilityManager;
import com.android.systemui.statusbar.notification.collection.render.NotifShadeEventSource;
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
import com.android.systemui.statusbar.phone.LockscreenGestureLogger;
import com.android.systemui.statusbar.phone.LockscreenWallpaper;
import com.android.systemui.statusbar.phone.NotificationIconAreaController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.phone.ShadeController;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.phone.StatusBarHideIconsForBouncerManager;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.phone.StatusBarLocationPublisher;
import com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy;
import com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager;
import com.android.systemui.statusbar.phone.UnlockedScreenOffAnimationController;
import com.android.systemui.statusbar.phone.dagger.StatusBarComponent;
import com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragmentLogger;
import com.android.systemui.statusbar.phone.ongoingcall.OngoingCallController;
import com.android.systemui.statusbar.phone.panelstate.PanelExpansionStateManager;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.ExtensionController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler;
import com.android.systemui.statusbar.policy.UserInfoControllerImpl;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.statusbar.window.StatusBarWindowController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.WallpaperController;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.concurrency.MessageRouter;
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

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import dagger.Lazy;

@SysUISingleton
public class StatusBarGoogle extends StatusBar {
    private static final String TAG = "StatusBarGoogle";
    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private long mAnimStartTime;
    private final BatteryController.BatteryStateChangeCallback mBatteryStateChangeCallback =
            new BatteryController.BatteryStateChangeCallback() {
                @Override
                public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
                    mReceivingBatteryLevel = level;
                    if (!mBatteryController.isWirelessCharging()) {
                        if (SystemClock.uptimeMillis() - mAnimStartTime > 1500)
                            mChargingAnimShown = false;
                        mReverseChargingAnimShown = false;
                    }
                    if (DEBUG) Log.d(TAG,
                            "onBatteryLevelChanged(): level=" + level
                                    + ",wlc=" + (mBatteryController.isWirelessCharging() ? 1 : 0)
                                    + ",wlcs=" + mChargingAnimShown
                                    + ",rtxs=" + mReverseChargingAnimShown
                                    + ",this=" + this);
                }

                @Override
                public void onReverseChanged(boolean isReverse, int level, String name) {
                    if (!isReverse && (level >= 0) && !TextUtils.isEmpty(name)
                            && mBatteryController.isWirelessCharging()
                            && mChargingAnimShown && !mReverseChargingAnimShown) {
                        mReverseChargingAnimShown = true;
                        long uptimeMillis = SystemClock.uptimeMillis() - mAnimStartTime;
                        long animationDelay = (uptimeMillis > 1500) ? 0L : (1500 - uptimeMillis);
                        showChargingAnimation(mReceivingBatteryLevel, level, animationDelay);
                    }
                    if (DEBUG) Log.d(TAG,
                            "onReverseChanged(): rtx=" + (isReverse ? 1 : 0)
                                    + ",rxlevel=" + mReceivingBatteryLevel
                                    + ",level=" + level
                                    + ",name=" + name
                                    + ",wlc=" + (mBatteryController.isWirelessCharging() ? 1 : 0)
                                    + ",wlcs=" + mChargingAnimShown
                                    + ",rtxs=" + mReverseChargingAnimShown
                                    + ",this=" + this);
                }
            };
    private boolean mChargingAnimShown;
    private final KeyguardIndicationControllerGoogle mKeyguardIndicationController;
    private int mReceivingBatteryLevel;
    private boolean mReverseChargingAnimShown;
    private Optional<ReverseChargingViewController> mReverseChargingViewControllerOptional;
    private final SmartSpaceController mSmartSpaceController;
    private SysuiStatusBarStateController mStatusBarStateController;
    private final Lazy<Optional<NotificationVoiceReplyClient>> mVoiceReplyClient;
    private WallpaperNotifier mWallpaperNotifier;

    @Inject
    public StatusBarGoogle(
            SmartSpaceController smartSpaceController,
            WallpaperNotifier wallpaperNotifier,
            Optional<ReverseChargingViewController> reverseChargingViewControllerOptional,
            Context context,
            NotificationsController notificationsController,
            FragmentService fragmentService,
            LightBarController lightBarController,
            AutoHideController autoHideController,
            StatusBarWindowController statusBarWindowController,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            StatusBarSignalPolicy statusBarSignalPolicy,
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
            NotifShadeEventSource notifShadeEventSource,
            NotificationEntryManager notificationEntryManager,
            NotificationGutsManager notificationGutsManager,
            NotificationLogger notificationLogger,
            NotificationInterruptStateProvider notificationInterruptStateProvider,
            NotificationViewHierarchyManager notificationViewHierarchyManager,
            PanelExpansionStateManager panelExpansionStateManager,
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
            LockscreenGestureLogger lockscreenGestureLogger,
            Lazy<BiometricUnlockController> biometricUnlockControllerLazy,
            Lazy<NotificationShadeDepthController> notificationShadeDepthControllerLazy,
            DozeServiceHost dozeServiceHost,
            PowerManager powerManager,
            ScreenPinningRequest screenPinningRequest,
            DozeScrimController dozeScrimController,
            VolumeComponent volumeComponent,
            CommandQueue commandQueue,
            CollapsedStatusBarFragmentLogger collapsedStatusBarFragmentLogger,
            StatusBarComponent.Factory statusBarComponentFactory,
            PluginManager pluginManager,
            Optional<LegacySplitScreen> splitScreenOptional,
            LightsOutNotifController lightsOutNotifController,
            StatusBarNotificationActivityStarter.Builder
                    statusBarNotificationActivityStarterBuilder,
            ShadeController shadeController,
            StatusBarKeyguardViewManager statusBarKeyguardViewManager,
            ViewMediatorCallback viewMediatorCallback,
            InitController initController,
            @Named(TIME_TICK_HANDLER_NAME) Handler timeTickHandler,
            PluginDependencyProvider pluginDependency,
            KeyguardDismissUtil keyguardDismissUtil,
            ExtensionController extensionController,
            UserInfoControllerImpl userInfoControllerImpl,
            OperatorNameViewController.Factory operatorNameViewControllerFactory,
            PhoneStatusBarPolicy phoneStatusBarPolicy,
            KeyguardIndicationControllerGoogle keyguardIndicationController,
            DismissCallbackRegistry dismissCallbackRegistry,
            DemoModeController demoModeController,
            StatusBarTouchableRegionManager statusBarTouchableRegionManager,
            NotificationIconAreaController notificationIconAreaController,
            BrightnessSliderController.Factory brightnessSliderFactory,
            WiredChargingRippleController wiredChargingRippleController,
            WallpaperController wallpaperController,
            OngoingCallController ongoingCallController,
            SystemStatusAnimationScheduler animationScheduler,
            StatusBarLocationPublisher locationPublisher,
            StatusBarIconController statusBarIconController,
            StatusBarHideIconsForBouncerManager statusBarHideIconsForBouncerManager,
            LockscreenShadeTransitionController lockscreenShadeTransitionController,
            FeatureFlags featureFlags,
            Lazy<Optional<NotificationVoiceReplyClient>> notificationVoiceReplyClient,
            KeyguardUnlockAnimationController keyguardUnlockAnimationController,
            @Main Handler mainHandler,
            @Main DelayableExecutor delayableExecutor,
            @Main MessageRouter messageRouter,
            WallpaperManager wallpaperManager,
            UnlockedScreenOffAnimationController unlockedScreenOffAnimationController,
            Optional<StartingSurface> startingSurfaceOptional,
            TunerService tunerService,
            DumpManager dumpManager,
            ActivityLaunchAnimator activityLaunchAnimator) {
        super(context, notificationsController, fragmentService, lightBarController, autoHideController,
                statusBarWindowController, keyguardUpdateMonitor, statusBarSignalPolicy, pulseExpansionHandler,
                notificationWakeUpCoordinator, keyguardBypassController, keyguardStateController,
                headsUpManagerPhone, dynamicPrivacyController, bypassHeadsUpNotifier, falsingManager,
                falsingCollector, broadcastDispatcher, notifShadeEventSource, notificationEntryManager,
                notificationGutsManager, notificationLogger, notificationInterruptStateProvider,
                notificationViewHierarchyManager, panelExpansionStateManager, keyguardViewMediator,
                displayMetrics, metricsLogger, uiBgExecutor, notificationMediaManager, lockScreenUserManager,
                remoteInputManager, userSwitcherController, networkController, batteryController,
                colorExtractor, screenLifecycle, wakefulnessLifecycle, statusBarStateController,
                bubblesManagerOptional, bubblesOptional, visualStabilityManager, deviceProvisionedController,
                navigationBarController, accessibilityFloatingMenuController, assistManagerLazy,
                configurationController, notificationShadeWindowController, dozeParameters, scrimController,
                lockscreenWallpaperLazy, lockscreenGestureLogger, biometricUnlockControllerLazy,
                dozeServiceHost, powerManager, screenPinningRequest, dozeScrimController, volumeComponent,
                commandQueue, collapsedStatusBarFragmentLogger, statusBarComponentFactory, pluginManager,
                splitScreenOptional, lightsOutNotifController, statusBarNotificationActivityStarterBuilder,
                shadeController, statusBarKeyguardViewManager, viewMediatorCallback, initController,
                timeTickHandler, pluginDependency, keyguardDismissUtil, extensionController, userInfoControllerImpl,
                operatorNameViewControllerFactory, phoneStatusBarPolicy, keyguardIndicationController,
                demoModeController, notificationShadeDepthControllerLazy, statusBarTouchableRegionManager,
                notificationIconAreaController, brightnessSliderFactory, wallpaperController,
                ongoingCallController, animationScheduler, locationPublisher, statusBarIconController,
                statusBarHideIconsForBouncerManager, lockscreenShadeTransitionController, featureFlags,
                keyguardUnlockAnimationController, mainHandler, delayableExecutor, messageRouter,
                wallpaperManager, unlockedScreenOffAnimationController, startingSurfaceOptional,
                tunerService, dumpManager, activityLaunchAnimator);
        mSmartSpaceController = smartSpaceController;
        mWallpaperNotifier = wallpaperNotifier;
        mReverseChargingViewControllerOptional = reverseChargingViewControllerOptional;
        mVoiceReplyClient = notificationVoiceReplyClient;
        mKeyguardIndicationController = keyguardIndicationController;
        mKeyguardIndicationController.setStatusBar(this);
        mStatusBarStateController = statusBarStateController;
    }

    @Override
    public void start() {
        super.start();
        mBatteryController.observe(getLifecycle(), mBatteryStateChangeCallback);
        ((NotificationLockscreenUserManagerGoogle) Dependency.get(NotificationLockscreenUserManager.class)).updateSmartSpaceVisibilitySettings();
        DockObserver dockObserver = (DockObserver) Dependency.get(DockManager.class);
        dockObserver.setDreamlinerGear(mNotificationShadeWindowView.findViewById(R.id.dreamliner_gear));
        dockObserver.setPhotoPreview(mNotificationShadeWindowView.findViewById(R.id.photo_preview));
        dockObserver.setIndicationController(new DockIndicationController(mContext, mKeyguardIndicationController, mStatusBarStateController, this));
        dockObserver.registerDockAlignInfo();
        mReverseChargingViewControllerOptional.ifPresent(ReverseChargingViewController::initialize);
        mWallpaperNotifier.attach();
        mVoiceReplyClient.get().ifPresent(NotificationVoiceReplyClient::startClient);
    }

    @Override
    public void setLockscreenUser(int newUserId) {
        super.setLockscreenUser(newUserId);
        mSmartSpaceController.reloadData();
    }

    @Override
    public void showWirelessChargingAnimation(int batteryLevel) {
        if (DEBUG) Log.d(TAG, "showWirelessChargingAnimation()");
        mChargingAnimShown = true;
        super.showWirelessChargingAnimation(batteryLevel);
        mAnimStartTime = SystemClock.uptimeMillis();
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        mSmartSpaceController.dump(fd, pw, args);
    }
}
