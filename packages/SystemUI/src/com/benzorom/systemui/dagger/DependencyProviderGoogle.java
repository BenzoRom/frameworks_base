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
package com.benzorom.systemui.dagger;

import static com.benzorom.systemui.Dependency.*;

import android.app.AlarmManager;
import android.app.IActivityManager;
import android.app.IWallpaperManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.StatsManager;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IThermalService;
import android.os.PowerManager;
import android.os.UserManager;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.Nullable;

import com.android.internal.app.IBatteryStats;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.BootCompleteCache;
import com.android.systemui.R;
import com.android.systemui.assist.AssistLogger;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.assist.ui.DefaultUiController;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dock.DockManager;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.flags.SystemPropertiesHelper;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.log.LogBuffer;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.BcSmartspaceDataPlugin;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.recents.OverviewProxyService;
import com.android.systemui.screenrecord.RecordingController;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.LockscreenShadeTransitionController;
import com.android.systemui.statusbar.NotificationClickNotifier;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.NotificationRemoteInputManager;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.notification.NotificationEntryManager;
import com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProvider;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.KeyguardDismissUtil;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.LockscreenWallpaper;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.phone.UnlockedScreenOffAnimationController;
import com.android.systemui.statusbar.phone.panelstate.PanelExpansionStateManager;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.theme.ThemeOverlayApplier;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.DeviceConfigProxy;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.settings.SecureSettings;
import com.android.systemui.util.wakelock.DelayedWakeLock;
import com.android.systemui.util.wakelock.WakeLock;
import com.android.wm.shell.tasksurfacehelper.TaskSurfaceHelper;
import com.android.wm.shell.legacysplitscreen.LegacySplitScreen;
import com.google.android.systemui.LiveWallpaperScrimController;
import com.google.android.systemui.NotificationLockscreenUserManagerGoogle;
import com.google.android.systemui.autorotate.AutorotateDataService;
import com.google.android.systemui.autorotate.DataLogger;
import com.google.android.systemui.columbus.ColumbusServiceWrapper;
import com.google.android.systemui.dreamliner.DockObserver;
import com.google.android.systemui.dreamliner.DreamlinerUtils;
import com.google.android.systemui.elmyra.ServiceConfigurationGoogle;
import com.google.android.systemui.gamedashboard.EntryPointController;
import com.google.android.systemui.gamedashboard.FpsController;
import com.google.android.systemui.gamedashboard.GameDashboardUiEventLogger;
import com.google.android.systemui.gamedashboard.GameModeDndController;
import com.google.android.systemui.gamedashboard.ScreenRecordController;
import com.google.android.systemui.gamedashboard.ShortcutBarController;
import com.google.android.systemui.gamedashboard.ToastController;
import com.google.android.systemui.power.PowerNotificationWarningsGoogleImpl;
import com.google.android.systemui.reversecharging.ReverseChargingController;
import com.google.android.systemui.reversecharging.ReverseChargingViewController;
import com.google.android.systemui.reversecharging.ReverseWirelessCharger;
import com.google.android.systemui.smartspace.KeyguardMediaViewController;
import com.google.android.systemui.smartspace.KeyguardSmartspaceController;
import com.google.android.systemui.smartspace.KeyguardZenAlarmViewController;
import com.google.android.systemui.smartspace.SmartSpaceController;
import com.google.android.systemui.statusbar.KeyguardIndicationControllerGoogle;
import com.google.android.systemui.statusbar.NotificationVoiceReplyManagerService;
import com.google.android.systemui.statusbar.notification.voicereplies.DebugNotificationVoiceReplyClient;
import com.google.android.systemui.statusbar.notification.voicereplies.NotificationVoiceReplyClient;
import com.google.android.systemui.statusbar.notification.voicereplies.NotificationVoiceReplyController;
import com.google.android.systemui.statusbar.notification.voicereplies.NotificationVoiceReplyLogger;
import com.google.android.systemui.statusbar.notification.voicereplies.NotificationVoiceReplyManager;
import com.google.android.systemui.statusbar.phone.WallpaperNotifier;
import com.google.android.systemui.theme.ThemeOverlayControllerGoogle;
import com.benzorom.systemui.GoogleServices;
import com.benzorom.systemui.assist.AssistGoogleModule;
import com.benzorom.systemui.assist.AssistManagerGoogle;
import com.benzorom.systemui.columbus.ColumbusModule;
import com.benzorom.systemui.elmyra.ElmyraModule;
import com.benzorom.systemui.log.dagger.NotifVoiceReplyLog;

import java.util.*;
import java.util.Optional;
import java.util.concurrent.Executor;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

/**
 * Provides dependencies for the root component of sysui injection.
 */
@Module(includes = {
        AssistGoogleModule.class,
        ColumbusModule.class,
        ElmyraModule.class})
public interface DependencyProviderGoogle {

    @Provides
    @SysUISingleton
    static KeyguardSmartspaceController provideKeyguardSmartspaceController(
            Context context,
            FeatureFlags featureFlags,
            KeyguardZenAlarmViewController zenController,
            KeyguardMediaViewController mediaController) {
        return new KeyguardSmartspaceController(
                context,
                featureFlags,
                zenController,
                mediaController);
    }

    @Provides
    @SysUISingleton
    static KeyguardZenAlarmViewController provideKeyguardZenAlarmViewController(
            Context context,
            BcSmartspaceDataPlugin plugin,
            ZenModeController zenModeController,
            AlarmManager alarmManager,
            NextAlarmController nextAlarmController,
            @Main Handler handler) {
        return new KeyguardZenAlarmViewController(
                context,
                plugin,
                zenModeController,
                alarmManager,
                nextAlarmController,
                handler);
    }

    @Provides
    @SysUISingleton
    static KeyguardMediaViewController provideKeyguardMediaViewController(
            Context context,
            BcSmartspaceDataPlugin plugin,
            @Main DelayableExecutor uiExecutor,
            NotificationMediaManager mediaManager,
            BroadcastDispatcher broadcastDispatcher) {
        return new KeyguardMediaViewController(
                context,
                plugin,
                uiExecutor,
                mediaManager,
                broadcastDispatcher);
    }

    @Provides
    @SysUISingleton
    static SmartSpaceController provideSmartSpaceController(
            Context context,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            @Main Handler handler,
            AlarmManager alarmManager,
            DumpManager dumpManager) {
        return new SmartSpaceController(
                context,
                keyguardUpdateMonitor,
                handler,
                alarmManager,
                dumpManager);
    }

    @Provides
    @SysUISingleton
    static NotificationLockscreenUserManagerGoogle provideLockscreenUserManagerGoogle(
            Context context,
            BroadcastDispatcher broadcastDispatcher,
            DevicePolicyManager devicePolicyManager,
            UserManager userManager,
            NotificationClickNotifier clickNotifier,
            KeyguardManager keyguardManager,
            StatusBarStateController statusBarStateController,
            @Main Handler mainHandler,
            DeviceProvisionedController deviceProvisionedController,
            KeyguardStateController keyguardStateController,
            Lazy<KeyguardBypassController> keyguardBypassController,
            SmartSpaceController smartSpaceController,
            DumpManager dumpManager) {
        return new NotificationLockscreenUserManagerGoogle(
                context,
                broadcastDispatcher,
                devicePolicyManager,
                userManager,
                clickNotifier,
                keyguardManager,
                statusBarStateController,
                mainHandler,
                deviceProvisionedController,
                keyguardStateController,
                keyguardBypassController,
                smartSpaceController,
                dumpManager);
    }

    @Provides
    @SysUISingleton
    static LiveWallpaperScrimController provideLiveWallpaperScrimController(
            LightBarController lightBarController,
            DozeParameters dozeParameters,
            AlarmManager alarmManager,
            KeyguardStateController keyguardStateController,
            DelayedWakeLock.Builder delayedWakeLockBuilder,
            Handler handler,
            @Nullable IWallpaperManager wallpaperManager,
            LockscreenWallpaper lockscreenWallpaper,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            ConfigurationController configurationController,
            DockManager dockManager,
            @Main Executor mainExecutor,
            UnlockedScreenOffAnimationController unlockedScreenOffAnimationController,
            PanelExpansionStateManager panelExpansionStateManager) {
        return new LiveWallpaperScrimController(
                lightBarController,
                dozeParameters,
                alarmManager,
                keyguardStateController,
                delayedWakeLockBuilder,
                handler,
                wallpaperManager,
                lockscreenWallpaper,
                keyguardUpdateMonitor,
                configurationController,
                dockManager,
                mainExecutor,
                unlockedScreenOffAnimationController,
                panelExpansionStateManager);
    }

    @Provides
    @SysUISingleton
    static DockObserver provideDockObserver(
            Context context,
            BroadcastDispatcher broadcastDispatcher,
            StatusBarStateController statusBarStateController,
            NotificationInterruptStateProvider notificationInterruptionState,
            ConfigurationController configurationController,
            @Main DelayableExecutor mainExecutor) {
        return new DockObserver(
                context,
                DreamlinerUtils.getInstance(context),
                broadcastDispatcher,
                statusBarStateController,
                notificationInterruptionState,
                configurationController,
                mainExecutor);
    }

    @Provides
    @SysUISingleton
    static GoogleServices provideGoogleServices(
            Context context,
            Lazy<ServiceConfigurationGoogle> serviceConfigurationGoogle,
            StatusBar statusBar,
            UiEventLogger uiEventLogger,
            Lazy<ColumbusServiceWrapper> columbusServiceLazy,
            AlarmManager alarmManager,
            AutorotateDataService autorotateDataService) {
        return new GoogleServices(
                context,
                serviceConfigurationGoogle,
                statusBar,
                uiEventLogger,
                columbusServiceLazy,
                alarmManager,
                autorotateDataService);
    }

    @Provides
    @SysUISingleton
    static AutorotateDataService provideAutorotateDataService(
            Context context,
            SensorManager sensorManager,
            DataLogger dataLogger,
            BroadcastDispatcher broadcastDispatcher,
            DeviceConfigProxy deviceConfig,
            @Main DelayableExecutor mainExecutor) {
        return new AutorotateDataService(
                context,
                sensorManager,
                dataLogger,
                broadcastDispatcher,
                deviceConfig,
                mainExecutor);
    }

    @Provides
    @SysUISingleton
    static DataLogger provideDataLogger(StatsManager statsManager) {
        return new DataLogger(statsManager);
    }

    @Provides
    @SysUISingleton
    static EntryPointController provideEntryPointController(
            Context context,
            AccessibilityManager accessibilityManager,
            BroadcastDispatcher broadcastDispatcher,
            CommandQueue commandQueue,
            GameModeDndController gameModeDndController,
            @Main Handler mainHandler,
            NavigationModeController navigationModeController,
            Optional<LegacySplitScreen> legacySplitScreenOptional,
            OverviewProxyService overviewProxyService,
            PackageManager packageManager,
            ShortcutBarController shortcutBarController,
            ToastController toast,
            GameDashboardUiEventLogger uiEventLogger,
            Optional<TaskSurfaceHelper> taskSurfaceHelper) {
        return new EntryPointController(
                context,
                accessibilityManager,
                broadcastDispatcher,
                commandQueue,
                gameModeDndController,
                mainHandler,
                navigationModeController,
                legacySplitScreenOptional,
                overviewProxyService,
                packageManager,
                shortcutBarController,
                toast,
                uiEventLogger,
                taskSurfaceHelper);
    }

    @Provides
    @SysUISingleton
    static GameDashboardUiEventLogger provideGameDashboardUiEventLogger(
            UiEventLogger uiEventLogger) {
        return new GameDashboardUiEventLogger(uiEventLogger);
    }

    @Provides
    @SysUISingleton
    static GameModeDndController provideGameModeDndController(
            Context context,
            NotificationManager notificationManager,
            BroadcastDispatcher broadcastDispatcher) {
        return new GameModeDndController(context, notificationManager, broadcastDispatcher);
    }

    @Provides
    @SysUISingleton
    static ShortcutBarController provideShortcutBarController(
            Context context,
            WindowManager windowManager,
            FpsController fpsController,
            ConfigurationController configurationController,
            @Main Handler screenshotHandler,
            ScreenRecordController screenRecordController,
            Optional<TaskSurfaceHelper> screenshotController,
            GameDashboardUiEventLogger uiEventLogger,
            ToastController toast) {
        return new ShortcutBarController(
                context,
                windowManager,
                fpsController,
                configurationController,
                screenshotHandler,
                screenRecordController,
                screenshotController,
                uiEventLogger,
                toast);
    }

    @Provides
    @SysUISingleton
    static FpsController provideFpsController(@Main Executor executor) {
        return new FpsController(executor);
    }

    @Provides
    @SysUISingleton
    static ScreenRecordController provideScreenRecordController(
            RecordingController controller,
            @Main Handler mainHandler,
            KeyguardDismissUtil keyguardDismissUtil,
            Context context,
            ToastController toast) {
        return new ScreenRecordController(
                controller,
                mainHandler,
                keyguardDismissUtil,
                context,
                toast);
    }

    @Provides
    @SysUISingleton
    static ToastController provideToastController(
            Context context,
            ConfigurationController configurationController,
            WindowManager windowManager,
            UiEventLogger uiEventLogger,
            NavigationModeController navigationModeController) {
        return new ToastController(
                context,
                configurationController,
                windowManager,
                uiEventLogger,
                navigationModeController);
    }

    @Provides
    @SysUISingleton
    static ThemeOverlayControllerGoogle provideThemeOverlayControllerGoogle(
            Context context,
            BroadcastDispatcher broadcastDispatcher,
            @Background Handler bgHandler,
            @Main Executor mainExecutor,
            @Background Executor bgExecutor,
            ThemeOverlayApplier themeOverlayApplier,
            SecureSettings secureSettings,
            SystemPropertiesHelper systemProperties,
            @Main Resources resources,
            WallpaperManager wallpaperManager,
            UserManager userManager,
            DumpManager dumpManager,
            DeviceProvisionedController deviceProvisionedController,
            UserTracker userTracker,
            FeatureFlags featureFlags,
            WakefulnessLifecycle wakefulnessLifecycle,
            ConfigurationController configurationController) {
        return new ThemeOverlayControllerGoogle(
                context,
                broadcastDispatcher,
                bgHandler,
                mainExecutor,
                bgExecutor,
                themeOverlayApplier,
                secureSettings,
                systemProperties,
                resources,
                wallpaperManager,
                userManager,
                dumpManager,
                deviceProvisionedController,
                userTracker,
                featureFlags,
                wakefulnessLifecycle,
                configurationController);
    }

    @Provides
    @SysUISingleton
    static NotificationVoiceReplyManagerService provideVoiceReplyManagerService(
            NotificationVoiceReplyManager.Initializer managerInitializer,
            NotificationVoiceReplyLogger logger) {
        return new NotificationVoiceReplyManagerService(managerInitializer, logger);
    }

    @Provides
    @SysUISingleton
    static WallpaperNotifier provideWallpaperNotifier(
            Context context,
            NotificationEntryManager entryManager,
            BroadcastDispatcher broadcastDispatcher) {
        return new WallpaperNotifier(context, entryManager, broadcastDispatcher);
    }

    @Provides
    @SysUISingleton
    static NotificationVoiceReplyManager.Initializer provideVoiceReplyController(
            NotificationEntryManager notificationEntryManager,
            NotificationLockscreenUserManager lockscreenUserManager,
            NotificationRemoteInputManager notificationRemoteInputManager,
            LockscreenShadeTransitionController shadeTransitionController,
            NotificationShadeWindowController notifShadeWindowController,
            StatusBarKeyguardViewManager statusBarKeyguardViewManager,
            StatusBar statusBar,
            SysuiStatusBarStateController statusBarStateController,
            HeadsUpManager headsUpManager,
            PowerManager powerManager,
            Context context,
            NotificationVoiceReplyLogger logger) {
        return new NotificationVoiceReplyController(
                notificationEntryManager,
                lockscreenUserManager,
                notificationRemoteInputManager,
                shadeTransitionController,
                notifShadeWindowController,
                statusBarKeyguardViewManager,
                statusBar,
                statusBarStateController,
                headsUpManager,
                powerManager,
                context,
                logger);
    }

    @Provides
    @SysUISingleton
    static Optional<NotificationVoiceReplyClient> provideVoiceReplyClient(
            BroadcastDispatcher broadcastDispatcher,
            NotificationLockscreenUserManager lockscreenUserManager,
            NotificationVoiceReplyManager.Initializer voiceReplyInitializer) {
        return Optional.of(
                new DebugNotificationVoiceReplyClient(
                        broadcastDispatcher,
                        lockscreenUserManager,
                        voiceReplyInitializer));
    }

    @Provides
    @SysUISingleton
    static NotificationVoiceReplyLogger provideNotificationVoiceReplyLogger(
            @NotifVoiceReplyLog LogBuffer logBuffer,
            UiEventLogger eventLogger) {
        return new NotificationVoiceReplyLogger(logBuffer, eventLogger);
    }

    @Provides
    @SysUISingleton
    static ReverseChargingController provideReverseChargingController(
            Context context,
            BroadcastDispatcher broadcastDispatcher,
            Optional<ReverseWirelessCharger> rtxChargerManagerOptional,
            AlarmManager alarmManager,
            Optional<UsbManager> usbManagerOptional,
            @Main Executor mainExecutor,
            @Background Executor bgExecutor,
            BootCompleteCache bootCompleteCache,
            IThermalService thermalService) {
        return new ReverseChargingController(
                context,
                broadcastDispatcher,
                rtxChargerManagerOptional,
                alarmManager,
                usbManagerOptional,
                mainExecutor,
                bgExecutor,
                bootCompleteCache,
                thermalService);
    }

    @Provides
    @SysUISingleton
    static ReverseChargingViewController provideReverseChargingViewController(
            Context context,
            BatteryController batteryController,
            Lazy<StatusBar> statusBarLazy,
            StatusBarIconController statusBarIconController,
            BroadcastDispatcher broadcastDispatcher,
            @Main Executor mainExecutor,
            KeyguardIndicationControllerGoogle keyguardIndicationController) {
        return new ReverseChargingViewController(
                context,
                batteryController,
                statusBarLazy,
                statusBarIconController,
                broadcastDispatcher,
                mainExecutor,
                keyguardIndicationController);
    }

    @Provides
    @SysUISingleton
    static KeyguardIndicationControllerGoogle provideKeyguardIndicationControllerGoogle(
            Context context,
            WakeLock.Builder wakeLockBuilder,
            KeyguardStateController keyguardStateController,
            StatusBarStateController statusBarStateController,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            DockManager dockManager,
            BroadcastDispatcher broadcastDispatcher,
            DevicePolicyManager devicePolicyManager,
            IBatteryStats iBatteryStats,
            UserManager userManager,
            TunerService tunerService,
            DeviceConfigProxy deviceConfig,
            @Main DelayableExecutor executor,
            FalsingManager falsingManager,
            LockPatternUtils lockPatternUtils,
            IActivityManager iActivityManager,
            KeyguardBypassController keyguardBypassController) {
        return new KeyguardIndicationControllerGoogle(
                context,
                wakeLockBuilder,
                keyguardStateController,
                statusBarStateController,
                keyguardUpdateMonitor,
                dockManager,
                broadcastDispatcher,
                devicePolicyManager,
                iBatteryStats,
                userManager,
                tunerService,
                deviceConfig,
                executor,
                falsingManager,
                lockPatternUtils,
                iActivityManager,
                keyguardBypassController);
    }

    @Provides
    @SysUISingleton
    static PowerNotificationWarningsGoogleImpl provideWarningsUiGoogle(
            Context context,
            ActivityStarter activityStarter,
            BroadcastDispatcher broadcastDispatcher,
            UiEventLogger uiEventLogger) {
        return new PowerNotificationWarningsGoogleImpl(
                context,
                activityStarter,
                broadcastDispatcher,
                uiEventLogger);
    }
}
